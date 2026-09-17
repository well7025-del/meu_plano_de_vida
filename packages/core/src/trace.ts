import { destination } from './geo.js';
import type { ActivityHint, LocationSample } from './types.js';

/**
 * Gerador de trajetorias sinteticas.
 *
 * Existe para testar o motor sem sair de casa: cada teste descreve um roteiro
 * ("caminhou 2 min, dirigiu 5 min, parou 3 min, caminhou 1 min") e verifica
 * que os eventos certos sairam. Tambem alimenta o simulador visual.
 */

/** PRNG deterministico (mulberry32) — testes com ruido, mas reprodutiveis. */
export function rng(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export interface LegOptions {
  /** Intervalo entre amostras, em segundos. */
  sampleIntervalS?: number;
  /** Ruido de posicao (desvio em metros). */
  noiseM?: number;
  /** Precisao reportada, em metros. */
  accuracyM?: number;
  /** Variacao aleatoria de rumo a cada amostra, em graus. */
  headingJitterDeg?: number;
  /** Sobrescreve a dica de atividade nativa. */
  activityHint?: ActivityHint | null;
  /** Nao reportar `speed` (simula aparelho/ambiente sem doppler). */
  omitSpeed?: boolean;
}

export class TraceBuilder {
  private samples: LocationSample[] = [];
  private t: number;
  private lat: number;
  private lon: number;
  private rand: () => number;

  constructor(start: { t: number; lat: number; lon: number }, seed = 42) {
    this.t = start.t;
    this.lat = start.lat;
    this.lon = start.lon;
    this.rand = rng(seed);
  }

  /** Parado (no bolso, na mesa, no semaforo). */
  still(durationS: number, opts: LegOptions = {}): this {
    return this.leg(durationS, 0, 0, 'still', opts);
  }

  /** Caminhando a ~1,4 m/s. */
  walk(durationS: number, bearingDeg: number, opts: LegOptions & { speed?: number } = {}): this {
    return this.leg(durationS, opts.speed ?? 1.4, bearingDeg, 'walking', opts);
  }

  /** Dirigindo — 8,3 m/s = 30 km/h por padrao. */
  drive(durationS: number, bearingDeg: number, opts: LegOptions & { speed?: number } = {}): this {
    return this.leg(durationS, opts.speed ?? 8.3, bearingDeg, 'vehicle', opts);
  }

  /** Trecho arbitrario, para casos de borda (bicicleta, transito lento...). */
  leg(
    durationS: number,
    speed: number,
    bearingDeg: number,
    hint: ActivityHint,
    opts: LegOptions = {},
  ): this {
    const dt = opts.sampleIntervalS ?? 1;
    const noise = opts.noiseM ?? 3;
    const accuracy = opts.accuracyM ?? 8;
    const jitter = opts.headingJitterDeg ?? 0;
    const steps = Math.max(1, Math.round(durationS / dt));

    for (let i = 0; i < steps; i++) {
      this.t += dt * 1000;
      if (speed > 0) {
        const brg = bearingDeg + (this.rand() - 0.5) * 2 * jitter;
        const next = destination({ lat: this.lat, lon: this.lon }, brg, speed * dt);
        this.lat = next.lat;
        this.lon = next.lon;
      }
      const jLat = destination(
        { lat: this.lat, lon: this.lon },
        this.rand() * 360,
        noise * (this.rand() - 0.5) * 2,
      );
      this.samples.push({
        t: this.t,
        lat: jLat.lat,
        lon: jLat.lon,
        speed: opts.omitSpeed ? null : Math.max(0, speed + (this.rand() - 0.5) * 0.6),
        accuracy,
        activityHint: opts.activityHint !== undefined ? opts.activityHint : hint,
      });
    }
    return this;
  }

  /** Buraco no sinal (tunel, garagem subterranea, app suspenso pelo SO). */
  gap(durationS: number): this {
    this.t += durationS * 1000;
    return this;
  }

  build(): LocationSample[] {
    return this.samples;
  }

  get position(): { lat: number; lon: number } {
    return { lat: this.lat, lon: this.lon };
  }

  get time(): number {
    return this.t;
  }
}
