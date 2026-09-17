import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import MapView, { Circle, Marker, type Region } from 'react-native-maps';
import * as Location from 'expo-location';
import { fetchSpots, sendFeedback, type Spot } from '../api/spots';
import { getParkedSpot } from '../state/parked';
import { ageLabel, theme, tierColor } from '../theme';
import { SpotSheet } from '../components/SpotSheet';

const REFRESH_MS = 20_000;

/**
 * Tela principal.
 *
 * Decisao de produto: o mapa mostra circulos, nao alfinetes. Alfinete promete
 * "a vaga esta exatamente aqui" — promessa que o dado nao sustenta. O circulo
 * diz a verdade: "em algum ponto deste trecho, ha pouco, alguem saiu".
 * O raio cresce conforme a informacao envelhece.
 */
export function MapScreen() {
  const mapRef = useRef<MapView | null>(null);
  const [region, setRegion] = useState<Region | null>(null);
  const [spots, setSpots] = useState<Spot[]>([]);
  const [selected, setSelected] = useState<Spot | null>(null);
  const [loading, setLoading] = useState(true);
  const parked = getParkedSpot();

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') return;
      const pos = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.Balanced,
      });
      if (cancelled) return;
      setRegion({
        latitude: pos.coords.latitude,
        longitude: pos.coords.longitude,
        latitudeDelta: 0.008,
        longitudeDelta: 0.008,
      });
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const load = useCallback(async (r: Region) => {
    const radiusM = Math.min(3000, r.latitudeDelta * 111_000);
    try {
      const data = await fetchSpots({ lat: r.latitude, lon: r.longitude }, radiusM);
      setSpots(data);
    } catch {
      // Sem rede o app fica util assim mesmo: o ultimo conjunto continua na
      // tela, com a idade visivel para o usuario julgar.
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!region) return;
    void load(region);
    const id = setInterval(() => void load(region), REFRESH_MS);
    return () => clearInterval(id);
  }, [region, load]);

  const onFeedback = useCallback(async (spot: Spot, found: boolean) => {
    setSelected(null);
    setSpots((prev) => (found ? prev : prev.filter((s) => s.cell !== spot.cell)));
    await sendFeedback(spot, found);
  }, []);

  if (!region) {
    return (
      <View style={[styles.fill, styles.center]}>
        <ActivityIndicator color={theme.color.high} />
        <Text style={styles.muted}>Localizando voce...</Text>
      </View>
    );
  }

  return (
    <View style={styles.fill}>
      <MapView
        ref={mapRef}
        style={styles.fill}
        initialRegion={region}
        onRegionChangeComplete={setRegion}
        showsUserLocation
        showsMyLocationButton={false}
        toolbarEnabled={false}
      >
        {spots.map((spot) => (
          <Circle
            key={spot.cell}
            center={{ latitude: spot.lat, longitude: spot.lon }}
            // Informacao velha vira circulo maior: incerteza que se ve.
            radius={30 + (1 - spot.probability) * 50}
            strokeColor={tierColor(spot.tier)}
            fillColor={`${tierColor(spot.tier)}55`}
            strokeWidth={2}
          />
        ))}

        {spots.map((spot) => (
          <Marker
            key={`m-${spot.cell}`}
            coordinate={{ latitude: spot.lat, longitude: spot.lon }}
            onPress={() => setSelected(spot)}
            tracksViewChanges={false}
            anchor={{ x: 0.5, y: 0.5 }}
          >
            <View style={[styles.dot, { backgroundColor: tierColor(spot.tier) }]} />
          </Marker>
        ))}

        {parked && (
          <Marker
            coordinate={{ latitude: parked.lat, longitude: parked.lon }}
            title="Seu carro"
            description={`Estacionado ${ageLabel(parked.t)}`}
            pinColor={theme.color.car}
          />
        )}
      </MapView>

      <View style={styles.legend} pointerEvents="none">
        <Legend color={theme.color.high} label="alta" />
        <Legend color={theme.color.medium} label="media" />
        <Text style={styles.legendNote}>
          {loading ? 'atualizando...' : `${spots.length} pontos`}
        </Text>
      </View>

      <Pressable
        style={styles.fab}
        onPress={() => mapRef.current?.animateToRegion(region, 300)}
        accessibilityLabel="Centralizar no mapa"
      >
        <Text style={styles.fabText}>◎</Text>
      </Pressable>

      {selected && (
        <SpotSheet
          spot={selected}
          onClose={() => setSelected(null)}
          onFeedback={onFeedback}
        />
      )}
    </View>
  );
}

function Legend({ color, label }: { color: string; label: string }) {
  return (
    <View style={styles.legendRow}>
      <View style={[styles.legendDot, { backgroundColor: color }]} />
      <Text style={styles.legendText}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1, backgroundColor: theme.color.bg },
  center: { alignItems: 'center', justifyContent: 'center', gap: 12 },
  muted: { color: theme.color.muted },
  dot: {
    width: 16,
    height: 16,
    borderRadius: 8,
    borderWidth: 2,
    borderColor: '#0b1220',
  },
  legend: {
    position: 'absolute',
    top: 56,
    left: 16,
    backgroundColor: 'rgba(11,18,32,0.85)',
    borderRadius: theme.radius.md,
    padding: 12,
    gap: 6,
  },
  legendRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  legendDot: { width: 10, height: 10, borderRadius: 5 },
  legendText: { color: theme.color.text, fontSize: 12 },
  legendNote: { color: theme.color.muted, fontSize: 11, marginTop: 2 },
  fab: {
    position: 'absolute',
    right: 16,
    bottom: 32,
    width: 52,
    height: 52,
    borderRadius: 26,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: theme.color.surface,
  },
  fabText: { color: theme.color.text, fontSize: 22 },
});
