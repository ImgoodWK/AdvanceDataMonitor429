import { useCallback, useEffect, useMemo, useState, type Key } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Collapse,
  Descriptions,
  Empty,
  InputNumber,
  Modal,
  Popconfirm,
  Progress,
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  DeleteOutlined,
  EyeOutlined,
  LineChartOutlined,
  LinkOutlined,
  RobotOutlined,
  SyncOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { getApiClient } from '@/api/client';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { formatTime } from '@/utils/format';
import { completePersonalAiWithFailover, isPersonalAiStoreConfigured, loadPersonalAiStore, loadPreferredAiSource, resolveEffectiveAiSource } from '@/utils/personalAi';
import {
  buildSparkInsights,
  compareSparkCategories,
  compareSparkHotspots,
  formatSparkMethod,
  sparkAnalysisConfidence,
  type SparkAnalyzedProfile,
  type SparkCategoryImpact,
  type SparkHotspot,
  type SparkInsightSeverity,
  type SparkThreadImpact,
} from '@/utils/sparkAnalysis';

const { Text, Paragraph } = Typography;
type SparkMode = 'server' | 'lagSpikes' | 'allThreads';

interface SparkProfile extends SparkAnalyzedProfile {
  id: string;
  status: string;
  initiatedBy: string;
  startedAt: number;
  samplingStoppedAt: number;
  completedAt: number;
  durationSeconds: number;
  mode?: SparkMode;
  intervalMillis?: number;
  onlyTicksOverMillis?: number;
  includeAllThreads?: boolean;
  resultUrl?: string;
  error?: string;
  messages?: string[];
  baselineMessages?: string[];
  completionMessages?: string[];
  messageCount?: number;
  baselineMessageCount?: number;
  completionMessageCount?: number;
  hotspotCount?: number;
  categoryCount?: number;
  threadCount?: number;
}

interface SparkResponse {
  success: boolean;
  enabled: boolean;
  available: boolean;
  adminOnly: boolean;
  running: boolean;
  current?: SparkProfile | null;
  history: SparkProfile[];
  defaultDurationSeconds: number;
  maxDurationSeconds: number;
  minIntervalMillis: number;
  maxIntervalMillis: number;
  minTickThresholdMillis: number;
  maxTickThresholdMillis: number;
}

interface SparkAiResult {
  analysis: string;
  providerId: string;
  model: string;
  profileIds: string[];
  comparison: boolean;
  dataPolicy: string;
}

interface SparkAiPreparedRequest {
  systemPrompt: string;
  userPrompt: string;
  profileIds: string[];
  comparison: boolean;
  dataPolicy: string;
}

function statusColor(status: string): string {
  if (status === 'completed') return 'success';
  if (status === 'failed') return 'error';
  if (status === 'running' || status === 'stopping') return 'processing';
  return 'default';
}

function elapsed(profile: SparkProfile): string {
  return `${elapsedSeconds(profile)}s`;
}

function elapsedSeconds(profile: SparkProfile): number {
  const end = profile.samplingStoppedAt > 0
    ? profile.samplingStoppedAt
    : profile.completedAt > 0 ? profile.completedAt : Date.now();
  return Math.max(0, Math.round((end - profile.startedAt) / 1000));
}

function profileMode(profile: SparkProfile): SparkMode {
  return profile.mode || 'server';
}

function insightColor(severity: SparkInsightSeverity): string {
  if (severity === 'critical') return 'error';
  if (severity === 'warning') return 'warning';
  if (severity === 'healthy') return 'success';
  return 'processing';
}

function deltaColor(delta: number): string {
  if (delta > 0.5) return 'var(--ant-color-error)';
  if (delta < -0.5) return 'var(--ant-color-success)';
  return 'var(--ant-color-text-secondary)';
}

