import { Pressable, StyleSheet, Text } from 'react-native';

import { radius, spacing, useTheme } from '@/src/theme';

type Props = {
  title: string;
  onPress: () => void;
  variant?: 'primary' | 'secondary' | 'danger';
  disabled?: boolean;
};

export function PrimaryButton({ title, onPress, variant = 'primary', disabled }: Props) {
  const theme = useTheme();
  const background = variant === 'primary' ? theme.accent : variant === 'danger' ? theme.danger : theme.card;
  const color = variant === 'secondary' ? theme.text : theme.white;
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      disabled={disabled}
      style={({ pressed }) => [
        styles.button,
        { backgroundColor: background, borderColor: theme.border, opacity: disabled ? 0.5 : pressed ? 0.8 : 1 },
      ]}>
      <Text style={[styles.label, { color }]}>{title}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    paddingVertical: spacing.sm + 4,
    paddingHorizontal: spacing.md,
    borderRadius: radius.md,
    borderWidth: StyleSheet.hairlineWidth,
    alignItems: 'center',
    marginTop: spacing.sm,
  },
  label: { fontSize: 16, fontWeight: '600' },
});
