import { useEffect, useState } from 'react';
import { ScrollView, StyleSheet, Switch, Text, View } from 'react-native';
import { loadConfig, setFlag, type AppConfig } from '../state/settings';
import { pendingCount } from '../api/queue';
import { theme } from '../theme';

/**
 * Ajustes.
 *
 * A primeira coisa da tela e o que o app faz com os dados, em portugues
 * simples. Um app que pede localizacao em segundo plano 24 h por dia tem a
 * obrigacao de explicar o negocio antes de pedir o consentimento.
 */
export function SettingsScreen() {
  const [cfg, setCfg] = useState<AppConfig | null>(null);
  const [pending, setPending] = useState(0);

  useEffect(() => {
    void loadConfig().then(setCfg);
    setPending(pendingCount());
  }, []);

  if (!cfg) return null;

  return (
    <ScrollView style={styles.fill} contentContainerStyle={styles.content}>
      <Text style={styles.h1}>Privacidade</Text>
      <Text style={styles.p}>
        A deteccao acontece dentro do seu celular. Seu trajeto nunca sai daqui.
        Quando o app conclui que uma vaga foi liberada, ele envia apenas tres
        coisas: o ponto aproximado, o horario e o quanto ele confia nisso —
        sem nome, sem conta, sem identificador do aparelho.
      </Text>

      <Row
        label="Contribuir com a rede"
        hint="Desligado, voce continua vendo as vagas, mas nao envia nenhuma."
        value={cfg.contribute}
        onChange={(v) => {
          setFlag('contribute', v);
          setCfg({ ...cfg, contribute: v });
        }}
      />

      <Row
        label="Avisar quando abrir vaga perto do destino"
        hint="So durante uma navegacao ativa."
        value={cfg.notifyNearDestination}
        onChange={(v) => {
          setFlag('notifyNearDestination', v);
          setCfg({ ...cfg, notifyNearDestination: v });
        }}
      />

      <Text style={styles.h1}>Zonas silenciosas</Text>
      <Text style={styles.p}>
        Dentro delas o app nunca anuncia vaga. Sua garagem entra aqui: a saida
        de casa tem a mesma assinatura de uma vaga de rua e anunciaria uma vaga
        que nao existe.
      </Text>
      {cfg.exclusionZones.length === 0 ? (
        <Text style={styles.empty}>Nenhuma zona cadastrada.</Text>
      ) : (
        cfg.exclusionZones.map((z) => (
          <Text key={z.id} style={styles.zone}>
            • {z.label ?? 'Sem nome'} — raio de {Math.round(z.radiusM)} m
          </Text>
        ))
      )}

      <Text style={styles.h1}>Fila de envio</Text>
      <Text style={styles.p}>
        {pending === 0
          ? 'Tudo enviado.'
          : `${pending} evento(s) aguardando rede. Eles saem em lote, com atraso de ate 2 minutos.`}
      </Text>
    </ScrollView>
  );
}

function Row({
  label,
  hint,
  value,
  onChange,
}: {
  label: string;
  hint: string;
  value: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <View style={styles.row}>
      <View style={styles.rowText}>
        <Text style={styles.label}>{label}</Text>
        <Text style={styles.hint}>{hint}</Text>
      </View>
      <Switch value={value} onValueChange={onChange} trackColor={{ true: theme.color.high }} />
    </View>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1, backgroundColor: theme.color.bg },
  content: { padding: 20, gap: 12, paddingBottom: 60 },
  h1: { color: theme.color.text, fontSize: 20, fontWeight: '700', marginTop: 16 },
  p: { color: theme.color.muted, fontSize: 14, lineHeight: 20 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: theme.color.surface,
    borderRadius: theme.radius.md,
    padding: 14,
  },
  rowText: { flex: 1, gap: 4 },
  label: { color: theme.color.text, fontSize: 15, fontWeight: '600' },
  hint: { color: theme.color.muted, fontSize: 12, lineHeight: 17 },
  empty: { color: theme.color.muted, fontStyle: 'italic' },
  zone: { color: theme.color.text, fontSize: 14 },
});
