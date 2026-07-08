import { useEffect, useMemo, useState, useCallback } from 'react';
import { Segmented, Progress } from 'antd';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { MapContainer, Marker, Tooltip, useMap } from 'react-leaflet';
import { useI18n } from '@/i18n';
import { useWorldMapProgress } from '@/hooks/useWorldMapProgress';
import {
  buildDynmapTileUrlAtDisplayZoom,
  DYNMAP_MAX_NATIVE_ZOOM,
  worldBoundsToLatLngBounds,
  DYNMAP_TILE_BLOCKS_Z0,
} from '@/utils/dynmapTiles';
import { resolveWorldMapTileViewId } from '@/utils/worldMapViews';
import type { TopologyNodeDto, WorldMapMarkerDto, WorldMapMetaDto, WorldMapViewDto } from '@/types/dto';
import type { TopologyDisplaySettings, WorldMapObliqueDirection } from '@/types/topologyDisplay';
import { filterMarkersByDim } from '@/utils/worldMapMarkers';
import { DynmapAeOverlayBridge } from '@/components/topology/DynmapAeOverlayBridge';

// Fix default Leaflet icon paths for bundlers
delete (L.Icon.Default.prototype as unknown as Record<string, unknown>)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

/** Creates a DivIcon for AE device markers. */
function createAeMarkerIcon(selected: boolean): L.DivIcon {
  const bgColor = selected ? '#1890ff' : '#722ed1';
  return L.divIcon({
    html: `<div style="
      width:16px;height:16px;border-radius:50%;
      background:${bgColor};border:2px solid #fff;
      box-shadow:0 1px 4px rgba(0,0,0,0.4);
    "></div>`,
    className: 'worldmap-ae-marker',
    iconSize: [16, 16],
    iconAnchor: [8, 8],
  });
}

export interface TopologyDynmapViewProps {
  meta: WorldMapMetaDto;
  markers: WorldMapMarkerDto[];
  networkId: number;
  nodeIndex: Map<string, TopologyNodeDto>;
  selectedNodeId: string | null;
  onNodeSelect: (node: TopologyNodeDto | null) => void;
  onDriveClick?: (node: TopologyNodeDto) => void;
  obliqueDirection: WorldMapObliqueDirection;
  displaySettings: TopologyDisplaySettings;
  height?: number;
  progressEpoch?: number;
}

/**
 * Converts Minecraft world X/Z coordinates to Leaflet lat/lng
 * using the same projection as the Dynmap tile layer (block / DYNMAP_TILE_BLOCKS_Z0).
 */
function markerWorldToLatLng(worldX: number, worldZ: number): L.LatLngTuple {
  const lat = worldZ / DYNMAP_TILE_BLOCKS_Z0;
  const lng = worldX / DYNMAP_TILE_BLOCKS_Z0;
  return [lat, lng];
}

/** Inner component that calls fitBounds and creates the Dynmap tile layer. */
function DynmapFitBounds({
  meta,
  activeDim,
  getTileUrl,
  maxNativeZoom,
}: {
  meta: WorldMapMetaDto;
  activeDim: number;
  getTileUrl: (coords: { x: number; y: number; z: number }) => string;
  maxNativeZoom: number;
}) {
  const map = useMap();

  // Create / update tile layer — always fetch maxNativeZoom tiles, scale via Leaflet
  useEffect(() => {
    const urlFn = (coords: L.Coords) =>
      getTileUrl({ x: coords.x, y: coords.y, z: coords.z });
    const tileLayer = (
      L.tileLayer as unknown as (u: (c: L.Coords) => string, o?: L.TileLayerOptions) => L.TileLayer
    )(urlFn, {
        minZoom: 0,
        maxZoom: maxNativeZoom + 2,
        maxNativeZoom,
      },
    ).addTo(map);

    return () => {
      tileLayer.remove();
    };
  }, [map, getTileUrl, maxNativeZoom]);

  // Fit bounds on active dimension change
  useEffect(() => {
    const dimInfo = meta.dimensions?.find((d) => d.dim === activeDim) ?? meta.dimensions?.[0];
    if (!dimInfo) return;

    const bounds = worldBoundsToLatLngBounds(
      dimInfo.minX,
      dimInfo.maxX,
      dimInfo.minZ,
      dimInfo.maxZ,
    );
    const leafletBounds = L.latLngBounds(
      L.latLng(bounds[0][0], bounds[0][1]),
      L.latLng(bounds[1][0], bounds[1][1]),
    );
    map.fitBounds(leafletBounds, { padding: [30, 30], maxZoom: 4 });
  }, [map, meta.dimensions, activeDim]);

  return null;
}

