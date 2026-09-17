export const theme = {
  color: {
    bg: '#0b1220',
    surface: '#151d2e',
    text: '#e8eefc',
    muted: '#93a3bf',
    // Verde = vaga provavel. E o unico verde do app, para nao competir com nada.
    high: '#22c55e',
    medium: '#f59e0b',
    low: '#64748b',
    car: '#38bdf8',
    danger: '#ef4444',
  },
  radius: { sm: 8, md: 14, lg: 22 },
  space: (n: number) => n * 8,
} as const;

export function tierColor(tier: 'alta' | 'media' | 'baixa'): string {
  if (tier === 'alta') return theme.color.high;
  if (tier === 'media') return theme.color.medium;
  return theme.color.low;
}

/** "ha 3 min" — idade e mais util que horario absoluto nessa tela. */
export function ageLabel(t: number, now = Date.now()): string {
  const min = Math.max(0, Math.round((now - t) / 60000));
  if (min < 1) return 'agora';
  if (min === 1) return 'ha 1 min';
  if (min < 60) return `ha ${min} min`;
  const h = Math.floor(min / 60);
  return h === 1 ? 'ha 1 h' : `ha ${h} h`;
}
