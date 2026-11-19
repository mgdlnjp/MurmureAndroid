import { router } from 'expo-router';
import { useCallback, useMemo, useState } from 'react';
import { PermissionsAndroid, Pressable, ScrollView, StyleSheet, Text } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import MurmureNative from '@/modules/murmure-native';
import { ModelPicker } from '@/src/components/ModelPicker';
import { PrimaryButton } from '@/src/components/PrimaryButton';
import { StepCard } from '@/src/components/StepCard';
import { strings } from '@/src/i18n';
import { nextOnboardingStep, ONBOARDING_STEPS, SKIPPABLE_STEPS, type StepId } from '@/src/onboarding';
import { spacing, useTheme } from '@/src/theme';
import { useModels, usePermissionStatus, useSettings } from '@/src/useNativeState';

const t = strings.onboarding;

export default function OnboardingScreen() {
  const theme = useTheme();
  const [status, refreshStatus] = usePermissionStatus();
  const { models } = useModels();
  const [settings, setSettings] = useSettings();
  const [seenWelcome, setSeenWelcome] = useState(settings.onboardingDone);
  const [skipped, setSkipped] = useState<Set<StepId>>(new Set());
  // Affichage forcé de l'explication « paramètres restreints » depuis l'étape accessibilité.
  const [forceRestricted, setForceRestricted] = useState(false);
  const [micDenied, setMicDenied] = useState(false);

  const modelInstalled = models.find((m) => m.id === settings.modelId)?.installed ?? false;
  const computed = nextOnboardingStep(status, modelInstalled, seenWelcome, skipped);
  const step: StepId = forceRestricted && computed === 'accessibility' ? 'restricted' : computed;
  const index = ONBOARDING_STEPS.indexOf(step) + 1;

  const skip = useCallback(() => {
    if (step === 'restricted') setForceRestricted(false);
    setSkipped((prev) => new Set(prev).add(step));
  }, [step]);

  const requestMicrophone = async () => {
    const result = await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.RECORD_AUDIO);
    setMicDenied(result === PermissionsAndroid.RESULTS.NEVER_ASK_AGAIN || result === PermissionsAndroid.RESULTS.DENIED);
    refreshStatus();
  };

  const requestNotifications = async () => {
    await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);
    refreshStatus();
    setSkipped((prev) => new Set(prev).add('notifications'));
  };

  const finish = () => {
    setSettings({ onboardingDone: true });
    router.replace('/');
  };

  const content = useMemo(() => {
    switch (step) {
      case 'welcome':
        return (
          <StepCard title={t.welcomeTitle} body={t.welcomeBody}>
            <Text style={[styles.tagline, { color: theme.accent }]}>{strings.tagline}</Text>
            <PrimaryButton title={t.start} onPress={() => setSeenWelcome(true)} />
          </StepCard>
        );
      case 'microphone':
        return (
          <StepCard title={t.micTitle} body={t.micBody} footer={micDenied ? t.micDenied : undefined}>
            <PrimaryButton title={t.micButton} onPress={requestMicrophone} />
            {micDenied ? (
              <PrimaryButton title={t.openAppSettings} variant="secondary" onPress={() => MurmureNative.openAppDetailsSettings()} />
            ) : null}
          </StepCard>
        );
      case 'notifications':
        return (
          <StepCard title={t.notifTitle} body={t.notifBody}>
            <PrimaryButton title={t.notifButton} onPress={requestNotifications} />
          </StepCard>
        );
      case 'overlay':
        return (
          <StepCard title={t.overlayTitle} body={t.overlayBody}>
            <PrimaryButton title={t.overlayButton} onPress={() => MurmureNative.openOverlaySettings()} />
          </StepCard>
        );
      case 'restricted':
        return (
          <StepCard title={t.restrictedTitle} body={t.restrictedBody} footer={t.restrictedNote}>
            <PrimaryButton title={t.restrictedButton} onPress={() => MurmureNative.openAppDetailsSettings()} />
          </StepCard>
        );
      case 'accessibility':
        return (
          <StepCard title={t.a11yTitle} body={t.a11yBody} footer={t.a11yPath}>
            <PrimaryButton title={t.a11yButton} onPress={() => MurmureNative.openAccessibilitySettings()} />
            {status.sdkInt >= 33 ? (
              <Pressable onPress={() => setForceRestricted(true)} accessibilityRole="link" style={styles.link}>
                <Text style={{ color: theme.accentDark, fontWeight: '600' }}>{t.a11yGreyed}</Text>
              </Pressable>
            ) : null}
          </StepCard>
        );
      case 'model':
        return (
          <StepCard title={t.modelTitle}>
            <ModelPicker />
          </StepCard>
        );
      case 'done':
        return (
          <StepCard title={t.doneTitle} body={t.doneBody}>
            <PrimaryButton title={t.finish} onPress={finish} />
          </StepCard>
        );
    }
  }, [step, theme, micDenied, status.sdkInt]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]}>
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={[styles.brand, { color: theme.accent }]}>{strings.appName}</Text>
        <Text style={[styles.progress, { color: theme.textSecondary }]}>{t.stepOf(index, ONBOARDING_STEPS.length)}</Text>
        {content}
        {SKIPPABLE_STEPS.has(step) ? <PrimaryButton title={t.skip} variant="secondary" onPress={skip} /> : null}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  content: { padding: spacing.md, paddingBottom: spacing.xl },
  brand: { fontSize: 28, fontWeight: '800', marginTop: spacing.sm },
  progress: { fontSize: 13, marginBottom: spacing.md },
  tagline: { fontSize: 17, fontWeight: '600', marginBottom: spacing.sm },
  link: { marginTop: spacing.md, alignSelf: 'flex-start' },
});
