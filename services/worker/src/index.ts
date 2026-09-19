/**
 * Vagas na borda da Cloudflare.
 *
 * Mesma API do servidor Node (`services/api`), mesmas regras (`@vagas/core`),
 * outro banco: D1 em vez de SQLite local. A vantagem para o piloto e simples —
 * o endereco fica de pe sem o computador de ninguem ligado, e perto de quem
 * usa, porque o Worker roda no data center mais proximo.
 *
 * O app em si nem passa por aqui: `run_worker_first` restringe o Worker as
 * rotas de API, e os arquivos estaticos saem direto da borda.
 */
import {
  MAX_BATCH,
  aggregateSpots,
  cellFor,
  queryBox,
  validateIncomingEvent,
  type StoredEvent,
} from '@vagas/core';

export interface Env {
  DB: D1Database;
  ASSETS: Fetcher;
  RETENTION_S?: string;
  WRITES_PER_MINUTE?: string;
}

interface EventRow {
  kind: 'departure' | 'arrival';
  lat: number;
  lon: number;
  t: number;
  confidence: number;
  cell: string;
}

const MAX_RADIUS_M = 3000;
const MAX_BODY_BYTES = 256 * 1024;

/**
 * Limite de taxa por origem.
 *
 * Vive na memoria do isolate, entao um atacante distribuido passa por cima —
 * e proposital: aqui ele so barra o caso ingenuo (um cliente modificado em
 * laco). A defesa de verdade contra enxurrada e a propria Cloudflare, no nivel
 * da rede, e ela nao custa codigo.
 */
const hits = new Map<string, number[]>();

function allow(key: string, limit: number, now: number): boolean {
  if (limit <= 0) return true;
  const cutoff = now - 60_000;
  const list = (hits.get(key) ?? []).filter((x) => x > cutoff);
  if (list.length >= limit) {
    hits.set(key, list);
    return false;
  }
  list.push(now);
  hits.set(key, list);
  if (hits.size > 2000) hits.clear();
  return true;
}

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' },
  });

async function readJson(request: Request): Promise<unknown> {
  const raw = await request.text();
  if (raw.length > MAX_BODY_BYTES) throw new Error('corpo grande demais');
  if (raw.trim() === '') return {};
  return JSON.parse(raw);
}

const clientKey = (request: Request) =>
  request.headers.get('cf-connecting-ip') ??
  request.headers.get('x-forwarded-for')?.split(',')[0]?.trim() ??
  'desconhecido';

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);
    const path = url.pathname;
    const now = Date.now();
    const retentionS = Number(env.RETENTION_S ?? 3600);
    const writeLimit = Number(env.WRITES_PER_MINUTE ?? 60);

    try {
      if (request.method === 'GET' && path === '/healthz') {
        return json(200, { ok: true, runtime: 'workers' });
      }

      if (request.method === 'POST' && path === '/v1/events') {
        if (!allow(clientKey(request), writeLimit, now)) return json(429, { error: 'rate_limited' });
        return await ingest(request, env, ctx, now, retentionS);
      }

      if (request.method === 'GET' && path === '/v1/spots') {
        return await spots(url, env, now, retentionS);
      }

      if (request.method === 'POST' && path === '/v1/feedback') {
        if (!allow(clientKey(request), writeLimit, now)) return json(429, { error: 'rate_limited' });
        return await feedback(request, env, now);
      }

      if (request.method === 'GET' && path === '/v1/stats') {
        const ev = await env.DB.prepare('SELECT COUNT(*) AS n, MAX(t) AS last FROM events').first<{
          n: number;
          last: number | null;
        }>();
        const fb = await env.DB.prepare(
          'SELECT COUNT(*) AS n, SUM(found) AS hits FROM feedback',
        ).first<{ n: number; hits: number | null }>();
        return json(200, {
          events: ev?.n ?? 0,
          lastEventT: ev?.last ?? null,
          feedback: fb?.n ?? 0,
          hitRate: fb && fb.n > 0 ? (fb.hits ?? 0) / fb.n : null,
        });
      }

      // Qualquer outra coisa e arquivo do app.
      return env.ASSETS.fetch(request);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'erro inesperado';
      return json(500, { error: 'internal', message });
    }
  },
} satisfies ExportedHandler<Env>;

