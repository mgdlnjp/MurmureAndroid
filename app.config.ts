import type { ExpoConfig } from 'expo/config';

const config: ExpoConfig = {
  name: 'Murmure',
  slug: 'murmure-android',
  scheme: 'murmure',
  version: '0.1.0',
  orientation: 'portrait',
  icon: './assets/images/icon.png',
  userInterfaceStyle: 'automatic',
  platforms: ['android'],
  android: {
    package: 'app.murmure.android',
    versionCode: 1,
    // Confidentialité : l'historique dicté ne doit jamais quitter le téléphone (ni Auto Backup ni transfert).
    allowBackup: false,
    // Permissions de stockage ajoutées par défaut par Expo : inutiles ici.
    blockedPermissions: ['android.permission.READ_EXTERNAL_STORAGE', 'android.permission.WRITE_EXTERNAL_STORAGE'],
    adaptiveIcon: {
      backgroundColor: '#F97316',
      foregroundImage: './assets/images/android-icon-foreground.png',
      backgroundImage: './assets/images/android-icon-background.png',
      monochromeImage: './assets/images/android-icon-monochrome.png',
    },
    predictiveBackGestureEnabled: false,
  },
  plugins: ['expo-router', 'expo-localization', './plugins/withMurmure.js'],
  experiments: {
    typedRoutes: true,
  },
};

export default config;
