import { memo, useEffect, useMemo, useRef, useState } from 'react';

import type { WorldMapTileRecord } from '@/hooks/useWorldMapTileLoader';
import { tileKey, type WorldMapTileCoord } from '@/utils/worldMapTerrain';
import {
  buildWorldMapAePalette,
  clearTintCache,
  tintAeIdBlob,
  type WorldMapAeColorPalette,
} from '@/utils/worldMapAeTint';
import type { WorldMapAeCategoryId } from '@/utils/worldMapAeCategories';

export interface WorldMapAeOverlayLayerProps {
  tileCoords: WorldMapTileCoord[];
  tiles: Record<string, WorldMapTileRecord>;
  chunkStyle: (chunkX: number, chunkZ: number) => {
    left: number;
    top: number;
    width: number;
    height: number;
    transform: string;
    transformOrigin: string;
  };
  /** Layer visibility; tiles still prefetch when mounted with active loader upstream. */
  visible?: boolean;
  opacity?: number;
  categoryColors: Record<WorldMapAeCategoryId, string>;
  itemColorOverrides?: Record<string, string>;
}

function WorldMapAeOverlayLayerInner({
  tileCoords,
  tiles,
  chunkStyle,
  visible = false,
  opacity = 0.85,
  categoryColors,
  itemColorOverrides = {},
}: WorldMapAeOverlayLayerProps) {
  const clampedOpacity = Math.max(0, Math.min(1, opacity ?? 1));
  const palette: WorldMapAeColorPalette = useMemo(
    () => buildWorldMapAePalette(categoryColors, itemColorOverrides),
    [categoryColors, itemColorOverrides]
  );

  const [tintedUrls, setTintedUrls] = useState<Record<string, string>>({});
  const tintEpochRef = useRef(0);

  useEffect(() => {
    if (!visible) {
      setTintedUrls({});
      return;
    }
    const epoch = ++tintEpochRef.current;

    let cancelled = false;
    const run = async () => {
      for (const { tileX, tileZ, zoom } of tileCoords) {
        if (cancelled || tintEpochRef.current !== epoch) {
          return;
        }
        const key = tileKey(tileX, tileZ, zoom);
        const rec = tiles[key];
        if (rec?.state !== 'loaded' || !rec.blobUrl || rec.tileStatus === 'empty') {
          continue;
        }
        try {
          const response = await fetch(rec.blobUrl);
          const blob = await response.blob();
          const tinted = await tintAeIdBlob(blob, palette, key, clampedOpacity);
          if (!cancelled && tintEpochRef.current === epoch) {
            setTintedUrls((prev) => ({ ...prev, [key]: tinted }));
          }
        } catch {
          // Keep prior tinted URL for this tile if any.
        }
      }
    };
    void run();
    return () => {
      cancelled = true;
    };
  }, [tileCoords, tiles, palette, clampedOpacity, visible]);

  useEffect(() => () => clearTintCache(), []);

  if (!visible) {
    return null;
  }

  return (
    <div
      className="worldmap-ae-overlay-layer"
      aria-hidden="true"
      style={{
        pointerEvents: 'none',
      }}
    >
      {tileCoords.map(({ tileX, tileZ, zoom }) => {
        const key = tileKey(tileX, tileZ, zoom);
        const rec = tiles[key];
        const style = chunkStyle(tileX, tileZ);
        const src = tintedUrls[key];

        if (rec?.state === 'loaded' && rec.tileStatus !== 'empty' && src) {
          return (
            <img
              key={key}
              className="worldmap-ae-overlay-tile"
              src={src}
              alt=""
              draggable={false}
              style={{ ...style, imageRendering: 'pixelated' }}
            />
          );
        }

        return null;
      })}
    </div>
  );
}

export const WorldMapAeOverlayLayer = memo(WorldMapAeOverlayLayerInner);
