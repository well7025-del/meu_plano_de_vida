import type { DatabaseSync } from 'node:sqlite';
import { createServer, type IncomingMessage, type ServerResponse } from 'node:http';
import {
  DEFAULT_AVAILABILITY_CONFIG,
  boundingBox,
  cellId,
  estimate,
  tierOf,
  type StoredEvent,
} from '@vagas/core';
import { CELL_SIZE_M, insertEvent, pruneEvents, selectInBox } from './db.js';

export interface AppOptions {
  db: DatabaseSync;
  /** Retencao dos eventos, em segundos. Padrao: 1 h. */
  retentionS?: number;
  /** Raio maximo aceito em uma consulta, em metros. */
  maxRadiusM?: number;
  now?: () => number;
}

interface IngestBody {
  events?: unknown;
}

const MAX_BATCH = 50;

/**
 * API do Vagas.
 *
 * Tres rotas e mais nada: manda evento, pede pontos, diz se acertou. Sem
 * cadastro, sem sessao, sem historico por usuario — o que reduz o servidor a
 * um agregador burro, que e exatamente o que se quer defender num app que
 * sabe onde as pessoas estacionam.
 */
export function createApp(opts: AppOptions) {
  const { db } = opts;
  const retentionS = opts.retentionS ?? 3600;
  const maxRadiusM = opts.maxRadiusM ?? 3000;
  const now = opts.now ?? (() => Date.now());

  async function handle(req: IncomingMessage, res: ServerResponse): Promise<void> {
    const url = new URL(req.url ?? '/', 'http://localhost');
    const path = url.pathname;

    try {
      if (req.method === 'GET' && path === '/healthz') {
        return json(res, 200, { ok: true });
      }

      if (req.method === 'POST' && path === '/v1/events') {
        return await ingest(req, res);
      }

      if (req.method === 'GET' && path === '/v1/spots') {
        return spots(url, res);
      }

      if (req.method === 'POST' && path === '/v1/feedback') {
        return await feedback(req, res);
      }

      if (req.method === 'GET' && path === '/v1/stats') {
        const row = db
          .prepare('SELECT COUNT(*) AS n, MAX(t) AS last FROM events')
          .get() as { n: number; last: number | null };
        const fb = db
          .prepare('SELECT COUNT(*) AS n, SUM(found) AS hits FROM feedback')
          .get() as { n: number; hits: number | null };
        return json(res, 200, {
          events: row.n,
          lastEventT: row.last,
          feedback: fb.n,
          hitRate: fb.n > 0 ? (fb.hits ?? 0) / fb.n : null,
        });
      }

      return json(res, 404, { error: 'not_found' });
    } catch (err) {
      const message = err instanceof Error ? err.message : 'erro inesperado';
      return json(res, 500, { error: 'internal', message });
    }
  }

  async function ingest(req: IncomingMessage, res: ServerResponse): Promise<void> {
    const body = (await readJson(req)) as IngestBody;
    const raw = body?.events;
    if (!Array.isArray(raw)) {
      return json(res, 400, { error: 'bad_request', message: 'esperado { events: [...] }' });
    }
    if (raw.length > MAX_BATCH) {
      return json(res, 413, { error: 'batch_too_large', max: MAX_BATCH });
    }

    const t = now();
    let accepted = 0;
    const rejected: string[] = [];

    for (const item of raw) {
      const problem = validateEvent(item, t, retentionS);
      if (problem) {
        rejected.push(problem);
        continue;
      }
      const e = item as StoredEvent;
      insertEvent(db, { kind: e.kind, lat: e.lat, lon: e.lon, t: e.t, confidence: e.confidence }, t);
      accepted++;
    }

    // Poda oportunista: sem cron, sem worker, sem estado extra.
    pruneEvents(db, t - retentionS * 1000);

    return json(res, accepted > 0 ? 202 : 400, { accepted, rejected });
  }

  function spots(url: URL, res: ServerResponse): void {
    const rawLat = url.searchParams.get('lat');
    const rawLon = url.searchParams.get('lon');
    const lat = Number(rawLat);
    const lon = Number(rawLon);
    const radius = Number(url.searchParams.get('radius') ?? 800);

    if (rawLat === null || rawLon === null || !Number.isFinite(lat) || !Number.isFinite(lon)) {
      return json(res, 400, { error: 'bad_request', message: 'lat e lon sao obrigatorios' });
    }
    if (Math.abs(lat) > 90 || Math.abs(lon) > 180) {
      return json(res, 400, { error: 'bad_request', message: 'coordenada fora do planeta' });
    }
    if (!Number.isFinite(radius) || radius <= 0 || radius > maxRadiusM) {
      return json(res, 400, { error: 'bad_request', message: `radius deve estar entre 1 e ${maxRadiusM}` });
    }

    const t = now();
    const box = boundingBox({ lat, lon }, radius);
    const rows = selectInBox(db, box, t - retentionS * 1000);

    const byCell = new Map<string, StoredEvent[]>();
    for (const r of rows) {
      const list = byCell.get(r.cell);
      const stored: StoredEvent = {
        kind: r.kind,
        lat: r.lat,
        lon: r.lon,
        t: r.t,
        confidence: r.confidence,
        cell: r.cell,
      };
      if (list) list.push(stored);
      else byCell.set(r.cell, [stored]);
    }

    const out = [];
    for (const [cell, events] of byCell) {
      const { lambda, probability } = estimate(events, t, { cellSizeM: CELL_SIZE_M });
      if (probability < DEFAULT_AVAILABILITY_CONFIG.minProbability) continue;
      const anchor = anchorOf(events);
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

    out.sort((a, b) => b.probability - a.probability);
    return json(res, 200, { now: t, spots: out.slice(0, 300) });
  }

  async function feedback(req: IncomingMessage, res: ServerResponse): Promise<void> {
    const body = (await readJson(req)) as Record<string, unknown>;
    const lat = Number(body?.lat);
    const lon = Number(body?.lon);
    const found = body?.found;
    const shownP = Number(body?.shownProbability ?? 0);

    if (!Number.isFinite(lat) || !Number.isFinite(lon) || typeof found !== 'boolean') {
      return json(res, 400, {
        error: 'bad_request',
        message: 'esperado { lat, lon, found, shownProbability? }',
      });
    }

    db.prepare('INSERT INTO feedback (cell, found, shown_p, t) VALUES (?, ?, ?, ?)').run(
      cellId({ lat, lon }, CELL_SIZE_M),
      found ? 1 : 0,
      Number.isFinite(shownP) ? shownP : 0,
      now(),
    );

    // Um "nao achei" tambem e informacao de ocupacao: se a vaga nao estava la,
    // alguem ja a ocupou. Entra no modelo como chegada de confianca alta —
    // quem esta no local vendo a rua e a melhor testemunha que existe, e um
    // unico relato desses derruba o ponto do mapa.
    //
    // O custo dessa escolha e abuso: bastaria um cliente modificado mandando
    // "nao achei" para apagar uma regiao inteira. Por isso a rota e limitada
    // por dispositivo e por celula no gateway (ver docs/ARQUITETURA.md), e o
    // peso cai quando varios relatos identicos chegam em sequencia.
    if (!found) {
      insertEvent(db, { kind: 'arrival', lat, lon, t: now(), confidence: 0.85 }, now());
    }

    return json(res, 202, { ok: true });
  }

  const server = createServer((req, res) => {
    void handle(req, res);
  });

  return { server, handle };
}

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

/**
 * Validacao de entrada. O servidor nao confia no cliente: um app modificado
 * poderia despejar saidas falsas para esvaziar uma rua concorrente, entao
 * coordenada, horario e confianca sao checados um a um, e eventos com data no
 * futuro ou velhos demais sao recusados.
 */
function validateEvent(item: unknown, now: number, retentionS: number): string | null {
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

function readJson(req: IncomingMessage): Promise<unknown> {
  return new Promise((resolve, reject) => {
    let raw = '';
    let size = 0;
    req.on('data', (chunk: Buffer) => {
      size += chunk.length;
      if (size > 256 * 1024) {
        reject(new Error('corpo grande demais'));
        req.destroy();
        return;
      }
      raw += chunk.toString('utf8');
    });
    req.on('end', () => {
      if (raw.trim() === '') return resolve({});
      try {
        resolve(JSON.parse(raw));
      } catch {
        reject(new Error('json invalido'));
      }
    });
    req.on('error', reject);
  });
}

function json(res: ServerResponse, status: number, body: unknown): void {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store',
  });
  res.end(payload);
}

const round3 = (v: number) => Math.round(v * 1000) / 1000;
/** ~11 cm: mais casas decimais seriam falsa precisao e rastro desnecessario. */
const round6 = (v: number) => Math.round(v * 1e6) / 1e6;
