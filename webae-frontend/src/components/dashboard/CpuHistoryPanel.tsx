import { useCallback, useMemo, useRef, useState, type ReactNode } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Empty,
  Select,
  Skeleton,
  Space,
  Spin,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import { ReloadOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { useI18n } from '@/i18n';
import { useCpuHistory } from '@/hooks/useCpuHistory';
import { usePageActivePolling } from '@/hooks/usePageActivePolling';
import { ChartTrendSvg } from '@/components/dashboard/ChartTrendSvg';
import { DataPageSection } from '@/components/Layout/DataPageSection';
import type { CpuCapacityWindow, CpuJobHistoryDto } from '@/types/dto';
import { formatDateTime, formatDuration } from '@/utils/format';
import {
  CPU_CAPACITY_WINDOW_OPTIONS,
  CPU_HISTORY_STATUSES,
  CPU_HISTORY_TABLE_PAGE_SIZE,
  boundedCpuPercent,
  buildCpuBusyRatePoints,
  cpuCapacityBottleneckLabelKey,
  cpuCapacityRecommendationLabelKey,
  cpuCapacityWindowLabelKey,
  cpuHistoryIsTruncated,
  cpuHistoryStatusFilterKey,
  cpuHistoryStatusLabelKey,
  cpuHistoryStatusTone,
  filterCpuHistoryJobs,
  normalizeCpuCapacityWindow,
  normalizeCpuHistoryStatus,
  type CpuHistoryStatusFilter,
} from '@/utils/cpuHistoryPresentation';

const { Text } = Typography;

interface CpuHistoryPanelProps {
  /** The Dashboard always observes the first selected network. */
  networkId: number | null;
}

function CpuHistoryValue({ value }: { value: ReactNode }) {
  return <Text>{value ?? '\u2014'}</Text>;
}

/**
 * Read-only, network-wide CPU history and capacity panel for Dashboard.
 *
 * The server owns sampling and retention. The panel only reads the bounded
 * responses exposed by useCpuHistory and refreshes at a deliberately slow
 * dashboard cadence; it never scans AE2 or starts a client-side sampler.
 */
export function CpuHistoryPanel({ networkId }: CpuHistoryPanelProps) {
  const { t, lang } = useI18n();
  const [capacityWindow, setCapacityWindow] = useState<CpuCapacityWindow>('24h');
  const [jobStatusFilter, setJobStatusFilter] = useState<CpuHistoryStatusFilter>('all');
  const active = networkId != null;
  const historyState = useCpuHistory(networkId, active, capacityWindow);
  const skipInitialPollRef = useRef(true);

  const reloadOnPoll = useCallback(() => {
    // useCpuHistory already loads once when this panel mounts. The shared
    // page-active polling hook also fires immediately when enabled, so ignore
    // that first tick and avoid issuing the same two API requests twice.
    if (skipInitialPollRef.current) {
      skipInitialPollRef.current = false;
      return;
    }
    historyState.reload();
  }, [historyState.reload]);

  // Keep the Dashboard view current without adding a high-frequency request or
  // any AE2 work. The hook pauses when the page/tab is inactive.
  usePageActivePolling(reloadOnPoll, 30_000, 'dashboard');

  const jobs = useMemo(
    () => filterCpuHistoryJobs(historyState.history?.jobs, undefined, jobStatusFilter),
    [historyState.history?.jobs, jobStatusFilter]
  );
  const allJobs = useMemo(
    () => filterCpuHistoryJobs(historyState.history?.jobs, undefined, 'all'),
    [historyState.history?.jobs]
  );
  const busyRatePoints = useMemo(
    () => buildCpuBusyRatePoints(historyState.history?.snapshots),
    [historyState.history?.snapshots]
  );
  const hasUnknownJobs = useMemo(
    () => allJobs.some((job) => normalizeCpuHistoryStatus(job.status) === 'unknown'),
    [allJobs]
  );
  const statusOptions = useMemo(
    () => [
      { value: 'all', label: t(cpuHistoryStatusFilterKey('all')) },
      ...CPU_HISTORY_STATUSES.map((status) => ({
        value: status,
        label: t(cpuHistoryStatusLabelKey(status)),
      })),
    ],
    [t]
  );
  const handleCapacityWindowChange = useCallback((value: string) => {
    setCapacityWindow(normalizeCpuCapacityWindow(value));
  }, []);
  const handleJobStatusChange = useCallback((value: string) => {
    setJobStatusFilter(value as CpuHistoryStatusFilter);
  }, []);

  const durationText = useCallback(
    (value: number | null | undefined) => (value == null ? t('cpuCapacityUnknownValue') : formatDuration(value)),
    [t]
  );
  const percentText = useCallback(
    (value: number | null | undefined) => {
      const bounded = boundedCpuPercent(value);
      return bounded == null ? t('cpuCapacityUnknownValue') : `${Math.round(bounded)}%`;
    },
    [t]
  );
  const renderStatus = useCallback(
    (status: string) => {
      const normalized = normalizeCpuHistoryStatus(status);
      const tag = <Tag color={cpuHistoryStatusTone(normalized)}>{t(cpuHistoryStatusLabelKey(normalized))}</Tag>;
      return normalized === 'unknown'
        ? <Tooltip title={t('cpuHistoryUnknownStatusHint')}>{tag}</Tooltip>
        : tag;
    },
    [t]
  );
  const jobColumns = useMemo(
    () => [
      {
        title: t('status'),
        dataIndex: 'status',
        key: 'status',
        width: 108,
        render: (status: string) => renderStatus(status),
      },
      {
        title: t('cpuName'),
        dataIndex: 'cpuName',
        key: 'cpuName',
        width: 150,
        ellipsis: true,
      },
      {
        title: t('cpuHistoryRecipe'),
        dataIndex: 'recipeKey',
        key: 'recipeKey',
        width: 190,
        ellipsis: true,
        render: (recipeKey: string | undefined) => recipeKey || t('cpuHistoryUnknownRecipe'),
      },
      {
        title: t('cpuHistoryDuration'),
        dataIndex: 'durationMs',
        key: 'durationMs',
        width: 92,
        render: (value: number | null | undefined) => durationText(value),
      },
      {
        title: t('cpuHistoryQueue'),
        dataIndex: 'queueMs',
        key: 'queueMs',
        width: 92,
        render: (value: number | null | undefined) => durationText(value),
      },
      {
        title: t('cpuHistoryFinishedAt'),
        key: 'finishedAt',
        width: 132,
        render: (_value: unknown, job: CpuJobHistoryDto) =>
          formatDateTime(job.finishedAt || job.startedAt || job.queuedAt, lang),
      },
      {
        title: t('cpuHistoryProgress'),
        dataIndex: 'progress',
        key: 'progress',
        width: 82,
        render: (value: number | null | undefined) =>
          value == null ? t('cpuCapacityUnknownValue') : `${Math.max(0, Math.min(100, value))}%`,
      },
      {
        title: t('cpuHistoryCoProcessors'),
        dataIndex: 'coProcessors',
        key: 'coProcessors',
        width: 78,
        render: (value: number | null | undefined) =>
          value == null ? t('cpuCapacityUnknownValue') : `x${value}`,
      },
    ],
    [durationText, lang, renderStatus, t]
  );

  const historyUnavailable = historyState.historyError && !historyState.history;
  const capacityUnavailable = historyState.capacityError && !historyState.capacity;
  const initialLoading = historyState.loading && !historyState.history && !historyState.capacity;

  if (!active) return null;

  return (
    <DataPageSection
      className="cpu-history-dashboard-panel"
      title={t('cpuHistoryDashboardTitle')}
      description={t('cpuHistoryDashboardDesc')}
      eyebrow={t('cpuHistoryDashboardEyebrow')}
      icon={<ThunderboltOutlined />}
      actions={(
        <Space size="small" wrap>
          {historyState.loading && <Spin size="small" aria-label={t('loading')} />}
          <Button
            size="small"
            icon={<ReloadOutlined />}
            onClick={historyState.reload}
            aria-label={t('refresh')}
          >
            {t('refresh')}
          </Button>
        </Space>
      )}
    >
      {initialLoading ? (
        <Skeleton active paragraph={{ rows: 8 }} />
      ) : (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 420px), 1fr))',
            gap: 16,
            alignItems: 'start',
          }}
        >
          <Card
            size="small"
            title={t('cpuHistoryRecentJobs')}
            extra={(
              <Space size={[4, 4]} wrap>
                <Tag>{t('cpuHistoryJobCount', { n: allJobs.length })}</Tag>
                <Tag>{t('cpuHistorySnapshotCount', { n: historyState.history?.snapshots?.length ?? 0 })}</Tag>
                {historyState.history && cpuHistoryIsTruncated(historyState.history) && (
                  <Tag color="warning">{t('cpuHistoryTruncated')}</Tag>
                )}
              </Space>
            )}
          >
            {historyUnavailable ? (
              <Alert
                type="error"
                showIcon
                message={t('cpuHistoryLoadError')}
                description={t(historyState.historyError || 'cpuHistoryRequestFailed')}
                action={<Button size="small" onClick={historyState.reload}>{t('retry')}</Button>}
              />
            ) : historyState.history ? (
              <>
                {cpuHistoryIsTruncated(historyState.history) && (
                  <Alert
                    type="warning"
                    showIcon
                    message={t('cpuHistoryTruncated')}
                    style={{ marginBottom: 10 }}
                  />
                )}
                {hasUnknownJobs && (
                  <Alert
                    type="warning"
                    showIcon
                    message={t('cpuHistoryUnknownStatusHint')}
                    style={{ marginBottom: 10 }}
                  />
                )}
                <Space wrap size="small" style={{ marginBottom: 10 }}>
                  <Text type="secondary">{t('cpuHistoryStatusFilter')}</Text>
                  <Select
                    size="small"
                    value={jobStatusFilter}
                    onChange={handleJobStatusChange}
                    options={statusOptions}
                    aria-label={t('cpuHistoryStatusFilter')}
                    style={{ minWidth: 150 }}
                  />
                </Space>
                <Text strong style={{ display: 'block', marginBottom: 6 }}>
                  {t('cpuHistoryBusyRate')}
                </Text>
                {busyRatePoints.length >= 2 ? (
                  <ChartTrendSvg
                    series={[{
                      id: 'busy-rate',
                      label: t('cpuHistoryBusyRate'),
                      points: busyRatePoints,
                      lineColor: '#1677ff',
                      areaColor: 'rgba(22,119,255,0.16)',
                    }]}
                    formatValue={(value) => `${Math.round(value)}%`}
                    formatTime={(timestamp) => formatDateTime(timestamp, lang)}
                    showValueAxis
                    showTimeAxis
                    yDomain={[0, 100]}
                    stretchMode="stretchX"
                    className="cpu-history-trend"
                  />
                ) : (
                  <Text type="secondary">{t('cpuHistoryNoTrend')}</Text>
                )}
                <Table<CpuJobHistoryDto>
                  dataSource={jobs}
                  columns={jobColumns}
                  rowKey={(job, index) => job.jobId || `${job.cpuName}-${job.queuedAt}-${index}`}
                  size="small"
                  bordered
                  scroll={{ x: 980, y: 300 }}
                  pagination={{
                    pageSize: CPU_HISTORY_TABLE_PAGE_SIZE,
                    showSizeChanger: false,
                    hideOnSinglePage: false,
                  }}
                  locale={{
                    emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('cpuHistoryNoJobs')} />,
                  }}
                />
              </>
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('cpuHistoryNoJobs')} />
            )}
          </Card>

          <Card
            size="small"
            title={t('cpuCapacityTitle', { window: t(cpuCapacityWindowLabelKey(capacityWindow)) })}
            extra={(
              <Select
                size="small"
                value={capacityWindow}
                onChange={handleCapacityWindowChange}
                options={CPU_CAPACITY_WINDOW_OPTIONS.map((window) => ({
                  value: window,
                  label: t(cpuCapacityWindowLabelKey(window)),
                }))}
                aria-label={t('cpuCapacityWindow')}
                style={{ minWidth: 118 }}
              />
            )}
          >
            {capacityUnavailable ? (
              <Alert
                type="error"
                showIcon
                message={t('cpuCapacityUnavailable')}
                description={t(historyState.capacityError || 'cpuCapacityRequestFailed')}
                action={<Button size="small" onClick={historyState.reload}>{t('retry')}</Button>}
              />
            ) : historyState.capacity ? (
              <>
                <Descriptions size="small" bordered column={{ xs: 1, sm: 2 }}>
                  <Descriptions.Item label={t('cpuCapacityWindow')}>
                    {t(cpuCapacityWindowLabelKey(historyState.capacity.window))}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('cpuCapacityCurrent')}>
                    <CpuHistoryValue value={historyState.capacity.currentCpuCount} />
                  </Descriptions.Item>
                  <Descriptions.Item label={t('cpuCapacityPeak')}>
                    <CpuHistoryValue value={historyState.capacity.peakConcurrent} />
                  </Descriptions.Item>
                  <Descriptions.Item label={t('cpuCapacityP50Duration')}>
                    {durationText(historyState.capacity.p50DurationMs)}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('cpuCapacityP95Duration')}>
                    {durationText(historyState.capacity.p95DurationMs)}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('cpuCapacityP95Queue')}>
                    {durationText(historyState.capacity.p95QueueMs)}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('cpuCapacityBusyRatio')}>
                    {percentText(historyState.capacity.busyRatio)}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('cpuCapacityStoragePressure')}>
                    {percentText(historyState.capacity.storagePressure)}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('cpuCapacityStuck')}>
                    <CpuHistoryValue value={historyState.capacity.stuckCount} />
                  </Descriptions.Item>
                  <Descriptions.Item label={t('cpuCapacityCoProcessors')}>
                    <CpuHistoryValue value={historyState.capacity.coProcessorObservedMax} />
                  </Descriptions.Item>
                  <Descriptions.Item label={t('cpuCapacityRequired')}>
                    <CpuHistoryValue value={historyState.capacity.requiredCpuCountEstimate} />
                  </Descriptions.Item>
                </Descriptions>
                <div style={{ marginTop: 12 }}>
                  <Text type="secondary">{t('cpuCapacityBottlenecks')}</Text>
                  <div style={{ marginTop: 4 }}>
                    {(historyState.capacity.bottlenecks ?? []).length > 0 ? (
                      historyState.capacity.bottlenecks.map((code) => (
                        <Tag key={code} color="warning">{t(cpuCapacityBottleneckLabelKey(code))}</Tag>
                      ))
                    ) : (
                      <Text type="secondary">{t('cpuCapacityNoBottlenecks')}</Text>
                    )}
                  </div>
                </div>
                <div style={{ marginTop: 12 }}>
                  <Text type="secondary">{t('cpuCapacityRecommendations')}</Text>
                  {(historyState.capacity.recommendations ?? []).length > 0 ? (
                    <ul style={{ margin: '4px 0 0 18px', padding: 0 }}>
                      {historyState.capacity.recommendations.map((code) => (
                        <li key={code}>{t(cpuCapacityRecommendationLabelKey(code))}</li>
                      ))}
                    </ul>
                  ) : (
                    <div style={{ marginTop: 4 }}>
                      <Text type="secondary">{t('cpuCapacityNoRecommendations')}</Text>
                    </div>
                  )}
                </div>
              </>
            ) : historyState.loading ? (
              <Spin size="small" aria-label={t('loading')} />
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('cpuCapacityNoData')} />
            )}
          </Card>
        </div>
      )}
    </DataPageSection>
  );
}
