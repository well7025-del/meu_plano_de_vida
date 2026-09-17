import { boundingBox, cellCenter, cellId, distanceM } from './geo.js';
import type { ParkingEvent, ParkingEventKind } from './types.js';

export interface AvailabilityConfig {
  /**
   * Meia-vida da informacao, em segundos. Uma vaga liberada perde metade do
   * seu valor a cada `halfLifeS`. Em centro comercial a rotatividade e alta e
   * este numero cai; em rua residencial a noite, sobe. O servidor aprende o
   * valor por celula a partir do intervalo medio entre saida e chegada.
   */
  halfLifeS: number;
  /** Acima desta idade o evento e descartado do indice (s). */
  maxAgeS: number;
  /** Lado da celula de agregacao, em metros. */
  cellSizeM: number;
  /**
   * Oferta de base: quantas vagas a celula costuma ter livres neste horario,
   * vinda do historico. Sem historico, 0 — o mapa so mostra o que foi visto.
   */
  baselineLambda: number;
  /** Probabilidade abaixo da qual o ponto nao aparece no mapa. */
  minProbability: number;
}

export const DEFAULT_AVAILABILITY_CONFIG: AvailabilityConfig = {
  halfLifeS: 12 * 60,
  maxAgeS: 60 * 60,
  cellSizeM: 40,
  baselineLambda: 0,
  minProbability: 0.15,
};

export interface StoredEvent {
  kind: ParkingEventKind;
  lat: number;
  lon: number;
  t: number;
  confidence: number;
  cell: string;
}

export interface SpotEstimate {
  cell: string;
  lat: number;
  lon: number;
  /** Numero esperado de vagas livres agora (parametro do Poisson). */
  lambda: number;
  /** P(pelo menos uma vaga livre) = 1 - e^-lambda. */
  probability: number;
  /** Faixa usada pela UI. */
  tier: 'alta' | 'media' | 'baixa';
  /** Horario do evento mais recente que sustenta a estimativa. */
  lastEventT: number;
  /** Quantos eventos entraram na conta. */
  support: number;
}

/** Peso restante de um evento com `ageS` segundos, por decaimento exponencial. */
export function decay(ageS: number, halfLifeS: number): number {
  if (ageS <= 0) return 1;
  return Math.pow(0.5, ageS / halfLifeS);
}

/**
 * Modelo de disponibilidade.
 *
 * Cada saida detectada adiciona `confianca * decaimento` vagas esperadas;
 * cada chegada subtrai o mesmo tanto — alguem ja ocupou. O total (lambda) vai
 * para um Poisson, e a probabilidade de existir pelo menos uma vaga livre e
 * `1 - e^-lambda`. E um modelo simples o bastante para ser explicado ao
 * usuario ("2 carros sairam daqui nos ultimos 6 minutos") e calibravel: se o
 * app medir que 40% dos avisos nao se confirmam, e a meia-vida que esta errada.
 */
export function estimate(
  events: StoredEvent[],
  now: number,
  config: Partial<AvailabilityConfig> = {},
): { lambda: number; probability: number } {
  const cfg = { ...DEFAULT_AVAILABILITY_CONFIG, ...config };
  let lambda = cfg.baselineLambda;
  for (const e of events) {
    const ageS = (now - e.t) / 1000;
    if (ageS < 0 || ageS > cfg.maxAgeS) continue;
    const w = e.confidence * decay(ageS, cfg.halfLifeS);
    lambda += e.kind === 'departure' ? w : -w;
  }
  lambda = Math.max(0, lambda);
  return { lambda, probability: 1 - Math.exp(-lambda) };
}

/**
 * Faixas da UI. O corte de 0,55 e calibrado para que UMA saida confiavel
 * e recente ja pinte o ponto de verde — que e exatamente a informacao que o
 * motorista quer ver. Se a taxa de acerto medida em campo cair, e este numero
 * (e a meia-vida) que se ajusta, nao o texto da tela.
 */
