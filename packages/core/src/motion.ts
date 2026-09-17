import { distanceM } from './geo.js';
import type {
  DetectorConfig,
  LocationSample,
  MotionMode,
} from './types.js';
import { DEFAULT_DETECTOR_CONFIG } from './types.js';

export interface MotionReading {
  sample: LocationSample;
  /** Velocidade suavizada (mediana da janela), em m/s. */
  speed: number;
  /** Classificacao instantanea, antes da histerese. */
  instant: MotionMode;
  /** Modo confirmado apos histerese — o que o restante do sistema consome. */
  mode: MotionMode;
  /** Momento em que o modo confirmado comecou. */
  modeSince: number;
  /** `true` na amostra em que `mode` mudou. */
  changed: boolean;
}

/**
 * Classificador de modo de movimento.
 *
 * Duas defesas contra o ruido tipico de GPS urbano (canyon de predios, tuneis,
 * saltos de dezenas de metros parado no semaforo):
 *
 * 1. filtro de mediana na velocidade — um pico isolado nao muda nada;
 * 2. histerese por tempo de permanencia — o modo so muda depois que o
 *    candidato se sustenta por `dwell*S` segundos.
 *
 * A faixa entre `walkMaxSpeed` e `vehicleMinSpeed` (~10 a 15 km/h) e
 * deliberadamente ambigua: bicicleta, corrida e carro em congestionamento
 * caem nela. Ali o classificador nao vota — mantem o candidato atual.
 */
export class MotionClassifier {
  private readonly cfg: DetectorConfig;
  private speeds: number[] = [];
  private prev: LocationSample | null = null;
  private mode: MotionMode = 'unknown';
  private modeSince = 0;
  private candidate: MotionMode = 'unknown';
  private candidateSince = 0;

  constructor(config: Partial<DetectorConfig> = {}) {
    this.cfg = { ...DEFAULT_DETECTOR_CONFIG, ...config };
  }

  /** Processa uma amostra. Retorna `null` quando a amostra e descartada. */
  push(sample: LocationSample): MotionReading | null {
    const acc = sample.accuracy ?? 0;
    if (acc > this.cfg.maxAccuracyM) return null;

    const speed = this.smoothSpeed(sample);
    const instant = this.classifyInstant(speed, sample);

    if (this.mode === 'unknown' && this.modeSince === 0) {
      this.modeSince = sample.t;
      this.candidateSince = sample.t;
    }

    let changed = false;
    if (instant !== 'unknown') {
      if (instant !== this.candidate) {
        this.candidate = instant;
        this.candidateSince = sample.t;
      }
      const held = (sample.t - this.candidateSince) / 1000;
      if (this.candidate !== this.mode && held >= this.dwellFor(this.candidate)) {
        this.mode = this.candidate;
        this.modeSince = this.candidateSince;
        changed = true;
      }
    }

    this.prev = sample;
    return {
      sample,
      speed,
      instant,
      mode: this.mode,
      modeSince: this.modeSince,
      changed,
    };
  }

  /** Velocidade suavizada corrente, em m/s. */
  get currentSpeed(): number {
    return median(this.speeds);
  }

  private dwellFor(mode: MotionMode): number {
    switch (mode) {
      case 'still':
        return this.cfg.dwellStillS;
      case 'walking':
        return this.cfg.dwellWalkS;
      case 'vehicle':
        return this.cfg.dwellVehicleS;
      default:
        return Infinity;
    }
  }

  private smoothSpeed(sample: LocationSample): number {
    const raw = this.rawSpeed(sample);
    this.speeds.push(raw);
    if (this.speeds.length > this.cfg.medianWindow) this.speeds.shift();
    return median(this.speeds);
  }

  /**
   * Prefere a velocidade do doppler do GPS (bem mais estavel que derivada de
   * posicao) e cai para distancia/tempo quando o SO nao informa.
   */
  private rawSpeed(sample: LocationSample): number {
    const reported = sample.speed;
    if (typeof reported === 'number' && Number.isFinite(reported) && reported >= 0) {
      return reported;
    }
    const prev = this.prev;
    if (!prev) return 0;
    const dt = (sample.t - prev.t) / 1000;
    if (dt <= 0.5 || dt > 60) return median(this.speeds);
    return distanceM(prev, sample) / dt;
  }

  private classifyInstant(speed: number, sample: LocationSample): MotionMode {
    const hint = sample.activityHint ?? null;
    if (speed < this.cfg.stillMaxSpeed) return 'still';
    if (speed < this.cfg.walkMaxSpeed) {
      // O celular no bolso de um passageiro em transito lento tambem cai aqui;
      // o sinal nativo desempata quando existe.
      return hint === 'vehicle' ? 'unknown' : 'walking';
    }
    if (speed < this.cfg.vehicleMinSpeed) {
      // Faixa ambigua: so o sinal nativo decide.
      if (hint === 'vehicle') return 'vehicle';
      if (hint === 'walking' || hint === 'running') return 'walking';
      return 'unknown';
    }
    return 'vehicle';
  }
}

export function median(values: number[]): number {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const mid = sorted.length >> 1;
  if (sorted.length % 2 === 1) return sorted[mid] as number;
  return ((sorted[mid - 1] as number) + (sorted[mid] as number)) / 2;
}
