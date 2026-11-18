// Cycle 1, A1 : la configuration Expo désactive la sauvegarde Android.
import config from '../app.config';

describe('app.config', () => {
  it('désactive la sauvegarde Android (allowBackup: false)', () => {
    expect(config.android?.allowBackup).toBe(false);
  });

  it('déclare le config plugin Murmure', () => {
    expect(config.plugins).toContain('./plugins/withMurmure.js');
  });

  // Cycle 2, R1 : minSdk 26 (NotificationChannel, isShowingHintText).
  it('fixe minSdkVersion 26 via expo-build-properties', () => {
    const entry = config.plugins?.find(
      (p): p is [string, { android?: { minSdkVersion?: number } }] =>
        Array.isArray(p) && p[0] === 'expo-build-properties',
    );
    expect(entry).toBeDefined();
    expect(entry?.[1].android?.minSdkVersion).toBe(26);
  });
});