/** Maps view id to a human-readable GWM perspective name. */
function perspectiveLabel(viewId: string, t: (k: string, ...args: string[]) => string): string {
  switch (viewId) {
    case 'flat':
      return t('dynmapPerspective_flat') || 'Flat / lowres';
    case 'oblique':
    case 'oblique_se':
    case 'iso_se':
      return t('dynmapPerspective_oblique') || 'ISO SE 30° hires';
    default:
      return viewId;
  }
}

/**
 * Leaflet-based world map view that renders Dynmap/GWM pre-rendered HD tiles
 * with AE device markers overlaid, sharing the same UX patterns as self mode.
 */
export function TopologyDynmapView({
  meta,
  markers,
  networkId,
  nodeIndex,
  selectedNodeId,
  onNodeSelect,
  onDriveClick,
  obliqueDirection,
  displaySettings,
  height = 520,
  progressEpoch = 0,
}: TopologyDynmapViewProps) {
  const { t } = useI18n();

  // --- Shared dimension switching ---
  const [activeDim, setActiveDim] = useState<number>(() => meta.dimensions[0]?.dim ?? 0);

  const defaultView = meta.views?.[0]?.id ?? 'flat';
  const [activeView, setActiveView] = useState<string>(defaultView);

  const viewOptions = useMemo(() => {
    const views: WorldMapViewDto[] =
      meta.views && meta.views.length > 0
        ? meta.views
        : [
            { id: 'flat', labelKey: 'adm.webae.worldmap.view.flat' },
            { id: 'oblique', labelKey: 'adm.webae.worldmap.view.oblique' },
          ];
    return views.map((v) => ({
      value: v.id,
      label: t(`worldMapView_${v.id}`),
    }));
  }, [meta.views, t]);

  const tileView = useMemo(
    () => resolveWorldMapTileViewId(activeView, obliqueDirection),
    [activeView, obliqueDirection],
  );

  // --- Dimension filter for AE markers ---
  const dimMarkers = useMemo(() => filterMarkersByDim(markers, activeDim), [markers, activeDim]);

  // --- Tile URL ---
  const dynmapWorld = meta.dynmapWorldName || 'world';
  const tileUrlTemplateBase =
    meta.dynmapTileUrlTemplate ||
    `/api/worldmap/dynmap-tiles/${dynmapWorld}/{z}/{x}/{y}.png`;

  const tileUrlTemplate = useMemo(() => {
    const base = tileUrlTemplateBase.split('?')[0];
    if (tileUrlTemplateBase.includes('?view=')) return tileUrlTemplateBase;
    return `${base}?view=${encodeURIComponent(tileView)}`;
  }, [tileUrlTemplateBase, tileView]);

  const maxNativeZoom = meta.dynmapMaxZoom ?? DYNMAP_MAX_NATIVE_ZOOM;

  const getTileUrl = useCallback(
    (coords: { x: number; y: number; z: number }): string => {
      return buildDynmapTileUrlAtDisplayZoom(
        tileUrlTemplate,
        dynmapWorld,
        coords.z,
        coords.x,
        coords.y,
        maxNativeZoom
      );
    },
    [tileUrlTemplate, dynmapWorld, maxNativeZoom],
  );

  // --- AE overlay progress (terrain is external; only poll when AE layer enabled) ---
  const aeOverlayQuality =
    (meta.aeOverlayQualityTier as 'low' | 'medium' | 'high' | 'ultra' | undefined) ?? 'ultra';
  const aeOverlayVisible = displaySettings.showWorldMapAeOverlay;
  const { progress, polling, startPolling, stopPolling } = useWorldMapProgress({
    networkId,
    view: tileView,
    dim: activeDim,
    quality: aeOverlayQuality,
    enabled: false,
  });

  useEffect(() => {
    if (!aeOverlayVisible) {
      stopPolling();
      return;
    }
    startPolling();
    return () => stopPolling();
  }, [networkId, tileView, activeDim, aeOverlayQuality, progressEpoch, aeOverlayVisible, startPolling, stopPolling]);

  const progressPercent =
    progress?.total && progress.total > 0
      ? Math.round(((progress.completed ?? 0) / progress.total) * 100)
      : 0;

  const showProgressUi =
    aeOverlayVisible &&
    (polling || ((progress?.total ?? 0) > 0 && (progress?.completed ?? 0) < (progress?.total ?? 0)));

  // --- AE marker overlay visibility (chunk tint layer is separate) ---
  const aeMarkerVisible = displaySettings.showWorldMapDeviceIcons;

  // --- Marker click handler (consistent with self mode) ---
  const handleMarkerClick = useCallback(
    (marker: WorldMapMarkerDto) => {
      const node = nodeIndex.get(marker.nodeId) ?? null;
      if (!node) return;
      onNodeSelect(node);
      if (node.type === 'drive') onDriveClick?.(node);
    },
    [nodeIndex, onNodeSelect, onDriveClick],
  );

  // --- Dimension tabs ---
  const dimOptions = meta.dimensions.map((d) => ({
    value: String(d.dim),
    label: d.name || t('worldMapDimTab', String(d.dim)),
  }));

  const terrainSourceLabel =
    meta.terrainSource === 'dynmap'
      ? t('worldMapTerrainSource_dynmap') || 'Dynmap 地形'
      : t('worldMapTerrainSource_self') || '内置渲染';

  const initialCenter: L.LatLngTuple = [0, 0];

  return (
    <div className="topology-worldmap-root">
      <div className="topology-worldmap-toolbar">
        {dimOptions.length > 1 && (
          <div className="topology-worldmap-dim-tabs">
            <Segmented
              size="small"
              value={String(activeDim)}
              onChange={(v) => setActiveDim(Number(v))}
              options={dimOptions}
            />
          </div>
        )}
        {viewOptions.length > 1 && (
          <div className="topology-worldmap-view-tabs">
            <Segmented
              size="small"
              value={activeView}
              onChange={(v) => setActiveView(String(v))}
              options={viewOptions}
            />
          </div>
        )}
        {/* Perspective label for quality mapping hint */}
        <span style={{ fontSize: 12, color: '#888', marginLeft: 8 }}>
          {perspectiveLabel(tileView, t)}
          {' \u00b7 '}
          {terrainSourceLabel}
        </span>
        {showProgressUi && (
          <div className="topology-worldmap-progress" style={{ flex: 1, minWidth: 160, maxWidth: 360 }}>
            <Progress
              percent={progressPercent}
              size="small"
              status={polling ? 'active' : 'normal'}
              format={() =>
                `${progress?.completed ?? 0}/${progress?.total ?? 0} ${t('worldMapProgressSuffix')}`
              }
            />
            <div className="topology-worldmap-progress-hint" title={t('worldMapProgressHint')}>
              {t('worldMapProgressHint')}
            </div>
          </div>
        )}
      </div>
      <div className="topology-worldmap-viewport" style={{ height, width: '100%' }}>
        <MapContainer
          center={initialCenter}
          zoom={2}
          minZoom={0}
          maxZoom={maxNativeZoom + 2}
          zoomControl={true}
          attributionControl={false}
          crs={L.CRS.Simple}
          style={{ height: '100%', width: '100%' }}
        >
          <DynmapFitBounds
            meta={meta}
            activeDim={activeDim}
            getTileUrl={getTileUrl}
            maxNativeZoom={maxNativeZoom}
          />
          <DynmapAeOverlayBridge
            meta={meta}
            networkId={networkId}
            activeDim={activeDim}
            tileView={tileView}
            displaySettings={displaySettings}
            progressEpoch={progressEpoch}
          />
          {aeMarkerVisible &&
            dimMarkers.map((marker) => {
              const pos = markerWorldToLatLng(marker.x, marker.z);
              const isSelected = marker.nodeId === selectedNodeId;

              return (
                <Marker
                  key={marker.id}
                  position={pos}
                  icon={createAeMarkerIcon(isSelected)}
                  eventHandlers={{
                    click: () => handleMarkerClick(marker),
                  }}
                >
                  {marker.displayName && (
                    <Tooltip direction="top" offset={[0, -10]}>
                      {marker.displayName}
                    </Tooltip>
                  )}
                </Marker>
              );
            })}
        </MapContainer>
      </div>
    </div>
  );
}
