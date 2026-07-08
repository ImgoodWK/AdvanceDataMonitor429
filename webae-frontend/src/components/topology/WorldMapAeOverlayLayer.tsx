import { memo, useEffect, useMemo, useState } from 'react';

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
  };
  /** Layer visibility (opacity); tiles still prefetch when mounted with active loader upstream. */
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
  const clampedOpacity = Math.max(0.5, Math.min(1, opacity));
  const palette: WorldMapAeColorPalette = useMemo(
    () => buildWorldMapAePalette(categoryColors, itemColorOverrides),
    [categoryColors, itemColorOverrides]
  );

  const [tintedUrls, setTintedUrls] = useState<Record<string, string>>({});

  useEffect(() => {
    let cancelled = false;
    const run = async () => {
      const next: Record<string, string> = {};
      for (const { tileX, tileZ, zoom } of tileCoords) {
        const key = tileKey(tileX, tileZ, zoom);
        const rec = tiles[key];
        if (rec?.state !== 'loaded' || !rec.blobUrl) {
          continue;
        }
        try {
          const response = await fetch(rec.blobUrl);
          const blob = await response.blob();
          const tinted = await tintAeIdBlob(blob, palette, key);
          if (!cancelled) {
            next[key] = tinted;
          }
        } catch {
          if (!cancelled && rec.blobUrl) {
            next[key] = rec.blobUrl;
          }
        }
      }
      if (!cancelled) {
        setTintedUrls(next);
      }
    };
    void run();
    return () => {
      cancelled = true;
    };
  }, [tileCoords, tiles, palette]);

  useEffect(() => () => clearTintCache(), []);

  return (
    <div
      className="worldmap-ae-overlay-layer"
      aria-hidden="true"
      style={{
        opacity: visible ? clampedOpacity : 0,
        visibility: visible ? 'visible' : 'hidden',
        pointerEvents: 'none',
      }}
    >
      {tileCoords.map(({ tileX, tileZ, zoom }) => {
        const key = tileKey(tileX, tileZ, zoom);
        const rec = tiles[key];
        const style = chunkStyle(tileX, tileZ);
        const src = tintedUrls[key] ?? (rec?.state === 'loaded' ? rec.blobUrl : undefined);

        if (rec?.state === 'loaded' && src) {
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
