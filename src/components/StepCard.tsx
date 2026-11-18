import type { ReactNode } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { radius, spacing, useTheme } from '@/src/theme';

type Props = { title: string; body?: string; footer?: string; children?: ReactNode };

export function StepCard({ title, body, footer, children }: Props) {
  const theme = useTheme();
  return (
    <View style={[styles.card, { backgroundColor: theme.card, borderColor: theme.border }]}>
      <Text style={[styles.title, { color: theme.text }]}>{title}</Text>
      {body ? <Text style={[styles.body, { color: theme.textSecondary }]}>{body}</Text> : null}
      {children}
      {footer ? <Text style={[styles.footer, { color: theme.textSecondary }]}>{footer}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  card: { borderRadius: radius.lg, borderWidth: StyleSheet.hairlineWidth, padding: spacing.md, marginBottom: spacing.md },
  title: { fontSize: 20, fontWeight: '700', marginBottom: spacing.sm },
  body: { fontSize: 15, lineHeight: 22, marginBottom: spacing.sm },
  footer: { fontSize: 13, marginTop: spacing.sm },
});
