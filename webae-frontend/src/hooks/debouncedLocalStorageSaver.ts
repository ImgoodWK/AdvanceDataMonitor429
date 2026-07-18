/**
 * Debounced localStorage writer core (pure, testable without React).
 * Immediate writers must call `cancel()` so a stale pending layout snapshot
 * cannot overwrite newer widget config after the debounce fires.
 */
export function createDebouncedLocalStorageSaver<T>(
  storageKey: string,
  debounceMs: number,
  deps: {
    setItem: (key: string, value: string) => void;
    setTimeout: typeof setTimeout;
    clearTimeout: typeof clearTimeout;
  }
) {
  let timer: ReturnType<typeof setTimeout> | null = null;
  let pending: T | null = null;

  const flush = () => {
    if (timer != null) {
      deps.clearTimeout(timer);
      timer = null;
    }
    if (pending === null) return;
    try {
      deps.setItem(storageKey, JSON.stringify(pending));
    } catch {
      /* ignore */
    }
    pending = null;
  };

  const cancel = () => {
    if (timer != null) {
      deps.clearTimeout(timer);
      timer = null;
    }
    pending = null;
  };

  const schedule = (data: T) => {
    pending = data;
    if (timer != null) deps.clearTimeout(timer);
    timer = deps.setTimeout(() => {
      timer = null;
      flush();
    }, debounceMs);
  };

  return { schedule, flush, cancel };
}
