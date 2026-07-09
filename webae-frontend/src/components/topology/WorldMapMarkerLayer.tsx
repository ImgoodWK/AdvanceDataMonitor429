import { memo, useMemo } from 'react';

import { Tooltip } from 'antd';

import { Icon } from '@/components/Icon';
import { useI18n } from '@/i18n';
import type { WorldMapMarkerDto } from '@/types/dto';
import { blockIconIdForNode } from '@/utils/aeCableColors';
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

function clusterTooltip(markers: WorldMapMarkerDto[], count: number, t: (key: string, ...args: string[]) => string) {
  const names = markers
    .slice(0, 4)
    .map((m) => m.displayName || m.type)
    .filter(Boolean);
  const head = t('worldMapMarkerCluster', String(count));
  if (names.length === 0) return head;
  const more = count > names.length ? `\n… +${count - names.length}` : '';
  return `${head}\n${names.join('\n')}${more}`;
}

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
          const clusterMarkers = props.marker ? [props.marker] : [];
          return (
            <Tooltip
              key={`cluster-${props.cluster_id}`}
              title={clusterTooltip(clusterMarkers, count, t)}
              mouseEnterDelay={0.2}
            >
              <button
                type="button"
                className="worldmap-marker-hit worldmap-cluster"
                style={{ left, top, width: MARKER_SIZE + 8, height: MARKER_SIZE + 8 }}
                aria-label={t('worldMapMarkerCluster', String(count))}
                onClick={() => onClusterClick?.(props.cluster_id!, worldX, worldZ)}
              >
                <span className="worldmap-cluster-count">{count}</span>
              </button>
            </Tooltip>
          );
        }

        const marker = props.marker;
        if (!marker) return null;
        const iconId = blockIconIdForNode(marker.type, marker.iconItemId);
        const selected = selectedNodeId === marker.nodeId;
        const label = marker.displayName || marker.type;

        return (
          <Tooltip key={marker.id} title={label} mouseEnterDelay={0.2}>
            <button
              type="button"
              className={`worldmap-marker-hit worldmap-marker${selected ? ' worldmap-marker-selected' : ''}`}
              style={{ left, top, width: MARKER_SIZE, height: MARKER_SIZE }}
              aria-label={label}
              onClick={() => onMarkerClick(marker)}
            >
              <Icon
                id={iconId}
                size={MARKER_SIZE - 4}
                alt={label}
                linkToWiki={false}
                className="worldmap-marker-icon"
              />
            </button>
          </Tooltip>
        );
      })}
    </div>
  );
}

export const WorldMapMarkerLayer = memo(WorldMapMarkerLayerInner);
