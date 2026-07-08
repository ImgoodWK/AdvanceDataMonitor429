import { useEffect, useMemo, useState } from 'react';
import { useMap } from 'react-leaflet';

import {
  WorldMapAeOverlayStack,
  worldMapBoundsFromDim,
} from '@/components/topology/WorldMapAeOverlayStack';
import type { WorldMapMetaDto } from '@/types/dto';
import type { TopologyDisplaySettings } from '@/types/topologyDisplay';
import { viewportFromLeafletMap } from '@/utils/worldMapLeafletSync';
import type { MapViewport } from '@/utils/worldMapProjection';

export interface DynmapAeOverlayBridgeProps {
  meta: WorldMapMetaDto;
  networkId: number;
  activeDim: number;
  tileView: string;
  displaySettings: TopologyDisplaySettings;
  progressEpoch?: number;
}

/**
 * Syncs Leaflet map pan/zoom to the self-rendered AE chunk overlay coordinate system.
 */
export function DynmapAeOverlayBridge({
  meta,
  networkId,
  activeDim,
  tileView,
  displaySettings,
  progressEpoch = 0,
}: DynmapAeOverlayBridgeProps) {
  const map = useMap();
  const [viewport, setViewport] = useState<MapViewport>({ panX: 0, panY: 0, scale: 1 });
  const [containerSize, setContainerSize] = useState({ w: 0, h: 0 });

  const dimInfo = useMemo(
    () => meta.dimensions.find((d) => d.dim === activeDim) ?? meta.dimensions[0],
    [meta.dimensions, activeDim]
  );

  const aeTier = meta.qualityTiers?.find((qt) => qt.id === (meta.aeOverlayQualityTier ?? 'ultra'));
  const pxPerBlock = aeTier?.pxPerBlock ?? meta.pxPerBlock ?? 32;
  const { origin } = useMemo(
    () => worldMapBoundsFromDim(dimInfo, pxPerBlock),
    [dimInfo, pxPerBlock]
  );

  useEffect(() => {
    const container = map.getContainer();
    const sync = () => {
      const w = container.clientWidth;
      const h = container.clientHeight;
      setContainerSize({ w, h });
      setViewport(viewportFromLeafletMap(map, origin, w, h));
    };
    sync();
    map.on('moveend', sync);
    map.on('zoomend', sync);
    map.on('resize', sync);
    const ro = new ResizeObserver(sync);
    ro.observe(container);
    return () => {
      map.off('moveend', sync);
      map.off('zoomend', sync);
      map.off('resize', sync);
      ro.disconnect();
    };
  }, [map, origin]);

  const aeVisible = meta.worldMapEnabled !== false && displaySettings.showWorldMapAeOverlay;
  if (!aeVisible) {
    return null;
  }

  return (
    <div
      className="worldmap-dynmap-ae-overlay"
      style={{
        position: 'absolute',
        inset: 0,
        pointerEvents: 'none',
        zIndex: 450,
        overflow: 'hidden',
      }}
    >
      <WorldMapAeOverlayStack
        meta={meta}
        networkId={networkId}
        activeDim={activeDim}
        tileView={tileView}
        displaySettings={displaySettings}
        viewport={viewport}
        origin={origin}
        containerWidth={containerSize.w}
        containerHeight={containerSize.h}
        progressEpoch={progressEpoch}
        pollProgress={true}
        showChunkStatus={true}
      />
    </div>
  );
}
