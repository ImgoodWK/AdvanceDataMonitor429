import { useCallback, useEffect, useRef, useState } from 'react';

import { getApiClient } from '@/api/client';
import { useAppContext } from '@/context/AppContext';
import { useVisibilityAwarePolling } from '@/hooks/useVisibilityAwarePolling';
import type { ServerDiagnosticsResponse } from '@/types/dto';

const DIAGNOSTICS_PAGE = 'diagnostics' as const;

/**
 * Polls GET /api/server/diagnostics for WebAE tick/HTTP/snapshot perf stats.
 */
export function useServerDiagnostics(pollMs = 3000) {
  const { pauseRefreshWhenHidden, reportPageFetch } = useAppContext();
  const [data, setData] = useState<ServerDiagnosticsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [lastFetchTime, setLastFetchTime] = useState<number | null>(null);
  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const poll = useCallback(async () => {
    try {
      const res = await getApiClient().get<ServerDiagnosticsResponse>('/api/server/diagnostics');
      if (res.success && mountedRef.current) {
        const now = Date.now();
        setData(res);
        setLastFetchTime(now);
        reportPageFetch(DIAGNOSTICS_PAGE, pollMs, now);
      }
    } catch {
      /* ignore */
    } finally {
      if (mountedRef.current) setLoading(false);
    }
  }, [pollMs, reportPageFetch]);

  useVisibilityAwarePolling(poll, pollMs, pauseRefreshWhenHidden);

  return { data, loading, refresh: poll, lastFetchTime };
}
