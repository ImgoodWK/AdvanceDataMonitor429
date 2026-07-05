import { useCallback, useEffect, useRef } from 'react';

/**
 * Debounced localStorage writer for GridStack layout changes.
 * Flushes pending data on unmount and page hide so layout is not lost when
 * navigating away or refreshing before the debounce window elapses.
 */
export function useDebouncedLocalStorageSaver<T>(storageKey: string, debounceMs = 400) {
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pendingRef = useRef<T | null>(null);

  const flush = useCallback(() => {
    if (debounceRef.current) {
      clearTimeout(debounceRef.current);
      debounceRef.current = null;
    }
    if (pendingRef.current === null) return;
    try {
      localStorage.setItem(storageKey, JSON.stringify(pendingRef.current));
    } catch {
      /* ignore */
    }
    pendingRef.current = null;
  }, [storageKey]);

  const schedule = useCallback(
    (data: T) => {
      pendingRef.current = data;
      if (debounceRef.current) clearTimeout(debounceRef.current);
      debounceRef.current = setTimeout(() => {
        debounceRef.current = null;
        flush();
      }, debounceMs);
    },
    [debounceMs, flush]
  );

  useEffect(() => {
    const onPageHide = () => flush();
    window.addEventListener('pagehide', onPageHide);
    return () => {
      window.removeEventListener('pagehide', onPageHide);
      flush();
    };
  }, [flush]);

  return { schedule, flush };
}
