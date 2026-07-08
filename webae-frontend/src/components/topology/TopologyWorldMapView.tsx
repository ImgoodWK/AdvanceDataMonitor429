import {

  forwardRef,

  useCallback,

  useEffect,

  useImperativeHandle,

  useMemo,

  useRef,

  useState,

} from 'react';



import { Segmented, Progress } from 'antd';



import { useI18n } from '@/i18n';

import { WorldMapChunkStatusOverlay } from '@/components/topology/WorldMapChunkStatusOverlay';

import { WorldMapMarkerLayer } from '@/components/topology/WorldMapMarkerLayer';

import { WorldMapAeOverlayLayer } from '@/components/topology/WorldMapAeOverlayLayer';

import { WorldMapTerrainLayer } from '@/components/topology/WorldMapTerrainLayer';

import type { TopologyGraphHandle } from '@/components/topology/topologyGraphHandle';

import { useMapViewport } from '@/hooks/useMapViewport';

import { useWorldMapProgress } from '@/hooks/useWorldMapProgress';

import { useWorldMapTileLoader } from '@/hooks/useWorldMapTileLoader';

import type { TopologyNodeDto, WorldMapMarkerDto, WorldMapMetaDto, WorldMapViewDto } from '@/types/dto';

import type { TopologyDisplaySettings, WorldMapObliqueDirection } from '@/types/topologyDisplay';

import { filterMarkersByDim } from '@/utils/worldMapMarkers';

import {

  boundsFromDimension,

  originFromBounds,

  type WorldBounds,

  type WorldMapOrigin,

} from '@/utils/worldMapProjection';

import { boundsFromChunkScope, selectWorldMapZoomLevel, type ChunkScope } from '@/utils/worldMapTerrain';

import { resolveWorldMapTileViewId } from '@/utils/worldMapViews';



function worldMapViewI18nKey(labelKey: string, viewId: string): string {

  const suffix = labelKey.replace(/^adm\.webae\.worldmap\.view\./, '');

  if (suffix !== labelKey) {

    return `worldMapView_${suffix}`;

  }

  return `worldMapView_${viewId}`;

}



export interface TopologyWorldMapViewProps {

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

  layoutEpoch?: string;

  /** Increment to start tile progress polling (refresh / snapshot). */
  progressEpoch?: number;

}



