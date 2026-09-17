import * as SQLite from 'expo-sqlite';

const db = SQLite.openDatabaseSync('vagas.db');

db.execSync(`
  CREATE TABLE IF NOT EXISTS parked (
    id   INTEGER PRIMARY KEY CHECK (id = 1),
    lat  REAL    NOT NULL,
    lon  REAL    NOT NULL,
    t    INTEGER NOT NULL,
    note TEXT
  );
`);

export interface ParkedSpot {
  lat: number;
  lon: number;
  t: number;
  note?: string | null;
}

/**
 * "Onde eu deixei o carro".
 *
 * Cai de graca: a mesma deteccao de chegada que alimenta a rede tambem marca
 * o carro no mapa do dono, sem ele apertar nada. E a funcionalidade que
 * segura o usuario no app nos dias em que ele nao esta procurando vaga.
 */
export async function rememberParkedSpot(spot: ParkedSpot): Promise<void> {
  db.runSync('INSERT OR REPLACE INTO parked (id, lat, lon, t, note) VALUES (1, ?, ?, ?, ?)', [
    spot.lat,
    spot.lon,
    spot.t,
    spot.note ?? null,
  ]);
}

export function getParkedSpot(): ParkedSpot | null {
  return db.getFirstSync<ParkedSpot>('SELECT lat, lon, t, note FROM parked WHERE id = 1') ?? null;
}

export function clearParkedSpot(): void {
  db.runSync('DELETE FROM parked WHERE id = 1');
}
