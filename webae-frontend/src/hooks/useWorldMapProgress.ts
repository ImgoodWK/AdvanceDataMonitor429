import { useCallback, useEffect, useRef, useState } from 'react';

import { getApiClient } from '@/api/client';
import type { WorldMapQualityTierId } from '@/utils/worldMapTerrain';

export interface WorldMapChunkProgress {
  terrain?: string;
  ae?: string;
}

export interface WorldMapProgressDto {
  success: boolean;
  networkId?: number;
  quality?: string;
  view?: string;
  dim?: number;
  total?: number;
  completed?: number;
  chunks?: Record<string, WorldMapChunkProgress>;
}

const POLL_MS = 1500;

export interface UseWorldMapProgressOptions {
  networkId: number;
  view: string;
  dim: number;
  quality: WorldMapQualityTierId;
  enabled?: boolean;
}

export function useWorldMapProgress({
  networkId,
  view,
  dim,
  quality,
  enabled = false,
}: UseWorldMapProgressOptions) {
  const [progress, setProgress] = useState<WorldMapProgressDto | null>(null);
  const [polling, setPolling] = useState(false);
  const timerRef = useRef<number | null>(null);

  const stopPolling = useCallback(() => {
    if (timerRef.current != null) {
      window.clearInterval(timerRef.current);
      timerRef.current = null;
    }
    setPolling(false);
  }, []);

  const fetchOnce = useCallback(async () => {
    try {
      const data = await getApiClient().get<WorldMapProgressDto>(
        `/api/worldmap/progress?network=${networkId}&view=${encodeURIComponent(view)}&dim=${dim}&quality=${encodeURIComponent(quality)}`
      );
      setProgress(data);
      const total = data.total ?? 0;
      const completed = data.completed ?? 0;
      if (total > 0 && completed >= total) {
        stopPolling();
      }
      return data;
    } catch {
      return null;
    }
  }, [networkId, view, dim, quality, stopPolling]);

  const startPolling = useCallback(() => {
    stopPolling();
    setPolling(true);
    void fetchOnce();
    timerRef.current = window.setInterval(() => {
      void fetchOnce();
    }, POLL_MS);
  }, [fetchOnce, stopPolling]);

  useEffect(() => {
    if (!enabled) {
      stopPolling();
      return;
    }
    return () => stopPolling();
  }, [enabled, stopPolling]);

  useEffect(() => {
    return () => stopPolling();
  }, [stopPolling]);

  return {
    progress,
    polling,
    startPolling,
    stopPolling,
    fetchOnce,
  };
}
