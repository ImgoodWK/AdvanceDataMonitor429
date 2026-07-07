import { memo, useMemo } from 'react';

import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import type { WorldMapMarkerDto } from '@/types/dto';
import { blockIconIdForNode } from '@/utils/aeCableColors';
import { buildIconUrl } from '@/utils/icon';
import {
  buildMarkerClusterIndex,
  queryClusters,
  scaleToClusterZoom,
  screenBBoxToWorldBBox,
  type ClusterFeatureProperties,
} from '@/utils/worldMapCluster';
import {
  worldToScreen,
  type MapViewport,
  type WorldMapOrigin,
} from '@/utils/worldMapProjection';

export interface WorldMapMarkerLayerProps {
  markers: WorldMapMarkerDto[];
  viewport: MapViewport;
  origin: WorldMapOrigin;
  containerWidth: number;
  containerHeight: number;
  selectedNodeId: string | null;
  onMarkerClick: (marker: WorldMapMarkerDto) => void;
  onClusterClick?: (clusterId: number, lng: number, lat: number) => void;
}

const MARKER_SIZE = 28;

function WorldMapMarkerLayerInner({
  markers,
  viewport,
  origin,
  containerWidth,
  containerHeight,
  selectedNodeId,
  onMarkerClick,
  onClusterClick,
}: WorldMapMarkerLayerProps) {
  const { iconPack, token, iconCacheEnabled, iconRenderMode } = useAppContext();
  const { t } = useI18n();

  const clusterIndex = useMemo(() => buildMarkerClusterIndex(markers), [markers]);

  const clusterZoom = scaleToClusterZoom(viewport.scale, origin.pxPerBlock);

  const features = useMemo(() => {
    if (containerWidth <= 0 || containerHeight <= 0 || markers.length === 0) return [];
    const toWorld = (sx: number, sy: number) => {
      const localX = (sx - viewport.panX) / viewport.scale;
      const localZ = -(sy - viewport.panY) / viewport.scale;
      return {
        x: origin.originX + localX / origin.pxPerBlock,
        z: origin.originZ + localZ / origin.pxPerBlock,
      };
    };
    const bbox = screenBBoxToWorldBBox(0, 0, containerWidth, containerHeight, toWorld);
    return queryClusters(clusterIndex, bbox, clusterZoom);
  }, [
    clusterIndex,
    clusterZoom,
    containerWidth,
    containerHeight,
    markers.length,
    origin.originX,
    origin.originZ,
    origin.pxPerBlock,
    viewport.panX,
    viewport.panY,
    viewport.scale,
  ]);

  return (
    <div className="worldmap-marker-layer" aria-hidden={false}>
      {features.map((feature) => {
        const props = feature.properties as ClusterFeatureProperties;
        const [worldX, worldZ] = feature.geometry.coordinates;
        const { sx, sy } = worldToScreen(worldX, worldZ, viewport, origin);
        const left = sx - MARKER_SIZE / 2;
        const top = sy - MARKER_SIZE / 2;

        if (props.cluster && props.cluster_id != null) {
          const count = props.point_count ?? 0;
          return (
            <button
              key={`cluster-${props.cluster_id}`}
              type="button"
              className="worldmap-marker-hit worldmap-cluster"
              style={{ left, top, width: MARKER_SIZE + 8, height: MARKER_SIZE + 8 }}
              title={t('worldMapMarkerCluster', String(count))}
              onClick={() => onClusterClick?.(props.cluster_id!, worldX, worldZ)}
            >
              {count}
            </button>
          );
        }

        const marker = props.marker;
        if (!marker) return null;
        const iconId = blockIconIdForNode(marker.type, marker.iconItemId);
        const iconUrl = buildIconUrl(iconId, iconPack, token, iconCacheEnabled, iconRenderMode);
        const selected = selectedNodeId === marker.nodeId;

        return (
          <button
            key={marker.id}
            type="button"
            className={`worldmap-marker-hit worldmap-marker${selected ? ' worldmap-marker-selected' : ''}`}
            style={{ left, top, width: MARKER_SIZE, height: MARKER_SIZE }}
            title={marker.displayName}
            onClick={() => onMarkerClick(marker)}
          >
            <img src={iconUrl} alt="" draggable={false} width={MARKER_SIZE - 4} height={MARKER_SIZE - 4} />
          </button>
        );
      })}
    </div>
  );
}

export const WorldMapMarkerLayer = memo(WorldMapMarkerLayerInner);
