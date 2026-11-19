import { DarkTheme, DefaultTheme, Stack, ThemeProvider, router, useSegments } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { useColorScheme } from 'react-native';

import { strings } from '@/src/i18n';
import { palette } from '@/src/theme';
import { useSettings } from '@/src/useNativeState';

export default function RootLayout() {
  const scheme = useColorScheme();
  const [settings] = useSettings();
  const segments = useSegments();

  // Onboarding non terminé → redirection (sauf si on y est déjà ou sur l'écran modèle).
  useEffect(() => {
    const current = segments[0];
    if (!settings.onboardingDone && current !== 'onboarding' && current !== 'model') {
      router.replace('/onboarding');
    }
  }, [settings.onboardingDone, segments]);

  const base = scheme === 'dark' ? DarkTheme : DefaultTheme;
  const theme = { ...base, colors: { ...base.colors, primary: palette.accent } };

  return (
    <ThemeProvider value={theme}>
      <StatusBar style="auto" />
      <Stack>
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="onboarding" options={{ headerShown: false }} />
        <Stack.Screen name="model" options={{ title: strings.model.title }} />
      </Stack>
    </ThemeProvider>
  );
}
