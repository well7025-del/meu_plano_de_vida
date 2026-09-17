import { distanceM } from './geo.js';
import { MotionClassifier, type MotionReading } from './motion.js';
import {
  DEFAULT_DETECTOR_CONFIG,
  type DetectorConfig,
  type DetectorState,
  type EventSignals,
  type LocationSample,
  type MotionMode,
  type ParkingEvent,
} from './types.js';

interface Segment {
  mode: MotionMode;
  startT: number;
  endT: number;
  startLat: number;
  startLon: number;
  lastLat: number;
  lastLon: number;
  distanceM: number;
  bestAccuracyM: number;
  samples: number;
}

interface PendingDeparture {
  anchorLat: number;
  anchorLon: number;
  t: number;
  walkDurationS: number;
  walkDistanceM: number;
  anchorAccuracyM: number;
}

interface PendingArrival {
  anchorLat: number;
  anchorLon: number;
  t: number;
  driveDurationS: number;
  driveDistanceM: number;
  anchorAccuracyM: number;
}

/**
 * Motor de deteccao de eventos de estacionamento.
 *
 * Regra de saida (vaga LIBERADA):
 *   caminhada -> entra no carro -> dirige de verdade
 *   O ponto ancora e onde o trecho veicular comecou: e ali que o carro estava.
 *
 * Regra de chegada (vaga OCUPADA):
 *   dirigindo -> para por tempo suficiente -> se afasta a pe
 *   O ponto ancora e onde o carro parou.
 *
 * As duas regras tem a mesma estrutura: um evento so e emitido depois que o
 * trecho SEGUINTE o confirma. Isso custa alguns minutos de latencia na saida
 * (o tempo de dirigir 200 m) e alguns segundos na chegada, e em troca elimina
 * a maior fonte de falso positivo: semaforo, transito parado e a pessoa que
 * atravessa a rua a pe.
 */
export class ParkingDetector {
  private readonly cfg: DetectorConfig;
  private readonly classifier: MotionClassifier;

  private state: DetectorState = 'unknown';
  private current: Segment | null = null;
  private lastWalk: Segment | null = null;
  private lastDrive: Segment | null = null;

  private pendingDeparture: PendingDeparture | null = null;
  private pendingArrival: PendingArrival | null = null;
  private stopStartedAt: number | null = null;

  /**
   * Amostras recentes. O modo so e confirmado alguns segundos DEPOIS de mudar
   * (histerese), e a essa altura o carro ja andou uma quadra. Guardar a janela
   * permite voltar no tempo e ancorar o evento onde ele realmente aconteceu.
   */
  private recent: LocationSample[] = [];
  private static readonly RECENT_LIMIT = 600;

  /** Ultima chegada detectada por este proprio aparelho — reforca a proxima saida. */
  private ownLastArrival: { lat: number; lon: number; t: number } | null = null;

  constructor(config: Partial<DetectorConfig> = {}) {
    this.cfg = { ...DEFAULT_DETECTOR_CONFIG, ...config };
    this.classifier = new MotionClassifier(this.cfg);
  }

  get currentState(): DetectorState {
    return this.state;
  }

  /** Processa uma amostra e devolve os eventos confirmados nela (0, 1 ou 2). */
  push(sample: LocationSample): ParkingEvent[] {
    const reading = this.classifier.push(sample);
    if (!reading) return [];

    this.recent.push(sample);
    if (this.recent.length > ParkingDetector.RECENT_LIMIT) this.recent.shift();

    this.trackSegment(reading);

    const events: ParkingEvent[] = [];
    if (reading.changed) this.onModeChange(reading, events);
    this.tryConfirm(reading, events);
    return events;
  }

  // --- segmentacao -------------------------------------------------------

  private trackSegment(reading: MotionReading): void {
    const { sample, mode } = reading;
    const acc = sample.accuracy ?? this.cfg.maxAccuracyM;

    if (!this.current || this.current.mode !== mode) {
      if (this.current) this.closeSegment(this.current);
      this.current = {
        mode,
        startT: reading.modeSince,
        endT: sample.t,
        startLat: sample.lat,
        startLon: sample.lon,
        lastLat: sample.lat,
        lastLon: sample.lon,
        distanceM: 0,
        bestAccuracyM: acc,
        samples: 1,
      };
      return;
    }

    const seg = this.current;
    seg.distanceM += distanceM(
      { lat: seg.lastLat, lon: seg.lastLon },
      { lat: sample.lat, lon: sample.lon },
    );
    seg.lastLat = sample.lat;
    seg.lastLon = sample.lon;
    seg.endT = sample.t;
    seg.bestAccuracyM = Math.min(seg.bestAccuracyM, acc);
    seg.samples += 1;
  }

