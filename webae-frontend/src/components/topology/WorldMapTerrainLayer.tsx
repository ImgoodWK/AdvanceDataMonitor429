import { memo } from 'react';

import type { WorldMapTileRecord } from '@/hooks/useWorldMapTileLoader';
import { tileKey, type WorldMapTileCoord } from '@/utils/worldMapTerrain';

export interface WorldMapTerrainLayerProps {
  tileCoords: WorldMapTileCoord[];
  tiles: Record<string, WorldMapTileRecord>;
  chunkStyle: (chunkX: number, chunkZ: number) => {
    left: number;
    top: number;
    width: number;
    height: number;
  };
  visible?: boolean;
}

function WorldMapTerrainLayerInner({
  tileCoords,
  tiles,
  chunkStyle,
  visible = true,
}: WorldMapTerrainLayerProps) {
  if (!visible) {
    return null;
  }

  return (
    <div className="worldmap-terrain-layer" aria-hidden="true">
      {tileCoords.map(({ tileX, tileZ, zoom }) => {
        const key = tileKey(tileX, tileZ, zoom);
        const rec = tiles[key];
        const style = chunkStyle(tileX, tileZ);

        if (rec?.state === 'loaded' && rec.blobUrl) {
          return (
            <img
              key={key}
              className="worldmap-terrain-tile"
              src={rec.blobUrl}
              alt=""
              draggable={false}
              style={style}
            />
          );
        }

        return (
          <div
            key={key}
            className={`worldmap-terrain-placeholder${
              rec?.state === 'loading' || rec?.state === 'pending'
                ? ' worldmap-terrain-placeholder-loading'
                : ''
            }`}
            style={style}
          />
        );
      })}
    </div>
  );
}

export const WorldMapTerrainLayer = memo(WorldMapTerrainLayerInner);
