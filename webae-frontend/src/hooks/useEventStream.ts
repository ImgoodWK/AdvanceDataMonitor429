import { useCallback, useEffect, useRef } from 'react';
import { useAppContext } from '@/context/AppContext';
import type { WebAlertDto } from '@/types/dto';
import { notifyAlertOnce } from '@/utils/alertNotification';
import { bumpIconVersion } from '@/utils/icon';

/**
 * SSE client for /api/events/stream (Phase 9). Falls back silently if unsupported.
 */
export function useEventStream(enabled: boolean) {
  const { isLoggedIn, token, notify, lang, pauseRefreshWhenHidden } = useAppContext();
  const sourceRef = useRef<EventSource | null>(null);

  const handleAlert = useCallback(
    (alert: WebAlertDto) => {
      notifyAlertOnce(alert, notify);
    },
    [notify]
  );

  useEffect(() => {
    if (!enabled || !isLoggedIn || !token || pauseRefreshWhenHidden) {
      sourceRef.current?.close();
      sourceRef.current = null;
      return;
    }
    if (typeof EventSource === 'undefined') return;

    const url = `./api/events/stream?token=${encodeURIComponent(token)}`;
    const es = new EventSource(url);
    sourceRef.current = es;

    es.addEventListener('alert', (ev) => {
      try {
        const alert = JSON.parse((ev as MessageEvent).data) as WebAlertDto;
        handleAlert(alert);
      } catch {
        /* ignore malformed */
      }
    });

    es.addEventListener('icon-ready', () => {
      bumpIconVersion();
      window.dispatchEvent(new CustomEvent('webae-icon-ready'));
    });

    es.onerror = () => {
      /* browser auto-reconnects; polling hook remains fallback */
    };

    return () => {
      es.close();
      if (sourceRef.current === es) sourceRef.current = null;
    };
  }, [enabled, isLoggedIn, token, pauseRefreshWhenHidden, lang, handleAlert]);

  useEffect(() => {
    if (!enabled || !isLoggedIn) return;
    if (typeof Notification !== 'undefined' && Notification.permission === 'default') {
      void Notification.requestPermission();
    }
  }, [enabled, isLoggedIn, lang]);
}
