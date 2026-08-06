import {
  forwardRef,
  useCallback,
  useEffect,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
} from 'react';

import { Button, Popover, Progress, Segmented, message } from 'antd';
import { HistoryOutlined, PlusOutlined } from '@ant-design/icons';

import { useI18n } from '@/i18n';
import { WorldMapChunkStatusOverlay } from '@/components/topology/WorldMapChunkStatusOverlay';
import { WorldMapClusterPopup } from '@/components/topology/WorldMapClusterPopup';
import { WorldMapLegendRail } from '@/components/topology/WorldMapLegendRail';
import { WorldMapMarkerLayer } from '@/components/topology/WorldMapMarkerLayer';
import { WorldMapAnnotationLayer } from '@/components/topology/WorldMapAnnotationLayer';
import {
  WorldMapAnnotationModal,
  type WorldMapAnnotationPosition,
} from '@/components/topology/WorldMapAnnotationModal';
import { WorldMapAeOverlayLayer } from '@/components/topology/WorldMapAeOverlayLayer';
import { WorldMapDiffOverlay } from '@/components/topology/WorldMapDiffOverlay';
import { WorldMapTerrainLayer } from '@/components/topology/WorldMapTerrainLayer';
import { WorldMapVersionControls } from '@/components/topology/WorldMapVersionControls';
import type { TopologyGraphHandle } from '@/components/topology/topologyGraphHandle';
import { useMapViewport } from '@/hooks/useMapViewport';
import { useNonPassiveWheelZoom } from '@/hooks/useNonPassiveWheelZoom';
import { useWorldMapProgress } from '@/hooks/useWorldMapProgress';
import { useWorldMapTileLoader } from '@/hooks/useWorldMapTileLoader';
import { useWorldMapVersionDiff } from '@/hooks/useWorldMapVersionDiff';
import type {
  TopologyNodeDto,
  WorldMapAnnotationDto,
  WorldMapAnnotationInput,
  WorldMapMarkerDto,
  WorldMapMetaDto,
  WorldMapViewDto,
} from '@/types/dto';
import type { TopologyDisplaySettings, WorldMapObliqueDirection } from '@/types/topologyDisplay';
import { filterNodesWithDetailPage } from '@/utils/topologyDevices';
import { collectIconIdsFromMarkers } from '@/utils/iconPrefetch';
import { trackVisibleIcons } from '@/utils/visibleIconRegistry';
import {
  consolidateMultiblockMarkers,
  filterMarkersByCategoryVisibility,
  filterMarkersByDim,
  uniqueNodesFromMarkers,
} from '@/utils/worldMapMarkers';
import {
  boundsFromDimension,
  originFromBounds,
  screenToWorld,
  type WorldBounds,
  type WorldMapOrigin,
} from '@/utils/worldMapProjection';
import { boundsFromChunkScope, type ChunkScope } from '@/utils/worldMapTerrain';
import { resolveWorldMapTileViewId } from '@/utils/worldMapViews';

function worldMapViewI18nKey(labelKey: string, viewId: string): string {
  const suffix = labelKey.replace(/^adm\.webae\.worldmap\.view\./, '');
  if (suffix !== labelKey) {
    return `worldMapView_${suffix}`;
  }
  return `worldMapView_${viewId}`;
}

interface ClusterPopupState {
  anchorX: number;
  anchorY: number;
  nodes: TopologyNodeDto[];
}

interface AnnotationEditorState {
  annotation: WorldMapAnnotationDto | null;
  position: WorldMapAnnotationPosition;
}

