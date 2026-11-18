import { router } from 'expo-router';
import { ScrollView, StyleSheet, Text, View } from 'react-native';

import { PrimaryButton } from '@/src/components/PrimaryButton';
import { StatusRow } from '@/src/components/StatusRow';
import { strings } from '@/src/i18n';
import { isReady } from '@/src/onboarding';
import { spacing, useTheme } from '@/src/theme';
import { useModels, usePermissionStatus, useSettings } from '@/src/useNativeState';

export default function HomeScreen() {
  const theme = useTheme();
  const [status] = usePermissionStatus();
  const { models } = useModels();
  const [settings] = useSettings();
  const selected = models.find((m) => m.id === settings.modelId);
  const modelInstalled = selected?.installed ?? false;
  const ready = isReady(status, modelInstalled);

  return (
    <ScrollView style={{ backgroundColor: theme.background }} contentContainerStyle={styles.content}>
      <Text style={[styles.title, { color: theme.accent }]}>{strings.appName}</Text>
      <Text style={[styles.tagline, { color: theme.text }]}>{strings.tagline}</Text>

      <View style={[styles.card, { backgroundColor: theme.card, borderColor: theme.border }]}>
        <Text style={[styles.section, { color: theme.textSecondary }]}>{strings.home.status}</Text>
        <StatusRow
          label={strings.home.service}
          ok={status.accessibility}
          value={status.accessibility ? strings.home.active : strings.home.inactive}
        />
        <StatusRow
          label={strings.home.microphone}
          ok={status.microphone}
          value={status.microphone ? strings.home.granted : strings.home.denied}
        />
        <StatusRow
          label={strings.home.model}
          ok={modelInstalled}
          value={modelInstalled ? strings.home.ready : strings.home.missing}
        />
        <StatusRow
          label={strings.home.bubble}
          ok={settings.bubbleEnabled}
          value={settings.bubbleEnabled ? strings.home.enabled : strings.home.disabled}
        />
      </View>

      {ready ? (
        <Text style={[styles.text, { color: theme.text }]}>{strings.home.allReady}</Text>
      ) : (
        <PrimaryButton title={strings.home.finishSetup} onPress={() => router.push('/onboarding')} />
      )}
      {!modelInstalled ? (
        <PrimaryButton title={strings.home.manageModel} variant="secondary" onPress={() => router.push('/model')} />
      ) : null}

      <Text style={[styles.text, { color: theme.textSecondary }]}>{strings.home.howTo}</Text>
      <Text style={[styles.text, { color: theme.textSecondary }]}>{strings.home.privacy}</Text>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: { padding: spacing.md, paddingBottom: spacing.xl },
  title: { fontSize: 34, fontWeight: '800', marginTop: spacing.sm },
  tagline: { fontSize: 18, fontWeight: '500', marginBottom: spacing.lg },
  card: { borderRadius: 16, borderWidth: StyleSheet.hairlineWidth, padding: spacing.md, marginBottom: spacing.md },
  section: { fontSize: 13, fontWeight: '600', textTransform: 'uppercase', marginBottom: spacing.xs },
  text: { fontSize: 15, lineHeight: 22, marginTop: spacing.md },
});
