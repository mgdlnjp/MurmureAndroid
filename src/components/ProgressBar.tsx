import { StyleSheet, View } from 'react-native';

import { useTheme } from '@/src/theme';

export function ProgressBar({ value }: { value: number }) {
  const theme = useTheme();
  const pct = Math.max(0, Math.min(1, Number.isFinite(value) ? value : 0));
  return (
    <View style={[styles.track, { backgroundColor: theme.border }]}>
      <View style={[styles.fill, { width: `${pct * 100}%`, backgroundColor: theme.accent }]} />
    </View>
  );
}

const styles = StyleSheet.create({
  track: { height: 8, borderRadius: 4, overflow: 'hidden', marginTop: 8 },
  fill: { height: 8, borderRadius: 4 },
});
