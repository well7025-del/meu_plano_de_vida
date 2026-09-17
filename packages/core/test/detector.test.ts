import { strict as assert } from 'node:assert';
import { test } from 'node:test';
import { ParkingDetector } from '../src/detector.js';
import { TraceBuilder } from '../src/trace.js';
import type { LocationSample, ParkingEvent } from '../src/types.js';
import { distanceM } from '../src/geo.js';

const T0 = Date.UTC(2026, 0, 15, 9, 0, 0);
const SP = { lat: -23.5613, lon: -46.6565 }; // Av. Paulista

function run(samples: LocationSample[], config = {}): ParkingEvent[] {
  const det = new ParkingDetector(config);
  const events: ParkingEvent[] = [];
  for (const s of samples) events.push(...det.push(s));
  return events;
}

test('caminhada -> dirigir e detectado como saida (vaga liberada)', () => {
  const trace = new TraceBuilder({ t: T0, ...SP })
    .still(60)
    .walk(120, 90)
    .still(20) // destravando o carro, entrando
    .drive(300, 0)
    .build();

  const events = run(trace);
  const departures = events.filter((e) => e.kind === 'departure');
  assert.equal(departures.length, 1, 'esperava exatamente uma saida');

  const dep = departures[0]!;
  assert.ok(dep.confidence >= 0.6, `confianca baixa: ${dep.confidence}`);
  assert.ok(dep.signals.driveDistanceM >= 200);
  assert.ok(dep.signals.walkDurationS >= 25);
});

test('o ponto ancora da saida fica onde o carro estava, nao onde ele chegou', () => {
  const builder = new TraceBuilder({ t: T0, ...SP }).still(60).walk(120, 90);
  const carSpot = builder.position;
  const trace = builder.still(20).drive(600, 0).build();

  const dep = run(trace).find((e) => e.kind === 'departure');
  assert.ok(dep, 'nenhuma saida detectada');
  const err = distanceM(dep!, carSpot);
  assert.ok(err < 60, `ancora ${err.toFixed(0)} m longe do ponto real`);
});

test('dirigir -> parar -> caminhar e detectado como chegada (vaga ocupada)', () => {
  const builder = new TraceBuilder({ t: T0, ...SP }).drive(420, 45);
  const parkedAt = builder.position;
  const trace = builder
    .still(180) // desligando o carro, pegando as coisas
    .walk(120, 180)
    .build();

  const arrivals = run(trace).filter((e) => e.kind === 'arrival');
  assert.equal(arrivals.length, 1);
  const arr = arrivals[0]!;
  assert.ok(distanceM(arr, parkedAt) < 60);
  assert.ok(arr.signals.stopDurationS >= 90);
});

test('parada de semaforo nao vira chegada', () => {
  const trace = new TraceBuilder({ t: T0, ...SP })
    .drive(300, 0)
    .still(45) // semaforo longo
    .drive(300, 0)
    .still(40)
    .drive(240, 0)
    .build();

  const events = run(trace);
  assert.equal(events.filter((e) => e.kind === 'arrival').length, 0);
});

test('transito parado nao vira chegada mesmo passando do tempo minimo, se o carro segue', () => {
  const trace = new TraceBuilder({ t: T0, ...SP })
    .drive(300, 0)
    .still(150) // fila longa
    .drive(400, 0)
    .build();

  assert.equal(run(trace).filter((e) => e.kind === 'arrival').length, 0);
});

test('parar e voltar a dirigir sem sair do carro nao gera chegada', () => {
  const trace = new TraceBuilder({ t: T0, ...SP })
    .drive(300, 0)
    .still(200)
    .drive(300, 0)
    .still(200)
    .drive(300, 0)
    .build();

  assert.equal(run(trace).filter((e) => e.kind === 'arrival').length, 0);
});

test('atravessar a rua a pe e voltar a andar nao gera saida', () => {
  const trace = new TraceBuilder({ t: T0, ...SP })
    .walk(180, 90)
    .leg(20, 5.0, 90, 'walking') // correu para atravessar: pico curto
    .walk(180, 90)
    .build();

  assert.equal(run(trace).filter((e) => e.kind === 'departure').length, 0);
});

