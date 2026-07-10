import { useEffect, type RefObject } from 'react';

/**
 * Attach a non-passive wheel listener so preventDefault() blocks page scroll
 * while zooming inside map/graph viewports.
 */
export function useNonPassiveWheelZoom(
  ref: RefObject<HTMLElement | null>,
  onWheel: (e: WheelEvent) => void,
  enabled = true
): void {
  useEffect(() => {
    const el = ref.current;
    if (!el || !enabled) return;
    const handler = (e: WheelEvent) => {
      e.preventDefault();
      onWheel(e);
    };
    el.addEventListener('wheel', handler, { passive: false });
    return () => el.removeEventListener('wheel', handler);
  }, [ref, onWheel, enabled]);
}
