import { useState } from 'react';
import { Alert, StyleSheet, Text, View } from 'react-native';

import MurmureNative, { type DownloadError, type ModelId, type ModelInfo } from '@/modules/murmure-native';
import { formatBytes } from '@/src/format';
import { isFrench, strings, uiLanguage } from '@/src/i18n';
import { radius, spacing, useTheme } from '@/src/theme';
import { useModels, useSettings } from '@/src/useNativeState';

import { PrimaryButton } from './PrimaryButton';
import { ProgressBar } from './ProgressBar';

const ERROR_CODES: DownloadError[] = ['NETWORK', 'CHECKSUM', 'INSUFFICIENT_STORAGE', 'CANCELLED', 'UNKNOWN'];

function toErrorCode(e: unknown): DownloadError {
  const code = (e as { code?: string } | null)?.code;
  return ERROR_CODES.find((c) => c === code) ?? 'UNKNOWN';
}

/** Choix et gestion des modèles (utilisé par l'écran /model et l'onboarding). */
export function ModelPicker() {
  const theme = useTheme();
  const { models, progress, refresh } = useModels();
  const [settings, setSettings] = useSettings();
  const [error, setError] = useState<string | null>(null);

  const selected = models.find((m) => m.selected);

  const download = async (id: ModelId) => {
    setError(null);
    const start = MurmureNative.downloadModel(id);
    refresh();
    try {
      await start;
    } catch (e) {
      const code = toErrorCode(e);
      if (code !== 'CANCELLED') setError(strings.model.errors[code]);
    } finally {
      refresh();
    }
  };

  const confirmDelete = (m: ModelInfo) => {
    Alert.alert(strings.model.deleteTitle, isFrench ? m.labelFr : m.labelEn, [
      { text: strings.history.cancel, style: 'cancel' },
      {
        text: strings.model.delete,
        style: 'destructive',
        onPress: () => {
          MurmureNative.deleteModel(m.id);
          refresh();
        },
      },
    ]);
  };

  return (
    <View>
      <Text style={[styles.intro, { color: theme.textSecondary }]}>{strings.model.intro}</Text>
      <Text style={[styles.intro, { color: theme.textSecondary }]}>{strings.model.mobileData}</Text>
      {selected && !selected.installed ? (
        <Text style={[styles.warning, { color: theme.accentDark }]}>{strings.model.required}</Text>
      ) : null}
      {error ? <Text style={[styles.warning, { color: theme.danger }]}>{error}</Text> : null}
      {models.map((m) => {
        const p = progress[m.id];
        const bytes = p?.bytes ?? m.partialBytes;
        const isSelected = m.id === settings.modelId;
        return (
          <View
            key={m.id}
            style={[
              styles.card,
              { backgroundColor: theme.card, borderColor: isSelected ? theme.accent : theme.border },
            ]}>
            <View style={styles.header}>
              <Text style={[styles.label, { color: theme.text }]}>{isFrench ? m.labelFr : m.labelEn}</Text>
              <Text style={{ color: theme.textSecondary }}>{formatBytes(m.sizeBytes, uiLanguage)}</Text>
            </View>
            <Text style={{ color: m.installed ? theme.success : theme.textSecondary }}>
              {[m.installed ? strings.model.installed : null, isSelected ? strings.model.selected : null]
                .filter(Boolean)
                .join(' · ')}
            </Text>
            {m.downloading || (bytes > 0 && !m.installed) ? (
              <View>
                <ProgressBar value={bytes / m.sizeBytes} />
                <Text style={[styles.small, { color: theme.textSecondary }]}>
                  {formatBytes(bytes, uiLanguage)} / {formatBytes(m.sizeBytes, uiLanguage)}
                </Text>
              </View>
            ) : null}
            {m.downloading ? (
              <PrimaryButton title={strings.model.cancel} variant="secondary" onPress={() => MurmureNative.cancelDownload(m.id)} />
            ) : !m.installed ? (
              <PrimaryButton
                title={m.partialBytes > 0 ? strings.model.resume : strings.model.download}
                onPress={() => download(m.id)}
                disabled={models.some((x) => x.downloading)}
              />
            ) : null}
            {!isSelected ? (
              <PrimaryButton title={strings.model.select} variant="secondary" onPress={() => setSettings({ modelId: m.id })} />
            ) : null}
            {(m.installed || m.partialBytes > 0) && !m.downloading ? (
              <PrimaryButton title={strings.model.delete} variant="danger" onPress={() => confirmDelete(m)} />
            ) : null}
          </View>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  intro: { fontSize: 15, lineHeight: 21, marginBottom: spacing.sm },
  warning: { fontSize: 14, fontWeight: '600', marginBottom: spacing.sm },
  card: { borderRadius: radius.md, borderWidth: 1.5, padding: spacing.md, marginBottom: spacing.md },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 },
  label: { fontSize: 17, fontWeight: '600', flexShrink: 1 },
  small: { fontSize: 12, marginTop: 4 },
});
