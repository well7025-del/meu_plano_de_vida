/**
 * Simulador de campo.
 *
 * Gera uma cidade sintetica com N motoristas, sabe exatamente onde e quando
 * cada um estacionou e saiu (a "verdade"), roda o motor em cima dos traces de
 * GPS e mede o que importa: quantos eventos reais foram pegos, quantos foram
 * inventados e quantos metros o ponto no mapa erra.
 *
 *   npm run simulate
 */
import { ParkingDetector } from './detector.js';
import { SpotIndex } from './availability.js';
import { TraceBuilder, rng } from './trace.js';
import { destination, distanceM } from './geo.js';
import type { LocationSample, ParkingEvent, ParkingEventKind } from './types.js';

interface Truth {
  kind: ParkingEventKind;
  lat: number;
  lon: number;
  t: number;
}

const CENTER = { lat: -23.5613, lon: -46.6565 };
const T0 = Date.UTC(2026, 0, 15, 8, 0, 0);
const DRIVERS = 60;

function buildDriver(seed: number): { samples: LocationSample[]; truth: Truth[] } {
  const rand = rng(seed);
  const truth: Truth[] = [];

  // Cada motorista comeca em um ponto aleatorio ate 1,5 km do centro.
  const start = destination(CENTER, rand() * 360, rand() * 1500);
  const b = new TraceBuilder({ t: T0 + Math.floor(rand() * 3600) * 1000, ...start }, seed);

  const noise = { noiseM: 2 + rand() * 6, accuracyM: 5 + rand() * 15 };

  // 1) chega de carro e estaciona
  b.drive(240 + Math.floor(rand() * 300), rand() * 360, { ...noise, headingJitterDeg: 8 });
  const parked = b.position;
  truth.push({ kind: 'arrival', lat: parked.lat, lon: parked.lon, t: b.time });
  b.still(120 + Math.floor(rand() * 180), noise);
  b.walk(90 + Math.floor(rand() * 180), rand() * 360, noise);

  // 2) fica um tempo no destino
  b.still(600 + Math.floor(rand() * 2400), noise);

  // 3) volta a pe e vai embora: a vaga e liberada
  b.walk(90 + Math.floor(rand() * 180), rand() * 360, noise);
  b.still(20 + Math.floor(rand() * 60), noise);
  const leaving = b.position;
  truth.push({ kind: 'departure', lat: leaving.lat, lon: leaving.lon, t: b.time });
  b.drive(300 + Math.floor(rand() * 300), rand() * 360, { ...noise, headingJitterDeg: 8 });

  return { samples: b.build(), truth };
}

/** Motoristas que so passam pela regiao: transito, semaforo, nada de estacionar. */
function buildPassThrough(seed: number): LocationSample[] {
  const rand = rng(seed);
  const start = destination(CENTER, rand() * 360, rand() * 1500);
  const b = new TraceBuilder({ t: T0 + Math.floor(rand() * 3600) * 1000, ...start }, seed);
  for (let i = 0; i < 6; i++) {
    b.drive(120 + Math.floor(rand() * 180), rand() * 360, { headingJitterDeg: 10 });
    b.still(30 + Math.floor(rand() * 60)); // semaforo / fila
  }
  return b.build();
}

function match(event: ParkingEvent, truth: Truth[]): { ok: boolean; errorM: number } {
  let best = Infinity;
  for (const tr of truth) {
    if (tr.kind !== event.kind) continue;
    if (Math.abs(tr.t - event.t) > 5 * 60 * 1000) continue;
    best = Math.min(best, distanceM(event, tr));
  }
  return { ok: best <= 80, errorM: best };
}

function main(): void {
  const index = new SpotIndex();
  const allTruth: Truth[] = [];
  const detected: ParkingEvent[] = [];
  const errors: number[] = [];
  let falsePositives = 0;

  for (let i = 0; i < DRIVERS; i++) {
    const { samples, truth } = buildDriver(1000 + i);
    allTruth.push(...truth);
    const det = new ParkingDetector();
    for (const s of samples) {
      for (const e of det.push(s)) {
        detected.push(e);
        index.add(e);
        const m = match(e, truth);
        if (m.ok) errors.push(m.errorM);
        else falsePositives++;
      }
    }
  }

  let noiseEvents = 0;
  for (let i = 0; i < 20; i++) {
    const det = new ParkingDetector();
    for (const s of buildPassThrough(9000 + i)) noiseEvents += det.push(s).length;
  }

  const truePositives = errors.length;
  const recall = truePositives / allTruth.length;
  const precision = truePositives / Math.max(1, detected.length);
  errors.sort((a, b) => a - b);
  const p50 = errors[Math.floor(errors.length * 0.5)] ?? 0;
  const p90 = errors[Math.floor(errors.length * 0.9)] ?? 0;

  const now = T0 + 90 * 60 * 1000;
  const spots = index.query(CENTER, 2000, now);

  console.log('\n=== Vagas — simulacao de campo ===');
  console.log(`motoristas simulados......... ${DRIVERS}`);
  console.log(`eventos reais (verdade)...... ${allTruth.length}`);
  console.log(`eventos detectados........... ${detected.length}`);
  console.log(`  saidas..................... ${detected.filter((e) => e.kind === 'departure').length}`);
  console.log(`  chegadas................... ${detected.filter((e) => e.kind === 'arrival').length}`);
  console.log(`cobertura (recall)........... ${(recall * 100).toFixed(1)}%`);
  console.log(`precisao..................... ${(precision * 100).toFixed(1)}%`);
  console.log(`falsos positivos............. ${falsePositives}`);
  console.log(`erro do ponto: mediana....... ${p50.toFixed(1)} m`);
  console.log(`erro do ponto: p90........... ${p90.toFixed(1)} m`);
  console.log(`trajetos so de passagem...... 20 percursos, ${noiseEvents} eventos gerados`);
  console.log(`\npontos no mapa 90 min depois. ${spots.length}`);
  for (const s of spots.slice(0, 8)) {
    const idadeMin = ((now - s.lastEventT) / 60000).toFixed(0);
    console.log(
      `  ${s.tier.padEnd(5)} p=${s.probability.toFixed(2)} ` +
        `(${s.lat.toFixed(5)}, ${s.lon.toFixed(5)}) ha ${idadeMin} min, ${s.support} evento(s)`,
    );
  }
  console.log('');
}

main();
