import { useCallback, useEffect, useRef, useState } from 'react';

import { getApiClient } from '@/api/client';
import { useAppContext } from '@/context/AppContext';
import { useVisibilityAwarePolling } from '@/hooks/useVisibilityAwarePolling';
import type { ServerDiagnosticsResponse } from '@/types/dto';

/**
 * Polls GET /api/server/diagnostics for WebAE tick/HTTP/snapshot perf stats.
 */
export function useServerDiagnostics(pollMs = 3000) {
  const { pauseRefreshWhenHidden } = useAppContext();
  const [data, setData] = useState<ServerDiagnosticsResponse | null>(null);
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
      const res = await getApiClient().get<ServerDiagnosticsResponse>('/api/server/diagnostics');
      if (res.success && mountedRef.current) {
        setData(res);
      }
    } catch {
      /* ignore */
    } finally {
      if (mountedRef.current) setLoading(false);
    }
  }, []);

  useVisibilityAwarePolling(poll, pollMs, pauseRefreshWhenHidden);

  return { data, loading, refresh: poll };
}
