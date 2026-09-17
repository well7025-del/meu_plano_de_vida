import { API_BASE } from './config';

export interface Spot {
  cell: string;
  lat: number;
  lon: number;
  probability: number;
  lambda: number;
  tier: 'alta' | 'media' | 'baixa';
  lastEventT: number;
  support: number;
}

export async function fetchSpots(
  center: { lat: number; lon: number },
  radiusM = 800,
  signal?: AbortSignal,
): Promise<Spot[]> {
  const url = `${API_BASE}/v1/spots?lat=${center.lat}&lon=${center.lon}&radius=${Math.round(radiusM)}`;
  const res = await fetch(url, { signal });
  if (!res.ok) throw new Error(`falha ao buscar vagas: HTTP ${res.status}`);
  const body = (await res.json()) as { spots: Spot[] };
  return body.spots;
}

/** "Achei" / "nao achei" — o que calibra o modelo. */
export async function sendFeedback(
  spot: { lat: number; lon: number; probability: number },
  found: boolean,
): Promise<void> {
  await fetch(`${API_BASE}/v1/feedback`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      lat: spot.lat,
      lon: spot.lon,
      found,
      shownProbability: spot.probability,
    }),
  });
}
