// Mock du module natif : la logique pure est testée sans Android.
jest.mock('./modules/murmure-native', () => {
  const settings = { bubbleEnabled: true, modelId: 'small-q5_1', language: 'auto', onboardingDone: false };
  const subscription = { remove: jest.fn() };
  return {
    __esModule: true,
    default: {
      getPermissionStatus: jest.fn(() => ({
        microphone: false,
        overlay: false,
        accessibility: false,
        notifications: true,
        sdkInt: 34,
      })),
      openAccessibilitySettings: jest.fn(),
      openOverlaySettings: jest.fn(),
      openAppDetailsSettings: jest.fn(),
      getSettings: jest.fn(() => ({ ...settings })),
      setSettings: jest.fn((patch: object) => ({ ...settings, ...patch })),
      getModels: jest.fn(() => []),
      downloadModel: jest.fn(() => Promise.resolve()),
      cancelDownload: jest.fn(),
      deleteModel: jest.fn(),
      getHistory: jest.fn(() => []),
      deleteHistoryItem: jest.fn(),
      clearHistory: jest.fn(),
      copyToClipboard: jest.fn(),
      addListener: jest.fn(() => subscription),
    },
  };
});

jest.mock('expo-localization', () => ({
  getLocales: () => [{ languageCode: 'fr', languageTag: 'fr-FR' }],
}));
