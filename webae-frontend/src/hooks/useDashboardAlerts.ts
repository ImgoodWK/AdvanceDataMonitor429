import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { getApiClient } from '@/api/client';
import { useAppContext } from '@/context/AppContext';
import { useVisibilityAwarePolling } from '@/hooks/useVisibilityAwarePolling';
import type { AlertsResponse, WebAlertDto } from '@/types/dto';

/**
 * Lightweight active-alerts poller for dashboard alertsSummary widgets.
 * Separate from {@link useWebAlerts} (notification side-effects).
 */
export function useDashboardAlerts(enabled: boolean, pollMs = 10_000) {
  const { isLoggedIn, pauseRefreshWhenHidden } = useAppContext();
  const [alerts, setAlerts] = useState<WebAlertDto[]>([]);
  const [loading, setLoading] = useState(false);
  const mountedRef = useRef(true);
  const alertsEmptyRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const poll = useCallback(async () => {
    if (!enabled || !isLoggedIn) {
      if (mountedRef.current) setAlerts([]);
      return;
    }
    try {
      setLoading((prev) => (alertsEmptyRef.current ? true : prev));
      const data = await getApiClient().get<AlertsResponse>('/api/alerts');
      if (!mountedRef.current) return;
      if (data.success && data.alerts) {
        alertsEmptyRef.current = data.alerts.length === 0;
        setAlerts(data.alerts);
      }
    } catch {
      /* best-effort */
    } finally {
      if (mountedRef.current) setLoading(false);
    }
  }, [enabled, isLoggedIn]);

  useVisibilityAwarePolling(
    poll,
    enabled && isLoggedIn ? pollMs : null,
    pauseRefreshWhenHidden
  );

  return { alerts, loading, refresh: poll };
}
