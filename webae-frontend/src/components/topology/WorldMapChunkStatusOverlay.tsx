import { memo } from 'react';
import { LoadingOutlined, ReloadOutlined } from '@ant-design/icons';

import { useI18n } from '@/i18n';
import type { WorldMapTileLoadState } from '@/hooks/useWorldMapTileLoader';
import type { WorldMapChunkProgress } from '@/hooks/useWorldMapProgress';
import { chunkKey, tileKey, type WorldMapTileCoord } from '@/utils/worldMapTerrain';

export interface WorldMapChunkStatusOverlayProps {
  tileCoords: WorldMapTileCoord[];
  terrainTiles: Record<string, { state: WorldMapTileLoadState }>;
  aeTiles: Record<string, { state: WorldMapTileLoadState }>;
  serverProgress?: Record<string, WorldMapChunkProgress> | null;
  chunkStyle: (chunkX: number, chunkZ: number) => {
    left: number;
    top: number;
    width: number;
    height: number;
  };
  showTerrain: boolean;
  showAe: boolean;
}

function layerBadge(
  layer: 'terrain' | 'ae',
  tileState: WorldMapTileLoadState | undefined,
  serverState: string | undefined
): 'hidden' | 'loading' | 'pending' | 'error' {
  if (serverState === 'done' || serverState === 'empty') {
    return 'hidden';
  }
  if (serverState === 'queued' || serverState === 'rendering') {
    return serverState === 'queued' ? 'pending' : 'loading';
  }
  if (serverState === 'failed') {
    return 'error';
  }
  if (tileState === 'loaded') {
    return 'hidden';
  }
  if (tileState === 'error') {
    return 'error';
  }
  if (tileState === 'pending' || tileState === 'upgrading') {
    return 'pending';
  }
  if (tileState === 'loading') {
    return 'loading';
  }
  return 'hidden';
}

function WorldMapChunkStatusOverlayInner({
  tileCoords,
  terrainTiles,
  aeTiles,
  serverProgress,
  chunkStyle,
  showTerrain,
  showAe,
}: WorldMapChunkStatusOverlayProps) {
  const { t } = useI18n();

  return (
    <div className="worldmap-chunk-status-layer" aria-hidden="true">
      {tileCoords.map(({ tileX, tileZ, zoom }) => {
        const key = tileKey(tileX, tileZ, zoom);
        const progressKey = zoom > 0 ? null : chunkKey(tileX, tileZ);
        const sp = progressKey ? serverProgress?.[progressKey] : undefined;
        const terrainBadge = showTerrain
          ? layerBadge('terrain', terrainTiles[key]?.state, sp?.terrain)
          : 'hidden';
        const aeBadge = showAe ? layerBadge('ae', aeTiles[key]?.state, sp?.ae) : 'hidden';
        if (terrainBadge === 'hidden' && aeBadge === 'hidden') {
          return null;
        }
        const style = chunkStyle(tileX, tileZ);
        return (
          <div key={key} className="worldmap-chunk-status" style={style}>
            {terrainBadge !== 'hidden' && (
              <span
                className={`worldmap-chunk-status-badge worldmap-chunk-status-${terrainBadge}`}
                title={t('worldMapLayerTerrain')}
              >
                {terrainBadge === 'loading' ? <LoadingOutlined spin /> : null}
                {terrainBadge === 'error' ? <ReloadOutlined /> : null}
                <span className="worldmap-chunk-status-label">T</span>
              </span>
            )}
            {aeBadge !== 'hidden' && (
              <span
                className={`worldmap-chunk-status-badge worldmap-chunk-status-${aeBadge}`}
                title={t('worldMapLayerAeOverlay')}
              >
                {aeBadge === 'loading' ? <LoadingOutlined spin /> : null}
                {aeBadge === 'error' ? <ReloadOutlined /> : null}
                <span className="worldmap-chunk-status-label">AE</span>
              </span>
            )}
          </div>
        );
      })}
    </div>
  );
}

export const WorldMapChunkStatusOverlay = memo(WorldMapChunkStatusOverlayInner);
