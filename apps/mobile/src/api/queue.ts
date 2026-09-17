import * as SQLite from 'expo-sqlite';
import type { ParkingEvent } from '@vagas/core';
import { API_BASE } from './config';

/**
 * Fila de saida.
 *
 * Evento detectado no subsolo do estacionamento, no tunel ou com 4G ruim nao
 * pode se perder — e tambem nao pode ser enviado na hora exata em que
 * aconteceu, porque isso entregaria ao servidor o horario preciso em que
 * aquela pessoa se moveu. A fila resolve os dois: guarda local, envia em lote
 * com atraso aleatorio de ate 2 minutos.
 */
const db = SQLite.openDatabaseSync('vagas.db');

db.execSync(`
  CREATE TABLE IF NOT EXISTS outbox (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    payload    TEXT    NOT NULL,
    send_after INTEGER NOT NULL,
    tries      INTEGER NOT NULL DEFAULT 0
  );
`);

const MAX_TRIES = 8;

export async function enqueueEvent(event: ParkingEvent): Promise<void> {
  const jitterMs = Math.floor(Math.random() * 120_000);
  db.runSync('INSERT INTO outbox (payload, send_after) VALUES (?, ?)', [
    JSON.stringify({
      kind: event.kind,
      // Menos casas decimais do que o GPS entrega: a vaga e da rua, nao do
      // metro quadrado. Isso tambem impede reconstruir uma trajetoria a
      // partir de eventos sucessivos.
      lat: Math.round(event.lat * 1e6) / 1e6,
      lon: Math.round(event.lon * 1e6) / 1e6,
      t: event.t,
      confidence: event.confidence,
    }),
    Date.now() + jitterMs,
  ]);
  await flushQueue();
}

export async function flushQueue(): Promise<number> {
  const now = Date.now();
  const rows = db.getAllSync<{ id: number; payload: string; tries: number }>(
    'SELECT id, payload, tries FROM outbox WHERE send_after <= ? ORDER BY id LIMIT 50',
    [now],
  );
  if (rows.length === 0) return 0;

  try {
    const res = await fetch(`${API_BASE}/v1/events`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ events: rows.map((r) => JSON.parse(r.payload)) }),
    });
    if (!res.ok && res.status !== 400) throw new Error(`HTTP ${res.status}`);

    const ids = rows.map((r) => r.id).join(',');
    db.execSync(`DELETE FROM outbox WHERE id IN (${ids})`);
    return rows.length;
  } catch {
    // Backoff: cada tentativa empurra o proximo envio para mais longe.
    for (const r of rows) {
      const tries = r.tries + 1;
      if (tries >= MAX_TRIES) {
        db.runSync('DELETE FROM outbox WHERE id = ?', [r.id]);
        continue;
      }
      db.runSync('UPDATE outbox SET tries = ?, send_after = ? WHERE id = ?', [
        tries,
        now + Math.min(30 * 60_000, 2 ** tries * 5_000),
        r.id,
      ]);
    }
    return 0;
  }
}

export function pendingCount(): number {
  const row = db.getFirstSync<{ n: number }>('SELECT COUNT(*) AS n FROM outbox');
  return row?.n ?? 0;
}
