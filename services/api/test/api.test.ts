import { strict as assert } from 'node:assert';
import { after, before, test } from 'node:test';
import type { AddressInfo } from 'node:net';
import { createApp } from '../src/app.js';
import { openDb } from '../src/db.js';

const db = openDb(':memory:');
let now = Date.UTC(2026, 0, 15, 12, 0, 0);
const { server } = createApp({ db, now: () => now });
let base = '';

const SP = { lat: -23.5613, lon: -46.6565 };

before(async () => {
  await new Promise<void>((r) => server.listen(0, r));
  const { port } = server.address() as AddressInfo;
  base = `http://127.0.0.1:${port}`;
});

after(() => {
  server.close();
  db.close();
});

function ev(kind: 'departure' | 'arrival', agoS: number, p = SP, confidence = 0.9) {
  return { kind, lat: p.lat, lon: p.lon, t: now - agoS * 1000, confidence };
}

async function post(path: string, body: unknown) {
  const res = await fetch(base + path, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
  });
  return { status: res.status, body: (await res.json()) as any };
}

async function get(path: string) {
  const res = await fetch(base + path);
  return { status: res.status, body: (await res.json()) as any };
}

test('healthz responde', async () => {
  const r = await get('/healthz');
  assert.equal(r.status, 200);
  assert.equal(r.body.ok, true);
});

test('ingestao aceita evento valido e ele aparece no mapa', async () => {
  const r = await post('/v1/events', { events: [ev('departure', 60)] });
  assert.equal(r.status, 202);
  assert.equal(r.body.accepted, 1);

  const spots = await get(`/v1/spots?lat=${SP.lat}&lon=${SP.lon}&radius=500`);
  assert.equal(spots.status, 200);
  assert.equal(spots.body.spots.length, 1);
  assert.equal(spots.body.spots[0].tier, 'alta');
});

test('coordenada devolvida e arredondada a ~11 cm', async () => {
  const spots = await get(`/v1/spots?lat=${SP.lat}&lon=${SP.lon}&radius=500`);
  const s = spots.body.spots[0];
  assert.equal(s.lat, Math.round(s.lat * 1e6) / 1e6);
});

test('evento no futuro e recusado', async () => {
  const r = await post('/v1/events', { events: [{ ...ev('departure', 0), t: now + 600_000 }] });
  assert.equal(r.status, 400);
  assert.deepEqual(r.body.rejected, ['evento no futuro']);
});

test('confianca fora de 0..1 e recusada', async () => {
  const r = await post('/v1/events', { events: [{ ...ev('departure', 30), confidence: 7 }] });
  assert.equal(r.body.accepted, 0);
  assert.deepEqual(r.body.rejected, ['confidence fora de 0..1']);
});

test('coordenada invalida e recusada', async () => {
  const r = await post('/v1/events', { events: [{ ...ev('departure', 30), lat: 120 }] });
  assert.deepEqual(r.body.rejected, ['lat invalida']);
});

test('lote grande demais e recusado', async () => {
  const events = Array.from({ length: 51 }, () => ev('departure', 30));
  const r = await post('/v1/events', { events });
  assert.equal(r.status, 413);
});

test('consulta sem coordenada da 400', async () => {
  assert.equal((await get('/v1/spots?radius=500')).status, 400);
});

test('raio absurdo da 400', async () => {
  assert.equal((await get(`/v1/spots?lat=${SP.lat}&lon=${SP.lon}&radius=99999`)).status, 400);
});

test('chegada derruba a probabilidade da celula', async () => {
  const antes = await get(`/v1/spots?lat=${SP.lat}&lon=${SP.lon}&radius=500`);
  const pAntes = antes.body.spots[0].probability;

  await post('/v1/events', { events: [ev('arrival', 5)] });

  const depois = await get(`/v1/spots?lat=${SP.lat}&lon=${SP.lon}&radius=500`);
  const pDepois = depois.body.spots[0]?.probability ?? 0;
  assert.ok(pDepois < pAntes, `${pDepois} deveria ser menor que ${pAntes}`);
});

test('"nao achei a vaga" entra como ocupacao e some do mapa', async () => {
  const p = { lat: -23.57, lon: -46.65 };
  await post('/v1/events', { events: [ev('departure', 30, p)] });
  assert.equal((await get(`/v1/spots?lat=${p.lat}&lon=${p.lon}&radius=200`)).body.spots.length, 1);

  const fb = await post('/v1/feedback', { lat: p.lat, lon: p.lon, found: false, shownProbability: 0.7 });
  assert.equal(fb.status, 202);

  const depois = await get(`/v1/spots?lat=${p.lat}&lon=${p.lon}&radius=200`);
  assert.equal(depois.body.spots.length, 0, 'ponto desmentido deveria sumir');
});

test('eventos envelhecem e saem do mapa sozinhos', async () => {
  const p = { lat: -23.58, lon: -46.66 };
  await post('/v1/events', { events: [ev('departure', 30, p)] });
  assert.equal((await get(`/v1/spots?lat=${p.lat}&lon=${p.lon}&radius=200`)).body.spots.length, 1);

  now += 70 * 60 * 1000; // uma hora e dez depois
  const depois = await get(`/v1/spots?lat=${p.lat}&lon=${p.lon}&radius=200`);
  assert.equal(depois.body.spots.length, 0);
});

test('stats reporta volume e taxa de acerto', async () => {
  const r = await get('/v1/stats');
  assert.equal(r.status, 200);
  assert.ok(r.body.events >= 0);
  assert.ok(r.body.feedback >= 1);
});

test('rota desconhecida da 404', async () => {
  assert.equal((await get('/v2/qualquer')).status, 404);
});

test('json invalido nao derruba o servidor', async () => {
  const res = await fetch(base + '/v1/events', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: '{ nao e json',
  });
  assert.equal(res.status, 500);
  assert.equal((await get('/healthz')).status, 200);
});
