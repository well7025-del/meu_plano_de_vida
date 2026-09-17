import { useEffect, useState } from 'react';
import { Pressable, SafeAreaView, StyleSheet, Text, View } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { MapScreen } from './src/screens/MapScreen';
import { SettingsScreen } from './src/screens/SettingsScreen';
import { startTracking } from './src/tracking/locationTask';
import { theme } from './src/theme';

type Tab = 'mapa' | 'ajustes';

export default function App() {
  const [tab, setTab] = useState<Tab>('mapa');
  const [permissao, setPermissao] = useState<'ok' | 'sem-permissao' | 'pendente'>('pendente');

  useEffect(() => {
    void startTracking().then(setPermissao);
  }, []);

  return (
    <SafeAreaView style={styles.fill}>
      <StatusBar style="light" />
      {tab === 'mapa' ? <MapScreen /> : <SettingsScreen />}

      {permissao === 'sem-permissao' && tab === 'mapa' && (
        <View style={styles.banner}>
          <Text style={styles.bannerText}>
            Sem permissao de localizacao em segundo plano o app so consulta vagas —
            nao detecta as suas.
          </Text>
        </View>
      )}

      <View style={styles.tabs}>
        <TabButton label="Mapa" active={tab === 'mapa'} onPress={() => setTab('mapa')} />
        <TabButton label="Ajustes" active={tab === 'ajustes'} onPress={() => setTab('ajustes')} />
      </View>
    </SafeAreaView>
  );
}

function TabButton({
  label,
  active,
  onPress,
}: {
  label: string;
  active: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable onPress={onPress} style={[styles.tab, active && styles.tabActive]}>
      <Text style={[styles.tabText, active && styles.tabTextActive]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1, backgroundColor: theme.color.bg },
  tabs: {
    flexDirection: 'row',
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: '#22304a',
    backgroundColor: theme.color.surface,
  },
  tab: { flex: 1, paddingVertical: 14, alignItems: 'center' },
  tabActive: { borderTopWidth: 2, borderTopColor: theme.color.high },
  tabText: { color: theme.color.muted, fontSize: 14 },
  tabTextActive: { color: theme.color.text, fontWeight: '600' },
  banner: {
    position: 'absolute',
    top: 8,
    left: 12,
    right: 12,
    backgroundColor: '#3b2a10',
    padding: 12,
    borderRadius: theme.radius.md,
  },
  bannerText: { color: '#fde68a', fontSize: 12, lineHeight: 17 },
});
