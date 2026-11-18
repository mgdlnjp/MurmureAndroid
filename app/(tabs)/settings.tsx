import Constants from 'expo-constants';
import { router } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, Switch, Text, View } from 'react-native';

import type { DictationLanguage } from '@/modules/murmure-native';
import { PrimaryButton } from '@/src/components/PrimaryButton';
import { strings } from '@/src/i18n';
import { palette, radius, spacing, useTheme } from '@/src/theme';
import { useSettings } from '@/src/useNativeState';

const LANGUAGES: { id: DictationLanguage; label: string }[] = [
  { id: 'auto', label: strings.settings.languageAuto },
  { id: 'fr', label: strings.settings.languageFr },
  { id: 'en', label: strings.settings.languageEn },
];

export default function SettingsScreen() {
  const theme = useTheme();
  const [settings, update] = useSettings();
  const cardStyle = [styles.card, { backgroundColor: theme.card, borderColor: theme.border }];

  return (
    <ScrollView style={{ backgroundColor: theme.background }} contentContainerStyle={styles.content}>
      <View style={cardStyle}>
        <View style={styles.row}>
          <Text style={[styles.label, { color: theme.text }]}>{strings.settings.bubble}</Text>
          <Switch
            value={settings.bubbleEnabled}
            onValueChange={(v) => update({ bubbleEnabled: v })}
            trackColor={{ true: palette.accent }}
            thumbColor={palette.white}
          />
        </View>
        <Text style={[styles.hint, { color: theme.textSecondary }]}>{strings.settings.bubbleHint}</Text>
      </View>

      <View style={cardStyle}>
        <Text style={[styles.label, { color: theme.text }]}>{strings.settings.language}</Text>
        <View style={styles.segment}>
          {LANGUAGES.map((l) => {
            const active = settings.language === l.id;
            return (
              <Pressable
                key={l.id}
                accessibilityRole="radio"
                accessibilityState={{ selected: active }}
                onPress={() => update({ language: l.id })}
                style={[
                  styles.segmentItem,
                  { borderColor: active ? theme.accent : theme.border, backgroundColor: active ? theme.accent : 'transparent' },
                ]}>
                <Text style={{ color: active ? theme.white : theme.text, fontWeight: '600' }}>{l.label}</Text>
              </Pressable>
            );
          })}
        </View>
        <Text style={[styles.hint, { color: theme.textSecondary }]}>{strings.settings.languageHint}</Text>
      </View>

      <View style={cardStyle}>
        <PrimaryButton title={strings.settings.model} variant="secondary" onPress={() => router.push('/model')} />
        <PrimaryButton title={strings.settings.permissions} variant="secondary" onPress={() => router.push('/onboarding')} />
      </View>

      <View style={cardStyle}>
        <Text style={[styles.label, { color: theme.text }]}>{strings.settings.about}</Text>
        <Text style={[styles.hint, { color: theme.textSecondary }]}>{strings.tagline}</Text>
        <Text style={[styles.hint, { color: theme.textSecondary }]}>{strings.settings.aboutText}</Text>
        <Text style={[styles.hint, { color: theme.textSecondary }]}>
          {strings.settings.version} {Constants.expoConfig?.version ?? '0.1.0'}
        </Text>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: { padding: spacing.md, paddingBottom: spacing.xl },
  card: { borderRadius: radius.lg, borderWidth: StyleSheet.hairlineWidth, padding: spacing.md, marginBottom: spacing.md },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  label: { fontSize: 16, fontWeight: '600' },
  hint: { fontSize: 14, lineHeight: 20, marginTop: spacing.sm },
  segment: { flexDirection: 'row', gap: spacing.sm, marginTop: spacing.sm },
  segmentItem: { flex: 1, alignItems: 'center', paddingVertical: 10, borderRadius: radius.sm, borderWidth: 1 },
});
