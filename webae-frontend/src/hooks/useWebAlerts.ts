import { useCallback, useEffect, useRef } from 'react';
import { getApiClient } from '@/api/client';
import { useAppContext } from '@/context/AppContext';
import { useVisibilityAwarePolling } from '@/hooks/useVisibilityAwarePolling';
import { markAlertsNotified, notifyAlertOnce } from '@/utils/alertNotification';
import type { AlertsResponse } from '@/types/dto';

const POLL_MS = 10_000;

export function useWebAlerts(enabled: boolean) {
  const { isLoggedIn, notify, lang, pauseRefreshWhenHidden } = useAppContext();
  const bootstrappedRef = useRef(false);
  const knownActiveRef = useRef<Set<string>>(new Set());

  const poll = useCallback(async () => {
    if (!enabled || !isLoggedIn) return;
    try {
      const data = await getApiClient().get<AlertsResponse>('/api/alerts');
      if (!data.success || !data.alerts) return;

      if (!bootstrappedRef.current) {
        markAlertsNotified(data.alerts);
        for (const alert of data.alerts) {
          knownActiveRef.current.add(alert.id || `${alert.type}:${alert.sourceKey}`);
        }
        bootstrappedRef.current = true;
        return;
      }

      const nextActive = new Set<string>();
      for (const alert of data.alerts) {
        const key = alert.id || `${alert.type}:${alert.sourceKey}`;
        nextActive.add(key);
        if (!knownActiveRef.current.has(key)) {
          notifyAlertOnce(alert, notify);
        }
      }
      knownActiveRef.current = nextActive;
    } catch {
      /* silent — alerts are best-effort */
    }
  }, [enabled, isLoggedIn, notify]);

  useEffect(() => {
    if (!enabled || !isLoggedIn) {
      bootstrappedRef.current = false;
      knownActiveRef.current.clear();
      return;
    }
    if (typeof Notification !== 'undefined' && Notification.permission === 'default') {
      void Notification.requestPermission();
    }
  }, [enabled, isLoggedIn, lang]);

  useVisibilityAwarePolling(
    poll,
    enabled && isLoggedIn ? POLL_MS : null,
    pauseRefreshWhenHidden
  );
}
