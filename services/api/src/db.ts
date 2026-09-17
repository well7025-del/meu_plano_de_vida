import { DatabaseSync } from 'node:sqlite';
import { cellId } from '@vagas/core';

/**
 * Persistencia.
 *
 * SQLite aqui para o projeto rodar com `npm start` e nada mais. Em producao a
 * mesma tabela vira Postgres + PostGIS (indice GIST em `geog`) ou Timescale,
 * porque a carga e quase toda "insere muito, consulta por raio, apaga o que
 * envelheceu". A matematica nao muda: e a mesma de @vagas/core.
 *
 * O que NAO existe nesta tabela, de proposito:
 *   - id de usuario ou de aparelho;
 *   - trajetoria;
 *   - qualquer coisa que ligue dois eventos a mesma pessoa.
 * Ver docs/PRIVACIDADE.md.
 */
export interface EventRow {
  id: number;
  kind: 'departure' | 'arrival';
  lat: number;
  lon: number;
  t: number;
  confidence: number;
  cell: string;
  received_at: number;
}

export const SCHEMA = `
CREATE TABLE IF NOT EXISTS events (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  kind        TEXT    NOT NULL CHECK (kind IN ('departure','arrival')),
  lat         REAL    NOT NULL,
  lon         REAL    NOT NULL,
  t           INTEGER NOT NULL,
  confidence  REAL    NOT NULL,
  cell        TEXT    NOT NULL,
  received_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_events_cell_t ON events (cell, t DESC);
CREATE INDEX IF NOT EXISTS idx_events_bbox   ON events (lat, lon);

-- Calibracao: o motorista diz se achou ou nao a vaga anunciada. E o unico
-- jeito honesto de saber se a meia-vida do modelo esta certa naquela regiao.
CREATE TABLE IF NOT EXISTS feedback (
  id        INTEGER PRIMARY KEY AUTOINCREMENT,
  cell      TEXT    NOT NULL,
  found     INTEGER NOT NULL,
  shown_p   REAL    NOT NULL,
  t         INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_feedback_cell ON feedback (cell);
`;

export function openDb(path = ':memory:'): DatabaseSync {
  const db = new DatabaseSync(path);
  db.exec('PRAGMA journal_mode = WAL;');
  db.exec(SCHEMA);
  return db;
}

export const CELL_SIZE_M = 40;

export function insertEvent(
  db: DatabaseSync,
  e: { kind: 'departure' | 'arrival'; lat: number; lon: number; t: number; confidence: number },
  receivedAt = Date.now(),
): void {
  db.prepare(
    `INSERT INTO events (kind, lat, lon, t, confidence, cell, received_at)
     VALUES (?, ?, ?, ?, ?, ?, ?)`,
  ).run(
    e.kind,
    e.lat,
    e.lon,
    e.t,
    e.confidence,
    cellId({ lat: e.lat, lon: e.lon }, CELL_SIZE_M),
    receivedAt,
  );
}

export function selectInBox(
  db: DatabaseSync,
  box: { minLat: number; maxLat: number; minLon: number; maxLon: number },
  since: number,
): EventRow[] {
  return db
    .prepare(
      `SELECT * FROM events
        WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ? AND t >= ?
        ORDER BY t DESC LIMIT 5000`,
    )
    .all(box.minLat, box.maxLat, box.minLon, box.maxLon, since) as unknown as EventRow[];
}

/** Apaga eventos vencidos. Retencao curta e feature, nao limitacao. */
export function pruneEvents(db: DatabaseSync, olderThan: number): number {
  const before = db.prepare('SELECT COUNT(*) AS n FROM events').get() as { n: number };
  db.prepare('DELETE FROM events WHERE t < ?').run(olderThan);
  const after = db.prepare('SELECT COUNT(*) AS n FROM events').get() as { n: number };
  return before.n - after.n;
}
