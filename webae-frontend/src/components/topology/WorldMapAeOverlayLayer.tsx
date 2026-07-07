import { memo, useEffect, useMemo, useRef, useState } from 'react';

import { useAppContext } from '@/context/AppContext';
import {
  buildWorldMapTileUrl,
  chunkKey,
  chunkTileScreenRect,
  filterChunksByScope,
  visibleChunksForViewport,
  type ChunkCoord,
  type ChunkScope,
} from '@/utils/worldMapTerrain';
import {
  type MapViewport,
  type WorldMapOrigin,
} from '@/utils/worldMapProjection';

const DEBOUNCE_MS = 150;
const MAX_CONCURRENT = 6;

export interface WorldMapAeOverlayLayerProps {
  dim: number;
  networkId: number;
  chunkScope: ChunkScope | null;
  viewport: MapViewport;
  origin: WorldMapOrigin;
  containerWidth: number;
  containerHeight: number;
  enabled?: boolean;
  view?: string;
  opacity?: number;
}

type TileLoadState = 'idle' | 'loading' | 'loaded' | 'pending' | 'error';

interface TileRecord {
  state: TileLoadState;
  blobUrl?: string;
}

function WorldMapAeOverlayLayerInner({
  dim,
  networkId,
  chunkScope,
  viewport,
  origin,
  containerWidth,
  containerHeight,
  enabled = false,
  view = 'flat',
  opacity = 0.85,
}: WorldMapAeOverlayLayerProps) {
  const { token } = useAppContext();
  const [tiles, setTiles] = useState<Record<string, TileRecord>>({});
  const [debouncedChunks, setDebouncedChunks] = useState<ChunkCoord[]>([]);
  const [retryEpoch, setRetryEpoch] = useState(0);

  const abortRef = useRef<Map<string, AbortController>>(new Map());
  const inflightRef = useRef(0);
  const pendingRef = useRef<ChunkCoord[]>([]);
  const tilesRef = useRef(tiles);
  tilesRef.current = tiles;

  const visibleChunks = useMemo(
    () =>
      enabled
        ? filterChunksByScope(
            visibleChunksForViewport(viewport, origin, containerWidth, containerHeight, 1),
            chunkScope
          )
        : [],
    [enabled, viewport, origin, containerWidth, containerHeight, chunkScope]
  );

  useEffect(() => {
    if (!enabled) {
      setDebouncedChunks([]);
      return;
    }
    const timer = window.setTimeout(() => setDebouncedChunks(visibleChunks), DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [enabled, visibleChunks]);

  useEffect(() => {
    if (!enabled) return;

    const wanted = new Set(debouncedChunks.map((c) => chunkKey(c.chunkX, c.chunkZ)));
    const abortMap = abortRef.current;

    for (const [key, controller] of abortMap.entries()) {
      if (!wanted.has(key)) {
        controller.abort();
        abortMap.delete(key);
      }
    }

    pendingRef.current = debouncedChunks.filter((c) => {
      const key = chunkKey(c.chunkX, c.chunkZ);
      const rec = tilesRef.current[key];
      return !rec || rec.state === 'idle' || rec.state === 'pending' || rec.state === 'error';
    });

    const pump = () => {
      while (inflightRef.current < MAX_CONCURRENT && pendingRef.current.length > 0) {
        const next = pendingRef.current.shift();
        if (!next) break;

        const key = chunkKey(next.chunkX, next.chunkZ);
        const existing = tilesRef.current[key];
        if (existing && (existing.state === 'loaded' || existing.state === 'loading')) {
          continue;
        }

        const controller = new AbortController();
        abortMap.set(key, controller);
        inflightRef.current += 1;

        setTiles((prev) => ({
          ...prev,
          [key]: { state: 'loading' },
        }));

        const url = buildWorldMapTileUrl(dim, next.chunkX, next.chunkZ, token, networkId, view, 'ae');

        fetch(url, {
          signal: controller.signal,
          headers: token ? { Authorization: `Bearer ${token}` } : undefined,
        })
          .then((resp) => {
            if (!resp.ok) {
              throw new Error(`tile ${resp.status}`);
            }
            const tileStatus = resp.headers.get('X-WorldMap-Tile-Status');
            return resp.blob().then((blob) => ({ blob, tileStatus }));
          })
          .then(({ blob, tileStatus }) => {
            if (controller.signal.aborted) return;
            if (tileStatus === 'pending') {
              setTiles((prev) => ({
                ...prev,
                [key]: { state: 'pending' },
              }));
              window.setTimeout(() => {
                if (controller.signal.aborted) return;
                setRetryEpoch((n) => n + 1);
              }, 2000);
              return;
            }
            const blobUrl = URL.createObjectURL(blob);
            setTiles((prev) => {
              const old = prev[key]?.blobUrl;
              if (old) URL.revokeObjectURL(old);
              return {
                ...prev,
                [key]: { state: 'loaded', blobUrl },
              };
            });
          })
          .catch(() => {
            if (controller.signal.aborted) return;
            setTiles((prev) => ({
              ...prev,
              [key]: { state: 'error' },
            }));
          })
          .finally(() => {
            abortMap.delete(key);
            inflightRef.current = Math.max(0, inflightRef.current - 1);
            pump();
          });
      }
    };

    pump();
  }, [debouncedChunks, dim, enabled, token, retryEpoch, networkId, view]);

  useEffect(() => {
    const abortMap = abortRef.current;
    for (const controller of abortMap.values()) {
      controller.abort();
    }
    abortMap.clear();
    inflightRef.current = 0;
    pendingRef.current = [];
    setTiles((prev) => {
      for (const rec of Object.values(prev)) {
        if (rec.blobUrl) URL.revokeObjectURL(rec.blobUrl);
      }
      return {};
    });
  }, [view, dim, networkId]);

  useEffect(() => {
    const abortMap = abortRef.current;
    return () => {
      for (const controller of abortMap.values()) {
        controller.abort();
      }
      abortMap.clear();
      setTiles((prev) => {
        for (const rec of Object.values(prev)) {
          if (rec.blobUrl) URL.revokeObjectURL(rec.blobUrl);
        }
        return {};
      });
    };
  }, []);

  useEffect(() => {
    const wanted = new Set(debouncedChunks.map((c) => chunkKey(c.chunkX, c.chunkZ)));
    setTiles((prev) => {
      let changed = false;
      const next: Record<string, TileRecord> = {};
      for (const [key, rec] of Object.entries(prev)) {
        if (wanted.has(key)) {
          next[key] = rec;
        } else if (rec.blobUrl) {
          URL.revokeObjectURL(rec.blobUrl);
          changed = true;
        } else {
          changed = true;
        }
      }
      return changed ? next : prev;
    });
  }, [debouncedChunks]);

  if (!enabled || containerWidth <= 0 || containerHeight <= 0) {
    return null;
  }

  const clampedOpacity = Math.max(0.5, Math.min(1, opacity));

  return (
    <div
      className="worldmap-ae-overlay-layer"
      aria-hidden="true"
      style={{ opacity: clampedOpacity }}
    >
      {debouncedChunks.map(({ chunkX, chunkZ }) => {
        const key = chunkKey(chunkX, chunkZ);
        const rec = tiles[key];
        const rect = chunkTileScreenRect(chunkX, chunkZ, viewport, origin);
        const style = {
          left: rect.left,
          top: rect.top,
          width: rect.size,
          height: rect.size,
        };

        if (rec?.state === 'loaded' && rec.blobUrl) {
          return (
            <img
              key={key}
              className="worldmap-ae-overlay-tile"
              src={rec.blobUrl}
              alt=""
              draggable={false}
              style={style}
            />
          );
        }

        return null;
      })}
    </div>
  );
}

export const WorldMapAeOverlayLayer = memo(WorldMapAeOverlayLayerInner);