interface AnnotationContextMenuState {
  left: number;
  top: number;
  position: WorldMapAnnotationPosition;
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
  onDisplaySettingsChange?: (patch: Partial<TopologyDisplaySettings>) => void;
  height?: number;
  layoutEpoch?: string;
  /** Increment to start tile progress polling (refresh / snapshot). */
  progressEpoch?: number;
  /** Browsing mode keeps history, diff, and annotations visible but disables annotation mutations. */
  readOnly?: boolean;
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
      onDisplaySettingsChange,
      height = 520,
      layoutEpoch = '',
      progressEpoch = 0,
      readOnly = false,
    },
    ref
  ) {
    const { t } = useI18n();
    const [messageApi, messageContextHolder] = message.useMessage();
    const [activeDim, setActiveDim] = useState<number>(() => meta.dimensions[0]?.dim ?? 0);
    const defaultView = meta.views?.[0]?.id ?? 'flat';
    const [activeView, setActiveView] = useState<string>(defaultView);
    const [containerSize, setContainerSize] = useState({ w: 0, h: 0 });
    const [popup, setPopup] = useState<ClusterPopupState | null>(null);
    const [annotationEditor, setAnnotationEditor] = useState<AnnotationEditorState | null>(null);
    const [annotationContextMenu, setAnnotationContextMenu] =
      useState<AnnotationContextMenuState | null>(null);
    const [annotationSaving, setAnnotationSaving] = useState(false);
    const previousScaleRef = useRef<number | null>(null);
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

    const {
      containerRef,
      viewport,
      setViewport,
      onPointerDown,
      onPointerMove,
      onPointerUp,
      fitBounds,
    } = useMapViewport();

    const versionHistory = useWorldMapVersionDiff({
      networkId,
      filter: { dimension: activeDim },
    });

    useEffect(() => {
      versionHistory.setFilter({ dimension: activeDim });
    }, [activeDim, versionHistory.setFilter]);

    const wheelHandler = useCallback(
      (e: WheelEvent) => {
        const delta = e.deltaY > 0 ? 0.9 : 1.1;
        setViewport((v) => ({
          ...v,
          scale: Math.min(6, Math.max(0.15, v.scale * delta)),
        }));
      },
      [setViewport]
    );
    useNonPassiveWheelZoom(containerRef, wheelHandler);

    const worldMapQuality = displaySettings.worldMapQuality ?? 'medium';
    const aeOverlayQuality =
      (meta.aeOverlayQualityTier as 'low' | 'medium' | 'high' | 'ultra' | undefined) ?? 'ultra';

    const pxPerBlock = useMemo(() => {
      const tier = meta.qualityTiers?.find((qt) => qt.id === worldMapQuality);
      return tier?.pxPerBlock ?? meta.pxPerBlock ?? 8;
    }, [meta.qualityTiers, meta.pxPerBlock, worldMapQuality]);

    const dimInfo = useMemo(
      () => meta.dimensions.find((d) => d.dim === activeDim) ?? meta.dimensions[0],
      [meta.dimensions, activeDim]
    );

    const legendMarkers = useMemo(() => {
      const byDim = filterMarkersByDim(markers, activeDim);
      return consolidateMultiblockMarkers(byDim);
    }, [markers, activeDim]);

    const visibleDimMarkers = useMemo(
      () =>
        filterMarkersByCategoryVisibility(
          legendMarkers,
          displaySettings.worldMapAeCategoryVisibility
        ),
      [legendMarkers, displaySettings.worldMapAeCategoryVisibility]
    );

    useEffect(() => {
      if (visibleDimMarkers.length === 0) return;
      const ids = collectIconIdsFromMarkers(visibleDimMarkers);
      return trackVisibleIcons(ids);
    }, [visibleDimMarkers]);

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

    const annotationPositionAtScreen = useCallback(
      (screenX: number, screenY: number): WorldMapAnnotationPosition => {
        const point = screenToWorld(screenX, screenY, viewport, origin);
        return {
          dimension: activeDim,
          x: Math.round(point.x),
          z: Math.round(point.z),
        };
      },
      [activeDim, origin, viewport],
    );

    const openAnnotationAtCenter = useCallback(() => {
      if (readOnly) return;
      const position = annotationPositionAtScreen(containerSize.w / 2, containerSize.h / 2);
      setAnnotationContextMenu(null);
      setAnnotationEditor({ annotation: null, position });
    }, [annotationPositionAtScreen, containerSize.h, containerSize.w, readOnly]);

    const handleViewportContextMenu = useCallback(
      (event: React.MouseEvent<HTMLDivElement>) => {
        if (readOnly) return;
        const target = event.target as HTMLElement;
        if (
          target.closest('.worldmap-cluster-popup') ||
          target.closest('.worldmap-context-menu') ||
          target.closest('.worldmap-diff-hit') ||
          target.closest('.worldmap-annotation-hit')
        ) return;
        event.preventDefault();
        const rect = event.currentTarget.getBoundingClientRect();
        const localX = event.clientX - rect.left;
        const localY = event.clientY - rect.top;
        setPopup(null);
        setAnnotationContextMenu({
          left: Math.max(8, Math.min(localX, Math.max(8, rect.width - 220))),
          top: Math.max(8, Math.min(localY, Math.max(8, rect.height - 72))),
          position: annotationPositionAtScreen(localX, localY),
        });
      },
      [annotationPositionAtScreen, readOnly],
    );

    const handleMarkerContextMenu = useCallback(
      (marker: WorldMapMarkerDto, clientX: number, clientY: number) => {
        if (readOnly) return;
        const rect = containerRef.current?.getBoundingClientRect();
        if (!rect) return;
        const localX = clientX - rect.left;
        const localY = clientY - rect.top;
        setPopup(null);
        setAnnotationContextMenu({
          left: Math.max(8, Math.min(localX, Math.max(8, rect.width - 220))),
          top: Math.max(8, Math.min(localY, Math.max(8, rect.height - 72))),
          position: {
            dimension: marker.dim,
            x: marker.x,
            y: marker.y,
            z: marker.z,
          },
        });
      },
      [containerRef, readOnly],
    );

    const handleEditAnnotation = useCallback(
      (annotation: WorldMapAnnotationDto) => {
        if (readOnly) return;
        setAnnotationContextMenu(null);
        setAnnotationEditor({
          annotation,
          position: {
            dimension: annotation.dimension,
            x: annotation.x,
            y: annotation.y,
            z: annotation.z,
          },
        });
      },
      [readOnly],
    );

    const handleSaveAnnotation = useCallback(
      async (input: WorldMapAnnotationInput) => {
        if (readOnly || !annotationEditor) return;
        setAnnotationSaving(true);
        try {
          if (annotationEditor.annotation) {
            await versionHistory.updateAnnotation(annotationEditor.annotation.id, input);
          } else {
            await versionHistory.createAnnotation(input);
          }
          setAnnotationEditor(null);
          messageApi.success(t('saved'));
        } catch (error) {
          if (error instanceof Error && error.name === 'AbortError') return;
          const detail = error instanceof Error && error.message
            ? error.message
            : t('worldMapAnnotationSaveFailed');
          messageApi.error(detail);
        } finally {
          setAnnotationSaving(false);
        }
      },
      [annotationEditor, messageApi, readOnly, t, versionHistory.createAnnotation, versionHistory.updateAnnotation],
    );

    const handleDeleteAnnotation = useCallback(
      async (annotation: WorldMapAnnotationDto) => {
        if (readOnly) return;
        try {
          await versionHistory.deleteAnnotation(annotation.id);
          messageApi.success(t('worldMapAnnotationDelete'));
        } catch (error) {
          if (error instanceof Error && error.name === 'AbortError') return;
          const detail = error instanceof Error && error.message
            ? error.message
            : t('worldMapAnnotationDeleteFailed');
          messageApi.error(detail);
        }
      },
      [messageApi, readOnly, t, versionHistory.deleteAnnotation],
    );

    useEffect(() => {
      setPopup(null);
      previousScaleRef.current = null;
      setAnnotationContextMenu(null);
    }, [activeDim]);

    useEffect(() => {
      setPopup(null);
      previousScaleRef.current = null;
      setAnnotationContextMenu(null);
      setAnnotationEditor(null);
    }, [networkId]);

    useEffect(() => {
      if (!readOnly) return;
      setAnnotationContextMenu(null);
      setAnnotationEditor(null);
    }, [readOnly]);

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
      zoom: 0,
      active: terrainEnabled,
      snapshotVersion: meta.snapshotVersion ?? 0,
      previousSnapshotVersion: meta.previousSnapshotVersion ?? 0,
      browserCacheEnabled: meta.snapshotMode === 'client_only',
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
      quality: aeOverlayQuality,
      zoom: 0,
      active: aeVisible,
      prefetch: aeVisible,
      snapshotVersion: meta.snapshotVersion ?? 0,
      previousSnapshotVersion: meta.previousSnapshotVersion ?? 0,
      browserCacheEnabled: meta.snapshotMode === 'client_only',
    });

    const overlayTiles = terrainLoader.debouncedTiles;

    const { progress, polling, startPolling, stopPolling } = useWorldMapProgress({
      networkId,
      view: tileView,
      dim: activeDim,
      quality: worldMapQuality,
      enabled: meta.snapshotMode !== 'client_only',
    });

    useEffect(() => {
      if (meta.snapshotMode === 'client_only') {
        stopPolling();
        return;
      }
      startPolling();
      return () => stopPolling();
    }, [networkId, tileView, activeDim, worldMapQuality, aeOverlayQuality, progressEpoch, startPolling, stopPolling, meta.snapshotMode]);

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

    const closePopup = useCallback(() => {
      setPopup(null);
      if (previousScaleRef.current != null) {
        const restore = previousScaleRef.current;
        previousScaleRef.current = null;
        setViewport((v) => ({ ...v, scale: restore }));
      }
    }, [setViewport]);

    const openPopupForMarkers = useCallback(
      (markerList: WorldMapMarkerDto[], clientX: number, clientY: number, zoomOnOpen: boolean) => {
        const nodes = filterNodesWithDetailPage(uniqueNodesFromMarkers(markerList, nodeIndex));
        if (nodes.length === 0) return;
        if (zoomOnOpen) {
          previousScaleRef.current = viewport.scale;
          setViewport((v) => ({
            ...v,
            scale: Math.min(6, v.scale * 1.5),
          }));
        }
        setPopup({ anchorX: clientX, anchorY: clientY, nodes });
      },
      [nodeIndex, setViewport, viewport.scale]
    );

    const handleMarkerClick = useCallback(
      (marker: WorldMapMarkerDto, clientX: number, clientY: number) => {
        openPopupForMarkers([marker], clientX, clientY, false);
      },
      [openPopupForMarkers]
    );

    const handleClusterClick = useCallback(
      (clusterId: number, clusterMarkers: WorldMapMarkerDto[], clientX: number, clientY: number) => {
        void clusterId;
        openPopupForMarkers(clusterMarkers, clientX, clientY, true);
      },
      [openPopupForMarkers]
    );

    const handlePopupSelectNode = useCallback(
      (node: TopologyNodeDto) => {
        onNodeSelect(node);
        setPopup(null);
        if (previousScaleRef.current != null) {
          const restore = previousScaleRef.current;
          previousScaleRef.current = null;
          setViewport((v) => ({ ...v, scale: restore }));
        }
        if (node.type === 'drive') onDriveClick?.(node);
      },
      [onNodeSelect, onDriveClick, setViewport]
    );

    const dimOptions = meta.dimensions.map((d) => ({
      value: String(d.dim),
      label: d.name || t('worldMapDimTab', String(d.dim)),
    }));

    return (
      <div className="topology-worldmap-root">
        {messageContextHolder}
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
          <Popover
            placement="bottomRight"
            trigger="click"
            overlayClassName="worldmap-version-popover"
            content={
              <WorldMapVersionControls
                history={versionHistory}
                readOnly={readOnly}
                onAddAnnotation={openAnnotationAtCenter}
              />
            }
          >
            <Button
              size="small"
              icon={<HistoryOutlined />}
              aria-label={t('worldMapVersionHistory')}
            >
              {t('worldMapVersionHistory')}
            </Button>
          </Popover>
          <span style={{ fontSize: 12, color: '#888', marginLeft: 8 }}>
            {t('worldMapTerrainSource_self') || '内置渲染'}
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

        <div
          ref={containerRef}
          className="topology-worldmap-viewport"
          style={{ height, touchAction: 'none', overscrollBehavior: 'contain' }}
          onPointerDown={(event) => {
            if (event.button === 0) setAnnotationContextMenu(null);
            onPointerDown(event);
          }}
          onPointerMove={onPointerMove}
          onPointerUp={onPointerUp}
          onContextMenu={handleViewportContextMenu}
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
            tileCoords={terrainLoader.debouncedTiles}
            tiles={aeLoader.tiles}
            chunkStyle={terrainLoader.chunkStyle}
            visible={aeVisible}
            opacity={displaySettings.worldMapAeOverlayOpacity}
            categoryColors={displaySettings.worldMapAeCategoryColors}
            itemColorOverrides={displaySettings.worldMapAeItemColorOverrides}
          />
          <WorldMapChunkStatusOverlay
            tileCoords={overlayTiles}
            terrainTiles={terrainLoader.tiles}
            aeTiles={aeLoader.tiles}
            serverProgress={progress?.chunks ?? null}
            chunkStyle={terrainLoader.chunkStyle}
            showTerrain={terrainEnabled}
            showAe={aeVisible}
          />
          <WorldMapDiffOverlay
            diff={versionHistory.diffState.filteredData}
            visible={versionHistory.diffEnabled}
            viewport={viewport}
            origin={origin}
            containerWidth={containerSize.w}
            containerHeight={containerSize.h}
          />
          {showProgressUi && (
            <div className="worldmap-chunk-status-legend" title={t('worldMapChunkBadgeHint')}>
              {t('worldMapChunkBadgeHint')}
            </div>
          )}
          {onDisplaySettingsChange && (
            <WorldMapLegendRail
              markers={legendMarkers}
              displaySettings={displaySettings}
              onChange={onDisplaySettingsChange}
            />
          )}
          {displaySettings.showWorldMapDeviceIcons && (
            <WorldMapMarkerLayer
              markers={visibleDimMarkers}
              viewport={viewport}
              origin={origin}
              containerWidth={containerSize.w}
              containerHeight={containerSize.h}
              selectedNodeId={selectedNodeId}
              displaySettings={displaySettings}
              onMarkerClick={handleMarkerClick}
              onMarkerContextMenu={handleMarkerContextMenu}
              onClusterClick={handleClusterClick}
            />
          )}
          <WorldMapAnnotationLayer
            annotations={versionHistory.annotationState.visible}
            dimension={activeDim}
            viewport={viewport}
            origin={origin}
            readOnly={readOnly}
            onEdit={handleEditAnnotation}
            onDelete={handleDeleteAnnotation}
          />
          {annotationContextMenu && !readOnly && (
            <div
              className="worldmap-context-menu"
              style={{ left: annotationContextMenu.left, top: annotationContextMenu.top }}
              role="menu"
              onPointerDown={(event) => event.stopPropagation()}
              onContextMenu={(event) => event.preventDefault()}
            >
              <Button
                type="text"
                size="small"
                block
                role="menuitem"
                icon={<PlusOutlined />}
                onClick={() => {
                  setAnnotationEditor({
                    annotation: null,
                    position: annotationContextMenu.position,
                  });
                  setAnnotationContextMenu(null);
                }}
              >
                {t('worldMapContextAddAnnotation')}
              </Button>
              <span className="worldmap-context-menu-coordinates">
                {`${annotationContextMenu.position.x}, ${annotationContextMenu.position.y ?? '?'}, ${annotationContextMenu.position.z}`}
              </span>
            </div>
          )}
          {popup && (
            <WorldMapClusterPopup
              open
              anchorX={popup.anchorX}
              anchorY={popup.anchorY}
              nodes={popup.nodes}
              selectedNodeId={selectedNodeId}
              viewportRef={containerRef}
              onClose={closePopup}
              onSelectNode={handlePopupSelectNode}
            />
          )}
        </div>
        <WorldMapAnnotationModal
          open={annotationEditor != null}
          networkId={networkId}
          annotation={annotationEditor?.annotation ?? null}
          initialPosition={annotationEditor?.position ?? null}
          saving={annotationSaving}
          onCancel={() => setAnnotationEditor(null)}
          onSave={handleSaveAnnotation}
        />
      </div>
    );
  }
);
