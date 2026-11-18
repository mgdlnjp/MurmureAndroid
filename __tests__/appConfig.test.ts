// Cycle 1, A1 : la configuration Expo désactive la sauvegarde Android.
import config from '../app.config';

describe('app.config', () => {
  it('désactive la sauvegarde Android (allowBackup: false)', () => {
    expect(config.android?.allowBackup).toBe(false);
  });

  it('déclare le config plugin Murmure', () => {
    expect(config.plugins).toContain('./plugins/withMurmure.js');
  });
});
