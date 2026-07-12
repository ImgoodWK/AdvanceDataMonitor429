import { useCallback, useEffect, useRef } from 'react';
import { useAppContext, type PageId } from '@/context/AppContext';
import { useVisibilityAwarePolling } from '@/hooks/useVisibilityAwarePolling';

/**
 * Polls only when {@code activePage} is in {@code activePages}.
 * Combines page-scoped activation with tab visibility pause.
 */
export function usePageActivePolling(
  callback: () => void,
  delayMs: number | null,
  activePages: PageId | PageId[],
  pauseWhenHidden?: boolean
) {
  const { activePage, pauseRefreshWhenHidden: ctxPause } = useAppContext();
  const pages = Array.isArray(activePages) ? activePages : [activePages];
  const pageActive = pages.includes(activePage);
  const pause = pauseWhenHidden ?? ctxPause;
  const effectiveDelay = pageActive && delayMs !== null && delayMs > 0 ? delayMs : null;

  const callbackRef = useRef(callback);
  callbackRef.current = callback;

  const fire = useCallback(() => {
    callbackRef.current();
  }, []);

  useVisibilityAwarePolling(fire, effectiveDelay, pause);
}
