import { useEffect, useRef } from 'react';

/**
 * Run a callback on a fixed interval. Pass null as delay to pause.
 * The callback receives a stable ref so it always sees fresh state.
 *
 * For tab-visibility-aware polling, prefer {@link useVisibilityAwarePolling}.
 */
export function useInterval(callback: () => void, delay: number | null) {
  const savedCallback = useRef(callback);
  useEffect(() => {
    savedCallback.current = callback;
  }, [callback]);

  useEffect(() => {
    if (delay === null) return;
    const id = setInterval(() => savedCallback.current(), delay);
    return () => clearInterval(id);
  }, [delay]);
}
