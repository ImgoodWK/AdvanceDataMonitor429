import { useCallback, useEffect, useMemo } from 'react';
import { createDebouncedLocalStorageSaver } from '@/hooks/debouncedLocalStorageSaver';

/**
 * Debounced localStorage writer for GridStack layout changes.
 * Flushes pending data on unmount and page hide so layout is not lost when
 * navigating away or refreshing before the debounce window elapses.
 *
 * Immediate writers (saveSettings) must call `cancel()` so a stale pending
 * layout snapshot cannot overwrite newer widget config after the debounce fires.
 */
export function useDebouncedLocalStorageSaver<T>(storageKey: string, debounceMs = 400) {
  const saver = useMemo(
    () =>
      createDebouncedLocalStorageSaver<T>(storageKey, debounceMs, {
        setItem: (key, value) => localStorage.setItem(key, value),
        setTimeout,
        clearTimeout,
      }),
    [storageKey, debounceMs]
  );

  const flush = useCallback(() => saver.flush(), [saver]);
  const cancel = useCallback(() => saver.cancel(), [saver]);
  const schedule = useCallback((data: T) => saver.schedule(data), [saver]);

  useEffect(() => {
    const onPageHide = () => flush();
    window.addEventListener('pagehide', onPageHide);
    return () => {
      window.removeEventListener('pagehide', onPageHide);
      flush();
    };
  }, [flush]);

  return { schedule, flush, cancel };
}
