import { memo, useMemo } from 'react';

import { Tooltip } from 'antd';

import { Icon } from '@/components/Icon';
import { useI18n } from '@/i18n';
import type { WorldMapMarkerDto } from '@/types/dto';
import type { TopologyDisplaySettings } from '@/types/topologyDisplay';
import { blockIconIdForNode } from '@/utils/aeCableColors';
import { iconItemFromRegistryId } from '@/utils/icon';
import {
  buildMarkerClusterIndex,
  queryClusters,
  scaleToClusterZoom,
  screenBBoxToWorldBBox,
  type ClusterFeatureProperties,
  type MarkerClusterIndex,
} from '@/utils/worldMapCluster';
import {
  DEFAULT_WORLD_MAP_AE_CATEGORY_COLORS,
  markerStyleFromCategory,
  resolveMarkerAeCategory,
} from '@/utils/worldMapAeCategories';
import {
  CLUSTER_TOOLTIP_MAX_ICON_TYPES,
  summarizeClusterMarkersByIcon,
} from '@/utils/worldMapMarkers';
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
  displaySettings: TopologyDisplaySettings;
  onMarkerClick: (marker: WorldMapMarkerDto, clientX: number, clientY: number) => void;
  onMarkerContextMenu?: (
    marker: WorldMapMarkerDto,
    clientX: number,
    clientY: number
  ) => void;
  onClusterClick?: (
    clusterId: number,
    markers: WorldMapMarkerDto[],
    clientX: number,
    clientY: number
  ) => void;
}

const MARKER_SIZE = 28;

function WorldMapClusterTooltipContent({
  markers,
  count,
  t,
}: {
  markers: WorldMapMarkerDto[];
  count: number;
  t: (key: string, ...args: string[]) => string;
}) {
  const groups = useMemo(() => summarizeClusterMarkersByIcon(markers), [markers]);
  const shown = groups.slice(0, CLUSTER_TOOLTIP_MAX_ICON_TYPES);
  const hiddenTypeCount = groups.length - shown.length;

  return (
    <div className="worldmap-cluster-tooltip">
      <div className="worldmap-cluster-tooltip-head">{t('worldMapMarkerCluster', String(count))}</div>
      {shown.length > 0 && (
        <div className="worldmap-cluster-tooltip-grid">
          {shown.map((group) => (
            <span key={group.iconId} className="worldmap-cluster-tooltip-item" title={group.label}>
              <Icon
                item={iconItemFromRegistryId(group.iconId, group.label)}
                size={20}
                linkToWiki={false}
                alt={group.label}
              />
              <span className="worldmap-cluster-tooltip-count">x{group.count}</span>
            </span>
          ))}
          {hiddenTypeCount > 0 && (
            <span className="worldmap-cluster-tooltip-more">… +{hiddenTypeCount}</span>
          )}
        </div>
      )}
    </div>
  );
}

function getClusterMarkers(index: MarkerClusterIndex, clusterId: number): WorldMapMarkerDto[] {
  const leaves = index.getLeaves(clusterId, Infinity);
  const out: WorldMapMarkerDto[] = [];
  for (const leaf of leaves) {
    const marker = (leaf.properties as ClusterFeatureProperties).marker;
    if (marker) out.push(marker);
  }
  return out;
}

function WorldMapMarkerLayerInner({
  markers,
  viewport,
  origin,
  containerWidth,
  containerHeight,
  selectedNodeId,
  displaySettings,
  onMarkerClick,
  onMarkerContextMenu,
  onClusterClick,
}: WorldMapMarkerLayerProps) {
  const { t } = useI18n();

  const clusterIndex = useMemo(() => buildMarkerClusterIndex(markers), [markers]);

  const clusterZoom = scaleToClusterZoom(viewport.scale, origin.pxPerBlock);

  const tintEnabled = displaySettings.worldMapMarkerTintEnabled !== false;

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
          const clusterMarkers = getClusterMarkers(clusterIndex, props.cluster_id);
          return (
            <Tooltip
              key={`cluster-${props.cluster_id}`}
              title={<WorldMapClusterTooltipContent markers={clusterMarkers} count={count} t={t} />}
              overlayClassName="worldmap-cluster-tooltip-overlay"
              mouseEnterDelay={0.2}
            >
              <button
                type="button"
                className="worldmap-marker-hit worldmap-cluster"
                style={{ left, top, width: MARKER_SIZE + 8, height: MARKER_SIZE + 8 }}
                aria-label={t('worldMapMarkerCluster', String(count))}
                onClick={(e) => {
                  e.stopPropagation();
                  onClusterClick?.(props.cluster_id!, clusterMarkers, e.clientX, e.clientY);
                }}
              >
                <span className="worldmap-cluster-count">{count}</span>
              </button>
            </Tooltip>
          );
        }

        const marker = props.marker;
        if (!marker) return null;
        const label = marker.displayName || marker.type;
        const iconId = blockIconIdForNode(marker.type, marker.iconItemId);
        const iconItem = iconItemFromRegistryId(iconId, label);
        const selected = selectedNodeId === marker.nodeId;
        const category = resolveMarkerAeCategory(marker);
        const categoryColor =
          displaySettings.worldMapAeCategoryColors[category] ??
          DEFAULT_WORLD_MAP_AE_CATEGORY_COLORS[category];
        const tintStyle = tintEnabled ? markerStyleFromCategory(categoryColor) : undefined;

        return (
          <Tooltip key={marker.id} title={label} mouseEnterDelay={0.2}>
            <button
              type="button"
              className={`worldmap-marker-hit worldmap-marker${selected ? ' worldmap-marker-selected' : ''}`}
              style={{
                left,
                top,
                width: MARKER_SIZE,
                height: MARKER_SIZE,
                ...(tintStyle
                  ? {
                      boxShadow: `0 0 0 2px ${tintStyle.borderColor}`,
                      background: tintStyle.background,
                    }
                  : {}),
              }}
              aria-label={label}
              onClick={(e) => {
                e.stopPropagation();
                onMarkerClick(marker, e.clientX, e.clientY);
              }}
              onContextMenu={(e) => {
                if (!onMarkerContextMenu) return;
                e.preventDefault();
                e.stopPropagation();
                onMarkerContextMenu(marker, e.clientX, e.clientY);
              }}
            >
              <Icon
                item={iconItem}
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
