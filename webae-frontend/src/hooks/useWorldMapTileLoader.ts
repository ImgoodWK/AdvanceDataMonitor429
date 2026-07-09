import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { useAppContext } from '@/context/AppContext';
import {
  buildWorldMapTileUrl,
  filterChunksByScope,
  pyramidTileScreenRect,
  tileKey,
  visibleTilesForViewport,
  type ChunkScope,
  type WorldMapQualityTierId,
  type WorldMapTileLayer,
  type WorldMapTileCoord,
} from '@/utils/worldMapTerrain';
import { buildIdbKey, getCachedTileBlob, putCachedTileBlob } from '@/utils/worldMapIdbCache';
import type { MapViewport, WorldMapOrigin } from '@/utils/worldMapProjection';

const DEBOUNCE_MS = 150;
const MAX_CONCURRENT = 6;

export type WorldMapTileLoadState = 'idle' | 'loading' | 'loaded' | 'pending' | 'upgrading' | 'error';

export interface WorldMapTileRecord {
  state: WorldMapTileLoadState;
  blobUrl?: string;
}

export interface UseWorldMapTileLoaderOptions {
  dim: number;
  networkId: number;
  chunkScope: ChunkScope | null;
  viewport: MapViewport;
  origin: WorldMapOrigin;
  containerWidth: number;
  containerHeight: number;
  view?: string;
  layer?: WorldMapTileLayer;
  quality?: WorldMapQualityTierId;
  /** Zoom pyramid level (0 = native chunk tiles). */
  zoom?: number;
  /** When false, skip fetching but keep cached tiles. */
  active?: boolean;
  /** When true, fetch even if layer is not visible (AE background prefetch). */
  prefetch?: boolean;
  /** Snapshot version for IndexedDB cache (0 = disabled). */
  snapshotVersion?: number;
  /** Owner scope key for IndexedDB (defaults to network-only bucket). */
  ownerCacheKey?: string;
  /** Enable browser IndexedDB tile cache. */
  browserCacheEnabled?: boolean;
}

export interface UseWorldMapTileLoaderResult {
  debouncedTiles: WorldMapTileCoord[];
  tiles: Record<string, WorldMapTileRecord>;
  chunkStyle: (tileX: number, tileZ: number) => {
    left: number;
    top: number;
    width: number;
    height: number;
  };
  resetTiles: () => void;
}

