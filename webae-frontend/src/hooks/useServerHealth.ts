import { useCallback, useEffect, useRef, useState } from 'react';

import { getApiClient } from '@/api/client';
import { useAppContext } from '@/context/AppContext';
import { useVisibilityAwarePolling } from '@/hooks/useVisibilityAwarePolling';
import type { ServerHealthResponse } from '@/types/dto';

export interface ServerHealthPoint {
  value: number;
  ts: number;
}

/**
 * Polls GET /api/server/health for TPS / MSPT trends (Phase 2).
 */
export function useServerHealth(pollMs = 5000) {
  const { pauseRefreshWhenHidden } = useAppContext();
  const [health, setHealth] = useState<ServerHealthResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const poll = useCallback(async () => {
    try {
      const data = await getApiClient().get<ServerHealthResponse>('/api/server/health');
      if (data.success && mountedRef.current) {
        setHealth(data);
      }
    } catch {
      /* ignore */
    } finally {
      if (mountedRef.current) setLoading(false);
    }
  }, []);

  useVisibilityAwarePolling(poll, pollMs, pauseRefreshWhenHidden);

  const getTpsHistory = useCallback((): ServerHealthPoint[] => {
    if (!health?.history?.tps?.length || !health.history.timestamps?.length) {
      return [];
    }
    const points: ServerHealthPoint[] = [];
    const len = Math.min(health.history.tps.length, health.history.timestamps.length);
    for (let i = 0; i < len; i++) {
      points.push({ value: health.history.tps[i], ts: health.history.timestamps[i] });
    }
    return points;
  }, [health]);

  const getMsptHistory = useCallback((): ServerHealthPoint[] => {
    if (!health?.history?.mspt?.length || !health.history.timestamps?.length) {
      return [];
    }
    const points: ServerHealthPoint[] = [];
    const len = Math.min(health.history.mspt.length, health.history.timestamps.length);
    for (let i = 0; i < len; i++) {
      points.push({ value: health.history.mspt[i], ts: health.history.timestamps[i] });
    }
    return points;
  }, [health]);

  return {
    health,
    loading,
    refresh: poll,
    getTpsHistory,
    getMsptHistory,
  };
}
