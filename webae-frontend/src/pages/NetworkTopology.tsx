import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Segmented,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from 'antd';
import { CameraOutlined, ReloadOutlined, ApartmentOutlined } from '@ant-design/icons';
import { getApiClient, ApiClientError } from '@/api/client';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { PageShell } from '@/components/Layout/PageShell';
import { ExportCsvButton } from '@/components/ExportCsvButton';
import { TopologyGraphSvg } from '@/components/topology/TopologyGraphSvg';
import { P2pMapPanel } from '@/components/topology/P2pMapPanel';
import { formatTime } from '@/utils/format';
import type {
  TopologyNodeDto,
  TopologyResponse,
  TopologySnapshotDto,
  NetworkCellSummaryDto,
  NetworkCellSummaryResponse,
} from '@/types/dto';

const { Text } = Typography;

type ViewMode = 'logical' | 'spatial' | 'p2p';

function formatCooldown(ms: number): string {
  const sec = Math.ceil(ms / 1000);
  if (sec >= 60) return `${Math.floor(sec / 60)}m ${sec % 60}s`;
  return `${sec}s`;
}

export function NetworkTopologyPage() {
  const { selectedNetworks, serverConfig, notify } = useAppContext();
  const { t } = useI18n();
  const [activeNetwork, setActiveNetwork] = useState<number | null>(null);
  const [viewMode, setViewMode] = useState<ViewMode>('logical');
  const [loading, setLoading] = useState(false);
  const [capturing, setCapturing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [cached, setCached] = useState(false);
  const [snapshot, setSnapshot] = useState<TopologySnapshotDto | null>(null);
  const [snapshotTs, setSnapshotTs] = useState<number | null>(null);
  const [cooldownRemainingMs, setCooldownRemainingMs] = useState(0);
  const [cooldownMs, setCooldownMs] = useState(300000);
  const [selectedNode, setSelectedNode] = useState<TopologyNodeDto | null>(null);
  const [cellSummary, setCellSummary] = useState<NetworkCellSummaryDto | null>(null);

  const currentNet = activeNetwork ?? selectedNetworks[0] ?? 0;
  const topologyEnabled = serverConfig?.topologyEnabled !== false;

  const applyResponse = useCallback((data: TopologyResponse, fromCache: boolean) => {
    if (data.cooldownRemainingMs != null) setCooldownRemainingMs(data.cooldownRemainingMs);
    if (data.cooldownMs != null) setCooldownMs(data.cooldownMs);
    if (data.success && data.data && data.hasSnapshot !== false) {
      setSnapshot(data.data);
      setSnapshotTs(data.timestamp ?? data.data.timestamp ?? null);
      setCached(fromCache || !!data.cached);
      setSelectedNode(null);
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
      const data = await getApiClient().get<TopologyResponse>(
        `/api/network/topology?network=${currentNet}&mode=${viewMode}`
      );
      applyResponse(data, true);
    } catch (e) {
      const msg = (e as Error).message || t('topologyLoadFailed');
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, [currentNet, viewMode, topologyEnabled, selectedNetworks.length, t, applyResponse]);

  const captureSnapshot = useCallback(async () => {
    if (!topologyEnabled || selectedNetworks.length === 0 || viewMode === 'p2p') return;
    if (cooldownRemainingMs > 0) {
      notify(t('topologyCooldownWait'), 'warning');
      return;
    }
    setCapturing(true);
    setError(null);
    try {
      const data = await getApiClient().post<TopologyResponse>(
        `/api/network/topology/snapshot?network=${currentNet}&mode=${viewMode}`
      );
      if (applyResponse(data, false)) {
        notify(t('topologyCaptureSnapshot'), 'success');
      }
    } catch (e) {
      if (e instanceof ApiClientError && e.code === 'cooldown') {
        setError(t('topologyCooldownWait'));
        notify(t('topologyCooldownWait'), 'warning');
        void loadCached();
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
  const simCh = meta?.channelsSimulated;
  const realCh = meta?.channelsReal;
  const captureDisabled = capturing || cooldownRemainingMs > 0;

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
            onClick={() => void captureSnapshot()}
            loading={capturing}
            disabled={captureDisabled}
          >
            {t('topologyCaptureSnapshot')}
          </Button>
          <Button
            icon={<ReloadOutlined />}
            onClick={() => void loadCached()}
            loading={loading}
            aria-label={t('refresh')}
          >
            {t('refresh')}
          </Button>
          <ExportCsvButton
            filename={`topology-net${currentNet}.csv`}
            headers={[t('topologyNodeType'), t('topologyNodeCount'), 'channelCost']}
            rows={(snapshot?.nodes || []).map((n) => [n.displayName || n.type, n.count, n.channelCost ?? ''])}
            disabled={!snapshot?.nodes?.length}
          />
        </Space>
      }
    >
      <Card size="small" style={{ marginBottom: 8 }}>
        <Space wrap size="middle" style={{ width: '100%', justifyContent: 'space-between' }}>
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
          <Segmented
            value={viewMode}
            onChange={(v) => setViewMode(v as ViewMode)}
            options={[
              { value: 'logical', label: t('topologyMode_logical') },
              { value: 'spatial', label: t('topologyMode_spatial') },
              { value: 'p2p', label: t('topologyMode_p2p') },
            ]}
          />
        </Space>
      </Card>

      {!snapshot && !loading && viewMode !== 'p2p' && (
        <Alert type="info" showIcon message={t('topologyNoSnapshot')} style={{ marginBottom: 8 }} />
      )}

      {error && (
        <Alert type="error" showIcon message={error} style={{ marginBottom: 8 }} closable />
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

      {meta && (
        <Card size="small" style={{ marginBottom: 8 }}>
          <Descriptions size="small" column={{ xs: 1, sm: 2, md: 4 }}>
            <Descriptions.Item label={t('topologyLayout')}>
              {meta.layout === 'tree'
                ? t('topologyLayout_tree')
                : meta.layout === 'star'
                  ? t('topologyLayout_star')
                  : meta.layout}
            </Descriptions.Item>
            <Descriptions.Item label={t('topologySimChannels')}>
              {simCh?.available ? `${simCh.used} / ${simCh.max}` : '—'}
              <div>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {t('topologySimChannelsHint')}
                </Text>
              </div>
            </Descriptions.Item>
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
        <Card
          className="topology-graph-card"
          styles={{ body: { padding: 0, position: 'relative', minHeight: 520 } }}
        >
          {loading && !snapshot ? (
            <div style={{ textAlign: 'center', padding: 80 }}>
              <Spin aria-label={t('loading')} />
            </div>
          ) : snapshot && snapshot.nodes.length > 0 ? (
            <TopologyGraphSvg
              nodes={snapshot.nodes}
              edges={snapshot.edges}
              mode={viewMode}
              layout={meta?.layout}
              selectedNodeId={selectedNode?.id ?? null}
              onNodeSelect={setSelectedNode}
            />
          ) : (
            <Empty description={t('topologyEmpty')} style={{ padding: 64 }} />
          )}
          {(loading || capturing) && snapshot && (
            <div className="topology-loading-overlay">
              <Spin size="small" />
            </div>
          )}
        </Card>
      )}

      {viewMode !== 'p2p' && (
        <Drawer
          title={selectedNode?.displayName ?? t('topologyNodeDetail')}
          open={!!selectedNode}
          onClose={() => setSelectedNode(null)}
          width={Math.min(520, window.innerWidth - 24)}
        >
          {selectedNode && (
            <>
              <Descriptions size="small" column={1} style={{ marginBottom: 16 }}>
                <Descriptions.Item label={t('topologyNodeType')}>{selectedNode.type}</Descriptions.Item>
                <Descriptions.Item label={t('topologyNodeCount')}>{selectedNode.count}</Descriptions.Item>
                <Descriptions.Item label={t('topologyChannelCost')}>{selectedNode.channelCost}</Descriptions.Item>
                {selectedNode.dim != null && selectedNode.dim !== -2147483648 && (
                  <Descriptions.Item label={t('topologyDim')}>{selectedNode.dim}</Descriptions.Item>
                )}
              </Descriptions>
              <Table
                size="small"
                pagination={{ pageSize: 8, showSizeChanger: false }}
                dataSource={(selectedNode.devices ?? []).map((d, i) => ({ ...d, key: i }))}
                columns={[
                  {
                    title: t('topologyDeviceName'),
                    dataIndex: 'displayName',
                    ellipsis: true,
                    render: (v: string, row) => v || row.className || '—',
                  },
                  {
                    title: t('topologyCoords'),
                    key: 'coords',
                    width: 140,
                    render: (_, row) => `${row.x}, ${row.y}, ${row.z}`,
                  },
                  {
                    title: 'Dim',
                    dataIndex: 'dim',
                    width: 48,
                  },
                ]}
              />
            </>
          )}
        </Drawer>
      )}
    </PageShell>
  );
}
