import { Pressable, StyleSheet, Text, View } from 'react-native';
import type { Spot } from '../api/spots';
import { ageLabel, theme, tierColor } from '../theme';

interface Props {
  spot: Spot;
  onClose: () => void;
  onFeedback: (spot: Spot, found: boolean) => void;
}

/**
 * Painel de detalhe.
 *
 * O texto explica de onde veio o numero ("2 carros sairam daqui nos ultimos
 * minutos") em vez de mostrar so uma porcentagem. Probabilidade sem
 * justificativa vira desconfianca na primeira vez que o usuario chega e nao
 * encontra a vaga.
 */
export function SpotSheet({ spot, onClose, onFeedback }: Props) {
  const pct = Math.round(spot.probability * 100);
  const saidas = spot.support;

  return (
    <View style={styles.sheet}>
      <View style={styles.header}>
        <View style={[styles.dot, { backgroundColor: tierColor(spot.tier) }]} />
        <Text style={styles.title}>{pct}% de chance de vaga</Text>
        <Pressable onPress={onClose} hitSlop={12}>
          <Text style={styles.close}>✕</Text>
        </Pressable>
      </View>

      <Text style={styles.body}>
        {saidas === 1
          ? 'Um carro saiu deste trecho '
          : `${saidas} movimentos registrados neste trecho, o ultimo `}
        {ageLabel(spot.lastEventT)}.
      </Text>
      <Text style={styles.note}>
        A estimativa cai com o tempo: em regiao movimentada, uma vaga dura
        poucos minutos.
      </Text>

      <View style={styles.actions}>
        <Pressable style={[styles.btn, styles.btnPrimary]} onPress={() => onFeedback(spot, true)}>
          <Text style={styles.btnText}>Achei a vaga</Text>
        </Pressable>
        <Pressable style={[styles.btn, styles.btnGhost]} onPress={() => onFeedback(spot, false)}>
          <Text style={styles.btnTextGhost}>Nao tinha nada</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  sheet: {
    position: 'absolute',
    left: 12,
    right: 12,
    bottom: 12,
    backgroundColor: theme.color.surface,
    borderRadius: theme.radius.lg,
    padding: 18,
    gap: 8,
  },
  header: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  dot: { width: 12, height: 12, borderRadius: 6 },
  title: { color: theme.color.text, fontSize: 18, fontWeight: '600', flex: 1 },
  close: { color: theme.color.muted, fontSize: 16 },
  body: { color: theme.color.text, fontSize: 14, lineHeight: 20 },
  note: { color: theme.color.muted, fontSize: 12, lineHeight: 17 },
  actions: { flexDirection: 'row', gap: 10, marginTop: 8 },
  btn: { flex: 1, paddingVertical: 12, borderRadius: theme.radius.md, alignItems: 'center' },
  btnPrimary: { backgroundColor: theme.color.high },
  btnGhost: { borderWidth: 1, borderColor: theme.color.muted },
  btnText: { color: '#04140a', fontWeight: '700' },
  btnTextGhost: { color: theme.color.text },
});
