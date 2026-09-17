import { strict as assert } from 'node:assert';
import { test } from 'node:test';
import { boundingBox, cellCenter, cellId, centroid, destination, distanceM } from '../src/geo.js';

const SP = { lat: -23.5613, lon: -46.6565 };

test('haversine bate com distancias conhecidas', () => {
  assert.ok(Math.abs(distanceM(SP, SP)) < 1e-9);
  const d = distanceM({ lat: -23.5505, lon: -46.6333 }, { lat: -22.9068, lon: -43.1729 });
  assert.ok(Math.abs(d - 360_000) < 15_000, `Sao Paulo -> Rio: ${Math.round(d)} m`);
});

test('destination e distanceM sao consistentes', () => {
  for (const brg of [0, 45, 90, 180, 270, 359]) {
    const p = destination(SP, brg, 250);
    assert.ok(Math.abs(distanceM(SP, p) - 250) < 0.5, `rumo ${brg}`);
  }
});

test('pontos proximos caem na mesma celula; distantes, em celulas diferentes', () => {
  const perto = destination(SP, 90, 5);
  const longe = destination(SP, 90, 500);
  assert.equal(cellId(SP, 40), cellId(perto, 40));
  assert.notEqual(cellId(SP, 40), cellId(longe, 40));
});

test('o centro da celula fica dentro da propria celula', () => {
  const id = cellId(SP, 40);
  const center = cellCenter(id);
  assert.equal(cellId(center, 40), id);
  assert.ok(distanceM(SP, center) < 45);
});

test('boundingBox cobre o raio pedido', () => {
  const box = boundingBox(SP, 500);
  for (const brg of [0, 90, 180, 270]) {
    const p = destination(SP, brg, 490);
    assert.ok(p.lat >= box.minLat && p.lat <= box.maxLat, `lat rumo ${brg}`);
    assert.ok(p.lon >= box.minLon && p.lon <= box.maxLon, `lon rumo ${brg}`);
  }
});

test('centroide de pontos simetricos volta ao centro', () => {
  const c = centroid([destination(SP, 0, 100), destination(SP, 180, 100)]);
  assert.ok(distanceM(c, SP) < 1);
});
