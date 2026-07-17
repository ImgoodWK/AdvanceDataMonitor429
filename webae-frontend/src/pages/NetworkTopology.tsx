import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import {

  Alert,

  Button,

  Card,

  Descriptions,

  Empty,

  Modal,

  Segmented,

  Space,

  Spin,

  Table,

  Tag,

  Typography,

  Tooltip,

} from 'antd';

import {

  CameraOutlined,

  ReloadOutlined,

  ApartmentOutlined,

  GlobalOutlined,

  SettingOutlined,

  ZoomInOutlined,

  ZoomOutOutlined,

  CompressOutlined,

  UnorderedListOutlined,

  CloudUploadOutlined,

} from '@ant-design/icons';

import { getApiClient, ApiClientError } from '@/api/client';

import { useAppContext } from '@/context/AppContext';

import { useI18n } from '@/i18n';

import { usePlayerLocations } from '@/hooks/usePlayerLocations';

import { useWorldMapData } from '@/hooks/useWorldMapData';

import { useTopologyDisplay } from '@/hooks/useTopologyDisplay';

import { PageShell } from '@/components/Layout/PageShell';

import { ExportCsvButton } from '@/components/ExportCsvButton';

import { TopologyCytoscapeGraph } from '@/components/topology/TopologyCytoscapeGraph';
import type { TopologyGraphHandle } from '@/components/topology/topologyGraphHandle';

import { TopologySimulatedView } from '@/components/topology/TopologySimulatedView';

import { TopologyWorldMapView } from '@/components/topology/TopologyWorldMapView';

import { TopologyDeviceList } from '@/components/topology/TopologyDeviceList';

import { TopologySettingsDrawer } from '@/components/topology/TopologySettingsDrawer';
import { TopologyNodeDetailDrawer } from '@/components/topology/TopologyNodeDetailDrawer';

import { DriveSimulatedGui } from '@/components/topology/DriveSimulatedGui';

import { P2pMapPanel } from '@/components/topology/P2pMapPanel';

import { formatTime } from '@/utils/format';

import { buildDynmapUrl } from '@/utils/dynmap';

import { buildNodeIndex } from '@/utils/worldMapMarkers';
import { clampWorldMapQuality } from '@/utils/worldMapTerrain';
import type { WorldMapQualityTierId } from '@/types/topologyDisplay';

import type {

  TopologyNodeDto,

  TopologyResponse,

  TopologySnapshotDto,

  NetworkCellSummaryDto,

  NetworkCellSummaryResponse,

} from '@/types/dto';



const { Text } = Typography;



type ViewMode = 'logical' | 'spatial' | 'p2p' | 'worldMap';



function topologyApiMode(viewMode: ViewMode): 'logical' | 'spatial' {

  return viewMode === 'spatial' ? 'spatial' : 'logical';

}



function formatCooldown(ms: number): string {

  const sec = Math.ceil(ms / 1000);

  if (sec >= 60) return `${Math.floor(sec / 60)}m ${sec % 60}s`;

  return `${sec}s`;

}