test('caminhar e pegar carona curta nao confirma saida (distancia insuficiente)', () => {
  const trace = new TraceBuilder({ t: T0, ...SP })
    .walk(120, 90)
    .drive(20, 0, { speed: 6 }) // 120 m: abaixo do minimo
    .walk(120, 90)
    .build();

  assert.equal(run(trace).filter((e) => e.kind === 'departure').length, 0);
});

test('ciclo completo: estaciona, anda, volta e sai — duas deteccoes', () => {
  const b = new TraceBuilder({ t: T0, ...SP });
  const trace = b
    .drive(420, 45)
    .still(200)
    .walk(180, 180)
    .still(600) // no destino
    .walk(180, 0)
    .still(30)
    .drive(420, 225)
    .build();

  const events = run(trace);
  assert.equal(events.filter((e) => e.kind === 'arrival').length, 1);
  assert.equal(events.filter((e) => e.kind === 'departure').length, 1);

  const dep = events.find((e) => e.kind === 'departure')!;
  assert.ok(
    dep.signals.penalties.includes('bonus:own_prior_arrival'),
    'saida no mesmo ponto da chegada deveria ganhar reforco',
  );
  assert.ok(dep.confidence > 0.8);
});

test('zona de exclusao (garagem de casa) suprime o evento', () => {
  const b = new TraceBuilder({ t: T0, ...SP }).still(60).walk(120, 90);
  const home = b.position;
  const trace = b.still(20).drive(420, 0).build();

  const events = run(trace, {
    exclusionZones: [{ id: 'casa', lat: home.lat, lon: home.lon, radiusM: 80 }],
  });
  assert.equal(events.length, 0);
});

test('estacionamento privado derruba a confianca abaixo do corte', () => {
  const trace = new TraceBuilder({ t: T0, ...SP })
    .drive(420, 45)
    .still(200)
    .walk(150, 180)
    .build();

  const semContexto = run(trace);
  assert.equal(semContexto.length, 1);

  const comContexto = run(trace, { contextProvider: () => ({ insidePrivateLot: true }) });
  assert.equal(comContexto.length, 0);
});

test('ponto de onibus reduz a confianca sem zerar', () => {
  const trace = new TraceBuilder({ t: T0, ...SP })
    .still(60)
    .walk(150, 90)
    .still(20)
    .drive(600, 0)
    .build();

  const base = run(trace)[0]!;
  const comPonto = run(trace, { contextProvider: () => ({ nearTransitStop: true }) })[0];
  assert.ok(comPonto, 'evento deveria continuar existindo, so mais fraco');
  assert.ok(comPonto!.confidence < base.confidence * 0.7);
});

test('aparelho sem velocidade do GPS ainda detecta (velocidade derivada da posicao)', () => {
  const trace = new TraceBuilder({ t: T0, ...SP })
    .walk(150, 90, { omitSpeed: true, activityHint: null })
    .still(20, { omitSpeed: true, activityHint: null })
    .drive(420, 0, { omitSpeed: true, activityHint: null })
    .build();

  assert.equal(run(trace).filter((e) => e.kind === 'departure').length, 1);
});

test('amostras muito imprecisas sao descartadas', () => {
  const trace = new TraceBuilder({ t: T0, ...SP })
    .walk(150, 90, { accuracyM: 120 })
    .drive(420, 0, { accuracyM: 120 })
    .build();

  assert.equal(run(trace).length, 0);
});

test('o evento carrega o horario do fato, nao o da confirmacao', () => {
  const trace = new TraceBuilder({ t: T0, ...SP }).walk(150, 90).drive(600, 0).build();
  const dep = run(trace).find((e) => e.kind === 'departure')!;
  assert.ok(dep.confirmedAt > dep.t, 'confirmacao deve vir depois do fato');
  assert.ok((dep.confirmedAt - dep.t) / 1000 >= 45, 'confirmacao exige trecho dirigido');
});
