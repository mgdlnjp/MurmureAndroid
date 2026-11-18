import { StyleSheet, Text, View } from 'react-native';

import { spacing, useTheme } from '@/src/theme';

export function StatusRow({ label, ok, value }: { label: string; ok: boolean; value: string }) {
  const theme = useTheme();
  return (
    <View style={[styles.row, { borderBottomColor: theme.border }]}>
      <Text style={[styles.label, { color: theme.text }]}>{label}</Text>
      <View style={styles.right}>
        <View style={[styles.dot, { backgroundColor: ok ? theme.success : theme.danger }]} />
        <Text style={{ color: theme.textSecondary }}>{value}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: spacing.sm + 2,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  label: { fontSize: 15 },
  right: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  dot: { width: 8, height: 8, borderRadius: 4 },
});
