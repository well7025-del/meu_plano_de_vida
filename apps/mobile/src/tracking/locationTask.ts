import * as Location from 'expo-location';
import * as TaskManager from 'expo-task-manager';
import { ParkingDetector, type LocationSample, type ParkingEvent } from '@vagas/core';
import { loadConfig } from '../state/settings';
import { enqueueEvent } from '../api/queue';
import { rememberParkedSpot } from '../state/parked';

export const LOCATION_TASK = 'vagas-location-updates';

/**
 * Tarefa de segundo plano.
 *
 * O motor vive AQUI, nao na tela: o app passa 99% do tempo fechado, e e
 * justamente nesse tempo que as vagas sao liberadas. A instancia do detector
 * e criada uma vez por processo e sobrevive entre as chamadas da tarefa.
 */
let detector: ParkingDetector | null = null;

async function getDetector(): Promise<ParkingDetector> {
  if (!detector) {
    const cfg = await loadConfig();
    detector = new ParkingDetector({
      exclusionZones: cfg.exclusionZones,
      // O contexto de mapa vem de um extrato offline do OpenStreetMap
      // embarcado por cidade; sem ele o motor ainda funciona, so com
      // confianca menor. Ver docs/ARQUITETURA.md.
      contextProvider: cfg.contextProvider,
    });
  }
  return detector;
}

TaskManager.defineTask(LOCATION_TASK, async ({ data, error }) => {
  if (error) {
    console.warn('[vagas] erro na tarefa de localizacao', error.message);
    return;
  }
  const locations = (data as { locations?: Location.LocationObject[] } | null)?.locations ?? [];
  if (locations.length === 0) return;

  const det = await getDetector();

  for (const loc of locations) {
    const sample: LocationSample = {
      t: loc.timestamp,
      lat: loc.coords.latitude,
      lon: loc.coords.longitude,
      speed: loc.coords.speed,
      accuracy: loc.coords.accuracy,
      speedAccuracy: (loc.coords as { speedAccuracy?: number }).speedAccuracy ?? null,
      activityHint: null,
    };

    for (const event of det.push(sample)) {
      await onEvent(event);
    }
  }

  await adaptAccuracy(det.currentState);
});

async function onEvent(event: ParkingEvent): Promise<void> {
  // Chegada tambem serve ao proprio dono: e o "onde eu deixei o carro".
  if (event.kind === 'arrival') {
    await rememberParkedSpot({ lat: event.lat, lon: event.lon, t: event.t });
  }
  // Fila local com reenvio: a deteccao costuma acontecer sem sinal bom.
  await enqueueEvent(event);
}

/**
 * Precisao adaptativa — o unico motivo pelo qual este app pode rodar o dia
 * todo sem acabar com a bateria.
 *
 *   parado/caminhando -> GPS fraco e raro (a pessoa nao esta num carro);
 *   dirigindo          -> GPS bom, porque e aqui que o evento acontece.
 */
async function adaptAccuracy(state: string): Promise<void> {
  const driving = state === 'in_vehicle' || state === 'vehicle_stopped';
  await Location.startLocationUpdatesAsync(LOCATION_TASK, {
    accuracy: driving ? Location.Accuracy.High : Location.Accuracy.Balanced,
    timeInterval: driving ? 3_000 : 20_000,
    distanceInterval: driving ? 10 : 50,
    deferredUpdatesInterval: driving ? 0 : 60_000,
    pausesUpdatesAutomatically: true,
    activityType: Location.ActivityType.AutomotiveNavigation,
    showsBackgroundLocationIndicator: true,
    foregroundService: {
      notificationTitle: 'Vagas',
      notificationBody: 'Detectando vagas liberadas na sua regiao',
      notificationColor: '#16a34a',
    },
  });
}

export async function startTracking(): Promise<'ok' | 'sem-permissao'> {
  const fg = await Location.requestForegroundPermissionsAsync();
  if (fg.status !== 'granted') return 'sem-permissao';
  const bg = await Location.requestBackgroundPermissionsAsync();
  if (bg.status !== 'granted') return 'sem-permissao';

  const already = await Location.hasStartedLocationUpdatesAsync(LOCATION_TASK);
  if (!already) await adaptAccuracy('unknown');
  return 'ok';
}

export async function stopTracking(): Promise<void> {
  const running = await Location.hasStartedLocationUpdatesAsync(LOCATION_TASK);
  if (running) await Location.stopLocationUpdatesAsync(LOCATION_TASK);
  detector = null;
}
