import * as SQLite from 'expo-sqlite';
import type { ExclusionZone, LocationContext } from '@vagas/core';

const db = SQLite.openDatabaseSync('vagas.db');

db.execSync(`
  CREATE TABLE IF NOT EXISTS settings (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
  );
  CREATE TABLE IF NOT EXISTS exclusion_zones (
    id      TEXT PRIMARY KEY,
    lat     REAL NOT NULL,
    lon     REAL NOT NULL,
    radius  REAL NOT NULL,
    label   TEXT
  );
`);

export interface AppConfig {
  /** Compartilhar deteccoes com a rede. Desligado = so consome, nao contribui. */
  contribute: boolean;
  /** Avisar por notificacao quando abrir vaga perto do destino. */
  notifyNearDestination: boolean;
  exclusionZones: ExclusionZone[];
  contextProvider?: (p: { lat: number; lon: number }) => LocationContext | null;
}

export async function loadConfig(): Promise<AppConfig> {
  const rows = db.getAllSync<{ key: string; value: string }>('SELECT key, value FROM settings');
  const map = new Map(rows.map((r) => [r.key, r.value]));
  const zones = db.getAllSync<{
    id: string;
    lat: number;
    lon: number;
    radius: number;
    label: string | null;
  }>('SELECT * FROM exclusion_zones');

  return {
    contribute: map.get('contribute') !== 'false',
    notifyNearDestination: map.get('notifyNearDestination') === 'true',
    exclusionZones: zones.map((z) => ({
      id: z.id,
      lat: z.lat,
      lon: z.lon,
      radiusM: z.radius,
      label: z.label ?? undefined,
    })),
  };
}

export function setFlag(key: keyof AppConfig, value: boolean): void {
  db.runSync('INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)', [key, String(value)]);
}

/**
 * Zona de exclusao: "aqui e minha garagem, nunca anuncie vaga".
 *
 * Alem da privacidade obvia, resolve um problema de qualidade: a saida da
 * garagem de casa tem exatamente a mesma assinatura de movimento de uma vaga
 * de rua, e anunciaria uma vaga que nao existe.
 */
export function addExclusionZone(zone: ExclusionZone): void {
  db.runSync(
    'INSERT OR REPLACE INTO exclusion_zones (id, lat, lon, radius, label) VALUES (?, ?, ?, ?, ?)',
    [zone.id, zone.lat, zone.lon, zone.radiusM, zone.label ?? null],
  );
}

export function removeExclusionZone(id: string): void {
  db.runSync('DELETE FROM exclusion_zones WHERE id = ?', [id]);
}