export const TopologyWorldMapView = forwardRef<TopologyGraphHandle, TopologyWorldMapViewProps>(

  function TopologyWorldMapView(

    {

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

      layoutEpoch = '',

      progressEpoch = 0,

    },

    ref

  ) {

    const { t } = useI18n();

    const [activeDim, setActiveDim] = useState<number>(() => meta.dimensions[0]?.dim ?? 0);

    const defaultView = meta.views?.[0]?.id ?? 'flat';

    const [activeView, setActiveView] = useState<string>(defaultView);

    const [containerSize, setContainerSize] = useState({ w: 0, h: 0 });

    const lastFitEpochRef = useRef('');



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

        label: t(worldMapViewI18nKey(v.labelKey, v.id)),

      }));

    }, [meta.views, t]);



    const tileView = useMemo(

      () => resolveWorldMapTileViewId(activeView, obliqueDirection),

      [activeView, obliqueDirection]

    );



    const pxPerBlock = meta.pxPerBlock ?? 8;



    const dimInfo = useMemo(

      () => meta.dimensions.find((d) => d.dim === activeDim) ?? meta.dimensions[0],

      [meta.dimensions, activeDim]

    );



    const dimMarkers = useMemo(() => filterMarkersByDim(markers, activeDim), [markers, activeDim]);



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



    const bounds: WorldBounds = useMemo(() => {

      if (!dimInfo) return { minX: 0, maxX: 0, minZ: 0, maxZ: 0 };

      const chunkBounds = boundsFromChunkScope(chunkScope);

      if (chunkBounds) return chunkBounds;

      return boundsFromDimension(dimInfo.minX, dimInfo.maxX, dimInfo.minZ, dimInfo.maxZ);

    }, [dimInfo, chunkScope]);



    const origin: WorldMapOrigin = useMemo(

      () => ({

        ...originFromBounds(bounds),

        pxPerBlock,

      }),

      [bounds, pxPerBlock]

    );



    const {

      containerRef,

      viewport,

      setViewport,

      onWheel,

      onPointerDown,

      onPointerMove,

      onPointerUp,

      fitBounds,

    } = useMapViewport();

    const worldMapQuality = displaySettings.worldMapQuality ?? 'medium';
    const maxZoomLevel = meta.zoomLevels?.length ?? 3;
    const mapZoom = useMemo(
      () => selectWorldMapZoomLevel(viewport.scale, pxPerBlock, maxZoomLevel),
      [viewport.scale, pxPerBlock, maxZoomLevel]
    );
    const terrainEnabled = meta.worldMapEnabled !== false && displaySettings.showWorldMapTerrain;
    const aeVisible = meta.worldMapEnabled !== false && displaySettings.showWorldMapAeOverlay;

    const terrainLoader = useWorldMapTileLoader({
      dim: activeDim,
      networkId,
      chunkScope,
      viewport,
      origin,
      containerWidth: containerSize.w,
      containerHeight: containerSize.h,
      view: tileView,
      layer: 'terrain',
      quality: worldMapQuality,
      zoom: mapZoom,
      active: terrainEnabled,
    });

    const aeLoader = useWorldMapTileLoader({
      dim: activeDim,
      networkId,
      chunkScope,
      viewport,
      origin,
      containerWidth: containerSize.w,
      containerHeight: containerSize.h,
      view: tileView,
      layer: 'ae',
      quality: worldMapQuality,
      zoom: mapZoom,
      active: aeVisible,
      prefetch: true,
    });

    const overlayTiles =
      terrainLoader.debouncedTiles.length >= aeLoader.debouncedTiles.length
        ? terrainLoader.debouncedTiles
        : aeLoader.debouncedTiles;

    const { progress, polling, startPolling, stopPolling } = useWorldMapProgress({
      networkId,
      view: tileView,
      dim: activeDim,
      quality: worldMapQuality,
      enabled: false,
    });

    useEffect(() => {
      startPolling();
      return () => stopPolling();
    }, [networkId, tileView, activeDim, worldMapQuality, progressEpoch, startPolling, stopPolling]);

    const progressPercent =
      progress?.total && progress.total > 0
        ? Math.round(((progress.completed ?? 0) / progress.total) * 100)
        : 0;

    const showProgressUi =
      polling || ((progress?.total ?? 0) > 0 && (progress?.completed ?? 0) < (progress?.total ?? 0));



    const fitView = useCallback(() => {

      fitBounds(bounds, pxPerBlock);

    }, [fitBounds, bounds, pxPerBlock]);



    useImperativeHandle(ref, () => ({

      resetView: () => setViewport({ panX: 0, panY: 0, scale: 1 }),

      fitView,

      zoomIn: () =>

        setViewport((v) => ({

          ...v,

          scale: Math.min(6, v.scale * 1.15),

        })),

      zoomOut: () =>

        setViewport((v) => ({

          ...v,

          scale: Math.max(0.15, v.scale / 1.15),

        })),

    }));



    useEffect(() => {

      if (meta.dimensions.length > 0 && !meta.dimensions.some((d) => d.dim === activeDim)) {

        setActiveDim(meta.dimensions[0].dim);

      }

    }, [meta.dimensions, activeDim]);



    useEffect(() => {

      const ids = viewOptions.map((o) => o.value);

      if (ids.length > 0 && !ids.includes(activeView)) {

        setActiveView(ids[0]);

      }

    }, [viewOptions, activeView]);



    useEffect(() => {

      const el = containerRef.current;

      if (!el) return;

      const ro = new ResizeObserver((entries) => {

        const entry = entries[0];

        if (entry) {

          setContainerSize({ w: entry.contentRect.width, h: entry.contentRect.height });

        }

      });

      ro.observe(el);

      setContainerSize({ w: el.clientWidth, h: el.clientHeight });

      return () => ro.disconnect();

    }, [containerRef]);



    useEffect(() => {

      if (!layoutEpoch || layoutEpoch === lastFitEpochRef.current) return;

      lastFitEpochRef.current = layoutEpoch;

      fitView();

    }, [layoutEpoch, fitView]);



    useEffect(() => {

      fitView();

    }, [activeDim, activeView, obliqueDirection, fitView]);



    const handleMarkerClick = useCallback(

      (marker: WorldMapMarkerDto) => {

        const node = nodeIndex.get(marker.nodeId) ?? null;

        if (!node) return;

        onNodeSelect(node);

        if (node.type === 'drive') onDriveClick?.(node);

      },

      [nodeIndex, onNodeSelect, onDriveClick]

    );



    const handleClusterClick = useCallback(

      (_clusterId: number, _lng: number, _lat: number) => {

        setViewport((v) => ({

          ...v,

          scale: Math.min(6, v.scale * 1.5),

        }));

      },

      [setViewport]

    );



    const dimOptions = meta.dimensions.map((d) => ({

      value: String(d.dim),

      label: d.name || t('worldMapDimTab', String(d.dim)),

    }));



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

        <div

          ref={containerRef}

          className="topology-worldmap-viewport"

          style={{ height, touchAction: 'none' }}

          onWheel={onWheel}

          onPointerDown={onPointerDown}

          onPointerMove={onPointerMove}

          onPointerUp={onPointerUp}

        >

          <div

            className="topology-worldmap-grid"

            style={{

              backgroundSize: `${origin.pxPerBlock * viewport.scale}px ${origin.pxPerBlock * viewport.scale}px`,

              backgroundPosition: `${viewport.panX}px ${viewport.panY}px`,

            }}

          />

          <WorldMapTerrainLayer
            tileCoords={terrainLoader.debouncedTiles}
            tiles={terrainLoader.tiles}
            chunkStyle={terrainLoader.chunkStyle}
            visible={terrainEnabled}
          />

          <WorldMapAeOverlayLayer
            tileCoords={aeLoader.debouncedTiles}
            tiles={aeLoader.tiles}
            chunkStyle={aeLoader.chunkStyle}
            visible={aeVisible}
            opacity={displaySettings.worldMapAeOverlayOpacity}
          />

          <WorldMapChunkStatusOverlay
            tileCoords={overlayTiles}
            terrainTiles={terrainLoader.tiles}
            aeTiles={aeLoader.tiles}
            serverProgress={progress?.chunks ?? null}
            chunkStyle={terrainLoader.chunkStyle}
            showTerrain={terrainEnabled}
            showAe={true}
          />

          {showProgressUi && (
            <div className="worldmap-chunk-status-legend" title={t('worldMapChunkBadgeHint')}>
              {t('worldMapChunkBadgeHint')}
            </div>
          )}

          {displaySettings.showWorldMapDeviceIcons && (

          <WorldMapMarkerLayer

            markers={dimMarkers}

            viewport={viewport}

            origin={origin}

            containerWidth={containerSize.w}

            containerHeight={containerSize.h}

            selectedNodeId={selectedNodeId}

            onMarkerClick={handleMarkerClick}

            onClusterClick={handleClusterClick}

          />

          )}

        </div>

      </div>

    );

  }

);


