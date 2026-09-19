-- Esquema do D1. Ja aplicado no banco `vagas`; fica aqui para recriar do zero
-- ou para subir um segundo ambiente:
--   npx wrangler d1 execute vagas --remote --file=schema.sql

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

CREATE TABLE IF NOT EXISTS feedback (
  id      INTEGER PRIMARY KEY AUTOINCREMENT,
  cell    TEXT    NOT NULL,
  found   INTEGER NOT NULL,
  shown_p REAL    NOT NULL,
  t       INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_feedback_cell ON feedback (cell);
