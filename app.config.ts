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
  plugins: [
    'expo-router',
    'expo-localization',
    // minSdk 26 (Android 8.0) : NotificationChannel, isShowingHintText, service de premier plan micro.
    ['expo-build-properties', { android: { minSdkVersion: 26 } }],
    './plugins/withMurmure.js',
  ],
  experiments: {
    typedRoutes: true,
  },
  extra: {
    eas: {
      projectId: '0a19d052-fb5f-4d43-a4c7-96d835c1caa1',
    },
  },
};

export default config;