  private closeSegment(seg: Segment): void {
    if (seg.mode === 'walking') this.lastWalk = seg;
    if (seg.mode === 'vehicle') this.lastDrive = seg;
  }

  /** Amostra mais proxima de `t` dentro da janela guardada. */
  private positionAt(t: number): LocationSample | null {
    let best: LocationSample | null = null;
    let bestDelta = Infinity;
    for (const s of this.recent) {
      const d = Math.abs(s.t - t);
      if (d < bestDelta) {
        bestDelta = d;
        best = s;
      }
    }
    return best;
  }

  private durationS(seg: Segment | null): number {
    if (!seg) return 0;
    return Math.max(0, (seg.endT - seg.startT) / 1000);
  }

  // --- maquina de estados ------------------------------------------------

  private onModeChange(reading: MotionReading, events: ParkingEvent[]): void {
    const { sample, mode } = reading;

    switch (mode) {
      case 'vehicle': {
        if (this.state === 'vehicle_stopped') {
          // Nao era estacionamento: semaforo, transito ou embarque/desembarque.
          this.stopStartedAt = null;
          this.pendingArrival = null;
        } else {
          // Sem caminhada observada antes, nao ha como afirmar que um carro
          // saiu dali: pode ser o app abrindo com o usuario ja em movimento,
          // ou o celular voltando de um buraco de sinal. Nesse caso o motor
          // apenas passa a acompanhar o trecho veicular.
          const walk = this.lastWalk;
          const walkS = this.durationS(walk);
          const walkRecent =
            walk !== null && sample.t - walk.endT <= 10 * 60 * 1000;

          if (this.state === 'walking' && walkRecent && walkS >= this.cfg.minWalkDurationS) {
            const anchor = this.positionAt(reading.modeSince) ?? sample;
            this.pendingDeparture = {
              // O carro estava onde o trecho veicular comecou.
              anchorLat: anchor.lat,
              anchorLon: anchor.lon,
              t: reading.modeSince,
              walkDurationS: walkS,
              walkDistanceM: walk?.distanceM ?? 0,
              anchorAccuracyM: anchor.accuracy ?? this.cfg.maxAccuracyM,
            };
          }
        }
        this.state = 'in_vehicle';
        break;
      }

      case 'still': {
        if (this.state === 'in_vehicle') {
          this.stopStartedAt = reading.modeSince;
          this.state = 'vehicle_stopped';
        }
        break;
      }

      case 'walking': {
        if (this.state === 'vehicle_stopped' && this.stopStartedAt !== null) {
          const stopS = (reading.modeSince - this.stopStartedAt) / 1000;
          const stillSeg = this.current;
          if (stopS >= this.cfg.minStopDurationS && stillSeg) {
            const anchor = this.positionAt(this.stopStartedAt) ?? {
              lat: stillSeg.startLat,
              lon: stillSeg.startLon,
            };
            this.pendingArrival = {
              anchorLat: anchor.lat,
              anchorLon: anchor.lon,
              t: this.stopStartedAt,
              driveDurationS: this.durationS(this.lastDrive),
              driveDistanceM: this.lastDrive?.distanceM ?? 0,
              anchorAccuracyM: stillSeg.bestAccuracyM,
            };
          }
          this.stopStartedAt = null;
        } else if (this.state === 'in_vehicle' && this.pendingDeparture) {
          // Voltou a andar a pe sem ter dirigido o minimo: nao foi saida.
          const drive = this.current?.mode === 'vehicle' ? this.current : this.lastDrive;
          if ((drive?.distanceM ?? 0) < this.cfg.minDriveDistanceM) {
            this.pendingDeparture = null;
          }
        }
        this.state = 'walking';
        break;
      }

      default:
        break;
    }

    void events;
  }

  // --- confirmacao -------------------------------------------------------

