import { lazy, Suspense, useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Avatar,
  Button,
  Card,
  Col,
  Descriptions,
  Drawer,
  Empty,
  Input,
  List,
  message,
  Popconfirm,
  Row,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import {
  ReloadOutlined,
  ThunderboltOutlined,
  DatabaseOutlined,
  SettingOutlined,
  FormOutlined,
  ApartmentOutlined,
  CrownOutlined,
  SafetyCertificateOutlined,
  ToolOutlined,
  CloudServerOutlined,
  GlobalOutlined,
  ExperimentOutlined,
  WarningOutlined,
  CloseCircleOutlined,
  BookOutlined,
  DashboardOutlined,
  LineChartOutlined,
  InfoCircleOutlined,
  UserOutlined,
  UserSwitchOutlined,
  DeleteOutlined,
  StopOutlined,
  PlayCircleOutlined,
  SearchOutlined,
  RobotOutlined,
  CodeOutlined,
} from '@ant-design/icons';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { getApiClient } from '@/api/client';
import { PageShell } from '@/components/Layout/PageShell';
import { useServerDiagnostics } from '@/hooks/useServerDiagnostics';
import { useAdminPlayers } from '@/hooks/useAdminPlayers';
import { SparkProfilerTab } from '@/pages/Spark';
import { QqBotPanel } from '@/components/admin/QqBotPanel';
import { formatTime } from '@/utils/format';
import type {
  AdminMeResponse,
  AdminGrantEntry,
  AdminGrantsResponse,
  AdminPlayerAccessResponse,
  AdminOwnedNetwork,
  AdminGuestNetworkRow,
  ServerHealthResponse,
  ServerDiagnosticsResponse,
  PerfPhaseView,
  PerfSlowHttpEntry,
} from '@/types/dto';

const { Text, Paragraph } = Typography;

const ServerConsolePanel = lazy(() => import('@/components/admin/ServerConsolePanel').then((module) => ({
  default: module.ServerConsolePanel,
})));

const DIAGNOSTICS_POLL_MS = 3000;

const PHASE_ORDER = [
  'serverTasks',
  'snapshotScheduler',
  'powerSampler',
  'metricSampler',
  'iconMissingQueue',
  'worldMapTileQueue',
  'worldMapCapture',
  'webAlertEngine',
  'qqBot',
  'misc',
];

interface GenericRefreshResult {
  success?: boolean;
  status?: string;
  message?: string;
}

function formatUptime(seconds: number): string {
  if (!seconds || seconds <= 0) return '-';
  const d = Math.floor(seconds / 86400);
  const h = Math.floor((seconds % 86400) / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const parts: string[] = [];
  if (d > 0) parts.push(`${d}d`);
  if (h > 0) parts.push(`${h}h`);
  if (m > 0) parts.push(`${m}m`);
  return parts.join(' ') || `${seconds}s`;
}

function msTag(ms: number) {
  let color: string = 'default';
  if (ms >= 10) color = 'error';
  else if (ms >= 5) color = 'warning';
  else if (ms > 0) color = 'success';
  return <Tag color={color}>{ms.toFixed(1)} ms</Tag>;
}

function msptShareTag(avgMs: number, mspt: number) {
  if (mspt <= 0 || avgMs <= 0) return <Text type="secondary">—</Text>;
  const pct = (avgMs / mspt) * 100;
  let color: string = 'default';
  if (pct >= 20) color = 'error';
  else if (pct >= 10) color = 'warning';
  else if (pct > 0) color = 'success';
  return (
    <Tooltip title={`${avgMs.toFixed(1)} ms / ${mspt.toFixed(1)} ms`}>
      <Tag color={color}>{pct.toFixed(1)}%</Tag>
    </Tooltip>
  );
}

function cumulativeCountTitle(t: (key: string) => string) {
  return (
    <Tooltip title={t('diagCumulativeCountTip')}>
      <span>
        {t('diagCumulativeCount')} <InfoCircleOutlined style={{ fontSize: 12, opacity: 0.65 }} />
      </span>
    </Tooltip>
  );
}

function phaseRows(phases: Record<string, PerfPhaseView> | undefined): Array<{ key: string } & PerfPhaseView> {
  if (!phases) return [];
  const keys = Object.keys(phases);
  keys.sort((a, b) => {
    const ia = PHASE_ORDER.indexOf(a);
    const ib = PHASE_ORDER.indexOf(b);
    if (ia >= 0 && ib >= 0) return ia - ib;
    if (ia >= 0) return -1;
    if (ib >= 0) return 1;
    return a.localeCompare(b);
  });
  return keys.map((key) => ({ key, ...phases[key] }));
}

export function AdminPage() {
  const { isAdmin, isOnlineOp, adminCapabilities, checkAdminStatus, revokeAdmin, networks, selectedNetworks, serverConfig } = useAppContext();
  const { t } = useI18n();
  const [activeTab, setActiveTab] = useState('status');
  const [loading, setLoading] = useState(false);

  // Status tab state
  const [meData, setMeData] = useState<AdminMeResponse | null>(null);
  const [grants, setGrants] = useState<AdminGrantEntry[]>([]);
  const [grantsLoading, setGrantsLoading] = useState(false);
  const [refreshLoading, setRefreshLoading] = useState<Record<string, boolean>>({});

  // Service tab state
  const [health, setHealth] = useState<ServerHealthResponse | null>(null);
  const [healthLoading, setHealthLoading] = useState(false);

  // Diagnostics tab state (auto-polling)
  const { data: diagData, loading: diagLoading } = useServerDiagnostics(DIAGNOSTICS_POLL_MS);

  // Players tab state
  const {
    players,
    loading: playersLoading,
    disablePlayer,
    enablePlayer,
    clearPlayerCache,
    fetchAccess,
    suspendNetwork,
    resumeNetwork,
    setAcl,
    revokeGuestToken,
  } = useAdminPlayers(activeTab === 'players');
  const [playerSearch, setPlayerSearch] = useState('');
  const [accessDrawerUuid, setAccessDrawerUuid] = useState<string | null>(null);
  const [accessDrawerName, setAccessDrawerName] = useState('');
  const [accessData, setAccessData] = useState<AdminPlayerAccessResponse | null>(null);
  const [accessLoading, setAccessLoading] = useState(false);

  // Resource tab state
  const [invalidateLoading, setInvalidateLoading] = useState(false);

  // Load me and grants
  const loadStatus = useCallback(async () => {
    await checkAdminStatus();
    setGrantsLoading(true);
    try {
      const me = await getApiClient().get<AdminMeResponse>('/api/auth/admin/me');
      if (me.status === 'ok') setMeData(me);
    } catch { /* ignore */ }
    try {
      const g = await getApiClient().get<AdminGrantsResponse>('/api/auth/admin/grants');
      if (g.status === 'ok') setGrants(g.grants || []);
    } catch { /* ignore */ }
    setGrantsLoading(false);
  }, [checkAdminStatus]);

  // Load health
  const loadHealth = useCallback(async () => {
    setHealthLoading(true);
    try {
      const h = await getApiClient().get<ServerHealthResponse>('/api/server/health');
      if (h.success) setHealth(h);
    } catch { /* ignore */ }
    setHealthLoading(false);
  }, []);

  useEffect(() => {
    if (activeTab === 'status') loadStatus();
    else if (activeTab === 'service') loadHealth();
  }, [activeTab, loadStatus, loadHealth]);

  // Force refresh
  const runRefresh = useCallback(async (endpoint: string, label: string) => {
    setRefreshLoading((prev) => ({ ...prev, [label]: true }));
    try {
      const networksParam = selectedNetworks?.length
        ? selectedNetworks.map((n) => `network=${encodeURIComponent(String(n))}`).join('&')
        : '';
      const url = networksParam ? `${endpoint}?${networksParam}` : endpoint;
      const result = await getApiClient().post<GenericRefreshResult>(url);
      if (result.success || result.status === 'ok') {
        message.success(t(label + 'Success') || t('adminRefreshOk'));
      } else {
        message.error(result.message || t('adminRefreshFailed'));
      }
    } catch (e: any) {
      message.error(e?.message || t('adminRefreshFailed'));
    }
    setRefreshLoading((prev) => ({ ...prev, [label]: false }));
  }, [selectedNetworks, t]);

  // Invalidate worldmap
  const handleInvalidate = useCallback(async () => {
    setInvalidateLoading(true);
    try {
      const networksParam = selectedNetworks?.length
        ? selectedNetworks.map((n) => `network=${encodeURIComponent(String(n))}`).join('&')
        : '';
      const url = networksParam ? `/api/worldmap/invalidate?${networksParam}` : '/api/worldmap/invalidate';
      const result = await getApiClient().post<GenericRefreshResult>(url);
      if (result.success) {
        message.success(t('adminWorldMapInvalidated'));
      } else {
        message.error(result.message || t('adminRefreshFailed'));
      }
    } catch (e: any) {
      message.error(e?.message || t('adminRefreshFailed'));
    }
    setInvalidateLoading(false);
  }, [selectedNetworks, t]);

  // Revoke grant
  const handleRevokeSelf = useCallback(async () => {
    await revokeAdmin();
    await loadStatus();
  }, [revokeAdmin, loadStatus]);

  // Player actions
  const handleDisablePlayer = useCallback(async (uuid: string) => {
    const ok = await disablePlayer(uuid);
    if (ok) message.success(t('adminPlayersDisableSuccess'));
    else message.error(t('adminPlayersOpFailed'));
  }, [disablePlayer, t]);

  const handleEnablePlayer = useCallback(async (uuid: string) => {
    const ok = await enablePlayer(uuid);
    if (ok) message.success(t('adminPlayersEnableSuccess'));
    else message.error(t('adminPlayersOpFailed'));
  }, [enablePlayer, t]);

  const handleClearPlayerCache = useCallback(async (uuid: string) => {
    const ok = await clearPlayerCache(uuid);
    if (ok) message.success(t('adminPlayersClearCacheSuccess'));
    else message.error(t('adminPlayersOpFailed'));
  }, [clearPlayerCache, t]);

  const openAccessDrawer = useCallback(async (uuid: string, name: string) => {
    setAccessDrawerUuid(uuid);
    setAccessDrawerName(name);
    setAccessLoading(true);
    setAccessData(null);
    const data = await fetchAccess(uuid);
    setAccessData(data);
    setAccessLoading(false);
  }, [fetchAccess]);

  const reloadAccess = useCallback(async () => {
    if (!accessDrawerUuid) return;
    setAccessLoading(true);
    const data = await fetchAccess(accessDrawerUuid);
    setAccessData(data);
    setAccessLoading(false);
  }, [accessDrawerUuid, fetchAccess]);

  const handleSuspendNet = useCallback(async (networkKey: string) => {
    if (!accessDrawerUuid) return;
    const ok = await suspendNetwork(accessDrawerUuid, networkKey);
    if (ok) {
      message.success(t('adminPlayersSuspendSuccess'));
      await reloadAccess();
    } else message.error(t('adminPlayersOpFailed'));
  }, [accessDrawerUuid, suspendNetwork, reloadAccess, t]);

  const handleResumeNet = useCallback(async (networkKey: string) => {
    if (!accessDrawerUuid) return;
    const ok = await resumeNetwork(accessDrawerUuid, networkKey);
    if (ok) {
      message.success(t('adminPlayersResumeSuccess'));
      await reloadAccess();
    } else message.error(t('adminPlayersOpFailed'));
  }, [accessDrawerUuid, resumeNetwork, reloadAccess, t]);

  const filteredPlayers = useMemo(() => {
    if (!playerSearch.trim()) return players;
    const q = playerSearch.toLowerCase();
    return players.filter(
      (p) => p.name.toLowerCase().includes(q) || p.uuid.toLowerCase().includes(q));
  }, [players, playerSearch]);

  // ---- Tab: Status ----
  const renderStatusTab = () => (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Card
        title={
          <Space>
            <CrownOutlined />
            <span>{t('adminPanelStatus')}</span>
          </Space>
        }
        extra={
          <Button size="small" icon={<ReloadOutlined />} onClick={loadStatus} loading={grantsLoading}>
            {t('adminRefreshStatus')}
          </Button>
        }
      >
        {meData ? (
          <Descriptions size="small" column={{ xs: 1, sm: 2 }}>
            <Descriptions.Item label={t('adminPanelActorName')}>{meData.actorName || '-'}</Descriptions.Item>
            <Descriptions.Item label={t('adminPanelTokenType')}>
              <Tag color={meData.tokenType === 'owner' ? 'blue' : 'orange'}>{meData.tokenType}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Admin">
              <Tag color={meData.isAdmin ? 'green' : 'default'}>
                {meData.isAdmin ? t('adminActive') : t('no')}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('adminOnlineOp')}>
              <Tag color={meData.isOnlineOp ? 'green' : 'default'}>
                {meData.isOnlineOp ? t('yes') : t('no')}
              </Tag>
            </Descriptions.Item>
          </Descriptions>
        ) : (
          <Spin />
        )}
      </Card>

      {meData?.capabilities && (
        <Card title={<Space><SafetyCertificateOutlined />{t('adminPanelCapabilities')}</Space>} size="small">
          <Space wrap>
            <Tag color={meData.capabilities.canForceSnapshot ? 'green' : 'default'}>
              {t('adminCanForceSnapshot')}: {meData.capabilities.canForceSnapshot ? '✓' : '✗'}
            </Tag>
            <Tag color={meData.capabilities.canEditRules ? 'green' : 'default'}>
              {t('adminCanEditRules')}: {meData.capabilities.canEditRules ? '✓' : '✗'}
            </Tag>
            <Tag color={meData.capabilities.canUploadPacks ? 'green' : 'default'}>
              {t('adminCanUploadPacks')}: {meData.capabilities.canUploadPacks ? '✓' : '✗'}
            </Tag>
            <Tag color={meData.capabilities.canManageTokens ? 'green' : 'default'}>
              {t('adminCanManageTokens')}: {meData.capabilities.canManageTokens ? '✓' : '✗'}
            </Tag>
          </Space>
        </Card>
      )}

      <Card
        title={<Space><SafetyCertificateOutlined />{t('adminPanelGrants')}</Space>}
        size="small"
      >
        {grantsLoading ? (
          <Spin />
        ) : grants.length === 0 ? (
          <Empty description={t('adminPanelNoGrants')} />
        ) : (
          <Table
            dataSource={grants}
            rowKey="adminToken"
            size="small"
            pagination={false}
            columns={[
              { title: t('adminPanelGrantLabel'), dataIndex: 'label', key: 'label', render: (v: string) => v || '-' },
              { title: t('adminPanelGrantActor'), dataIndex: 'boundActorName', key: 'actor', render: (v: string) => v || '-' },
              { title: t('adminPanelGrantIssued'), dataIndex: 'issuedAt', key: 'issuedAt', render: (v: number) => (v ? new Date(v).toLocaleString() : '-') },
              {
                title: t('adminPanelGrantExpires'), dataIndex: 'expiresAt', key: 'expiresAt',
                render: (v: number) => {
                  if (!v || v === 0) return <Tag color="green">{t('adminPanelGrantNever')}</Tag>;
                  const expired = Date.now() > v;
                  return <Tag color={expired ? 'red' : 'green'}>{new Date(v).toLocaleString()}</Tag>;
                },
              },
              {
                title: t('adminPanelGrantToken'), dataIndex: 'adminToken', key: 'token',
                render: (v: string) => (
                  <Text code copyable={{ text: v }} style={{ fontSize: '0.75rem' }}>{v.substring(0, 12)}...</Text>
                ),
              },
            ]}
          />
        )}
      </Card>

      {isAdmin && (
        <Card size="small">
          <Popconfirm
            title={t('adminRevokeConfirm')}
            onConfirm={handleRevokeSelf}
            okText={t('confirm')}
            cancelText={t('cancel')}
          >
            <Button danger icon={<CloseCircleOutlined />}>{t('adminRevokeButton')}</Button>
          </Popconfirm>
        </Card>
      )}
    </Space>
  );

  // ---- Tab: Maintenance ----
  const refreshActions = [
    { key: 'storageRefresh', icon: <DatabaseOutlined />, label: t('adminRefreshStorage'), endpoint: '/api/storage/refresh' },
    { key: 'powerRefresh', icon: <ThunderboltOutlined />, label: t('adminRefreshPower'), endpoint: '/api/power/refresh' },
    { key: 'gtRefresh', icon: <SettingOutlined />, label: t('adminRefreshGt'), endpoint: '/api/gt/machines/refresh' },
    { key: 'patternRefresh', icon: <FormOutlined />, label: t('adminRefreshPatterns'), endpoint: '/api/patterns/browse/refresh' },
    { key: 'topologySnapshot', icon: <ApartmentOutlined />, label: t('adminRefreshTopology'), endpoint: '/api/network/topology/snapshot' },
  ];

  const renderRefreshTab = () => (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      {selectedNetworks && selectedNetworks.length === 0 && (
        <Alert type="info" message={t('adminSelectNetworkHint')} showIcon />
      )}
      <Card title={<Space><ToolOutlined />{t('adminPanelRefresh')}</Space>}>
        <Space direction="vertical" size="small" style={{ width: '100%' }}>
          <Text type="secondary">{t('adminRefreshActiveNetwork')}</Text>
          {refreshActions.map((action) => (
            <Button
              key={action.key}
              icon={action.icon}
              loading={refreshLoading[action.key]}
              onClick={() => runRefresh(action.endpoint, action.key)}
              block
            >
              {action.label}
            </Button>
          ))}
        </Space>
      </Card>
    </Space>
  );

  // ---- Tab: Service (simplified — server health only) ----
  const renderServiceTab = () => (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Card
        title={<Space><CloudServerOutlined />{t('adminPanelServerHealth')}</Space>}
        extra={
          <Button size="small" icon={<ReloadOutlined />} onClick={loadHealth} loading={healthLoading}>
            {t('refresh')}
          </Button>
        }
      >
        {health ? (
          <Descriptions size="small" column={{ xs: 1, sm: 2 }}>
            <Descriptions.Item
              label={
                <Tooltip title={t('serverHealthForgeTip')}>
                  <span>
                    TPS <InfoCircleOutlined style={{ fontSize: 12, opacity: 0.65 }} />
                  </span>
                </Tooltip>
              }
            >
              <Tag color={health.tps >= 18 ? 'green' : health.tps >= 15 ? 'orange' : 'red'}>
                {health.tps.toFixed(1)}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item
              label={
                <Tooltip title={t('serverHealthForgeTip')}>
                  <span>
                    MSPT <InfoCircleOutlined style={{ fontSize: 12, opacity: 0.65 }} />
                  </span>
                </Tooltip>
              }
            >
              <Tag color={health.mspt <= 40 ? 'green' : health.mspt <= 50 ? 'orange' : 'red'}>
                {health.mspt.toFixed(1)}ms
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('adminPanelOnlinePlayers')}>
              {health.onlinePlayers}
            </Descriptions.Item>
            <Descriptions.Item label={t('adminPanelUptime')}>
              {formatUptime(health.uptimeSeconds)}
            </Descriptions.Item>
          </Descriptions>
        ) : (
          healthLoading ? <Spin /> : <Empty />
        )}
      </Card>
    </Space>
  );

  // ---- Tab: Diagnostics (full content from DiagnosticsPage, auto-polling) ----
  const renderDiagnosticsTab = () => {
    const phaseData = phaseRows(diagData?.phases);
    const collectData = phaseRows(diagData?.collects);
    const mspt = diagData?.mspt ?? 0;

    return (
      <Spin spinning={diagLoading && !diagData}>
        <Paragraph type="secondary" style={{ marginTop: 0 }}>
          <DashboardOutlined /> {t('diagnosticsReadonlyNote')}
        </Paragraph>

        <Row gutter={[16, 16]}>
          <Col xs={12} sm={8} md={4}>
            <Card
              size="small"
              title={
                <Tooltip title={t('serverHealthForgeTip')}>
                  <span>
                    TPS <InfoCircleOutlined style={{ fontSize: 12, opacity: 0.65 }} />
                  </span>
                </Tooltip>
              }
            >
              <Text strong style={{ fontSize: 22 }}>{diagData ? diagData.tps.toFixed(1) : '—'}</Text>
            </Card>
          </Col>
          <Col xs={12} sm={8} md={4}>
            <Card
              size="small"
              title={
                <Tooltip title={t('serverHealthForgeTip')}>
                  <span>
                    MSPT <InfoCircleOutlined style={{ fontSize: 12, opacity: 0.65 }} />
                  </span>
                </Tooltip>
              }
            >
              <Text strong style={{ fontSize: 22 }}>{diagData ? diagData.mspt.toFixed(1) : '—'}</Text>
            </Card>
          </Col>
          <Col xs={12} sm={8} md={4}>
            <Card size="small" title={t('diagQueueDepth')}>
              <Text strong style={{ fontSize: 22 }}>{diagData?.queueDepth ?? '—'}</Text>
            </Card>
          </Col>
          <Col xs={12} sm={8} md={4}>
            <Card size="small" title={t('diagTasksThisTick')}>
              <Text strong style={{ fontSize: 22 }}>{diagData?.tasksProcessedThisTick ?? '—'}</Text>
            </Card>
          </Col>
          <Col xs={12} sm={8} md={4}>
            <Card size="small" title={t('diagActiveNetworks')}>
              <Text strong style={{ fontSize: 22 }}>{diagData?.activeNetworks ?? '—'}</Text>
            </Card>
          </Col>
          <Col xs={12} sm={8} md={4}>
            <Card size="small" title={t('diagSnapshotCache')}>
              <Text strong style={{ fontSize: 22 }}>{diagData?.snapshotCacheSize ?? '—'}</Text>
            </Card>
          </Col>
        </Row>

        {diagData?.config && (
          <Card size="small" title={t('diagConfigSummary')} style={{ marginTop: 16 }}>
            <Text type="secondary">
              refresh={diagData.config.refreshIntervalMs}ms · gt={diagData.config.gtRefreshIntervalMs}ms · patternTtl=
              {diagData.config.patternCacheTtlMs}ms · mapBudget={diagData.config.worldMapTileBudgetPerTick}/tick · webaePerf=
              {diagData.config.perfDebugEnabled ? 'on' : 'off'}
            </Text>
          </Card>
        )}

        <Card size="small" title={t('diagTickPhases')} style={{ marginTop: 16 }}>
          <Table
            size="small" pagination={false} rowKey="key"
            dataSource={phaseData}
            columns={[
              { title: t('diagPhase'), dataIndex: 'key' },
              { title: t('diagLastMs'), dataIndex: 'lastMs', render: (v: number) => msTag(v) },
              { title: t('diagAvgMs'), dataIndex: 'avgMs', render: (v: number) => `${Number(v).toFixed(1)}` },
              { title: t('diagMaxMs'), dataIndex: 'maxMs' },
              {
                title: (
                  <Tooltip title={t('diagMsptShareTip')}>
                    <span>{t('diagMsptShare')} <InfoCircleOutlined style={{ fontSize: 12, opacity: 0.65 }} /></span>
                  </Tooltip>
                ),
                key: 'msptShare',
                render: (_: unknown, row: PerfPhaseView) => msptShareTag(row.avgMs, mspt),
              },
              { title: cumulativeCountTitle(t), dataIndex: 'count' },
            ]}
          />
        </Card>

        <Card size="small" title={t('diagCollects')} style={{ marginTop: 16 }}>
          <Table
            size="small" pagination={false} rowKey="key"
            dataSource={collectData}
            locale={{ emptyText: t('diagNoData') }}
            columns={[
              { title: t('diagPhase'), dataIndex: 'key' },
              { title: t('diagLastMs'), dataIndex: 'lastMs', render: (v: number) => msTag(v) },
              { title: t('diagAvgMs'), dataIndex: 'avgMs', render: (v: number) => `${Number(v).toFixed(1)}` },
              { title: t('diagMaxMs'), dataIndex: 'maxMs' },
              { title: cumulativeCountTitle(t), dataIndex: 'count' },
            ]}
          />
        </Card>

        <Card size="small" title={t('diagTopRoutes')} style={{ marginTop: 16 }}>
          <Alert type="info" showIcon message={t('diagHttpNoTpsImpact')} style={{ marginBottom: 12 }} />
          <Table
            size="small" pagination={false} rowKey="route"
            dataSource={diagData?.topRoutes ?? []}
            locale={{ emptyText: t('diagNoData') }}
            columns={[
              { title: t('diagRoute'), dataIndex: 'route' },
              { title: t('diagRouteCount'), dataIndex: 'count' },
              { title: t('diagAvgMs'), dataIndex: 'avgMs', render: (v: number) => `${Number(v).toFixed(1)}` },
              { title: t('diagMaxMs'), dataIndex: 'maxMs', render: (v: number) => msTag(v) },
              { title: t('diagTotalMs'), dataIndex: 'totalMs' },
            ]}
          />
        </Card>

        <Card size="small" title={t('diagSlowHttp')} style={{ marginTop: 16 }}>
          <Table
            size="small" pagination={{ pageSize: 10 }}
            rowKey={(r: PerfSlowHttpEntry) => `${r.ts}-${r.route}-${r.durationMs}`}
            dataSource={[...(diagData?.slowHttp ?? [])].reverse()}
            locale={{ emptyText: t('diagNoData') }}
            columns={[
              { title: t('diagTime'), dataIndex: 'ts', render: (v: number) => formatTime(v) },
              { title: t('diagRoute'), dataIndex: 'route' },
              { title: t('diagDurationMs'), dataIndex: 'durationMs', render: (v: number) => msTag(v) },
            ]}
          />
        </Card>
      </Spin>
    );
  };

  // ---- Tab: Players ----
  const renderPlayersTab = () => (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Input
        prefix={<SearchOutlined />}
        placeholder={t('adminPlayersSearchPlaceholder')}
        value={playerSearch}
        onChange={(e) => setPlayerSearch(e.target.value)}
        allowClear
        style={{ maxWidth: 400 }}
      />
      <Spin spinning={playersLoading}>
        {filteredPlayers.length === 0 ? (
          <Empty description={t('adminPlayersNoPlayers')} />
        ) : (
          <List
            dataSource={filteredPlayers}
            renderItem={(player) => (
              <List.Item
                actions={[
                  <Button
                    key="access"
                    size="small"
                    icon={<SafetyCertificateOutlined />}
                    onClick={() => openAccessDrawer(player.uuid, player.name)}
                  >
                    {t('adminPlayersAccess')}
                  </Button>,
                  meData?.ownerUuid !== player.uuid
                    ? player.disabled
                      ? (
                        <Popconfirm
                          key="enable"
                          title={t('adminPlayersConfirmEnable')}
                          onConfirm={() => handleEnablePlayer(player.uuid)}
                          okText={t('confirm')}
                          cancelText={t('cancel')}
                        >
                          <Button size="small" icon={<PlayCircleOutlined />} type="primary">
                            {t('adminPlayersEnable')}
                          </Button>
                        </Popconfirm>
                      )
                      : (
                        <Popconfirm
                          key="disable"
                          title={t('adminPlayersConfirmDisable')}
                          onConfirm={() => handleDisablePlayer(player.uuid)}
                          okText={t('confirm')}
                          cancelText={t('cancel')}
                        >
                          <Button size="small" icon={<StopOutlined />} danger>
                            {t('adminPlayersDisable')}
                          </Button>
                        </Popconfirm>
                      )
                    : null,
                  <Popconfirm
                    key="clear"
                    title={t('adminPlayersConfirmClear')}
                    onConfirm={() => handleClearPlayerCache(player.uuid)}
                    okText={t('confirm')}
                    cancelText={t('cancel')}
                  >
                    <Button size="small" icon={<DeleteOutlined />}>
                      {t('adminPlayersClearCache')}
                    </Button>
                  </Popconfirm>,
                ].filter(Boolean)}
              >
                <List.Item.Meta
                  avatar={
                    <Avatar icon={<UserOutlined />} style={{
                      backgroundColor: player.online ? '#52c41a' : '#d9d9d9',
                    }} />
                  }
                  title={
                    <Space>
                      <Text strong>{player.name}</Text>
                      <Text type="secondary" style={{ fontSize: '0.75rem' }}>{player.uuid.substring(0, 8)}…</Text>
                      <Tag color={player.online ? 'green' : 'default'}>
                        {player.online ? t('adminPlayersOnline') : t('adminPlayersOffline')}
                      </Tag>
                      {player.disabled && (
                        <Tag color="red">{t('adminPlayersDisabled')}</Tag>
                      )}
                    </Space>
                  }
                  description={
                    <Space size="middle" wrap>
                      <Text type="secondary">
                        <ApartmentOutlined /> {t('adminPlayersNetworks')}: {player.networkCount}
                      </Text>
                      <Text type="secondary">
                        <DatabaseOutlined /> {t('adminPlayersItems')}: {player.totalItems.toLocaleString()}
                      </Text>
                      <Text type="secondary">
                        <ExperimentOutlined /> {t('adminPlayersFluids')}: {player.totalFluids.toLocaleString()}
                      </Text>
                      <Text type="secondary">
                        {t('adminPlayersRequestCount')}: {player.requestCount.toLocaleString()}
                      </Text>
                      <Text type="secondary">
                        {t('adminPlayersAvgResponse')}: {player.avgResponseMs}ms
                      </Text>
                      {player.lastActiveAt > 0 && (
                        <Text type="secondary">
                          {t('adminPlayersLastActive')}: {new Date(player.lastActiveAt).toLocaleString()}
                        </Text>
                      )}
                    </Space>
                  }
                />
              </List.Item>
            )}
          />
        )}
      </Spin>

      <Drawer
        title={accessDrawerUuid ? `${t('adminPlayersAccess')} — ${accessDrawerName}` : t('adminPlayersAccess')}
        open={!!accessDrawerUuid}
        onClose={() => {
          setAccessDrawerUuid(null);
          setAccessData(null);
        }}
        width={720}
        destroyOnClose
      >
        <Spin spinning={accessLoading}>
          {!accessData ? (
            <Empty description={t('adminPlayersLoadingDetail')} />
          ) : (
            <Space direction="vertical" style={{ width: '100%' }} size="large">
              <div>
                <Text strong>{t('adminPlayersOwnedNetworks')}</Text>
                <Table
                  size="small"
                  style={{ marginTop: 8 }}
                  pagination={false}
                  rowKey="networkKey"
                  dataSource={accessData.ownedNetworks || []}
                  locale={{ emptyText: t('adminPlayersNoPlayers') }}
                  columns={[
                    {
                      title: 'ID',
                      dataIndex: 'networkId',
                      width: 48,
                    },
                    {
                      title: 'Key',
                      dataIndex: 'networkKey',
                      render: (v: string) => <Text code style={{ fontSize: 11 }}>{v}</Text>,
                    },
                    {
                      title: t('status'),
                      key: 'st',
                      render: (_: unknown, row: AdminOwnedNetwork) => (
                        <Space size={4}>
                          <Tag color={row.healthy ? 'green' : 'default'}>{row.healthy ? 'OK' : '—'}</Tag>
                          {row.suspended && <Tag color="red">{t('adminPlayersSuspended')}</Tag>}
                        </Space>
                      ),
                    },
                    {
                      title: t('adminPlayersAction'),
                      key: 'act',
                      render: (_: unknown, row: AdminOwnedNetwork) =>
                        row.suspended ? (
                          <Popconfirm
                            title={t('adminPlayersConfirmResume')}
                            onConfirm={() => handleResumeNet(row.networkKey)}
                          >
                            <Button size="small" type="link" icon={<PlayCircleOutlined />}>
                              {t('adminPlayersResume')}
                            </Button>
                          </Popconfirm>
                        ) : (
                          <Popconfirm
                            title={t('adminPlayersConfirmSuspend')}
                            onConfirm={() => handleSuspendNet(row.networkKey)}
                          >
                            <Button size="small" type="link" danger icon={<StopOutlined />}>
                              {t('adminPlayersSuspend')}
                            </Button>
                          </Popconfirm>
                        ),
                    },
                  ]}
                />
              </div>

              <div>
                <Text strong>{t('adminPlayersGuestAccess')}</Text>
                {(accessData.guestAccess || []).length === 0 ? (
                  <Empty style={{ marginTop: 8 }} description="—" />
                ) : (
                  (accessData.guestAccess || []).map((ga) => (
                    <Card
                      key={ga.token}
                      size="small"
                      style={{ marginTop: 8 }}
                      title={
                        <Space>
                          <Text>{ga.ownerName || ga.ownerUuid.substring(0, 8)}</Text>
                          <Text type="secondary" code>{ga.tokenPrefix}…</Text>
                        </Space>
                      }
                      extra={
                        <Popconfirm
                          title={t('adminPlayersRevokeGuestToken')}
                          onConfirm={async () => {
                            const ok = await revokeGuestToken(accessDrawerUuid!, ga.token);
                            if (ok) {
                              message.success(t('adminPlayersRevokeGuestSuccess'));
                              await reloadAccess();
                            } else message.error(t('adminPlayersOpFailed'));
                          }}
                        >
                          <Button size="small" danger icon={<DeleteOutlined />}>
                            {t('adminPlayersRevokeGuestToken')}
                          </Button>
                        </Popconfirm>
                      }
                    >
                      <Table
                        size="small"
                        pagination={false}
                        rowKey="networkKey"
                        dataSource={ga.networks || []}
                        columns={[
                          { title: 'Key', dataIndex: 'networkKey', render: (v: string) => <Text code style={{ fontSize: 11 }}>{v}</Text> },
                          {
                            title: t('status'),
                            key: 'flags',
                            render: (_: unknown, row: AdminGuestNetworkRow) => (
                              <Space size={4} wrap>
                                {!row.inAllowlist && <Tag>deny-allowlist</Tag>}
                                {row.deniedByAcl && <Tag color="orange">{t('adminPlayersDenyNet')}</Tag>}
                                {row.suspended && <Tag color="red">{t('adminPlayersSuspended')}</Tag>}
                              </Space>
                            ),
                          },
                          {
                            title: t('adminPlayersAction'),
                            key: 'acl',
                            render: (_: unknown, row: AdminGuestNetworkRow) =>
                              row.deniedByAcl ? (
                                <Button
                                  size="small"
                                  type="link"
                                  onClick={async () => {
                                    const ok = await setAcl(accessDrawerUuid!, ga.ownerUuid, row.networkKey, 'allow');
                                    if (ok) {
                                      message.success(t('adminPlayersAclSuccess'));
                                      await reloadAccess();
                                    } else message.error(t('adminPlayersOpFailed'));
                                  }}
                                >
                                  {t('adminPlayersAllowNet')}
                                </Button>
                              ) : (
                                <Button
                                  size="small"
                                  type="link"
                                  danger
                                  onClick={async () => {
                                    const ok = await setAcl(accessDrawerUuid!, ga.ownerUuid, row.networkKey, 'deny');
                                    if (ok) {
                                      message.success(t('adminPlayersAclSuccess'));
                                      await reloadAccess();
                                    } else message.error(t('adminPlayersOpFailed'));
                                  }}
                                >
                                  {t('adminPlayersDenyNet')}
                                </Button>
                              ),
                          },
                        ]}
                      />
                    </Card>
                  ))
                )}
              </div>
            </Space>
          )}
        </Spin>
      </Drawer>
    </Space>
  );

  // ---- Tab: Resources ----
  const renderResourceTab = () => (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Card title={<Space><GlobalOutlined />{t('adminPanelWorldMap')}</Space>}>
        <Space direction="vertical" size="small" style={{ width: '100%' }}>
          <Text type="secondary">{t('adminWorldMapInvalidateDesc')}</Text>
          <Button
            icon={<WarningOutlined />}
            danger
            loading={invalidateLoading}
            onClick={handleInvalidate}
            block
          >
            {t('adminInvalidateWorldMap')}
          </Button>
        </Space>
      </Card>

      <Card title={<Space><BookOutlined />{t('recipes')}</Space>} size="small">
        <Space direction="vertical" size="small" style={{ width: '100%' }}>
          <Text type="secondary">{t('adminRecipesCacheHint')}</Text>
          <Button
            icon={<ReloadOutlined />}
            loading={refreshLoading['patternRefresh']}
            onClick={() => runRefresh('/api/patterns/browse/refresh', 'patternRefresh')}
            block
          >
            {t('adminRefreshPatterns')}
          </Button>
        </Space>
      </Card>

      <Card title={<Space><CloudServerOutlined />{t('adminPanelIconPack')}</Space>} size="small">
        <Space direction="vertical" size="small" style={{ width: '100%' }}>
          <Text type="secondary">{t('adminIconPackHint')}</Text>
          <Button
            icon={<ReloadOutlined />}
            onClick={() => {
              window.location.hash = '#settings';
              window.dispatchEvent(new CustomEvent('webae-nav', { detail: { page: 'settings' } }));
            }}
            block
          >
            {t('adminGoToIconSettings')}
          </Button>
        </Space>
      </Card>
    </Space>
  );

  const tabItems = [
    {
      key: 'status',
      label: <Space size={4}><CrownOutlined />{t('adminTabStatus')}</Space>,
      children: renderStatusTab(),
    },
    {
      key: 'refresh',
      label: <Space size={4}><ToolOutlined />{t('adminTabRefresh')}</Space>,
      children: renderRefreshTab(),
    },
    {
      key: 'service',
      label: <Space size={4}><CloudServerOutlined />{t('adminTabService')}</Space>,
      children: renderServiceTab(),
    },
    {
      key: 'diagnostics',
      label: <Space size={4}><DashboardOutlined />{t('adminTabDiagnostics')}</Space>,
      children: renderDiagnosticsTab(),
    },
    ...(serverConfig?.sparkEnabled ? [{
      key: 'spark',
      label: <Space size={4}><LineChartOutlined />{t('adminTabSpark')}</Space>,
      children: <SparkProfilerTab />,
    }] : []),
    {
      key: 'qqbot',
      label: <Space size={4}><RobotOutlined />{t('adminTabQqBot')}</Space>,
      children: <QqBotPanel active={activeTab === 'qqbot'} />,
    },
    {
      key: 'console',
      label: <Space size={4}><CodeOutlined />{t('adminTabConsole')}</Space>,
      children: (
        <Suspense fallback={<Spin spinning />}>
          <ServerConsolePanel active={activeTab === 'console'} />
        </Suspense>
      ),
    },
    {
      key: 'players',
      label: <Space size={4}><UserSwitchOutlined />{t('adminTabPlayers')}</Space>,
      children: renderPlayersTab(),
    },
    {
      key: 'resources',
      label: <Space size={4}><GlobalOutlined />{t('adminTabResources')}</Space>,
      children: renderResourceTab(),
    },
  ];

  return (
    <PageShell
      title={t('adminPage')}
      description={t('adminPageDesc')}
    >
      {!isAdmin && !isOnlineOp ? (
        <Alert
          type="warning"
          message={t('adminAccessDenied')}
          description={t('adminAccessDeniedDesc')}
          showIcon
        />
      ) : (
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={tabItems}
          size="small"
        />
      )}
    </PageShell>
  );
}
