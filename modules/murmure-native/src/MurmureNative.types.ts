export type PermissionStatus = {
  /** RECORD_AUDIO accordée */
  microphone: boolean;
  /** Settings.canDrawOverlays */
  overlay: boolean;
  /** Service d'accessibilité Murmure activé */
  accessibility: boolean;
  /** POST_NOTIFICATIONS accordée (API < 33 → true) */
  notifications: boolean;
  /** Build.VERSION.SDK_INT */
  sdkInt: number;
};

export type ModelId = 'small-q5_1' | 'base-q5_1';

export type ModelInfo = {
  id: ModelId;
  labelFr: string;
  labelEn: string;
  sizeBytes: number;
  installed: boolean;
  partialBytes: number;
  selected: boolean;
  downloading: boolean;
};

export type DictationLanguage = 'auto' | 'fr' | 'en';

export type Settings = {
  bubbleEnabled: boolean;
  modelId: ModelId;
  language: DictationLanguage;
  onboardingDone: boolean;
};

export type InsertOutcome = 'SET_TEXT' | 'PASTED' | 'CLIPBOARD_ONLY' | 'NOTHING';

export type HistoryItem = {
  id: string;
  date: number;
  text: string;
  raw: string;
  durationSec: number;
  language: 'fr' | 'en' | null;
  appPackage: string | null;
  outcome: InsertOutcome;
  modelId: string;
};

export type DownloadError = 'NETWORK' | 'CHECKSUM' | 'INSUFFICIENT_STORAGE' | 'CANCELLED' | 'UNKNOWN';

export type DownloadProgressEvent = { modelId: ModelId; bytes: number; total: number };

export type DownloadEndEvent = { modelId: ModelId; ok: boolean; error?: DownloadError };

export type MurmureNativeModuleEvents = {
  onDownloadProgress: (event: DownloadProgressEvent) => void;
  onDownloadEnd: (event: DownloadEndEvent) => void;
  onHistoryChanged: () => void;
  onSettingsChanged: (settings: Settings) => void;
};

export interface MurmureNativeModuleType {
  getPermissionStatus(): PermissionStatus;
  openAccessibilitySettings(): void;
  openOverlaySettings(): void;
  openAppDetailsSettings(): void;
  getSettings(): Settings;
  setSettings(patch: Partial<Settings>): Settings;
  getModels(): ModelInfo[];
  downloadModel(id: ModelId): Promise<void>;
  cancelDownload(id: ModelId): void;
  deleteModel(id: ModelId): void;
  getHistory(): HistoryItem[];
  deleteHistoryItem(id: string): void;
  clearHistory(): void;
  copyToClipboard(text: string): void;
}
