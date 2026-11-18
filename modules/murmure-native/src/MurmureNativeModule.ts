import { NativeModule, requireNativeModule } from 'expo';

import type { MurmureNativeModuleEvents, MurmureNativeModuleType } from './MurmureNative.types';

declare class MurmureNativeModule
  extends NativeModule<MurmureNativeModuleEvents>
  implements MurmureNativeModuleType
{
  getPermissionStatus: MurmureNativeModuleType['getPermissionStatus'];
  openAccessibilitySettings: MurmureNativeModuleType['openAccessibilitySettings'];
  openOverlaySettings: MurmureNativeModuleType['openOverlaySettings'];
  openAppDetailsSettings: MurmureNativeModuleType['openAppDetailsSettings'];
  getSettings: MurmureNativeModuleType['getSettings'];
  setSettings: MurmureNativeModuleType['setSettings'];
  getModels: MurmureNativeModuleType['getModels'];
  downloadModel: MurmureNativeModuleType['downloadModel'];
  cancelDownload: MurmureNativeModuleType['cancelDownload'];
  deleteModel: MurmureNativeModuleType['deleteModel'];
  getHistory: MurmureNativeModuleType['getHistory'];
  deleteHistoryItem: MurmureNativeModuleType['deleteHistoryItem'];
  clearHistory: MurmureNativeModuleType['clearHistory'];
  copyToClipboard: MurmureNativeModuleType['copyToClipboard'];
}

export default requireNativeModule<MurmureNativeModule>('MurmureNative');