async function ingest(
  request: Request,
  env: Env,
  ctx: ExecutionContext,
  now: number,
  retentionS: number,
): Promise<Response> {
  const body = (await readJson(request)) as { events?: unknown };
  if (!Array.isArray(body?.events)) {
    return json(400, { error: 'bad_request', message: 'esperado { events: [...] }' });
  }
  if (body.events.length > MAX_BATCH) {
    return json(413, { error: 'batch_too_large', max: MAX_BATCH });
  }

  const rejected: string[] = [];
  const statements = [];
  const insert = env.DB.prepare(
    `INSERT INTO events (kind, lat, lon, t, confidence, cell, received_at)
     VALUES (?, ?, ?, ?, ?, ?, ?)`,
  );

  for (const item of body.events) {
    const problem = validateIncomingEvent(item, now, retentionS);
    if (problem) {
      rejected.push(problem);
      continue;
    }
    const e = item as { kind: string; lat: number; lon: number; t: number; confidence: number };
    statements.push(
      insert.bind(e.kind, e.lat, e.lon, e.t, e.confidence, cellFor(e), now),
    );
  }

  if (statements.length > 0) await env.DB.batch(statements);

  // Poda depois de responder: o celular nao espera por manutencao.
  ctx.waitUntil(
    env.DB.prepare('DELETE FROM events WHERE t < ?').bind(now - retentionS * 1000).run(),
  );

  return json(statements.length > 0 ? 202 : 400, { accepted: statements.length, rejected });
}

async function spots(url: URL, env: Env, now: number, retentionS: number): Promise<Response> {
  const rawLat = url.searchParams.get('lat');
  const rawLon = url.searchParams.get('lon');
  const lat = Number(rawLat);
  const lon = Number(rawLon);
  const radius = Number(url.searchParams.get('radius') ?? 800);

  if (rawLat === null || rawLon === null || !Number.isFinite(lat) || !Number.isFinite(lon)) {
    return json(400, { error: 'bad_request', message: 'lat e lon sao obrigatorios' });
  }
  if (Math.abs(lat) > 90 || Math.abs(lon) > 180) {
    return json(400, { error: 'bad_request', message: 'coordenada fora do planeta' });
  }
  if (!Number.isFinite(radius) || radius <= 0 || radius > MAX_RADIUS_M) {
    return json(400, { error: 'bad_request', message: `radius deve estar entre 1 e ${MAX_RADIUS_M}` });
  }

  const box = queryBox({ lat, lon }, radius);
  const { results } = await env.DB.prepare(
    `SELECT kind, lat, lon, t, confidence, cell FROM events
      WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ? AND t >= ?
      ORDER BY t DESC LIMIT 5000`,
  )
    .bind(box.minLat, box.maxLat, box.minLon, box.maxLon, now - retentionS * 1000)
    .all<EventRow>();

  const events: StoredEvent[] = (results ?? []).map((r) => ({
    kind: r.kind,
    lat: r.lat,
    lon: r.lon,
    t: r.t,
    confidence: r.confidence,
    cell: r.cell,
  }));

  const out = aggregateSpots(events, { lat, lon }, radius, now);
  return json(200, { now, spots: out.slice(0, 300) });
}

async function feedback(request: Request, env: Env, now: number): Promise<Response> {
  const body = (await readJson(request)) as Record<string, unknown>;
  const lat = Number(body?.lat);
  const lon = Number(body?.lon);
  const found = body?.found;
  const shownP = Number(body?.shownProbability ?? 0);

  if (!Number.isFinite(lat) || !Number.isFinite(lon) || typeof found !== 'boolean') {
    return json(400, {
      error: 'bad_request',
      message: 'esperado { lat, lon, found, shownProbability? }',
    });
  }

  const cell = cellFor({ lat, lon });
  const statements = [
    env.DB.prepare('INSERT INTO feedback (cell, found, shown_p, t) VALUES (?, ?, ?, ?)').bind(
      cell,
      found ? 1 : 0,
      Number.isFinite(shownP) ? shownP : 0,
      now,
    ),
  ];

  // "Nao achei" e testemunho de quem esta no local: entra como ocupacao de
  // confianca alta e derruba o ponto do mapa na proxima consulta.
  if (!found) {
    statements.push(
      env.DB.prepare(
        `INSERT INTO events (kind, lat, lon, t, confidence, cell, received_at)
         VALUES ('arrival', ?, ?, ?, 0.85, ?, ?)`,
      ).bind(lat, lon, now, cell, now),
    );
  }

  await env.DB.batch(statements);
  return json(202, { ok: true });
}
