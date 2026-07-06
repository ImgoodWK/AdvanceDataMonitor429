import { useCallback, useMemo } from 'react';
import { useLocalStorage } from '@/hooks/useLocalStorage';
import {
  DEFAULT_TOPOLOGY_DISPLAY,
  mergeTopologyDisplay,
  TOPOLOGY_DISPLAY_STORAGE_KEY,
  type TopologyDisplaySettings,
} from '@/types/topologyDisplay';

export function useTopologyDisplay(): [
  TopologyDisplaySettings,
  (patch: Partial<TopologyDisplaySettings> | ((prev: TopologyDisplaySettings) => TopologyDisplaySettings)) => void,
  () => void,
] {
  const [raw, setRaw] = useLocalStorage<Partial<TopologyDisplaySettings>>(
    TOPOLOGY_DISPLAY_STORAGE_KEY,
    DEFAULT_TOPOLOGY_DISPLAY
  );

  const settings = useMemo(() => mergeTopologyDisplay(raw), [raw]);

  const setSettings = useCallback(
    (patch: Partial<TopologyDisplaySettings> | ((prev: TopologyDisplaySettings) => TopologyDisplaySettings)) => {
      setRaw((prev) => {
        const merged = mergeTopologyDisplay(prev);
        const next = typeof patch === 'function' ? patch(merged) : mergeTopologyDisplay({ ...merged, ...patch });
        return next;
      });
    },
    [setRaw]
  );

  const resetSettings = useCallback(() => {
    setRaw(DEFAULT_TOPOLOGY_DISPLAY);
  }, [setRaw]);

  return [settings, setSettings, resetSettings];
}
