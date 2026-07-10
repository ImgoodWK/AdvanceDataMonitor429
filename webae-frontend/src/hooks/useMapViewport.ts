import {
  useCallback,
  useRef,
  useState,
  type WheelEvent,
} from 'react';
import {
  fitViewForBounds,
  WORLD_MAP_MAX_SCALE,
  WORLD_MAP_MIN_SCALE,
  type MapViewport,
  type WorldBounds,
} from '@/utils/worldMapProjection';

export interface UseMapViewportOptions {
  onViewportChange?: (viewport: MapViewport) => void;
}

export interface UseMapViewportResult {
  containerRef: React.RefObject<HTMLDivElement>;
  viewport: MapViewport;
  setViewport: React.Dispatch<React.SetStateAction<MapViewport>>;
  onWheel: (e: WheelEvent<HTMLDivElement>) => void;
  onPointerDown: (e: React.PointerEvent<HTMLDivElement>) => void;
  onPointerMove: (e: React.PointerEvent<HTMLDivElement>) => void;
  onPointerUp: (e: React.PointerEvent<HTMLDivElement>) => void;
  fitBounds: (bounds: WorldBounds, pxPerBlock: number) => void;
  resetView: () => void;
}

export function useMapViewport(options: UseMapViewportOptions = {}): UseMapViewportResult {
  const containerRef = useRef<HTMLDivElement>(null);
  const [viewport, setViewport] = useState<MapViewport>({ panX: 0, panY: 0, scale: 1 });
  const dragRef = useRef({ active: false, startX: 0, startY: 0, panX: 0, panY: 0 });

  const notify = useCallback(
    (next: MapViewport) => {
      options.onViewportChange?.(next);
    },
    [options.onViewportChange]
  );

  const applyViewport = useCallback(
    (updater: MapViewport | ((prev: MapViewport) => MapViewport)) => {
      setViewport((prev) => {
        const next = typeof updater === 'function' ? updater(prev) : updater;
        notify(next);
        return next;
      });
    },
    [notify]
  );

  const fitBounds = useCallback(
    (bounds: WorldBounds, pxPerBlock: number) => {
      const el = containerRef.current;
      if (!el) return;
      const next = fitViewForBounds(bounds, el.clientWidth, el.clientHeight, pxPerBlock);
      applyViewport(next);
    },
    [applyViewport]
  );

  const resetView = useCallback(() => {
    applyViewport({ panX: 0, panY: 0, scale: 1 });
  }, [applyViewport]);

  const onWheel = useCallback(
    (e: WheelEvent<HTMLDivElement>) => {
      e.preventDefault();
      const delta = e.deltaY > 0 ? 0.9 : 1.1;
      applyViewport((v) => ({
        ...v,
        scale: Math.min(WORLD_MAP_MAX_SCALE, Math.max(WORLD_MAP_MIN_SCALE, v.scale * delta)),
      }));
    },
    [applyViewport]
  );

  const onPointerDown = useCallback(
    (e: React.PointerEvent<HTMLDivElement>) => {
      const target = e.target as HTMLElement;
      if (target.closest('.worldmap-marker-hit')) return;
      if (target.closest('.worldmap-cluster-popup')) return;
      if (target.closest('.worldmap-legend-rail')) return;
      dragRef.current = {
        active: true,
        startX: e.clientX,
        startY: e.clientY,
        panX: viewport.panX,
        panY: viewport.panY,
      };
      e.currentTarget.setPointerCapture(e.pointerId);
    },
    [viewport.panX, viewport.panY]
  );

  const onPointerMove = useCallback(
    (e: React.PointerEvent<HTMLDivElement>) => {
      if (!dragRef.current.active) return;
      applyViewport({
        panX: dragRef.current.panX + (e.clientX - dragRef.current.startX),
        panY: dragRef.current.panY + (e.clientY - dragRef.current.startY),
        scale: viewport.scale,
      });
    },
    [applyViewport, viewport.scale]
  );

  const onPointerUp = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
    dragRef.current.active = false;
    try {
      e.currentTarget.releasePointerCapture(e.pointerId);
    } catch {
      /* ignore */
    }
  }, []);

  return {
    containerRef,
    viewport,
    setViewport: applyViewport,
    onWheel,
    onPointerDown,
    onPointerMove,
    onPointerUp,
    fitBounds,
    resetView,
  };
}
