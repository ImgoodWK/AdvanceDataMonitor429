import { useCallback, useMemo } from 'react';
import { useLocalStorage } from '@/hooks/useLocalStorage';
import {
  DEFAULT_QUEST_DISPLAY,
  mergeQuestDisplay,
  QUEST_DISPLAY_STORAGE_KEY,
  type QuestDisplaySettings,
} from '@/types/questDisplay';

export function useQuestDisplay(): [
  QuestDisplaySettings,
  (patch: Partial<QuestDisplaySettings> | ((prev: QuestDisplaySettings) => QuestDisplaySettings)) => void,
  () => void,
] {
  const [raw, setRaw] = useLocalStorage<Partial<QuestDisplaySettings>>(
    QUEST_DISPLAY_STORAGE_KEY,
    DEFAULT_QUEST_DISPLAY
  );

  const settings = useMemo(() => mergeQuestDisplay(raw), [raw]);

  const setSettings = useCallback(
    (patch: Partial<QuestDisplaySettings> | ((prev: QuestDisplaySettings) => QuestDisplaySettings)) => {
      setRaw((prev) => {
        const merged = mergeQuestDisplay(prev);
        const next = typeof patch === 'function' ? patch(merged) : mergeQuestDisplay({ ...merged, ...patch });
        return next;
      });
    },
    [setRaw]
  );

  const resetSettings = useCallback(() => {
    setRaw(DEFAULT_QUEST_DISPLAY);
  }, [setRaw]);

  return [settings, setSettings, resetSettings];
}