export function useWorldMapTileLoader({
  dim,
  networkId,
  chunkScope,
  viewport,
  origin,
  containerWidth,
  containerHeight,
  view = 'flat',
  layer = 'terrain',
  quality = 'medium',
  zoom = 0,
  active = true,
  prefetch = false,
  snapshotVersion = 0,
  ownerCacheKey = 'owner',
  browserCacheEnabled = true,
}: UseWorldMapTileLoaderOptions): UseWorldMapTileLoaderResult {
  const { token } = useAppContext();
  const [tiles, setTiles] = useState<Record<string, WorldMapTileRecord>>({});
  const [debouncedTiles, setDebouncedTiles] = useState<WorldMapTileCoord[]>([]);
  const [retryEpoch, setRetryEpoch] = useState(0);

  const abortRef = useRef<Map<string, AbortController>>(new Map());
  const inflightRef = useRef(0);
  const pendingRef = useRef<WorldMapTileCoord[]>([]);
  const tilesRef = useRef(tiles);
  tilesRef.current = tiles;

  const fetchEnabled = active || prefetch;

  const visibleTiles = useMemo(() => {
    if (!fetchEnabled || containerWidth <= 0 || containerHeight <= 0) {
      return [];
    }
    const tiles = visibleTilesForViewport(
      viewport,
      origin,
      containerWidth,
      containerHeight,
      zoom,
      1
    );
    if (zoom <= 0 && chunkScope) {
      return tiles.filter((t) =>
        filterChunksByScope([{ chunkX: t.tileX, chunkZ: t.tileZ }], chunkScope).length > 0
      );
    }
    return tiles;
  }, [fetchEnabled, viewport, origin, containerWidth, containerHeight, chunkScope, zoom]);

  const resetTiles = useCallback(() => {
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
  }, []);

  useEffect(() => {
    if (!fetchEnabled) {
      setDebouncedTiles([]);
      return;
    }
    const timer = window.setTimeout(() => setDebouncedTiles(visibleTiles), DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [fetchEnabled, visibleTiles, containerWidth, containerHeight, view, dim, chunkScope, zoom]);

  useEffect(() => {
    if (!fetchEnabled) return;

    const wanted = new Set(debouncedTiles.map((c) => tileKey(c.tileX, c.tileZ, zoom)));
    const abortMap = abortRef.current;

    for (const [key, controller] of abortMap.entries()) {
      if (!wanted.has(key)) {
        controller.abort();
        abortMap.delete(key);
      }
    }

    pendingRef.current = debouncedTiles.filter((c) => {
      const key = tileKey(c.tileX, c.tileZ, zoom);
      const rec = tilesRef.current[key];
      return !rec || rec.state === 'idle' || rec.state === 'pending' || rec.state === 'upgrading' || rec.state === 'error';
    });

    const pump = () => {
      while (inflightRef.current < MAX_CONCURRENT && pendingRef.current.length > 0) {
        const next = pendingRef.current.shift();
        if (!next) break;

        const key = tileKey(next.tileX, next.tileZ, zoom);
        const existing = tilesRef.current[key];
        if (existing?.state === 'loading') {
          continue;
        }
        if (existing?.state === 'loaded') {
          continue;
        }

        const controller = new AbortController();
        abortMap.set(key, controller);
        inflightRef.current += 1;

        setTiles((prev) => ({
          ...prev,
          [key]: { state: 'loading' },
        }));

        const idbKey =
          browserCacheEnabled && snapshotVersion > 0
            ? buildIdbKey({
                ownerKey: ownerCacheKey,
                networkId,
                version: snapshotVersion,
                layer,
                dim,
                chunkX: next.tileX,
                chunkZ: next.tileZ,
              })
            : null;

        const loadFromNetwork = () => {
          const url = buildWorldMapTileUrl(
            dim,
            next.tileX,
            next.tileZ,
            token,
            networkId,
            view,
            layer,
            quality,
            zoom
          );

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
              if (tileStatus === 'pending' || tileStatus === 'missing') {
                setTiles((prev) => ({
                  ...prev,
                  [key]: { state: tileStatus === 'missing' ? 'loaded' : 'pending' },
                }));
                if (tileStatus === 'pending') {
                  window.setTimeout(() => {
                    if (controller.signal.aborted) return;
                    setRetryEpoch((n) => n + 1);
                  }, 2000);
                }
                return;
              }
              if (tileStatus === 'empty') {
                const blobUrl = URL.createObjectURL(blob);
                setTiles((prev) => {
                  const old = prev[key]?.blobUrl;
                  if (old) URL.revokeObjectURL(old);
                  return {
                    ...prev,
                    [key]: { state: 'loaded', blobUrl },
                  };
                });
                return;
              }
              if (tileStatus === 'upgrading') {
                const blobUrl = URL.createObjectURL(blob);
                setTiles((prev) => {
                  const old = prev[key]?.blobUrl;
                  if (old) URL.revokeObjectURL(old);
                  return {
                    ...prev,
                    [key]: { state: 'upgrading', blobUrl },
                  };
                });
                window.setTimeout(() => {
                  if (controller.signal.aborted) return;
                  setRetryEpoch((n) => n + 1);
                }, 3000);
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
              if (idbKey && (tileStatus === 'cached' || !tileStatus)) {
                void putCachedTileBlob(idbKey, blob);
              }
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
        };

        if (idbKey) {
          void getCachedTileBlob(idbKey).then((cached) => {
            if (controller.signal.aborted) return;
            if (cached) {
              const blobUrl = URL.createObjectURL(cached);
              setTiles((prev) => {
                const old = prev[key]?.blobUrl;
                if (old) URL.revokeObjectURL(old);
                return {
                  ...prev,
                  [key]: { state: 'loaded', blobUrl },
                };
              });
              abortMap.delete(key);
              inflightRef.current = Math.max(0, inflightRef.current - 1);
              pump();
              return;
            }
            loadFromNetwork();
          });
        } else {
          loadFromNetwork();
        }
      }
    };

    pump();
  }, [
    debouncedTiles,
    dim,
    fetchEnabled,
    token,
    retryEpoch,
    networkId,
    view,
    layer,
    quality,
    zoom,
    snapshotVersion,
    ownerCacheKey,
    browserCacheEnabled,
  ]);

  useEffect(() => {
    resetTiles();
  }, [view, dim, networkId, quality, zoom, snapshotVersion, resetTiles]);

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
    const wanted = new Set(debouncedTiles.map((c) => tileKey(c.tileX, c.tileZ, zoom)));
    setTiles((prev) => {
      let changed = false;
      const next: Record<string, WorldMapTileRecord> = {};
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
  }, [debouncedTiles, zoom]);

  const chunkStyle = useCallback(
    (tileX: number, tileZ: number) => {
      const rect = pyramidTileScreenRect(tileX, tileZ, zoom, viewport, origin);
      return {
        left: Math.round(rect.left),
        top: Math.round(rect.top),
        width: Math.round(rect.size),
        height: Math.round(rect.size),
      };
    },
    [viewport, origin, zoom]
  );

  return { debouncedTiles, tiles, chunkStyle, resetTiles };
}
