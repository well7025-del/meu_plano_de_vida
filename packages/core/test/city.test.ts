import { strict as assert } from 'node:assert';
import { test } from 'node:test';
import { runCity } from '../src/city.js';

test('o bairro simulado mantem a ocupacao alvo e nao duplica carros na mesma vaga', () => {
  const r = runCity({ durationS: 900, penetration: 0.5, seed: 11 });
  const ocupacao = 1 - r.freeSpots / r.totalSpots;
  assert.ok(ocupacao > 0.85 && ocupacao < 0.97, `ocupacao fora do esperado: ${ocupacao}`);
  assert.ok(r.totalSpots > 1000);
});

test('sem ninguem usando o app, nao ha evento nem ponto no mapa', () => {
  const r = runCity({ durationS: 1800, penetration: 0, seed: 3 });
  assert.equal(r.appDrivers, 0);
  assert.equal(r.events, 0);
  assert.equal(r.avgDots, 0);
});

test('mais cobertura gera mais eventos e mais pontos', () => {
  const baixa = runCity({ durationS: 2 * 3600, penetration: 0.1, seed: 5 });
  const alta = runCity({ durationS: 2 * 3600, penetration: 0.6, seed: 5 });
  assert.ok(alta.events > baixa.events * 2, `${alta.events} vs ${baixa.events}`);
  assert.ok(alta.avgDots > baixa.avgDots);
});

test('a precisao do mapa quase nao depende da cobertura — o que falta e volume', () => {
  const baixa = runCity({ durationS: 2 * 3600, penetration: 0.1, seed: 5 });
  const alta = runCity({ durationS: 2 * 3600, penetration: 0.6, seed: 5 });
  assert.ok(baixa.precision > 0.8, `precisao baixa demais com pouca cobertura: ${baixa.precision}`);
  assert.ok(Math.abs(alta.precision - baixa.precision) < 0.15);
});

test('a utilidade para quem procura vaga cresce com a cobertura', () => {
  const baixa = runCity({ durationS: 3 * 3600, penetration: 0.1, seed: 5 });
  const alta = runCity({ durationS: 3 * 3600, penetration: 0.6, seed: 5 });
  const uBaixa = baixa.searches > 0 ? baixa.withTrueSuggestion / baixa.searches : 0;
  const uAlta = alta.searches > 0 ? alta.withTrueSuggestion / alta.searches : 0;
  assert.ok(uAlta > uBaixa + 0.2, `utilidade ${uAlta} vs ${uBaixa}`);
});
