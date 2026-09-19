/**
 * Regras de servidor compartilhadas.
 *
 * O projeto tem dois servidores — um em Node (`services/api`, para rodar na
 * sua maquina) e um em Cloudflare Workers (`services/worker`, para o endereco
 * publico ficar de pe sem depender dela). Validacao e agregacao vivem aqui
 * para que os dois respondam exatamente a mesma coisa; a unica diferenca
 * legitima entre eles e como falam com o banco.
 */
import { boundingBox, cellId, distanceM } from './geo.js';
import {
  DEFAULT_AVAILABILITY_CONFIG,
  estimate,
  tierOf,
  type AvailabilityConfig,
  type StoredEvent,
} from './availability.js';
import type { ParkingEventKind } from './types.js';

export const CELL_SIZE_M = 40;
export const MAX_BATCH = 50;

/** Ponto como o app recebe. */
export interface PublicSpot {
  cell: string;
  lat: number;
  lon: number;
  probability: number;
  lambda: number;
  tier: 'alta' | 'media' | 'baixa';
  lastEventT: number;
  support: number;
}

export interface IncomingEvent {
  kind: ParkingEventKind;
  lat: number;
  lon: number;
  t: number;
  confidence: number;
}

/**
 * Valida um evento vindo do celular.
 *
 * O servidor nao confia no cliente: um app modificado poderia despejar saidas
 * falsas para esvaziar uma rua concorrente. Devolve `null` quando esta tudo
 * certo, ou o motivo da recusa.
 */
export function validateIncomingEvent(
  item: unknown,
  now: number,
  retentionS: number,
): string | null {
  if (typeof item !== 'object' || item === null) return 'nao e objeto';
  const e = item as Record<string, unknown>;
  if (e.kind !== 'departure' && e.kind !== 'arrival') return 'kind invalido';
  const lat = Number(e.lat);
  const lon = Number(e.lon);
  if (!Number.isFinite(lat) || Math.abs(lat) > 90) return 'lat invalida';
  if (!Number.isFinite(lon) || Math.abs(lon) > 180) return 'lon invalida';
  const t = Number(e.t);
  if (!Number.isFinite(t)) return 't invalido';
  if (t > now + 60_000) return 'evento no futuro';
  if (t < now - retentionS * 1000) return 'evento velho demais';
  const c = Number(e.confidence);
  if (!Number.isFinite(c) || c < 0 || c > 1) return 'confidence fora de 0..1';
  return null;
}

export function cellFor(p: { lat: number; lon: number }): string {
  return cellId(p, CELL_SIZE_M);
}

/** Caixa envolvente para a consulta no banco (indice cuida do resto). */
export function queryBox(center: { lat: number; lon: number }, radiusM: number) {
  return boundingBox(center, radiusM);
}

/**
 * Transforma linhas cruas em pontos para o mapa.
 *
 * O corte por distancia no fim nao e detalhe: a consulta no banco usa caixa
 * envolvente (quadrada) porque e o que o indice sabe fazer rapido, e sem este
 * filtro o app receberia pontos ate 41% mais longe do que pediu — os cantos
 * do quadrado.
 */
export function aggregateSpots(
  rows: StoredEvent[],
  center: { lat: number; lon: number },
  radiusM: number,
  now: number,
  config: Partial<AvailabilityConfig> = {},
): PublicSpot[] {
  const cfg = { ...DEFAULT_AVAILABILITY_CONFIG, cellSizeM: CELL_SIZE_M, ...config };
  const byCell = new Map<string, StoredEvent[]>();
  for (const r of rows) {
    const list = byCell.get(r.cell);
    if (list) list.push(r);
    else byCell.set(r.cell, [r]);
  }

  const out: PublicSpot[] = [];
  for (const [cell, events] of byCell) {
    const { lambda, probability } = estimate(events, now, cfg);
    if (probability < cfg.minProbability) continue;
    const anchor = anchorOf(events);
    if (distanceM(center, anchor) > radiusM) continue;
    out.push({
      cell,
      lat: round6(anchor.lat),
      lon: round6(anchor.lon),
      probability: round3(probability),
      lambda: round3(lambda),
      tier: tierOf(probability),
      lastEventT: Math.max(...events.map((e) => e.t)),
      support: events.length,
    });
  }
  return out.sort((a, b) => b.probability - a.probability);
}

/** Media das saidas — o ponto exibido nao e a posicao de uma pessoa so. */
function anchorOf(events: StoredEvent[]): { lat: number; lon: number } {
  const departures = events.filter((e) => e.kind === 'departure');
  const list = departures.length > 0 ? departures : events;
  let lat = 0;
  let lon = 0;
  for (const e of list) {
    lat += e.lat;
    lon += e.lon;
  }
  return { lat: lat / list.length, lon: lon / list.length };
}

const round3 = (v: number) => Math.round(v * 1000) / 1000;
/** ~11 cm: mais casas seriam falsa precisao e rastro desnecessario. */
const round6 = (v: number) => Math.round(v * 1e6) / 1e6;
