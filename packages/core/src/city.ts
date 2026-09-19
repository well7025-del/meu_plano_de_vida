/**
 * Simulador de bairro com mundo compartilhado.
 *
 * Diferente de `simulate.ts`, que roda motoristas isolados para medir o motor,
 * aqui existe UM estoque de vagas disputado por todos: quando um carro sai, a
 * vaga fica livre de verdade e outro pode ocupa-la. E o unico jeito de medir o
 * que realmente importa para o negocio — **quantos motoristas precisam ter o
 * app para o mapa ser util** — porque as vagas continuam abrindo e fechando
 * mesmo quando ninguem esta olhando.
 */
import { ParkingDetector } from './detector.js';
import { SpotIndex, type SpotEstimate } from './availability.js';
import { rng } from './trace.js';
import type { LatLon } from './geo.js';
import type { LocationSample } from './types.js';

export interface CityOptions {
  /** Distancia entre ruas paralelas, em metros. */
  blockM: number;
  /** Quadras por lado. `blockM * n` da o lado do bairro. */
  blocks: number;
  /** Espacamento entre vagas no meio-fio, em metros. */
  spotGapM: number;
  /** Motoristas em movimento no bairro (com e sem app). */
  drivers: number;
  /** Fracao dos motoristas que carrega o app (0..1). */
  penetration: number;
  /** Ocupacao alvo das vagas (0..1). */
  occupancy: number;
  /** Duracao simulada, em segundos. */
  durationS: number;
  seed: number;
  origin: LatLon;
  /** Raio em que o motorista considera uma sugestao util, em metros. */
  suggestionRadiusM: number;
  /** Distancia do destino em que ele comeca a procurar, em metros. */
  searchStartM: number;
  /**
   * Descarta as chegadas antes de alimentar o mapa.
   *
   * Serve para um experimento especifico: quanto da qualidade do mapa vem da
   * confirmacao colaborativa ("alguem ocupou, apaga o ponto") e quanto vem
   * apenas do decaimento temporal? Ver docs/REVISAO-IDEIAS.md.
   */
  ignoreArrivals?: boolean;
  /**
   * Modelo realista de busca: o motorista nao "reserva" a vaga de longe. Ele
   * dirige ate a regiao do destino e so entao circula procurando, ocupando a
   * primeira vaga livre que enxerga. E o que permite medir o tempo de procura
   * — a metrica norte do produto — e o que faz aparecer a corrida pela vaga.
   */
  cruising?: boolean;
  /** Com `cruising`, motoristas com o app dirigem ate o ponto verde. */
  followMap?: boolean;
  /** Distancia em que o motorista enxerga uma vaga livre ao circular (m). */
  sightM?: number;
  /** Raio em torno do destino onde ele aceita uma sugestao do app (m). */
  followRadiusM?: number;
  /** Teto para a circulacao, em segundos, para a simulacao nao travar. */
  maxCruiseS?: number;
}

export const DEFAULT_CITY: CityOptions = {
  blockM: 120,
  blocks: 6,
  spotGapM: 15,
  drivers: 60,
  penetration: 0.4,
  occupancy: 0.92,
  durationS: 4 * 3600,
  seed: 7,
  origin: { lat: -23.5613, lon: -46.6565 },
  suggestionRadiusM: 150,
  searchStartM: 300,
  cruising: false,
  followMap: false,
  sightM: 20,
  followRadiusM: 150,
  maxCruiseS: 900,
};

export interface CityResult {
  penetration: number;
  drivers: number;
  appDrivers: number;
  /** Eventos detectados no periodo. */
  events: number;
  /** Media de pontos exibidos no mapa. */
  avgDots: number;
  /** Fracao dos pontos exibidos que tinham vaga livre real a <=40 m. */
  precision: number;
  /** Quantas vezes um motorista comecou a procurar vaga. */
  searches: number;
  /** Dessas, quantas o app tinha alguma sugestao perto do destino. */
  withSuggestion: number;
  /** Dessas, quantas a sugestao era verdadeira. */
  withTrueSuggestion: number;
  /** Vagas livres reais no fim da simulacao. */
  freeSpots: number;
  totalSpots: number;
  /** Tempo medio de procura (s), so com `cruising`. */
  cruiseApp: number;
  cruiseNoApp: number;
  parksApp: number;
  parksNoApp: number;
  /** Vezes em que um motorista seguiu um ponto verde e nao achou nada la. */
  wastedTrips: number;
  chases: number;
}