export const TIER_HIGH = 0.55;
export const TIER_MEDIUM = 0.25;

export function tierOf(probability: number): SpotEstimate['tier'] {
  if (probability >= TIER_HIGH) return 'alta';
  if (probability >= TIER_MEDIUM) return 'media';
  return 'baixa';
}

/**
 * Indice em memoria de eventos por celula.
 *
 * O servico de producao usa Postgres/PostGIS com a mesma matematica; esta
 * classe e o que roda nos testes, no simulador e no cache offline do app.
 */
export class SpotIndex {
  private readonly cfg: AvailabilityConfig;
  private readonly byCell = new Map<string, StoredEvent[]>();

  constructor(config: Partial<AvailabilityConfig> = {}) {
    this.cfg = { ...DEFAULT_AVAILABILITY_CONFIG, ...config };
  }

  add(event: ParkingEvent | StoredEvent): StoredEvent {
    const cell = 'cell' in event ? event.cell : cellId(event, this.cfg.cellSizeM);
    const stored: StoredEvent = {
      kind: event.kind,
      lat: event.lat,
      lon: event.lon,
      t: event.t,
      confidence: event.confidence,
      cell,
    };
    const list = this.byCell.get(cell);
    if (list) list.push(stored);
    else this.byCell.set(cell, [stored]);
    return stored;
  }

  /** Remove eventos vencidos. Chamado periodicamente pelo servico. */
  prune(now: number): number {
    let removed = 0;
    for (const [cell, list] of this.byCell) {
      const kept = list.filter((e) => (now - e.t) / 1000 <= this.cfg.maxAgeS);
      removed += list.length - kept.length;
      if (kept.length === 0) this.byCell.delete(cell);
      else this.byCell.set(cell, kept);
    }
    return removed;
  }

  /** Pontos a desenhar no mapa dentro de um raio. Ordenados por probabilidade. */
  query(
    center: { lat: number; lon: number },
    radiusM: number,
    now: number,
  ): SpotEstimate[] {
    const box = boundingBox(center, radiusM);
    const out: SpotEstimate[] = [];

    for (const [cell, list] of this.byCell) {
      const c = cellCenter(cell);
      if (c.lat < box.minLat || c.lat > box.maxLat) continue;
      if (c.lon < box.minLon || c.lon > box.maxLon) continue;
      if (distanceM(center, c) > radiusM) continue;

      const fresh = list.filter((e) => (now - e.t) / 1000 <= this.cfg.maxAgeS);
      if (fresh.length === 0) continue;

      const { lambda, probability } = estimate(fresh, now, this.cfg);
      if (probability < this.cfg.minProbability) continue;

      // O ponto no mapa fica na media dos eventos, nao no centro da celula:
      // cai mais perto da vaga real sem expor a coordenada de um usuario so.
      const anchor = weightedAnchor(fresh, now, this.cfg.halfLifeS) ?? c;
      out.push({
        cell,
        lat: anchor.lat,
        lon: anchor.lon,
        lambda: Math.round(lambda * 1000) / 1000,
        probability: Math.round(probability * 1000) / 1000,
        tier: tierOf(probability),
        lastEventT: Math.max(...fresh.map((e) => e.t)),
        support: fresh.length,
      });
    }

    return out.sort((a, b) => b.probability - a.probability);
  }

  get size(): number {
    let n = 0;
    for (const list of this.byCell.values()) n += list.length;
    return n;
  }
}

function weightedAnchor(
  events: StoredEvent[],
  now: number,
  halfLifeS: number,
): { lat: number; lon: number } | null {
  let wsum = 0;
  let lat = 0;
  let lon = 0;
  for (const e of events) {
    if (e.kind !== 'departure') continue;
    const w = e.confidence * decay((now - e.t) / 1000, halfLifeS);
    wsum += w;
    lat += e.lat * w;
    lon += e.lon * w;
  }
  if (wsum === 0) return null;
  return { lat: lat / wsum, lon: lon / wsum };
}
