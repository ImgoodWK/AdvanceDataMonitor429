import { useEffect, useMemo } from 'react';

import { WorldMapAeOverlayLayer } from '@/components/topology/WorldMapAeOverlayLayer';
import { WorldMapChunkStatusOverlay } from '@/components/topology/WorldMapChunkStatusOverlay';
import { useWorldMapProgress } from '@/hooks/useWorldMapProgress';
import { useWorldMapTileLoader } from '@/hooks/useWorldMapTileLoader';
import type { WorldMapMetaDto } from '@/types/dto';
import type { TopologyDisplaySettings } from '@/types/topologyDisplay';
import {
  boundsFromDimension,
  originFromBounds,
  type MapViewport,
  type WorldBounds,
  type WorldMapOrigin,
} from '@/utils/worldMapProjection';
import { boundsFromChunkScope, type ChunkScope } from '@/utils/worldMapTerrain';
import type { WorldMapQualityTierId } from '@/utils/worldMapTerrain';

export interface WorldMapAeOverlayStackProps {
  meta: WorldMapMetaDto;
  networkId: number;
  activeDim: number;
  tileView: string;
  displaySettings: TopologyDisplaySettings;
  viewport: MapViewport;
  origin: WorldMapOrigin;
  containerWidth: number;
  containerHeight: number;
  progressEpoch?: number;
  /** When false, skip progress polling (e.g. dynmap terrain-only). */
  pollProgress?: boolean;
  /** When false, parent renders combined chunk status overlay. */
  showChunkStatus?: boolean;
  className?: string;
}

function resolveAeQuality(meta: WorldMapMetaDto): WorldMapQualityTierId {
  const raw = meta.aeOverlayQualityTier ?? 'ultra';
  if (raw === 'low' || raw === 'medium' || raw === 'high' || raw === 'ultra') {
    return raw;
  }
  return 'ultra';
}

export function WorldMapAeOverlayStack({
  meta,
  networkId,
  activeDim,
  tileView,
  displaySettings,
  viewport,
  origin,
  containerWidth,
  containerHeight,
  progressEpoch = 0,
  pollProgress = true,
  showChunkStatus = true,
  className = 'worldmap-ae-overlay-stack',
}: WorldMapAeOverlayStackProps) {
  const aeQuality = resolveAeQuality(meta);
  const aeVisible = meta.worldMapEnabled !== false && displaySettings.showWorldMapAeOverlay;

  const dimInfo = useMemo(
    () => meta.dimensions.find((d) => d.dim === activeDim) ?? meta.dimensions[0],
    [meta.dimensions, activeDim]
  );

  const chunkScope: ChunkScope | null = useMemo(() => {
    if (!dimInfo) return null;
    if (
      dimInfo.minChunkX == null ||
      dimInfo.maxChunkX == null ||
      dimInfo.minChunkZ == null ||
      dimInfo.maxChunkZ == null
    ) {
      return null;
    }
    return {
      minChunkX: dimInfo.minChunkX,
      maxChunkX: dimInfo.maxChunkX,
      minChunkZ: dimInfo.minChunkZ,
      maxChunkZ: dimInfo.maxChunkZ,
      allowedChunks: dimInfo.allowedChunks,
    };
  }, [dimInfo]);

  const aeLoader = useWorldMapTileLoader({
    dim: activeDim,
    networkId,
    chunkScope,
    viewport,
    origin,
    containerWidth,
    containerHeight,
    view: tileView,
    layer: 'ae',
    quality: aeQuality,
    zoom: 0,
    active: aeVisible,
    prefetch: true,
  });

  const { progress, startPolling, stopPolling } = useWorldMapProgress({
    networkId,
    view: tileView,
    dim: activeDim,
    quality: aeQuality,
    enabled: false,
  });

  useEffect(() => {
    if (!pollProgress || !aeVisible) {
      stopPolling();
      return;
    }
    startPolling();
    return () => stopPolling();
  }, [networkId, tileView, activeDim, aeQuality, progressEpoch, pollProgress, aeVisible, startPolling, stopPolling]);

  if (!aeVisible) {
    return null;
  }

  return (
    <div className={className} aria-hidden={!aeVisible}>
      <WorldMapAeOverlayLayer
        tileCoords={aeLoader.debouncedTiles}
        tiles={aeLoader.tiles}
        chunkStyle={aeLoader.chunkStyle}
        visible={aeVisible}
        opacity={displaySettings.worldMapAeOverlayOpacity}
        categoryColors={displaySettings.worldMapAeCategoryColors}
        itemColorOverrides={displaySettings.worldMapAeItemColorOverrides}
      />
      {showChunkStatus && (
        <WorldMapChunkStatusOverlay
          tileCoords={aeLoader.debouncedTiles}
          terrainTiles={{}}
          aeTiles={aeLoader.tiles}
          serverProgress={progress?.chunks ?? null}
          chunkStyle={aeLoader.chunkStyle}
          showTerrain={false}
          showAe={true}
        />
      )}
    </div>
  );
}

export function worldMapBoundsFromDim(
  dimInfo: WorldMapMetaDto['dimensions'][number] | undefined,
  pxPerBlock: number
): { bounds: WorldBounds; origin: WorldMapOrigin } {
  if (!dimInfo) {
    const bounds = { minX: 0, maxX: 0, minZ: 0, maxZ: 0 };
    return { bounds, origin: { ...originFromBounds(bounds), pxPerBlock } };
  }
  const chunkScope: ChunkScope | null =
    dimInfo.minChunkX != null &&
    dimInfo.maxChunkX != null &&
    dimInfo.minChunkZ != null &&
    dimInfo.maxChunkZ != null
      ? {
          minChunkX: dimInfo.minChunkX,
          maxChunkX: dimInfo.maxChunkX,
          minChunkZ: dimInfo.minChunkZ,
          maxChunkZ: dimInfo.maxChunkZ,
          allowedChunks: dimInfo.allowedChunks,
        }
      : null;
  const chunkBounds = boundsFromChunkScope(chunkScope);
  const bounds =
    chunkBounds ??
    boundsFromDimension(dimInfo.minX, dimInfo.maxX, dimInfo.minZ, dimInfo.maxZ);
  return { bounds, origin: { ...originFromBounds(bounds), pxPerBlock } };
}
