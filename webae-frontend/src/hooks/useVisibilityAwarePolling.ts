import { useCallback, useEffect, useRef } from 'react';
import { useInterval } from '@/hooks/useInterval';
import { usePageVisibility } from '@/hooks/usePageVisibility';

/**
 * Polling helper built on {@link useInterval}. When {@code pauseWhenHidden} is true
 * and the tab is hidden, the interval is paused; becoming visible triggers one
 * immediate poll. Also polls once when polling becomes active while visible.
 */
export function useVisibilityAwarePolling(
  callback: () => void,
  delayMs: number | null,
  pauseWhenHidden: boolean
) {
  const isVisible = usePageVisibility();
  const callbackRef = useRef(callback);
  callbackRef.current = callback;

  const active = delayMs !== null && delayMs > 0;
  const paused = pauseWhenHidden && !isVisible;
  const effectiveDelay = active && !paused ? delayMs : null;

  const fire = useCallback(() => {
    callbackRef.current();
  }, []);

  useInterval(fire, effectiveDelay);

  useEffect(() => {
    if (!active) return;
    if (pauseWhenHidden && !isVisible) return;
    fire();
  }, [active, pauseWhenHidden, isVisible, fire, delayMs]);
}
