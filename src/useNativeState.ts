import { useCallback, useEffect, useState } from 'react';
import { AppState } from 'react-native';

import MurmureNative, {
  type HistoryItem,
  type ModelInfo,
  type PermissionStatus,
  type Settings,
} from '@/modules/murmure-native';

/** Rappelle [refresh] quand l'app revient au premier plan. */
function useOnAppActive(refresh: () => void) {
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => {
      if (state === 'active') refresh();
    });
    return () => sub.remove();
  }, [refresh]);
}

export function usePermissionStatus(): [PermissionStatus, () => void] {
  const [status, setStatus] = useState<PermissionStatus>(() => MurmureNative.getPermissionStatus());
  const refresh = useCallback(() => setStatus(MurmureNative.getPermissionStatus()), []);
  useOnAppActive(refresh);
  useEffect(() => {
    const sub = MurmureNative.addListener('onSettingsChanged', refresh);
    return () => sub.remove();
  }, [refresh]);
  return [status, refresh];
}

export type DownloadState = Record<string, { bytes: number; total: number }>;

export function useModels(): { models: ModelInfo[]; progress: DownloadState; refresh: () => void } {
  const [models, setModels] = useState<ModelInfo[]>(() => MurmureNative.getModels());
  const [progress, setProgress] = useState<DownloadState>({});
  const refresh = useCallback(() => setModels(MurmureNative.getModels()), []);
  useOnAppActive(refresh);
  useEffect(() => {
    const p = MurmureNative.addListener('onDownloadProgress', (e) => {
      setProgress((prev) => ({ ...prev, [e.modelId]: { bytes: e.bytes, total: e.total } }));
    });
    const end = MurmureNative.addListener('onDownloadEnd', (e) => {
      setProgress((prev) => {
        const next = { ...prev };
        delete next[e.modelId];
        return next;
      });
      refresh();
    });
    const s = MurmureNative.addListener('onSettingsChanged', refresh);
    return () => {
      p.remove();
      end.remove();
      s.remove();
    };
  }, [refresh]);
  return { models, progress, refresh };
}

export function useSettings(): [Settings, (patch: Partial<Settings>) => void] {
  const [settings, setLocal] = useState<Settings>(() => MurmureNative.getSettings());
  const refresh = useCallback(() => setLocal(MurmureNative.getSettings()), []);
  useOnAppActive(refresh);
  useEffect(() => {
    const sub = MurmureNative.addListener('onSettingsChanged', (s) => setLocal(s));
    return () => sub.remove();
  }, []);
  const update = useCallback((patch: Partial<Settings>) => setLocal(MurmureNative.setSettings(patch)), []);
  return [settings, update];
}

export function useHistory(): [HistoryItem[], () => void] {
  const [items, setItems] = useState<HistoryItem[]>(() => MurmureNative.getHistory());
  const refresh = useCallback(() => setItems(MurmureNative.getHistory()), []);
  useOnAppActive(refresh);
  useEffect(() => {
    const sub = MurmureNative.addListener('onHistoryChanged', refresh);
    return () => sub.remove();
  }, [refresh]);
  return [items, refresh];
}
