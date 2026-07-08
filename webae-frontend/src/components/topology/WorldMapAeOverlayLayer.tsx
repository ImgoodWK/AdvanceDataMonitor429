import { memo } from 'react';

import type { WorldMapTileRecord } from '@/hooks/useWorldMapTileLoader';
import { tileKey, type WorldMapTileCoord } from '@/utils/worldMapTerrain';

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
}

function WorldMapAeOverlayLayerInner({
  tileCoords,
  tiles,
  chunkStyle,
  visible = false,
  opacity = 0.85,
}: WorldMapAeOverlayLayerProps) {
  const clampedOpacity = Math.max(0.5, Math.min(1, opacity));

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