interface Spot {
  x: number;
  y: number;
  lat: number;
  lon: number;
  car: object | 'estatico' | null;
}

type Phase = 'away' | 'walk_to_car' | 'board' | 'drive' | 'cruise' | 'parked' | 'walk_away';

interface Waypoint {
  x: number;
  y: number;
}

const M_PER_DEG_LAT = 111_320;

class Driver {
  x = 0;
  y = 0;
  speed = 0;
  phase: Phase = 'away';
  until = 0;
  path: Waypoint[] = [];
  spot: Spot | null = null;
  target: Spot | null = null;
  detector: ParkingDetector | null;
  /** Marcado quando o motorista ja foi contado como "procurando" nesta viagem. */
  searchCounted = false;
  /** Ponto verde que ele esta perseguindo nesta viagem, se houver. */
  chasing: Waypoint | null = null;
  destination: Waypoint | null = null;
  cruiseStart = 0;

  constructor(
    readonly id: number,
    readonly hasApp: boolean,
  ) {
    this.detector = hasApp ? new ParkingDetector() : null;
  }
}

/** Roda a simulacao e devolve as metricas de cobertura. */
export function runCity(options: Partial<CityOptions> = {}): CityResult {
  const cfg = { ...DEFAULT_CITY, ...options };
  const rand = rng(cfg.seed);
  const size = cfg.blockM * cfg.blocks;
  const lines = Array.from({ length: cfg.blocks + 1 }, (_, i) => i * cfg.blockM);

  const mPerDegLon = M_PER_DEG_LAT * Math.cos((cfg.origin.lat * Math.PI) / 180);
  const toLatLon = (x: number, y: number) => ({
    lat: cfg.origin.lat + y / M_PER_DEG_LAT,
    lon: cfg.origin.lon + x / mPerDegLon,
  });

  // --- vagas de meio-fio nos dois lados de cada trecho -------------------
  const spots: Spot[] = [];
  const addSpot = (x: number, y: number) => {
    const ll = toLatLon(x, y);
    spots.push({ x, y, lat: ll.lat, lon: ll.lon, car: null });
  };
  const CURB = 5;
  for (const y of lines) {
    for (let x = cfg.spotGapM; x < size; x += cfg.spotGapM) {
      const off = x % cfg.blockM;
      if (off < cfg.spotGapM || off > cfg.blockM - cfg.spotGapM) continue;
      addSpot(x, y - CURB);
      addSpot(x, y + CURB);
    }
  }
  for (const x of lines) {
    for (let y = cfg.spotGapM; y < size; y += cfg.spotGapM) {
      const off = y % cfg.blockM;
      if (off < cfg.spotGapM || off > cfg.blockM - cfg.spotGapM) continue;
      addSpot(x - CURB, y);
      addSpot(x + CURB, y);
    }
  }

  const near = (a: Waypoint, b: Waypoint) => Math.hypot(a.x - b.x, a.y - b.y);

  // Grade de 40 m sobre as vagas: circular procurando vaga precisa ser uma
  // busca local, nao uma varredura das 1176 vagas a cada segundo.
  const bucketOf = (x: number, y: number) => `${Math.floor(x / 40)}:${Math.floor(y / 40)}`;
  const buckets = new Map<string, Spot[]>();
  for (const sp of spots) {
    const key = bucketOf(sp.x, sp.y);
    const list = buckets.get(key);
    if (list) list.push(sp);
    else buckets.set(key, [sp]);
  }
  /** Vaga livre mais proxima de um ponto, dentro de `radius` metros. */
  function nearestFree(p: Waypoint, radius: number): Spot | null {
    const cells = Math.ceil(radius / 40);
    const cx = Math.floor(p.x / 40);
    const cy = Math.floor(p.y / 40);
    let best: Spot | null = null;
    let bd = radius;
    for (let dx = -cells; dx <= cells; dx++) {
      for (let dy = -cells; dy <= cells; dy++) {
        const list = buckets.get(`${cx + dx}:${cy + dy}`);
        if (!list) continue;
        for (const sp of list) {
          if (sp.car) continue;
          const d = near(sp, p);
          if (d < bd) {
            bd = d;
            best = sp;
          }
        }
      }
    }
    return best;
  }
  const snap = (v: number) =>
    lines.reduce((best, c) => (Math.abs(c - v) < Math.abs(best - v) ? c : best), lines[0] as number);

  /** Rota em L pelo grid de ruas. */
  function route(from: Waypoint, to: Waypoint): Waypoint[] {
    const sx = snap(from.x);
    const sy = snap(from.y);
    const tx = snap(to.x);
    const ty = snap(to.y);
    const horizontalFirst = Math.abs(from.y - sy) < Math.abs(from.x - sx);
    return horizontalFirst
      ? [{ x: from.x, y: sy }, { x: tx, y: sy }, { x: tx, y: ty }, { x: to.x, y: to.y }]
      : [{ x: sx, y: from.y }, { x: sx, y: ty }, { x: tx, y: ty }, { x: to.x, y: to.y }];
  }

  // --- carros que nao se mexem: definem a escassez -----------------------
  const staticCars = Math.round(spots.length * cfg.occupancy) - cfg.drivers;
  const pool = [...spots].sort(() => rand() - 0.5);
  for (let i = 0; i < staticCars && i < pool.length; i++) {
    (pool[i] as Spot).car = 'estatico';
  }

  // --- motoristas --------------------------------------------------------
  const appCount = Math.round(cfg.drivers * cfg.penetration);
  const drivers: Driver[] = [];
  for (let i = 0; i < cfg.drivers; i++) {
    const d = new Driver(i, i < appCount);
    const free = spots.filter((s) => !s.car);
    const spot = free[Math.floor(rand() * free.length)];
    if (!spot) break;
    spot.car = d;
    d.spot = spot;
    const ang = rand() * Math.PI * 2;
    const away = 60 + rand() * 160;
    d.x = Math.max(5, Math.min(size - 5, spot.x + Math.cos(ang) * away));
    d.y = Math.max(5, Math.min(size - 5, spot.y + Math.sin(ang) * away));
    d.until = 60 + rand() * 900;
    drivers.push(d);
  }

  const index = new SpotIndex();
  const t0 = Date.UTC(2026, 0, 15, 8, 0, 0);
  let now = t0;

  let events = 0;
  let searches = 0;
  let withSuggestion = 0;
  let withTrueSuggestion = 0;
  let cruiseApp = 0;
  let cruiseNoApp = 0;
  let parksApp = 0;
  let parksNoApp = 0;
  let wastedTrips = 0;
  let chases = 0;
  let dotSamples = 0;
  let dotTotal = 0;
  let dotHits = 0;
  let dotShown = 0;

  const center = toLatLon(size / 2, size / 2);

  function move(d: Driver, v: number, dt: number): void {
    d.speed = v;
    let budget = v * dt;
    while (budget > 0 && d.path.length > 0) {
      const p = d.path[0] as Waypoint;
      const dd = near(p, d);
      if (dd <= budget) {
        d.x = p.x;
        d.y = p.y;
        budget -= dd;
        d.path.shift();
      } else {
        d.x += ((p.x - d.x) / dd) * budget;
        d.y += ((p.y - d.y) / dd) * budget;
        budget = 0;
      }
    }
  }

  /** Melhor ponto verde perto de um destino, em coordenadas do bairro. */
  function bestDotNear(p: Waypoint, at: number): Waypoint | null {
    const dots = index.query(center, size, at);
    let best: Waypoint | null = null;
    let bestP = 0;
    for (const dot of dots) {
      if (dot.tier === 'baixa') continue;
      const x = (dot.lon - cfg.origin.lon) * mPerDegLon;
      const y = (dot.lat - cfg.origin.lat) * M_PER_DEG_LAT;
      if (Math.hypot(x - p.x, y - p.y) > (cfg.followRadiusM ?? 150)) continue;
      if (dot.probability > bestP) {
        bestP = dot.probability;
        best = { x, y };
      }
    }
    return best;
  }

  /** Um motorista com app chegando perto do destino: o mapa ajuda ou nao? */
  function evaluateSearch(d: Driver, dots: SpotEstimate[]): void {
    if (!d.target || d.searchCounted) return;
    if (near(d, d.target) > cfg.searchStartM) return;
    d.searchCounted = true;
    searches++;

    const targetLL = { lat: d.target.lat, lon: d.target.lon };
    let best: SpotEstimate | null = null;
    for (const dot of dots) {
      if (dot.tier === 'baixa') continue;
      const dx = (dot.lon - targetLL.lon) * mPerDegLon;
      const dy = (dot.lat - targetLL.lat) * M_PER_DEG_LAT;
      if (Math.hypot(dx, dy) <= cfg.suggestionRadiusM) {
        if (!best || dot.probability > best.probability) best = dot;
      }
    }
    if (!best) return;
    withSuggestion++;

    // A sugestao era verdadeira se havia mesmo vaga livre a <=40 m do ponto.
    const bx = (best.lon - cfg.origin.lon) * mPerDegLon;
    const by = (best.lat - cfg.origin.lat) * M_PER_DEG_LAT;
    const real = spots.some((s) => !s.car && Math.hypot(s.x - bx, s.y - by) <= 40);
    if (real) withTrueSuggestion++;
  }

  const dt = 1;
  const steps = Math.floor(cfg.durationS / dt);

  for (let step = 0; step < steps; step++) {
    now += dt * 1000;

    // Amostra o mapa a cada minuto: e o que o usuario veria.
    const sampleMap = step % 60 === 0;
    const dots = sampleMap ? index.query(center, size, now) : null;
    if (dots) {
      dotSamples++;
      dotShown += dots.length;
      for (const dot of dots) {
        dotTotal++;
        const bx = (dot.lon - cfg.origin.lon) * mPerDegLon;
        const by = (dot.lat - cfg.origin.lat) * M_PER_DEG_LAT;
        if (spots.some((s) => !s.car && Math.hypot(s.x - bx, s.y - by) <= 40)) dotHits++;
      }
    }

    for (const d of drivers) {
      switch (d.phase) {
        case 'away':
          d.speed = 0;
          if (now - t0 >= d.until * 1000 && d.spot) {
            d.path = route(d, d.spot);
            d.phase = 'walk_to_car';
          }
          break;
        case 'walk_to_car':
          move(d, 1.35, dt);
          if (d.path.length === 0) {
            d.phase = 'board';
            d.until = (now - t0) / 1000 + 15 + rand() * 30;
            d.speed = 0;
          }
          break;
        case 'board':
          d.speed = 0;
          if (now - t0 >= d.until * 1000) {
            if (d.spot) d.spot.car = null;
            d.spot = null;
            d.searchCounted = false;
            d.chasing = null;

            if (cfg.cruising) {
              // Escolhe um DESTINO, nao uma vaga: quem esta na rua nao sabe
              // onde ha vaga, so para onde quer ir.
              const far = spots[Math.floor(rand() * spots.length)] as Spot;
              if (near(far, d) < 260) {
                d.until = (now - t0) / 1000 + 5;
                break;
              }
              d.destination = { x: far.x, y: far.y };
              d.target = far;

              let goto: Waypoint = d.destination;
              if (d.hasApp && cfg.followMap) {
                const suggestion = bestDotNear(d.destination, now);
                if (suggestion) {
                  goto = suggestion;
                  d.chasing = suggestion;
                  chases++;
                }
              }
              d.path = route(d, goto);
              d.phase = 'drive';
              break;
            }

            const free = spots.filter((s) => !s.car && near(s, d) > 260);
            const target = free[Math.floor(rand() * free.length)] ?? null;
            if (!target) {
              d.phase = 'away';
              d.until = (now - t0) / 1000 + 60;
              break;
            }
            target.car = d; // reserva: dois carros nao param na mesma vaga
            d.target = target;
            d.path = route(d, target);
            d.phase = 'drive';
          }
          break;
        case 'drive':
          move(d, 7.5 + rand() * 3, dt);
          if (dots && d.hasApp) evaluateSearch(d, dots);
          if (d.path.length === 0) {
            if (cfg.cruising) {
              d.phase = 'cruise';
              d.cruiseStart = now;
              break;
            }
            if (d.target) {
              d.spot = d.target;
              d.x = d.spot.x;
              d.y = d.spot.y;
              d.phase = 'parked';
              d.until = (now - t0) / 1000 + 100 + rand() * 140;
              d.speed = 0;
            }
          }
          break;

        case 'cruise': {
          // Circulando devagar, de olho no meio-fio.
          move(d, 4.5 + rand() * 1.5, dt);
          const spotSeen = nearestFree(d, cfg.sightM ?? 20);
          const elapsed = (now - d.cruiseStart) / 1000;

          if (spotSeen) {
            spotSeen.car = d;
            d.spot = spotSeen;
            d.x = spotSeen.x;
            d.y = spotSeen.y;
            if (d.hasApp) {
              cruiseApp += elapsed;
              parksApp++;
              // Perseguiu um ponto verde e levou mais de um minuto: o ponto
              // nao se sustentou — outro carro chegou primeiro, ou nunca houve.
              if (d.chasing && elapsed > 60) wastedTrips++;
            } else {
              cruiseNoApp += elapsed;
              parksNoApp++;
            }
            d.phase = 'parked';
            d.until = (now - t0) / 1000 + 100 + rand() * 140;
            d.speed = 0;
            break;
          }

          if (elapsed > (cfg.maxCruiseS ?? 900)) {
            // Desistiu: vai para um estacionamento e sai da simulacao de rua.
            if (d.hasApp) {
              cruiseApp += elapsed;
              parksApp++;
              if (d.chasing) wastedTrips++;
            } else {
              cruiseNoApp += elapsed;
              parksNoApp++;
            }
            d.phase = 'away';
            d.until = (now - t0) / 1000 + 600 + rand() * 1200;
            d.spot = null;
            break;
          }

          if (d.path.length === 0) {
            // Vira a esquina: escolhe um cruzamento vizinho.
            const gx = snap(d.x);
            const gy = snap(d.y);
            const step = cfg.blockM * (rand() < 0.5 ? 1 : -1);
            const nx = Math.max(0, Math.min(size, rand() < 0.5 ? gx + step : gx));
            const ny = Math.max(0, Math.min(size, nx === gx ? gy + step : gy));
            d.path = route(d, { x: nx, y: ny });
          }
          break;
        }
        case 'parked':
          d.speed = 0;
          if (now - t0 >= d.until * 1000) {
            const ang = rand() * Math.PI * 2;
            const dd = 90 + rand() * 170;
            d.path = route(d, {
              x: Math.max(5, Math.min(size - 5, d.x + Math.cos(ang) * dd)),
              y: Math.max(5, Math.min(size - 5, d.y + Math.sin(ang) * dd)),
            });
            d.phase = 'walk_away';
          }
          break;
        case 'walk_away':
          move(d, 1.35, dt);
          if (d.path.length === 0) {
            d.phase = 'away';
            d.until = (now - t0) / 1000 + 240 + rand() * 1260;
            d.speed = 0;
          }
          break;
      }

      if (!d.detector) continue;
      const noise = 3.5;
      const ll = toLatLon(d.x + (rand() - 0.5) * noise * 2, d.y + (rand() - 0.5) * noise * 2);
      const sample: LocationSample = {
        t: now,
        lat: ll.lat,
        lon: ll.lon,
        speed: Math.max(0, d.speed + (rand() - 0.5) * 0.5),
        accuracy: 8,
        activityHint: null,
      };
      for (const e of d.detector.push(sample)) {
        if (cfg.ignoreArrivals && e.kind === 'arrival') continue;
        index.add(e);
        events++;
      }
    }

    if (step % 600 === 0) index.prune(now);
  }

  return {
    penetration: cfg.penetration,
    drivers: drivers.length,
    appDrivers: drivers.filter((d) => d.hasApp).length,
    events,
    avgDots: dotSamples > 0 ? dotShown / dotSamples : 0,
    precision: dotTotal > 0 ? dotHits / dotTotal : 0,
    searches,
    withSuggestion,
    withTrueSuggestion,
    freeSpots: spots.filter((s) => !s.car).length,
    totalSpots: spots.length,
    cruiseApp: parksApp > 0 ? cruiseApp / parksApp : 0,
    cruiseNoApp: parksNoApp > 0 ? cruiseNoApp / parksNoApp : 0,
    parksApp,
    parksNoApp,
    wastedTrips,
    chases,
  };
}