  private tryConfirm(reading: MotionReading, events: ParkingEvent[]): void {
    const { sample } = reading;

    // Saida: confirma quando o trecho veicular provou ser um deslocamento real.
    if (this.pendingDeparture && this.current?.mode === 'vehicle') {
      const drive = this.current;
      const driveS = this.durationS(drive);
      if (
        driveS >= this.cfg.minVehicleDurationS &&
        drive.distanceM >= this.cfg.minDriveDistanceM
      ) {
        const p = this.pendingDeparture;
        const event = this.build('departure', p.anchorLat, p.anchorLon, p.t, sample.t, {
          walkDurationS: p.walkDurationS,
          walkDistanceM: p.walkDistanceM,
          driveDurationS: driveS,
          driveDistanceM: drive.distanceM,
          stopDurationS: 0,
          anchorAccuracyM: p.anchorAccuracyM,
          penalties: [],
        });
        this.pendingDeparture = null;
        if (event) events.push(event);
      }
    }

    // Chegada: confirma quando o usuario se afasta a pe do ponto de parada.
    if (this.pendingArrival && this.current?.mode === 'walking') {
      const walk = this.current;
      const away = distanceM(
        { lat: this.pendingArrival.anchorLat, lon: this.pendingArrival.anchorLon },
        { lat: sample.lat, lon: sample.lon },
      );
      const walkS = this.durationS(walk);
      if (away >= this.cfg.minWalkAwayDistanceM && walkS >= this.cfg.minWalkDurationS) {
        const p = this.pendingArrival;
        const stopS = Math.max(0, (walk.startT - p.t) / 1000);
        const event = this.build('arrival', p.anchorLat, p.anchorLon, p.t, sample.t, {
          walkDurationS: walkS,
          walkDistanceM: away,
          driveDurationS: p.driveDurationS,
          driveDistanceM: p.driveDistanceM,
          stopDurationS: stopS,
          anchorAccuracyM: p.anchorAccuracyM,
          penalties: [],
        });
        this.pendingArrival = null;
        if (event) {
          this.ownLastArrival = { lat: event.lat, lon: event.lon, t: event.t };
          events.push(event);
        }
      }
    }
  }

  // --- confianca ---------------------------------------------------------

  private build(
    kind: 'departure' | 'arrival',
    lat: number,
    lon: number,
    t: number,
    confirmedAt: number,
    signals: EventSignals,
  ): ParkingEvent | null {
    for (const zone of this.cfg.exclusionZones) {
      if (distanceM({ lat, lon }, zone) <= zone.radiusM) return null;
    }

    const cfg = this.cfg;
    const walkF = saturate(signals.walkDurationS / (2 * cfg.minWalkDurationS));
    const driveF = saturate(signals.driveDistanceM / (3 * cfg.minDriveDistanceM));
    const accF = 1 - clamp01((signals.anchorAccuracyM - 10) / 40);
    const stopF =
      kind === 'arrival' ? saturate(signals.stopDurationS / (3 * cfg.minStopDurationS)) : 1;

    let confidence = clamp01(
      0.25 + 0.2 * walkF + 0.2 * driveF + 0.2 * accF + 0.15 * stopF,
    );

    // Reforco forte: a saida acontece onde este mesmo aparelho estacionou antes.
    if (kind === 'departure' && this.ownLastArrival) {
      const d = distanceM({ lat, lon }, this.ownLastArrival);
      if (d <= 60) {
        confidence = clamp01(confidence + 0.2);
        signals.penalties.push('bonus:own_prior_arrival');
      }
    }

    const ctx = cfg.contextProvider?.({ lat, lon }) ?? null;
    if (ctx) {
      if (ctx.insidePrivateLot) {
        confidence *= 0.1;
        signals.penalties.push('private_lot');
      }
      if (ctx.onDrivableStreet === false) {
        confidence *= 0.5;
        signals.penalties.push('off_street');
      }
      if (ctx.parkingAllowed === false) {
        confidence *= 0.35;
        signals.penalties.push('parking_forbidden');
      }
      if (ctx.nearTransitStop) {
        // Assinatura de onibus: descer/embarcar no ponto imita o padrao.
        confidence *= 0.6;
        signals.penalties.push('near_transit_stop');
      }
      if (ctx.roadClass === 'motorway') {
        confidence *= 0.2;
        signals.penalties.push('motorway');
      }
    }

    if (confidence < cfg.minConfidence) return null;

    return {
      kind,
      lat,
      lon,
      t,
      confirmedAt,
      confidence: round3(confidence),
      radiusM: Math.round(cfg.baseAnchorRadiusM + signals.anchorAccuracyM),
      signals,
    };
  }
}

function clamp01(v: number): number {
  return Math.max(0, Math.min(1, v));
}

function saturate(v: number): number {
  return clamp01(v);
}

function round3(v: number): number {
  return Math.round(v * 1000) / 1000;
}
