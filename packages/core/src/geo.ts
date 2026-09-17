/** Utilitarios geodesicos minimos (sem dependencias externas). */

const EARTH_RADIUS_M = 6_371_008.8;
const DEG = Math.PI / 180;

export interface LatLon {
  lat: number;
  lon: number;
}

/** Distancia em metros pela formula de haversine. */
export function distanceM(a: LatLon, b: LatLon): number {
  const dLat = (b.lat - a.lat) * DEG;
  const dLon = (b.lon - a.lon) * DEG;
  const lat1 = a.lat * DEG;
  const lat2 = b.lat * DEG;
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
  return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(h)));
}

/** Move um ponto `distM` metros no rumo `bearingDeg` (0 = norte, horario). */
export function destination(from: LatLon, bearingDeg: number, distM: number): LatLon {
  const d = distM / EARTH_RADIUS_M;
  const brg = bearingDeg * DEG;
  const lat1 = from.lat * DEG;
  const lon1 = from.lon * DEG;
  const lat2 = Math.asin(
    Math.sin(lat1) * Math.cos(d) + Math.cos(lat1) * Math.sin(d) * Math.cos(brg),
  );
  const lon2 =
    lon1 +
    Math.atan2(
      Math.sin(brg) * Math.sin(d) * Math.cos(lat1),
      Math.cos(d) - Math.sin(lat1) * Math.sin(lat2),
    );
  return { lat: lat2 / DEG, lon: ((lon2 / DEG + 540) % 360) - 180 };
}

/** Centroide simples de uma lista de pontos (suficiente em escala de quarteirao). */
export function centroid(points: LatLon[]): LatLon {
  if (points.length === 0) throw new Error('centroid: lista vazia');
  let lat = 0;
  let lon = 0;
  for (const p of points) {
    lat += p.lat;
    lon += p.lon;
  }
  return { lat: lat / points.length, lon: lon / points.length };
}

/**
 * Identificador de celula de grade com lado aproximado de `sizeM` metros.
 *
 * Serve para agregar eventos no servidor sem guardar coordenada exata e para
 * indexar consultas por area. Nao substitui map matching em via — e a camada
 * barata que roda antes dele.
 */
export function cellId(p: LatLon, sizeM = 40): string {
  const latStep = (sizeM / EARTH_RADIUS_M) / DEG;
  const lonStep = latStep / Math.max(0.01, Math.cos(p.lat * DEG));
  const y = Math.floor(p.lat / latStep);
  const x = Math.floor(p.lon / lonStep);
  return `${sizeM}:${y}:${x}`;
}

/** Centro geografico de uma celula produzida por `cellId`. */
export function cellCenter(id: string): LatLon {
  const parts = id.split(':');
  const sizeM = Number(parts[0]);
  const y = Number(parts[1]);
  const x = Number(parts[2]);
  const latStep = (sizeM / EARTH_RADIUS_M) / DEG;
  const lat = (y + 0.5) * latStep;
  const lonStep = latStep / Math.max(0.01, Math.cos(lat * DEG));
  return { lat, lon: (x + 0.5) * lonStep };
}

/** Caixa envolvente (em graus) para um raio em metros — usada nas consultas. */
export function boundingBox(center: LatLon, radiusM: number) {
  const dLat = (radiusM / EARTH_RADIUS_M) / DEG;
  const dLon = dLat / Math.max(0.01, Math.cos(center.lat * DEG));
  return {
    minLat: center.lat - dLat,
    maxLat: center.lat + dLat,
    minLon: center.lon - dLon,
    maxLon: center.lon + dLon,
  };
}