/** Spark controls rendered inside the admin console. */
export function SparkProfilerTab() {
  const { t } = useI18n();
  const { isAdmin, isLoggedIn, serverConfig, notify, lang, actorUuid } = useAppContext();
  const [data, setData] = useState<SparkResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [duration, setDuration] = useState(serverConfig?.sparkDefaultDurationSeconds ?? 30);
  const [mode, setMode] = useState<SparkMode>('server');
  const [intervalMillis, setIntervalMillis] = useState(4);
  const [tickThresholdMillis, setTickThresholdMillis] = useState(50);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [viewing, setViewing] = useState<SparkProfile | null>(null);
  const [detailLoadingId, setDetailLoadingId] = useState<string | null>(null);
  const [compareOpen, setCompareOpen] = useState(false);
  const [compareLoading, setCompareLoading] = useState(false);
  const [compareProfiles, setCompareProfiles] = useState<SparkProfile[]>([]);
  const [recoveringId, setRecoveringId] = useState<string | null>(null);
  const [aiAnalysis, setAiAnalysis] = useState<SparkAiResult | null>(null);
  const [aiAnalysisScope, setAiAnalysisScope] = useState('');
  const [aiAnalysisLoading, setAiAnalysisLoading] = useState(false);

  const load = useCallback(async () => {
    if (!isLoggedIn) return;
    try {
      const response = await getApiClient().get<SparkResponse>('/api/spark');
      if (response.success) {
        setData(response);
        setDuration((current) => current || response.defaultDurationSeconds);
      }
    } catch {
      setData(null);
    } finally {
      setLoading(false);
    }
  }, [isLoggedIn]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!data?.running) return undefined;
    const timer = window.setInterval(() => void load(), 3000);
    return () => window.clearInterval(timer);
  }, [data?.running, load]);

  const selectedProfiles = useMemo(
    () => selectedIds.map((id) => data?.history.find((profile) => profile.id === id)).filter(Boolean) as SparkProfile[],
    [data?.history, selectedIds]
  );
  const viewingInsights = useMemo(() => buildSparkInsights(viewing), [viewing]);
  const categoryComparison = useMemo(
    () => compareSparkCategories(compareProfiles[0], compareProfiles[1]),
    [compareProfiles]
  );
  const hotspotComparison = useMemo(
    () => compareSparkHotspots(compareProfiles[0], compareProfiles[1]),
    [compareProfiles]
  );

  const completedCount = data?.history.filter((profile) => !!profile.resultUrl).length ?? 0;
  const missingResultCount = data?.history.filter((profile) =>
    !profile.resultUrl && ['finished', 'stopped', 'interrupted'].includes(profile.status)
  ).length ?? 0;

  const changeMode = (nextMode: SparkMode) => {
    setMode(nextMode);
    setIntervalMillis(nextMode === 'allThreads' ? 10 : 4);
  };

  const start = async () => {
    try {
      await getApiClient().post('/api/spark/profile', {
        durationSeconds: duration,
        mode,
        intervalMillis,
        onlyTicksOverMillis: mode === 'lagSpikes' ? tickThresholdMillis : 0,
      });
      notify(t('sparkStarted'), 'success');
      await load();
    } catch {
      notify(t('sparkStartFailed'), 'error');
    }
  };

  const stop = async () => {
    try {
      await getApiClient().post('/api/spark/stop');
      notify(t('sparkStopRequested'), 'success');
      await load();
    } catch {
      notify(t('sparkStopFailed'), 'error');
    }
  };

  const remove = async (id: string) => {
    try {
      await getApiClient().delete(`/api/spark/history/${encodeURIComponent(id)}`);
      setSelectedIds((ids) => ids.filter((value) => value !== id));
      await load();
    } catch {
      notify(t('sparkDeleteFailed'), 'error');
    }
  };

  const openDetail = async (id: string) => {
    setAiAnalysis(null);
    setAiAnalysisScope('');
    setDetailLoadingId(id);
    try {
      const response = await getApiClient().get<{ success: boolean; profile: SparkProfile }>(
        `/api/spark/history/${encodeURIComponent(id)}`
      );
      setViewing(response.profile);
    } catch {
      notify(t('sparkDetailFailed'), 'error');
    } finally {
      setDetailLoadingId(null);
    }
  };

  const openCompare = async () => {
    if (selectedIds.length !== 2) return;
    setCompareOpen(true);
    setAiAnalysis(null);
    setAiAnalysisScope('');
    setCompareLoading(true);
    try {
      const responses = await Promise.all(selectedIds.map((id) =>
        getApiClient().get<{ success: boolean; profile: SparkProfile }>(
          `/api/spark/history/${encodeURIComponent(id)}`
        )
      ));
      setCompareProfiles(responses.map((response) => response.profile));
    } catch {
      setCompareProfiles([]);
      notify(t('sparkCompareFailed'), 'error');
    } finally {
      setCompareLoading(false);
    }
  };

  const runAiAnalysis = async (profileIds: string[], scope: string) => {
    setAiAnalysisScope(scope);
    setAiAnalysis(null);
    setAiAnalysisLoading(true);
    try {
      const response = await getApiClient().post<{
        success: boolean;
        result?: SparkAiResult;
        request?: SparkAiPreparedRequest;
      }>(
        '/api/spark/analyze',
        {
          profileIds,
          locale: lang === 'en' ? 'en_US' : 'zh_CN',
          aiSource: resolveEffectiveAiSource({
            serverEnabled: serverConfig?.webAiServerKeyEnabled ?? (serverConfig?.webAiKeyMode !== 'browser'),
            browserEnabled: serverConfig?.webAiBrowserKeyEnabled ?? (serverConfig?.webAiKeyMode === 'browser'),
            preferred: loadPreferredAiSource(
              undefined,
              actorUuid || '',
              (serverConfig?.webAiBrowserKeyEnabled ?? (serverConfig?.webAiKeyMode === 'browser'))
                && !(serverConfig?.webAiServerKeyEnabled ?? (serverConfig?.webAiKeyMode !== 'browser'))
                ? 'browser'
                : 'server'
            ),
            serverConfigured: serverConfig?.webAiShared?.configured,
            browserConfigured: isPersonalAiStoreConfigured(
              loadPersonalAiStore(serverConfig?.webAiProviders || [], undefined, actorUuid || '')
            ),
          }),
        }
      );
      if (response.request) {
        const store = loadPersonalAiStore(
          serverConfig?.webAiProviders || [],
          undefined,
          actorUuid || ''
        );
        if (!isPersonalAiStoreConfigured(store)) throw new Error(t('aiPersonalNotConfigured'));
        const completed = await completePersonalAiWithFailover(store, [
          { role: 'system', content: response.request.systemPrompt },
          { role: 'user', content: response.request.userPrompt },
        ]);
        const profile = store.profiles.find((item) => item.id === completed.profileId);
        setAiAnalysis({
          analysis: completed.content,
          providerId: profile?.providerId || 'personal',
          model: profile?.model || '',
          profileIds: response.request.profileIds,
          comparison: response.request.comparison,
          dataPolicy: response.request.dataPolicy,
        });
      } else if (response.result) {
        setAiAnalysis(response.result);
      } else {
        throw new Error(t('sparkAiFailed'));
      }
      setAiAnalysisScope(scope);
    } catch (error) {
      notify((error as Error).message || t('sparkAiFailed'), 'error');
    } finally {
      setAiAnalysisLoading(false);
    }
  };

  const recover = async (id: string) => {
    setRecoveringId(id);
    try {
      const response = await getApiClient().post<{ success: boolean; recovered: boolean; profile: SparkProfile }>(
        '/api/spark/recover',
        { id }
      );
      notify(t(response.recovered ? 'sparkRecoverSuccess' : 'sparkRecoverNotFound'), response.recovered ? 'success' : 'warning');
      if (response.recovered) {
        setViewing((current) => current?.id === id ? response.profile : current);
      }
      await load();
    } catch {
      notify(t('sparkRecoverFailed'), 'error');
    } finally {
      setRecoveringId(null);
    }
  };

  const columns = [
    {
      title: t('sparkStatus'),
      dataIndex: 'status',
      width: 120,
      filters: ['completed', 'running', 'stopping', 'finished', 'stopped', 'failed', 'interrupted'].map((value) => ({
        text: t(`sparkStatus_${value}`),
        value,
      })),
      onFilter: (value: boolean | Key, row: SparkProfile) => row.status === value,
      render: (value: string) => <Tag color={statusColor(value)}>{t(`sparkStatus_${value}`)}</Tag>,
    },
    {
      title: t('sparkMode'),
      key: 'mode',
      width: 130,
      filters: (['server', 'lagSpikes', 'allThreads'] as SparkMode[]).map((value) => ({
        text: t(`sparkMode_${value}`),
        value,
      })),
      onFilter: (value: boolean | Key, row: SparkProfile) => profileMode(row) === value,
      render: (_: unknown, row: SparkProfile) => <Tag>{t(`sparkMode_${profileMode(row)}`)}</Tag>,
    },
    {
      title: t('sparkStartedAt'),
      dataIndex: 'startedAt',
      width: 180,
      render: (value: number) => <Text type="secondary">{formatTime(value)}</Text>,
    },
    { title: t('sparkInitiatedBy'), dataIndex: 'initiatedBy', width: 140 },
    {
      title: t('sparkDuration'),
      key: 'duration',
      width: 110,
      render: (_: unknown, row: SparkProfile) => elapsed(row),
    },
    {
      title: t('sparkBuiltInAnalysis'),
      key: 'analysis',
      width: 150,
      render: (_: unknown, row: SparkProfile) => (
        <Tag color={row.analysisStatus === 'ready' ? 'success' : row.analysisStatus === 'pending' ? 'processing' : 'default'}>
          {row.analysisStatus === 'ready'
            ? t('sparkHotspotCount', { count: row.hotspotCount ?? 0 })
            : t(`sparkAnalysisStatus_${row.analysisStatus || 'legacy'}`)}
        </Tag>
      ),
    },
    {
      title: t('sparkResult'),
      key: 'result',
      render: (_: unknown, row: SparkProfile) =>
        row.resultUrl ? (
          <a href={row.resultUrl} target="_blank" rel="noreferrer">
            <LinkOutlined /> {t('sparkOpenViewer')}
          </a>
        ) : row.status === 'running' ? (
          <Text type="secondary">{t('sparkCollecting')}</Text>
        ) : row.status === 'stopping' ? (
          <Text type="secondary"><SyncOutlined spin /> {t('sparkUploading')}</Text>
        ) : (
          <Space size="small" wrap>
            <Text type={row.error ? 'danger' : 'secondary'}>{row.error || t('sparkNoResult')}</Text>
            {isAdmin && !row.error && (
              <Button
                size="small"
                icon={<SyncOutlined spin={recoveringId === row.id} />}
                loading={recoveringId === row.id}
                onClick={() => void recover(row.id)}
              >
                {t('sparkRecover')}
              </Button>
            )}
          </Space>
        ),
    },
    {
      title: t('actions'),
      key: 'actions',
      width: 150,
      render: (_: unknown, row: SparkProfile) => (
        <Space size="small">
          <Button
            size="small"
            icon={<EyeOutlined />}
            loading={detailLoadingId === row.id}
            onClick={() => void openDetail(row.id)}
          >
            {t('view')}
          </Button>
          {isAdmin && (
            <Popconfirm title={t('sparkDeleteConfirm')} onConfirm={() => void remove(row.id)}>
              <Button
                size="small"
                danger
                icon={<DeleteOutlined />}
                aria-label={t('delete')}
                disabled={['running', 'stopping'].includes(row.status)}
              />
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 12 }}>
        <Button onClick={() => void load()}>{t('refresh')}</Button>
      </div>
      <Spin spinning={loading && !data}>
        {!data ? (
          <Alert type="warning" showIcon message={t('sparkUnavailable')} />
        ) : (
          <>
            <Alert
              type="info"
              showIcon
              icon={<LineChartOutlined />}
              message={t('sparkSafetyNote')}
              description={(
                <Space direction="vertical" size={2}>
                  <Text>{isAdmin ? t('sparkAdminHint') : t('sparkReadOnlyHint')}</Text>
                  <Text type="secondary">{t('sparkIdleCostNote')}</Text>
                </Space>
              )}
              style={{ marginBottom: 16 }}
            />

            <Row gutter={[16, 16]}>
              <Col xs={24} lg={10}>
                <Card size="small" title={t('sparkRunCard')}>
                  <Space direction="vertical" style={{ width: '100%' }}>
                    <Text strong>{t('sparkMode')}</Text>
                    <Select<SparkMode>
                      value={mode}
                      onChange={changeMode}
                      disabled={!isAdmin || data.running}
                      aria-label={t('sparkMode')}
                      options={(['server', 'lagSpikes', 'allThreads'] as SparkMode[]).map((value) => ({
                        value,
                        label: t(`sparkMode_${value}`),
                      }))}
                    />
                    <Text type="secondary">{t(`sparkMode_${mode}_hint`)}</Text>

                    <Text type="secondary">{t('sparkDurationHint', { max: data.maxDurationSeconds })}</Text>
                    <InputNumber
                      min={5}
                      max={data.maxDurationSeconds}
                      value={duration}
                      onChange={(value) => setDuration(value ?? data.defaultDurationSeconds)}
                      addonAfter="s"
                      style={{ width: '100%' }}
                      disabled={!isAdmin || data.running}
                      aria-label={t('sparkDuration')}
                    />
                    <Text type="secondary">{t('sparkIntervalHint')}</Text>
                    <InputNumber
                      min={mode === 'allThreads' ? 10 : (data.minIntervalMillis ?? 2)}
                      max={data.maxIntervalMillis ?? 100}
                      value={intervalMillis}
                      onChange={(value) => setIntervalMillis(
                        mode === 'allThreads'
                          ? Math.max(10, value ?? 10)
                          : value ?? 4
                      )}
                      addonAfter="ms"
                      style={{ width: '100%' }}
                      disabled={!isAdmin || data.running}
                      aria-label={t('sparkInterval')}
                    />
                    {mode === 'lagSpikes' && (
                      <>
                        <Text type="secondary">{t('sparkTickThresholdHint')}</Text>
                        <InputNumber
                          min={data.minTickThresholdMillis ?? 25}
                          max={data.maxTickThresholdMillis ?? 1000}
                          value={tickThresholdMillis}
                          onChange={(value) => setTickThresholdMillis(value ?? 50)}
                          addonAfter="ms"
                          style={{ width: '100%' }}
                          disabled={!isAdmin || data.running}
                          aria-label={t('sparkTickThreshold')}
                        />
                      </>
                    )}
                    <Tag color={mode === 'allThreads' ? 'warning' : mode === 'lagSpikes' ? 'processing' : 'success'}>
                      {t(`sparkOverhead_${mode}`)}
                    </Tag>
                    <Space>
                      <Button type="primary" onClick={() => void start()} disabled={!isAdmin || data.running}>
                        {t('sparkStart')}
                      </Button>
                      <Button icon={<StopOutlined />} onClick={() => void stop()} disabled={!isAdmin || !data.running}>
                        {t('sparkStop')}
                      </Button>
                    </Space>
                  </Space>
                </Card>
              </Col>
              <Col xs={24} sm={12} lg={7}>
                <Card size="small" title={t('sparkCurrentRun')}>
                  {data.current ? (
                    <Space direction="vertical" style={{ width: '100%' }}>
                      <Space wrap>
                        <Tag color={statusColor(data.current.status)}>{t(`sparkStatus_${data.current.status}`)}</Tag>
                        <Tag>{t(`sparkMode_${profileMode(data.current)}`)}</Tag>
                      </Space>
                      <Text>{t('sparkElapsed')}: {elapsed(data.current)}</Text>
                      <Progress
                        size="small"
                        percent={Math.min(100, Math.round(elapsedSeconds(data.current) * 100 / Math.max(1, data.current.durationSeconds)))}
                        status={data.current.status === 'stopping' ? 'active' : 'normal'}
                      />
                      <Text type="secondary">
                        {t('sparkEffectiveConfig', {
                          interval: data.current.intervalMillis || 4,
                          threshold: data.current.onlyTicksOverMillis || '—',
                        })}
                      </Text>
                    </Space>
                  ) : (
                    <Text type="secondary">{t('sparkIdle')}</Text>
                  )}
                </Card>
              </Col>
              <Col xs={24} sm={12} lg={7}>
                <Card size="small" title={t('sparkHistoryCount')}>
                  <Row gutter={8}>
                    <Col span={8}><Statistic value={data.history.length} title={t('sparkHistoryTotal')} /></Col>
                    <Col span={8}><Statistic value={completedCount} title={t('sparkHistoryLinked')} valueStyle={{ color: 'var(--ant-color-success)' }} /></Col>
                    <Col span={8}><Statistic value={missingResultCount} title={t('sparkHistoryMissing')} valueStyle={missingResultCount ? { color: 'var(--ant-color-warning)' } : undefined} /></Col>
                  </Row>
                </Card>
              </Col>
            </Row>

            <Card
              size="small"
              title={t('sparkHistory')}
              style={{ marginTop: 16 }}
              extra={
                <Button disabled={selectedProfiles.length !== 2} onClick={() => void openCompare()}>
                  {t('sparkCompare')} ({selectedProfiles.length}/2)
                </Button>
              }
            >
              {data.history.length === 0 ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('sparkHistoryEmpty')} />
              ) : (
                <Table
                  size="small"
                  rowKey="id"
                  dataSource={data.history}
                  columns={columns}
                  scroll={{ x: 1230 }}
                  rowSelection={{
                    selectedRowKeys: selectedIds,
                    onChange: (keys: Key[]) => setSelectedIds(keys.slice(-2) as string[]),
                  }}
                  pagination={{ pageSize: 20, showSizeChanger: false }}
                />
              )}
            </Card>
          </>
        )}
      </Spin>

      <Modal
        open={!!viewing}
        title={t('sparkDetail')}
        onCancel={() => setViewing(null)}
        footer={null}
        width={1120}
        styles={{ body: { maxHeight: '78vh', overflowY: 'auto' } }}
      >
        {viewing && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <Descriptions size="small" bordered column={{ xs: 1, sm: 2 }}>
              <Descriptions.Item label={t('sparkStatus')}>
                <Tag color={statusColor(viewing.status)}>{t(`sparkStatus_${viewing.status}`)}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label={t('sparkMode')}>
                {t(`sparkMode_${profileMode(viewing)}`)}
              </Descriptions.Item>
              <Descriptions.Item label={t('sparkStartedAt')}>{formatTime(viewing.startedAt)}</Descriptions.Item>
              <Descriptions.Item label={t('sparkInitiatedBy')}>{viewing.initiatedBy}</Descriptions.Item>
              <Descriptions.Item label={t('sparkDuration')}>{elapsed(viewing)} / {viewing.durationSeconds}s</Descriptions.Item>
              <Descriptions.Item label={t('sparkInterval')}>{viewing.intervalMillis || 4}ms</Descriptions.Item>
              {profileMode(viewing) === 'lagSpikes' && (
                <Descriptions.Item label={t('sparkTickThreshold')}>{viewing.onlyTicksOverMillis || 50}ms</Descriptions.Item>
              )}
            </Descriptions>
            {viewing.error && <Alert type="error" showIcon message={viewing.error} />}

            {viewing.analysisStatus === 'ready' ? (
              <>
                <Row gutter={[12, 12]}>
                  <Col xs={12} md={6}>
                    <Card size="small"><Statistic title={t('sparkSamples')} value={viewing.sampleCount || 0} /></Card>
                  </Col>
                  <Col xs={12} md={6}>
                    <Card size="small"><Statistic title={t('sparkSampledTime')} value={viewing.sampledTimeMillis || 0} precision={0} suffix="ms" /></Card>
                  </Col>
                  <Col xs={12} md={6}>
                    <Card size="small"><Statistic title={t('sparkAnalyzedNodes')} value={viewing.analyzedNodeCount || 0} /></Card>
                  </Col>
                  <Col xs={12} md={6}>
                    <Card size="small"><Statistic title={t('sparkThreadGroups')} value={(viewing.threads || []).length} /></Card>
                  </Col>
                </Row>

                <Card
                  size="small"
                  title={t('sparkSmartAnalysis')}
                  extra={(
                    <Space wrap>
                      <Tag>{t(`sparkConfidence_${sparkAnalysisConfidence(viewing.sampleCount)}`)}</Tag>
                      {isAdmin && (
                        <Button
                          size="small"
                          icon={<RobotOutlined />}
                          loading={aiAnalysisLoading && aiAnalysisScope === viewing.id}
                          onClick={() => void runAiAnalysis([viewing.id], viewing.id)}
                        >
                          {t('sparkAiAnalyze')}
                        </Button>
                      )}
                    </Space>
                  )}
                >
                  <Alert
                    type={viewingInsights[0]?.severity === 'critical' ? 'error' : viewingInsights[0]?.severity === 'warning' ? 'warning' : 'info'}
                    showIcon
                    message={t('sparkSmartAnalysisSummary')}
                    description={t('sparkSmartAnalysisExplain')}
                    style={{ marginBottom: 12 }}
                  />
                  <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                    {viewingInsights.map((insight) => (
                      <div key={insight.category}>
                        <Space wrap>
                          <Tag color={insightColor(insight.severity)}>{t(`sparkInsightSeverity_${insight.severity}`)}</Tag>
                          <Text strong>{t(`sparkCategory_${insight.category}`)}</Text>
                          {insight.percent > 0 && <Text>{insight.percent.toFixed(1)}%</Text>}
                        </Space>
                        <div>
                          <Text type="secondary">
                            {insight.category === 'balanced'
                              ? t('sparkInsightBalanced')
                              : t('sparkInsightEvidence', { method: insight.evidence })}
                          </Text>
                        </div>
                        <div><Text>{t(`sparkRecommendation_${insight.category}`)}</Text></div>
                      </div>
                    ))}
                  </Space>
                  {aiAnalysis && aiAnalysisScope === viewing.id && (
                    <Alert
                      style={{ marginTop: 16 }}
                      type="info"
                      showIcon
                      icon={<RobotOutlined />}
                      message={`${t('sparkAiResult')} · ${aiAnalysis.providerId}/${aiAnalysis.model}`}
                      description={<Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>{aiAnalysis.analysis}</Paragraph>}
                    />
                  )}
                </Card>

                <Row gutter={[12, 12]}>
                  <Col xs={24} lg={14}>
                    <Card size="small" title={t('sparkImpactCategories')}>
                      <Table<SparkCategoryImpact>
                        size="small"
                        pagination={false}
                        rowKey="id"
                        dataSource={viewing.categories || []}
                        columns={[
                          { title: t('sparkImpactCategory'), dataIndex: 'id', width: 150, render: (value: string) => t(`sparkCategory_${value}`) },
                          {
                            title: t('sparkImpactShare'), dataIndex: 'percent', width: 190,
                            render: (value: number) => <Progress percent={Math.min(100, Math.round(value))} size="small" />,
                          },
                          {
                            title: t('sparkEvidenceMethod'), key: 'method', ellipsis: true,
                            render: (_: unknown, row: SparkCategoryImpact) => (
                              <Text code title={formatSparkMethod(row.topClassName, row.topMethodName)}>
                                {formatSparkMethod(row.topClassName, row.topMethodName)}
                              </Text>
                            ),
                          },
                        ]}
                      />
                    </Card>
                  </Col>
                  <Col xs={24} lg={10}>
                    <Card size="small" title={t('sparkThreadBreakdown')}>
                      <Table<SparkThreadImpact>
                        size="small"
                        pagination={false}
                        rowKey="name"
                        dataSource={viewing.threads || []}
                        columns={[
                          { title: t('sparkThread'), dataIndex: 'name', ellipsis: true },
                          { title: t('sparkImpactShare'), dataIndex: 'percent', width: 90, render: (value: number) => `${value.toFixed(1)}%` },
                        ]}
                      />
                    </Card>
                  </Col>
                </Row>

                <Card size="small" title={t('sparkMethodHotspots')}>
                  <Table<SparkHotspot>
                    size="small"
                    rowKey={(row) => `${row.className}\n${row.methodName}`}
                    dataSource={viewing.hotspots || []}
                    scroll={{ x: 980 }}
                    pagination={{ pageSize: 12, showSizeChanger: false }}
                    columns={[
                      {
                        title: t('sparkHotMethod'), key: 'method', width: 410, ellipsis: true,
                        render: (_: unknown, row: SparkHotspot) => (
                          <Text code copyable={{ text: formatSparkMethod(row.className, row.methodName) }}>
                            {formatSparkMethod(row.className, row.methodName)}{row.lineNumber >= 0 ? `:${row.lineNumber}` : ''}
                          </Text>
                        ),
                      },
                      { title: t('sparkImpactCategory'), dataIndex: 'category', width: 150, render: (value: string) => t(`sparkCategory_${value}`) },
                      {
                        title: t('sparkSelfShare'), dataIndex: 'percent', width: 180,
                        render: (value: number) => <Progress percent={Math.min(100, Math.round(value))} size="small" />,
                      },
                      { title: t('sparkSelfTime'), dataIndex: 'selfTimeMillis', width: 110, render: (value: number) => `${value.toFixed(1)}ms` },
                      { title: t('sparkDominantThread'), dataIndex: 'dominantThread', width: 150, ellipsis: true },
                    ]}
                  />
                </Card>
              </>
            ) : (
              <Alert
                type={viewing.analysisStatus === 'pending' ? 'info' : 'warning'}
                showIcon
                message={t(`sparkAnalysisStatus_${viewing.analysisStatus || 'legacy'}`)}
                description={t(`sparkAnalysisHelp_${viewing.analysisStatus || 'legacy'}`)}
              />
            )}

            <Collapse
              size="small"
              items={[{
                key: 'raw',
                label: t('sparkRawAndViewer'),
                children: (
                  <Space direction="vertical" style={{ width: '100%' }}>
                    <Space wrap>
                      {viewing.resultUrl && (
                        <Button type="link" href={viewing.resultUrl} target="_blank" rel="noreferrer" icon={<LinkOutlined />}>
                          {t('sparkOpenViewer')}
                        </Button>
                      )}
                      {!viewing.resultUrl && isAdmin && !['running', 'stopping'].includes(viewing.status) && !viewing.error && (
                        <Button
                          icon={<SyncOutlined spin={recoveringId === viewing.id} />}
                          loading={recoveringId === viewing.id}
                          onClick={() => void recover(viewing.id)}
                        >
                          {t('sparkRecover')}
                        </Button>
                      )}
                    </Space>
                    <Row gutter={[12, 12]}>
                      <Col xs={24} md={12}>
                        <Card size="small" title={t('sparkBaseline')}>
                          <Paragraph copyable={{ text: (viewing.baselineMessages || []).join('\n') }} style={{ maxHeight: 180, overflow: 'auto', whiteSpace: 'pre-wrap', marginBottom: 0 }}>
                            {(viewing.baselineMessages || []).join('\n') || t('sparkNoContextOutput')}
                          </Paragraph>
                        </Card>
                      </Col>
                      <Col xs={24} md={12}>
                        <Card size="small" title={t('sparkCompletionSnapshot')}>
                          <Paragraph copyable={{ text: (viewing.completionMessages || []).join('\n') }} style={{ maxHeight: 180, overflow: 'auto', whiteSpace: 'pre-wrap', marginBottom: 0 }}>
                            {(viewing.completionMessages || []).join('\n') || t('sparkNoContextOutput')}
                          </Paragraph>
                        </Card>
                      </Col>
                    </Row>
                    <Text strong>{t('sparkFullOutput')}</Text>
                    <Paragraph code copyable={{ text: (viewing.messages || []).join('\n') }} style={{ maxHeight: 300, overflow: 'auto', whiteSpace: 'pre-wrap' }}>
                      {(viewing.messages || []).join('\n') || t('sparkNoOutput')}
                    </Paragraph>
                  </Space>
                ),
              }]}
            />
          </Space>
        )}
      </Modal>

      <Modal
        open={compareOpen}
        title={t('sparkCompare')}
        onCancel={() => setCompareOpen(false)}
        footer={null}
        width={1060}
        styles={{ body: { maxHeight: '78vh', overflowY: 'auto' } }}
      >
        <Spin spinning={compareLoading}>
          {compareProfiles.length === 2 && (
            <Space direction="vertical" style={{ width: '100%' }}>
              <Alert type="info" showIcon message={t('sparkCompareDeltaHelp')} />
              {isAdmin && (
                <Space wrap>
                  <Button
                    type="primary"
                    icon={<RobotOutlined />}
                    loading={aiAnalysisLoading && aiAnalysisScope === compareProfiles.map((profile) => profile.id).join(':')}
                    onClick={() => {
                      const ids = compareProfiles.map((profile) => profile.id);
                      void runAiAnalysis(ids, ids.join(':'));
                    }}
                  >
                    {t('sparkAiCompare')}
                  </Button>
                  <Text type="secondary">{t('sparkAiDataHint')}</Text>
                </Space>
              )}
              {aiAnalysis && aiAnalysisScope === compareProfiles.map((profile) => profile.id).join(':') && (
                <Alert
                  type="info"
                  showIcon
                  icon={<RobotOutlined />}
                  message={`${t('sparkAiResult')} · ${aiAnalysis.providerId}/${aiAnalysis.model}`}
                  description={<Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>{aiAnalysis.analysis}</Paragraph>}
                />
              )}
              <Table
                size="small"
                pagination={false}
                rowKey="metric"
                dataSource={[
                  { metric: t('sparkCompareStatus'), a: t(`sparkStatus_${compareProfiles[0].status}`), b: t(`sparkStatus_${compareProfiles[1].status}`) },
                  { metric: t('sparkCompareMode'), a: t(`sparkMode_${profileMode(compareProfiles[0])}`), b: t(`sparkMode_${profileMode(compareProfiles[1])}`) },
                  { metric: t('sparkCompareStartedAt'), a: formatTime(compareProfiles[0].startedAt), b: formatTime(compareProfiles[1].startedAt) },
                  { metric: t('sparkCompareElapsed'), a: elapsed(compareProfiles[0]), b: elapsed(compareProfiles[1]) },
                  { metric: t('sparkCompareInterval'), a: `${compareProfiles[0].intervalMillis || 4}ms`, b: `${compareProfiles[1].intervalMillis || 4}ms` },
                  { metric: t('sparkSamples'), a: compareProfiles[0].sampleCount || 0, b: compareProfiles[1].sampleCount || 0 },
                ]}
                columns={[
                  { title: t('sparkCompareMetric'), dataIndex: 'metric', width: 180 },
                  { title: t('sparkCompareA'), dataIndex: 'a', ellipsis: true },
                  { title: t('sparkCompareB'), dataIndex: 'b', ellipsis: true },
                ]}
              />

              {compareProfiles.every((profile) => profile.analysisStatus === 'ready') ? (
                <>
                  <Card size="small" title={t('sparkCompareCategories')}>
                    <Table
                      size="small"
                      pagination={false}
                      rowKey="id"
                      dataSource={categoryComparison}
                      columns={[
                        { title: t('sparkImpactCategory'), dataIndex: 'id', render: (value: string) => t(`sparkCategory_${value}`) },
                        { title: t('sparkCompareA'), dataIndex: 'a', width: 120, render: (value: number) => `${value.toFixed(1)}%` },
                        { title: t('sparkCompareB'), dataIndex: 'b', width: 120, render: (value: number) => `${value.toFixed(1)}%` },
                        {
                          title: t('sparkCompareDelta'), dataIndex: 'delta', width: 130,
                          render: (value: number) => <Text style={{ color: deltaColor(value) }}>{value > 0 ? '+' : ''}{value.toFixed(1)}%</Text>,
                        },
                      ]}
                    />
                  </Card>
                  <Card size="small" title={t('sparkCompareHotspots')}>
                    <Table
                      size="small"
                      rowKey="key"
                      dataSource={hotspotComparison}
                      pagination={{ pageSize: 10, showSizeChanger: false }}
                      scroll={{ x: 780 }}
                      columns={[
                        {
                          title: t('sparkHotMethod'), key: 'method', ellipsis: true,
                          render: (_: unknown, row) => (
                            <Text code title={formatSparkMethod(row.className, row.methodName)}>
                              {formatSparkMethod(row.className, row.methodName)}
                            </Text>
                          ),
                        },
                        { title: t('sparkCompareA'), dataIndex: 'a', width: 120, render: (value: number) => `${value.toFixed(1)}%` },
                        { title: t('sparkCompareB'), dataIndex: 'b', width: 120, render: (value: number) => `${value.toFixed(1)}%` },
                        {
                          title: t('sparkCompareDelta'), dataIndex: 'delta', width: 130,
                          render: (value: number) => <Text style={{ color: deltaColor(value) }}>{value > 0 ? '+' : ''}{value.toFixed(1)}%</Text>,
                        },
                      ]}
                    />
                  </Card>
                </>
              ) : (
                <Alert type="warning" showIcon message={t('sparkCompareAnalysisUnavailable')} />
              )}
            </Space>
          )}
        </Spin>
      </Modal>
    </>
  );
}