export function NetworkTopologyPage() {

  const { selectedNetworks, serverConfig, notify } = useAppContext();

  const { t } = useI18n();

  const [displaySettings, setDisplaySettings, resetDisplaySettings] = useTopologyDisplay();

  const graphRef = useRef<TopologyGraphHandle>(null);

  const [activeNetwork, setActiveNetwork] = useState<number | null>(null);

  const [viewMode, setViewMode] = useState<ViewMode>('logical');

  const [loading, setLoading] = useState(false);

  const [capturing, setCapturing] = useState(false);

  const [error, setError] = useState<string | null>(null);

  const [cached, setCached] = useState(false);

  const [snapshot, setSnapshot] = useState<TopologySnapshotDto | null>(null);

  const [snapshotTs, setSnapshotTs] = useState<number | null>(null);

  const [cooldownRemainingMs, setCooldownRemainingMs] = useState(0);

  const [cooldownMs, setCooldownMs] = useState(10000);

  const [selectedNode, setSelectedNode] = useState<TopologyNodeDto | null>(null);

  const [hoveredNodeId, setHoveredNodeId] = useState<string | null>(null);

  const [cellSummary, setCellSummary] = useState<NetworkCellSummaryDto | null>(null);

  const [settingsOpen, setSettingsOpen] = useState(false);

  const [driveModalNode, setDriveModalNode] = useState<TopologyNodeDto | null>(null);

  const [canForceSnapshot, setCanForceSnapshot] = useState(false);

  const [worldMapProgressEpoch, setWorldMapProgressEpoch] = useState(0);

  const [deviceListOpen, setDeviceListOpen] = useState(false);

  const [requestingWorldMapSnapshot, setRequestingWorldMapSnapshot] = useState(false);



  const currentNet = activeNetwork ?? selectedNetworks[0] ?? 0;

  const topologyEnabled = serverConfig?.topologyEnabled !== false;

  const topologySimulatedEnabled = serverConfig?.topologySimulatedEnabled === true;

  const worldMapEnabled = serverConfig?.worldMapEnabled !== false && topologyEnabled;

  const dynmapBaseUrl = (serverConfig?.dynmapBaseUrl ?? '').trim();

  const { locations: playerLocations, loading: locationsLoading } = usePlayerLocations(10000);

  const {

    meta: worldMapMeta,

    markers: worldMapMarkers,

    loading: worldMapLoading,

    error: worldMapError,

    reload: reloadWorldMap,

    snapshotStatus: worldMapSnapshotStatus,

    requestSnapshotUpdate,

  } = useWorldMapData(currentNet, viewMode === 'worldMap' && worldMapEnabled, displaySettings.worldMapQuality);

  const bumpWorldMapTileEpoch = useCallback(() => {
    setWorldMapProgressEpoch((n) => n + 1);
  }, []);

  const reloadWorldMapView = useCallback(async () => {
    if (viewMode !== 'worldMap' || !worldMapEnabled) return;
    bumpWorldMapTileEpoch();
    await reloadWorldMap();
  }, [viewMode, worldMapEnabled, bumpWorldMapTileEpoch, reloadWorldMap]);

  const requestWorldMapSnapshot = useCallback(async () => {
    if (viewMode !== 'worldMap' || !worldMapEnabled) return;
    setRequestingWorldMapSnapshot(true);
    const result = await requestSnapshotUpdate();
    setRequestingWorldMapSnapshot(false);
    if (result.ok) {
      notify(t('worldMapRequestSnapshotSent'), 'success');
    } else if (result.error) {
      notify(result.error, 'warning');
    }
  }, [viewMode, worldMapEnabled, requestSnapshotUpdate, notify, t]);

  const worldMapSnapshotBusy =
    requestingWorldMapSnapshot ||
    worldMapSnapshotStatus?.captureState === 'awaiting_consent' ||
    worldMapSnapshotStatus?.captureState === 'capturing';

  const worldMapMaxQuality: WorldMapQualityTierId =
    serverConfig?.worldMapMaxQualityTier === 'low' ||
    serverConfig?.worldMapMaxQualityTier === 'medium' ||
    serverConfig?.worldMapMaxQualityTier === 'high' ||
    serverConfig?.worldMapMaxQualityTier === 'ultra'
      ? serverConfig.worldMapMaxQualityTier
      : worldMapMeta?.maxQualityTier === 'low' ||
          worldMapMeta?.maxQualityTier === 'medium' ||
          worldMapMeta?.maxQualityTier === 'high' ||
          worldMapMeta?.maxQualityTier === 'ultra'
        ? worldMapMeta.maxQualityTier
        : 'ultra';

  const showSimulated =

    topologySimulatedEnabled && viewMode === 'logical' && displaySettings.renderMode === 'simulated';

  useEffect(() => {
    if (!topologySimulatedEnabled && displaySettings.renderMode === 'simulated') {
      setDisplaySettings({ renderMode: 'abstract' });
    }
  }, [topologySimulatedEnabled, displaySettings.renderMode, setDisplaySettings]);

  const applyResponse = useCallback((data: TopologyResponse, fromCache: boolean) => {

    if (data.cooldownRemainingMs != null) setCooldownRemainingMs(data.cooldownRemainingMs);

    if (data.cooldownMs != null) setCooldownMs(data.cooldownMs);

    if (data.canForceSnapshot != null) setCanForceSnapshot(data.canForceSnapshot);

    if (data.success && data.data && data.hasSnapshot !== false) {

      setSnapshot(data.data);

      setSnapshotTs(data.timestamp ?? data.data.timestamp ?? null);

      setCached(fromCache || !!data.cached);

      setSelectedNode(null);

      setDriveModalNode(null);

      setError(null);

      return true;

    }

    if (data.hasSnapshot === false) {

      setSnapshot(null);

      setSnapshotTs(null);

      setCached(false);

    }

    return false;

  }, []);



  const loadCached = useCallback(async () => {

    if (!topologyEnabled || selectedNetworks.length === 0 || viewMode === 'p2p') return;

    setLoading(true);

    setError(null);

    try {

      const apiMode = topologyApiMode(viewMode);

      const data = await getApiClient().get<TopologyResponse>(

        `/api/network/topology?network=${currentNet}&mode=${apiMode}`

      );

      applyResponse(data, true);

      if (viewMode === 'worldMap') {

        await reloadWorldMapView();

      }

    } catch (e) {

      const msg = (e as Error).message || t('topologyLoadFailed');

      setError(msg);

    } finally {

      setLoading(false);

    }

  }, [currentNet, viewMode, topologyEnabled, selectedNetworks.length, t, applyResponse, reloadWorldMapView]);



  const captureSnapshot = useCallback(async (force = false) => {

    if (!topologyEnabled || selectedNetworks.length === 0 || viewMode === 'p2p') return;

    if (!force && cooldownRemainingMs > 0) {

      notify(t('topologyCooldownWait'), 'warning');

      return;

    }

    setCapturing(true);

    setError(null);

    try {

      const forceParam = force ? '&force=1' : '';

      const apiMode = topologyApiMode(viewMode);

      const data = await getApiClient().post<TopologyResponse>(

        `/api/network/topology/snapshot?network=${currentNet}&mode=${apiMode}${forceParam}`

      );

      if (data.canForceSnapshot != null) setCanForceSnapshot(data.canForceSnapshot);

      if (applyResponse(data, false)) {

        notify(force ? t('topologyForceSnapshot') : t('topologyCaptureSnapshot'), 'success');

        if (viewMode === 'worldMap') {

          await reloadWorldMapView();

        }

      }

    } catch (e) {

      if (e instanceof ApiClientError && e.code === 'cooldown') {

        setError(t('topologyCooldownWait'));

        notify(t('topologyCooldownWait'), 'warning');

        void loadCached();

      } else if (e instanceof ApiClientError && e.code === 'forbidden') {

        const msg = (e as Error).message || t('topologyForceSnapshotHint');

        setError(msg);

        notify(msg, 'error');

      } else {

        const msg = (e as Error).message || t('topologyLoadFailed');

        setError(msg);

        notify(msg, 'error');

      }

    } finally {

      setCapturing(false);

    }

  }, [

    currentNet,

    viewMode,

    topologyEnabled,

    selectedNetworks.length,

    cooldownRemainingMs,

    t,

    notify,

    applyResponse,

    loadCached,

    reloadWorldMapView,

  ]);



  const loadCellSummary = useCallback(async () => {

    if (selectedNetworks.length === 0) return;

    try {

      const data = await getApiClient().get<NetworkCellSummaryResponse>(

        `/api/network/cells?network=${currentNet}`

      );

      if (data.success && data.data) setCellSummary(data.data);

    } catch {

      setCellSummary(null);

    }

  }, [currentNet, selectedNetworks.length]);



  useEffect(() => {

    void loadCached();

  }, [loadCached]);



  useEffect(() => {

    if (cooldownRemainingMs <= 0) return;

    const id = window.setInterval(() => {

      setCooldownRemainingMs((prev) => Math.max(0, prev - 1000));

    }, 1000);

    return () => window.clearInterval(id);

  }, [cooldownRemainingMs]);



  useEffect(() => {

    if (snapshot) void loadCellSummary();

  }, [snapshot, loadCellSummary]);



  useEffect(() => {

    const onKey = (e: KeyboardEvent) => {

      if (viewMode === 'p2p' || (!snapshot && viewMode !== 'worldMap')) return;

      if (e.key === '+' || e.key === '=') {

        graphRef.current?.zoomIn();

      } else if (e.key === '-') {

        graphRef.current?.zoomOut();

      }

    };

    window.addEventListener('keydown', onKey);

    return () => window.removeEventListener('keydown', onKey);

  }, [viewMode, snapshot]);



  const nodeIndex = useMemo(

    () => (snapshot ? buildNodeIndex(snapshot.nodes) : new Map()),

    [snapshot]

  );



  const driveCellChildren =
    driveModalNode && snapshot
      ? snapshot.edges
          .filter((e) => e.from === driveModalNode.id)
          .map((e) => snapshot.nodes.find((n) => n.id === e.to))
          .filter((n): n is TopologyNodeDto => !!n && n.type === 'cell')
      : [];



  if (selectedNetworks.length === 0) {

    return (

      <PageShell title={t('topology')} description={t('topologyDesc')}>

        <Card>

          <Empty description={t('selectNetworkFirst')} />

        </Card>

      </PageShell>

    );

  }



  if (!topologyEnabled) {

    return (

      <PageShell title={t('topology')} description={t('topologyDesc')}>

        <Alert type="warning" showIcon message={t('topologyDisabled')} />

      </PageShell>

    );

  }



  const meta = snapshot?.meta;

  const layoutEpoch = snapshot
    ? `${snapshotTs ?? 0}:${viewMode}:${displaySettings.renderMode}:${displaySettings.abstractLayout}:${snapshot.nodes.length}:${snapshot.edges.length}`
    : worldMapMeta?.timestamp != null
      ? `wm:${worldMapMeta.timestamp}:${worldMapMarkers.length}`
      : '';

  const simCh = meta?.channelsSimulated;

  const realCh = meta?.channelsReal;

  const captureDisabled = capturing || cooldownRemainingMs > 0;



  const openDynmapForPlayer = (x: number, y: number, z: number, dim: number) => {

    const url = buildDynmapUrl(dynmapBaseUrl, x, y, z, dim);

    if (url) window.open(url, '_blank', 'noopener,noreferrer');

  };



  const openDynmapDefault = () => {

    if (!dynmapBaseUrl) return;

    const first = playerLocations[0];

    if (first) {

      openDynmapForPlayer(first.x, first.y, first.z, first.dim);

    } else {

      window.open(dynmapBaseUrl.replace(/\/+$/, ''), '_blank', 'noopener,noreferrer');

    }

  };



  return (

    <PageShell

      title={t('topology')}

      description={t('topologyDesc')}

      actions={

        <Space wrap>

          {snapshotTs != null && (

            <Text type="secondary">

              {t('topologySnapshotAt')}: {formatTime(snapshotTs)}

            </Text>

          )}

          {cooldownRemainingMs > 0 && (

            <Tag color="orange">

              {t('topologyCooldown')}: {formatCooldown(cooldownRemainingMs)}

            </Tag>

          )}

          {cached && snapshot && <Tag color="blue">{t('cached')}</Tag>}

          <Button

            type="primary"

            icon={<CameraOutlined />}

            onClick={() => void captureSnapshot(false)}

            loading={capturing}

            disabled={captureDisabled}

          >

            {t('topologyCaptureSnapshot')}

          </Button>

          {canForceSnapshot && (

            <Tooltip title={t('topologyForceSnapshotHint')}>

              <Button

                icon={<ReloadOutlined />}

                onClick={() => void captureSnapshot(true)}

                loading={capturing}

              >

                {t('topologyForceSnapshot')}

              </Button>

            </Tooltip>

          )}

          <Button icon={<ReloadOutlined />} onClick={() => void loadCached()} loading={loading} aria-label={t('refresh')}>

            {t('refresh')}

          </Button>

          <ExportCsvButton

            filename={`topology-net${currentNet}.csv`}

            headers={[t('topologyNodeType'), t('topologyNodeCount'), 'channelCost']}

            rows={(snapshot?.nodes || []).map((n) => [n.displayName || n.type, n.count, n.channelCost ?? ''])}

            disabled={!snapshot?.nodes?.length}

          />

          {dynmapBaseUrl ? (

            <Button icon={<GlobalOutlined />} onClick={openDynmapDefault}>

              {t('openInDynmap')}

            </Button>

          ) : null}

        </Space>

      }

    >

      {dynmapBaseUrl ? (

        <Card size="small" style={{ marginBottom: 8 }} title={t('playerLocationsTitle')}>

          <Table

            size="small"

            loading={locationsLoading}

            pagination={false}

            rowKey="uuid"

            dataSource={playerLocations}

            locale={{ emptyText: t('playerLocationsEmpty') }}

            columns={[

              { title: t('playerName'), dataIndex: 'name', key: 'name' },

              {

                title: t('playerLocationCoords'),

                key: 'coords',

                render: (_, row) => `${row.x}, ${row.y}, ${row.z}`,

              },

              { title: t('playerLocationDim'), dataIndex: 'dim', key: 'dim', width: 72 },

              {

                title: t('actions'),

                key: 'actions',

                width: 120,

                render: (_, row) => (

                  <Button

                    type="link"

                    size="small"

                    icon={<GlobalOutlined />}

                    onClick={() => openDynmapForPlayer(row.x, row.y, row.z, row.dim)}

                  >

                    {t('openInDynmap')}

                  </Button>

                ),

              },

            ]}

          />

        </Card>

      ) : null}



      <Card size="small" style={{ marginBottom: 8 }} className="topology-toolbar-card">

        <div className="topology-toolbar-row">

          <Segmented

            value={viewMode}

            onChange={(v) => setViewMode(v as ViewMode)}

            options={[

              { value: 'logical', label: t('topologyMode_logical') },

              { value: 'spatial', label: t('topologyMode_spatial') },

              { value: 'p2p', label: t('topologyMode_p2p') },

              ...(worldMapEnabled

                ? [{ value: 'worldMap' as const, label: t('topologyMode_worldMap') }]

                : []),

            ]}

          />

          {selectedNetworks.length > 1 ? (

            <Segmented

              value={String(currentNet)}

              onChange={(v) => setActiveNetwork(Number(v))}

              options={selectedNetworks.map((nid) => ({

                value: String(nid),

                label: `${t('networkId')} ${nid}`,

              }))}

            />

          ) : (

            <Text type="secondary">

              <ApartmentOutlined /> {t('networkId')} {currentNet}

            </Text>

          )}

        </div>

        <div

          className={`topology-toolbar-context${viewMode === 'p2p' ? ' topology-toolbar-context--placeholder' : ''}`}

          aria-hidden={viewMode === 'p2p'}

        >

          <Space wrap>

            {viewMode === 'logical' && topologySimulatedEnabled && (

              <Segmented

                value={displaySettings.renderMode}

                onChange={(v) => setDisplaySettings({ renderMode: v as 'abstract' | 'simulated' })}

                options={[

                  { value: 'abstract', label: t('topologyRenderMode_abstract') },

                  { value: 'simulated', label: t('topologyRenderMode_simulated') },

                ]}

              />

            )}

            <Button icon={<SettingOutlined />} onClick={() => setSettingsOpen(true)} aria-label={t('topologySettingsTitle')} />

            {viewMode === 'worldMap' && (

              <>

                <Tooltip title={t('worldMapRequestSnapshotHint')}>

                  <Button

                    icon={<CloudUploadOutlined />}

                    onClick={() => void requestWorldMapSnapshot()}

                    loading={requestingWorldMapSnapshot}

                    disabled={worldMapSnapshotBusy}

                    aria-label={t('worldMapRequestSnapshot')}

                  >

                    {t('worldMapRequestSnapshot')}

                  </Button>

                </Tooltip>

              </>

            )}

            <Button icon={<ZoomInOutlined />} onClick={() => graphRef.current?.zoomIn()} aria-label="Zoom in" />

            <Button icon={<ZoomOutOutlined />} onClick={() => graphRef.current?.zoomOut()} aria-label="Zoom out" />

            <Button icon={<CompressOutlined />} onClick={() => graphRef.current?.fitView()} aria-label={t('topologyFitView')} />

          </Space>

        </div>

      </Card>



      {showSimulated && (

        <Alert type="info" showIcon message={t('topologySimulatedHint')} style={{ marginBottom: 8 }} />

      )}



      {!snapshot && !loading && viewMode !== 'p2p' && viewMode !== 'worldMap' && (

        <Alert type="info" showIcon message={t('topologyNoSnapshot')} style={{ marginBottom: 8 }} />

      )}



      {viewMode === 'worldMap' && worldMapMeta?.boundsTooLarge && (

        <Alert type="warning" showIcon message={t('worldMapBoundsTooLarge')} style={{ marginBottom: 8 }} />

      )}

      {viewMode === 'worldMap' && worldMapSnapshotStatus?.captureState === 'awaiting_consent' && (

        <Alert
          type="info"
          showIcon
          message={t('worldMapSnapshotAwaitingConsent')}
          description={worldMapSnapshotStatus.message}
          style={{ marginBottom: 8 }}
        />

      )}

      {viewMode === 'worldMap' && worldMapSnapshotStatus?.captureState === 'capturing' && (

        <Alert
          type="info"
          showIcon
          message={t('worldMapSnapshotCapturing')
            .replace('{done}', String(worldMapSnapshotStatus.completedChunks ?? 0))
            .replace('{total}', String(worldMapSnapshotStatus.totalChunks ?? 0))}
          style={{ marginBottom: 8 }}
        />

      )}

      {viewMode === 'worldMap' &&
        worldMapMeta?.snapshotMode === 'client_only' &&
        (worldMapMeta.snapshotVersion ?? 0) === 0 &&
        worldMapMeta.hasLogicalSnapshot && (

        <Alert type="warning" showIcon message={t('worldMapSnapshotNone')} style={{ marginBottom: 8 }} />

      )}



      {(error || worldMapError) && (

        <Alert type="error" showIcon message={error || worldMapError} style={{ marginBottom: 8 }} closable />

      )}



      {cellSummary && (

        <Card size="small" title={t('cellSummaryTitle')} style={{ marginBottom: 8 }}>

          <Descriptions size="small" column={{ xs: 1, sm: 2, md: 3 }}>

            <Descriptions.Item label={t('bytesUsed')}>

              {cellSummary.itemUsedBytes} / {cellSummary.itemTotalBytes}

              {cellSummary.itemUsagePercent > 0 ? ` (${cellSummary.itemUsagePercent.toFixed(1)}%)` : ''}

            </Descriptions.Item>

            <Descriptions.Item label={t('fluidTypes')}>

              {cellSummary.fluidUsedBytes} / {cellSummary.fluidTotalBytes}

              {cellSummary.fluidUsagePercent > 0 ? ` (${cellSummary.fluidUsagePercent.toFixed(1)}%)` : ''}

            </Descriptions.Item>

            {cellSummary.hasInfiniteItemCells && (

              <Descriptions.Item label="∞">{t('cellSummaryInfiniteItems')}</Descriptions.Item>

            )}

            {cellSummary.hasInfiniteFluidCells && (

              <Descriptions.Item label="∞">{t('cellSummaryInfiniteFluids')}</Descriptions.Item>

            )}

          </Descriptions>

        </Card>

      )}



      {meta && viewMode === 'worldMap' && (

        <Card size="small" style={{ marginBottom: 8 }}>

          <Descriptions size="small" column={{ xs: 1, sm: 2, md: 3 }}>

            <Descriptions.Item label={t('topologyMetaNodes')}>{snapshot?.nodes.length ?? '—'}</Descriptions.Item>

            <Descriptions.Item label={t('topologyMetaDevices')}>

              {snapshot?.nodes.reduce((sum, n) => sum + (n.devices?.length ?? 0), 0) ?? '—'}

            </Descriptions.Item>

            {worldMapMeta?.snapshotVersion != null && worldMapMeta.snapshotVersion > 0 && (

              <Descriptions.Item label={t('worldMapSnapshotVersion')}>

                v{worldMapMeta.snapshotVersion}

              </Descriptions.Item>

            )}

          </Descriptions>

        </Card>

      )}



      {meta && viewMode !== 'worldMap' && (

        <Card size="small" style={{ marginBottom: 8 }}>

          <Descriptions size="small" column={{ xs: 1, sm: 2, md: 4 }}>

            <Descriptions.Item label={t('topologyLayout')}>

              {viewMode === 'logical'

                ? displaySettings.abstractLayout === 'star'

                  ? t('topologyLayout_star')

                  : t('topologyLayout_channelLanes')

                : meta.layout === 'tree'

                  ? t('topologyLayout_channelLanes')

                  : meta.layout === 'star'

                    ? t('topologyLayout_star')

                    : meta.layout}

            </Descriptions.Item>

            {meta.channelModel && (

              <Descriptions.Item label={t('topologyChannelModel')}>

                {meta.channelModel}

              </Descriptions.Item>

            )}

            <Descriptions.Item label={t('topologySimChannels')}>

              {simCh?.available ? `${simCh.used} / ${simCh.max}` : '—'}

              <div>

                <Text type="secondary" style={{ fontSize: 12 }}>

                  {t('topologySimChannelsHint')}

                </Text>

              </div>

            </Descriptions.Item>

            {viewMode === 'logical' && meta.lanes && meta.lanes.length > 0 && (

              <Descriptions.Item label={t('topologyLanes')}>

                <Space size={4} wrap>

                  {meta.lanes.map((lane) => (

                    <Tag key={lane.index} color={lane.overflow ? 'error' : undefined}>

                      L{lane.index}: {lane.used}/{lane.max}

                      {lane.overflow ? ` ${t('topologyLaneOverflow')}` : ''}

                    </Tag>

                  ))}

                </Space>

              </Descriptions.Item>

            )}

            {viewMode === 'logical' && meta.orbitCounts && Object.keys(meta.orbitCounts).length > 0 && (

              <Descriptions.Item label={t('topologyOrbit')}>

                {Object.entries(meta.orbitCounts)

                  .map(([k, v]) => `${k}:${v}`)

                  .join(' · ')}

              </Descriptions.Item>

            )}

            <Descriptions.Item label={t('topologyRealChannels')}>

              {realCh?.available ? `${realCh.used} / ${realCh.max}` : t('topologyRealUnavailable')}

            </Descriptions.Item>

            {viewMode === 'spatial' && meta.spatialBinSize != null && (

              <Descriptions.Item label={t('topologyBinSize')}>

                {meta.spatialBinSize}×{meta.spatialBinSize}

              </Descriptions.Item>

            )}

            {cooldownMs > 0 && (

              <Descriptions.Item label={t('topologyCooldown')}>

                {formatCooldown(cooldownMs)}

              </Descriptions.Item>

            )}

          </Descriptions>

        </Card>

      )}



      {viewMode === 'p2p' ? (

        <P2pMapPanel networkId={currentNet} />

      ) : (

        <div className="topology-page-main">

          <Card

            className="topology-graph-card topology-page-graph"

            styles={{ body: { padding: 0, position: 'relative', minHeight: 520 } }}

          >

            {((snapshot && snapshot.nodes.length > 0) ||

              (viewMode === 'worldMap' && snapshot && worldMapMeta?.hasLogicalSnapshot)) && (

              <Button

                type="default"

                size="small"

                className="topology-device-list-fab"

                icon={<UnorderedListOutlined />}

                onClick={() => setDeviceListOpen(true)}

                aria-label={t('topologyDeviceListOpen')}

              >

                {t('topologyDeviceListTitle')}

              </Button>

            )}

            {loading && !snapshot && viewMode !== 'worldMap' ? (

              <div style={{ textAlign: 'center', padding: 80 }}>

                <Spin aria-label={t('loading')} />

              </div>

            ) : viewMode === 'worldMap' ? (

              worldMapLoading && !worldMapMeta ? (

                <div style={{ textAlign: 'center', padding: 80 }}>

                  <Spin aria-label={t('loading')} />

                </div>

              ) : worldMapMeta?.hasLogicalSnapshot === false || !snapshot ? (

                <Empty description={t('worldMapNoSnapshot')} style={{ padding: 64 }}>

                  <Button type="primary" icon={<CameraOutlined />} onClick={() => void captureSnapshot(false)} loading={capturing}>

                    {t('topologyCaptureSnapshot')}

                  </Button>

                </Empty>

              ) : worldMapMeta?.hasLogicalSnapshot && worldMapMeta.dimensions?.length > 0 ? (

                <TopologyWorldMapView

                  ref={graphRef}

                  meta={worldMapMeta}

                  markers={worldMapMarkers}

                  networkId={currentNet}

                  nodeIndex={nodeIndex}

                  selectedNodeId={selectedNode?.id ?? null}

                  onNodeSelect={setSelectedNode}

                  layoutEpoch={layoutEpoch}

                  progressEpoch={worldMapProgressEpoch}

                  obliqueDirection={displaySettings.worldMapObliqueDirection}

                  displaySettings={displaySettings}

                  onDisplaySettingsChange={setDisplaySettings}

                  onDriveClick={(node) => {

                    setSelectedNode(node);

                    setDriveModalNode(node);

                  }}

                />

              ) : (

                <Empty description={t('topologyEmpty')} style={{ padding: 64 }} />

              )

            ) : snapshot && snapshot.nodes.length > 0 ? (

              showSimulated ? (

                <TopologySimulatedView

                  ref={graphRef}

                  nodes={snapshot.nodes}

                  edges={snapshot.edges}

                  displaySettings={displaySettings}

                  selectedNodeId={selectedNode?.id ?? null}

                  onNodeSelect={setSelectedNode}

                  layoutEpoch={layoutEpoch}

                  onDriveClick={(node) => {

                    setSelectedNode(node);

                    setDriveModalNode(node);

                  }}

                />

              ) : (

                <TopologyCytoscapeGraph

                  ref={graphRef}

                  nodes={snapshot.nodes}

                  edges={snapshot.edges}

                  mode={viewMode === 'spatial' ? 'spatial' : 'logical'}

                  layout={meta?.layout}

                  displaySettings={displaySettings}

                  selectedNodeId={selectedNode?.id ?? null}

                  hoveredNodeId={hoveredNodeId}

                  onNodeSelect={setSelectedNode}

                  onNodeHover={setHoveredNodeId}

                  layoutEpoch={layoutEpoch}

                />

              )

            ) : (

              <Empty description={t('topologyEmpty')} style={{ padding: 64 }} />

            )}

            {(loading || capturing || worldMapLoading) && (snapshot || worldMapMeta) && (

              <div className="topology-loading-overlay">

                <Spin size="small" />

              </div>

            )}

          </Card>

        </div>

      )}



      <Modal

        title={t('topologyDeviceListTitle')}

        open={deviceListOpen}

        onCancel={() => setDeviceListOpen(false)}

        footer={null}

        width={520}

        destroyOnClose={false}

        className="topology-device-list-modal"

      >

        {snapshot && (

          <TopologyDeviceList

            nodes={snapshot.nodes}

            selectedNodeId={selectedNode?.id ?? null}

            hoveredNodeId={hoveredNodeId}

            hideCableNodes={displaySettings.hideCableNodes}

            onSelectNode={(node) => {

              setSelectedNode(node);

              if (node) setDeviceListOpen(false);

            }}

            onHoverNode={setHoveredNodeId}

            onSelectDevice={(nodeId) => {

              const node = snapshot.nodes.find((n) => n.id === nodeId);

              if (node?.type === 'drive') setDriveModalNode(node);

            }}

            height={480}

          />

        )}

      </Modal>



      {showSimulated && snapshot && (

        <Card size="small" style={{ marginTop: 8 }} title={t('topologyCableLegend')}>

          <Space wrap>

            <Tag color={displaySettings.colors.smart}>{t('topologyCable_smart')}</Tag>

            <Tag color={displaySettings.colors.covered}>{t('topologyCable_covered')}</Tag>

            <Tag color={displaySettings.colors.dense}>{t('topologyCable_dense')}</Tag>

            <Text type="secondary">{t('topologySimChannelsHint')}</Text>

          </Space>

        </Card>

      )}



      <TopologySettingsDrawer

        open={settingsOpen}

        onClose={() => setSettingsOpen(false)}

        settings={displaySettings}

        onChange={(next) => {
          const prevDir = displaySettings.worldMapObliqueDirection;
          const prevQuality = displaySettings.worldMapQuality;
          const clamped = {
            ...next,
            worldMapQuality: clampWorldMapQuality(next.worldMapQuality ?? 'medium', worldMapMaxQuality),
          };
          setDisplaySettings(clamped);
          if (viewMode === 'worldMap') {
            if (
              clamped.worldMapObliqueDirection !== prevDir ||
              clamped.worldMapQuality !== prevQuality
            ) {
              bumpWorldMapTileEpoch();
              void reloadWorldMap();
            }
          }
        }}

        onReset={resetDisplaySettings}

        showRenderMode={viewMode === 'logical'}

        topologySimulatedEnabled={topologySimulatedEnabled}

        showWorldMapSettings={viewMode === 'worldMap'}

        obliqueDirectionOptions={worldMapMeta?.obliqueDirections}
        qualityTierOptions={worldMapMeta?.qualityTiers}
        maxQualityTier={worldMapMaxQuality}
        terrainSource={worldMapMeta?.terrainSource}
        hdAvailable={worldMapMeta?.hdAvailable}
        clientCaptureMode={worldMapMeta?.clientCaptureMode}
        dynmapBaseUrl={dynmapBaseUrl || undefined}
        snapshotSourcePriority={worldMapMeta?.snapshotSourcePriority}
        snapshotSourceStats={worldMapMeta?.snapshotSourceStats}
        snapshotSource={worldMapMeta?.snapshotSource}
      />

      <DriveSimulatedGui
        node={driveModalNode}
        cellChildNodes={driveCellChildren}
        open={!!driveModalNode}
        onClose={() => setDriveModalNode(null)}
      />

      {viewMode !== 'p2p' && (
        <TopologyNodeDetailDrawer
          node={selectedNode}
          open={!!selectedNode && !driveModalNode}
          onClose={() => setSelectedNode(null)}
          onOpenDriveGui={(node) => {
            setSelectedNode(node);
            setDriveModalNode(node);
          }}
        />
      )}

    </PageShell>

  );

}

