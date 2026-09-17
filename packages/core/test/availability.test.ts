import { strict as assert } from 'node:assert';
import { test } from 'node:test';
import { SpotIndex, decay, estimate, tierOf } from '../src/availability.js';
import type { StoredEvent } from '../src/availability.js';
import { cellId, distanceM } from '../src/geo.js';
import type { ParkingEvent } from '../src/types.js';

const NOW = Date.UTC(2026, 0, 15, 12, 0, 0);
const SP = { lat: -23.5613, lon: -46.6565 };

function ev(kind: 'departure' | 'arrival', agoS: number, confidence = 0.9, p = SP): StoredEvent {
  return { kind, lat: p.lat, lon: p.lon, t: NOW - agoS * 1000, confidence, cell: cellId(p, 40) };
}

function fullEvent(kind: 'departure' | 'arrival', agoS: number, p = SP): ParkingEvent {
  return {
    kind,
    lat: p.lat,
    lon: p.lon,
    t: NOW - agoS * 1000,
    confirmedAt: NOW - agoS * 1000 + 60_000,
    confidence: 0.9,
    radiusM: 20,
    signals: {
      walkDurationS: 60,
      walkDistanceM: 80,
      driveDurationS: 300,
      driveDistanceM: 2000,
      stopDurationS: 0,
      anchorAccuracyM: 8,
      penalties: [],
    },
  };
}

test('decaimento cai pela metade a cada meia-vida', () => {
  assert.equal(decay(0, 600), 1);
  assert.ok(Math.abs(decay(600, 600) - 0.5) < 1e-9);
  assert.ok(Math.abs(decay(1200, 600) - 0.25) < 1e-9);
});

test('uma saida recente gera probabilidade alta; uma antiga, baixa', () => {
  const recente = estimate([ev('departure', 30)], NOW).probability;
  const antiga = estimate([ev('departure', 45 * 60)], NOW).probability;
  assert.ok(recente > 0.55, `recente=${recente}`);
  assert.ok(antiga < 0.1, `antiga=${antiga}`);
  assert.ok(recente > antiga);
});

test('chegada cancela saida: alguem ja ocupou a vaga', () => {
  const soSaida = estimate([ev('departure', 60)], NOW).probability;
  const comChegada = estimate([ev('departure', 60), ev('arrival', 30)], NOW).probability;
  assert.ok(comChegada < soSaida * 0.3, `${comChegada} vs ${soSaida}`);
});

test('lambda nunca fica negativo', () => {
  const { lambda, probability } = estimate(
    [ev('arrival', 10), ev('arrival', 20), ev('arrival', 30)],
    NOW,
  );
  assert.equal(lambda, 0);
  assert.equal(probability, 0);
});

test('varias saidas na mesma celula somam confianca', () => {
  const uma = estimate([ev('departure', 60)], NOW).probability;
  const tres = estimate([ev('departure', 60), ev('departure', 90), ev('departure', 120)], NOW)
    .probability;
  assert.ok(tres > uma);
  assert.ok(tres < 1);
});

test('confianca do evento pesa na probabilidade', () => {
  const forte = estimate([ev('departure', 60, 0.95)], NOW).probability;
  const fraca = estimate([ev('departure', 60, 0.5)], NOW).probability;
  assert.ok(forte > fraca);
});

test('eventos acima da idade maxima sao ignorados', () => {
  const { probability } = estimate([ev('departure', 2 * 60 * 60)], NOW, { maxAgeS: 3600 });
  assert.equal(probability, 0);
});

test('faixas da UI', () => {
  assert.equal(tierOf(0.8), 'alta');
  assert.equal(tierOf(0.45), 'media');
  assert.equal(tierOf(0.2), 'baixa');
});

test('o indice so devolve pontos dentro do raio pedido', () => {
  const idx = new SpotIndex();
  const longe = { lat: SP.lat + 0.02, lon: SP.lon };
  idx.add(fullEvent('departure', 60));
  idx.add(fullEvent('departure', 60, longe));

  const perto = idx.query(SP, 300, NOW);
  assert.equal(perto.length, 1);
  assert.ok(distanceM(perto[0]!, SP) < 100);

  assert.equal(idx.query(SP, 5000, NOW).length, 2);
});

test('o indice ordena por probabilidade e classifica em faixas', () => {
  const idx = new SpotIndex();
  const outraCelula = { lat: SP.lat + 0.0012, lon: SP.lon };
  idx.add(fullEvent('departure', 30));
  idx.add(fullEvent('departure', 50 * 60, outraCelula));

  const r = idx.query(SP, 1000, NOW);
  assert.equal(r.length, 1, 'ponto velho deveria cair abaixo do corte');
  assert.equal(r[0]!.tier, 'alta');
});

test('prune remove eventos vencidos', () => {
  const idx = new SpotIndex({ maxAgeS: 600 });
  idx.add(fullEvent('departure', 30));
  idx.add(fullEvent('departure', 3600));
  assert.equal(idx.size, 2);
  assert.equal(idx.prune(NOW), 1);
  assert.equal(idx.size, 1);
});

test('o ponto desenhado fica na media ponderada das saidas, nao no centro da celula', () => {
  const idx = new SpotIndex();
  const a = { lat: SP.lat + 0.0001, lon: SP.lon + 0.0001 };
  idx.add(fullEvent('departure', 30, a));
  const [spot] = idx.query(SP, 500, NOW);
  assert.ok(spot);
  assert.ok(distanceM(spot!, a) < 5, 'ancora deveria coincidir com a saida observada');
});

test('oferta de base do historico sustenta uma probabilidade mesmo sem evento recente', () => {
  const semHistorico = estimate([], NOW).probability;
  const comHistorico = estimate([], NOW, { baselineLambda: 0.7 }).probability;
  assert.equal(semHistorico, 0);
  assert.ok(comHistorico > 0.4 && comHistorico < 0.6);
});
