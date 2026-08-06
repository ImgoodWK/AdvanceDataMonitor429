import { useCallback, useEffect, useMemo, useState } from 'react';
import { Tabs, Table, Input, Empty, Card, Drawer, Descriptions, Progress, Spin, Tag, Typography, Alert, Button, Select, Space, Tooltip } from 'antd';
import { SearchOutlined, UnorderedListOutlined } from '@ant-design/icons';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { formatNetworkOptionLabel } from '@/utils/networkHealth';
import { useSnapshotData } from '@/hooks/useSnapshotData';
import { useCpuHistory } from '@/hooks/useCpuHistory';
import { useNumberFormat } from '@/hooks/useNumberFormat';
import { PageShell } from '@/components/Layout/PageShell';
import { DataPageSection } from '@/components/Layout/DataPageSection';
import { OverviewWidgetGrid } from '@/components/dashboard/OverviewWidgetGrid';
import {
  DEFAULT_CPU_OVERVIEW_SETTINGS,
  CPU_OVERVIEW_CONFIG_KEY,
} from '@/utils/presets';
import { CPU_OVERVIEW_DATA_SOURCES } from '@/utils/overviewDataSources';
import type { OverviewSnapshot } from '@/utils/overviewDataSources';
import type { StorageCpu, StorageDto } from '@/types/dto';
import { useCpuColumns, cpuRowKey, estimateRemainingMs } from '@/utils/cpuColumns';
import { formatBytes, formatDateTime, formatDuration } from '@/utils/format';
import { ChartTrendSvg } from '@/components/dashboard/ChartTrendSvg';
import type { CpuCapacityWindow, CpuJobHistoryDto } from '@/types/dto';
import {
  CPU_CAPACITY_WINDOW_OPTIONS,
  CPU_HISTORY_TABLE_PAGE_SIZE,
  CPU_HISTORY_STATUSES,
  boundedCpuPercent,
  buildCpuBusyRatePoints,
  cpuCapacityBottleneckLabelKey,
  cpuCapacityRecommendationLabelKey,
  cpuCapacityWindowLabelKey,
  cpuHistoryStatusFilterKey,
  cpuHistoryStatusLabelKey,
  cpuHistoryStatusTone,
  cpuHistoryIsTruncated,
  filterCpuHistoryJobs,
  normalizeCpuCapacityWindow,
  normalizeCpuHistoryStatus,
  type CpuHistoryStatusFilter,
} from '@/utils/cpuHistoryPresentation';

const { Text } = Typography;

function mergeCpusFromStorages(
  storages: StorageDto[],
  displayMode: 'split' | 'merged',
  networkIds: number[]
): { snapshot: OverviewSnapshot; cpus: StorageCpu[]; perNetwork: Array<{ id: number; cpus: StorageCpu[] }> } {
  if (displayMode === 'merged') {
    const allCpus: StorageCpu[] = [];
    let bytesUsed = 0;
    let bytesMax = 0;
    for (let i = 0; i < storages.length; i++) {
      const s = storages[i];
      const nid = networkIds[i] ?? s.networkId;
      bytesUsed += s.bytesUsed || 0;
      bytesMax += s.bytesMax || 0;
      for (const c of s.cpus || []) {
        allCpus.push({ ...c, networkId: nid });
      }
    }
    return {
      snapshot: {
        items: [],
        fluids: [],
        essentia: [],
        bytesUsed,
        bytesMax,
        cpus: allCpus,
      },
      cpus: allCpus,
      perNetwork: networkIds.map((id, i) => ({
        id,
        cpus: (storages[i]?.cpus || []).map((c) => ({ ...c, networkId: id })),
      })),
    };
  }
  const first = storages[0];
  const nid = networkIds[0] ?? first?.networkId ?? 0;
  const cpus = (first?.cpus || []).map((c) => ({ ...c, networkId: nid }));
  return {
    snapshot: {
      items: first?.items,
      fluids: first?.fluids,
      essentia: first?.essentia,
      bytesUsed: first?.bytesUsed || 0,
      bytesMax: first?.bytesMax || 0,
      cpus,
    },
    cpus,
    perNetwork: [{ id: nid, cpus }],
  };
}

function CpuDetailDrawer({
  cpu,
  networkLabel,
  open,
  onClose,
}: {
  cpu: StorageCpu | null;
  networkLabel: string;
  open: boolean;
  onClose: () => void;
}) {
  const { t, lang } = useI18n();
  const fmtNum = useNumberFormat();
  const [capacityWindow, setCapacityWindow] = useState<CpuCapacityWindow>('24h');
  const [jobStatusFilter, setJobStatusFilter] = useState<CpuHistoryStatusFilter>('all');
  const historyState = useCpuHistory(cpu?.networkId ?? null, open, capacityWindow);
  const cpuName = cpu?.name ?? '';
  const cpuJobs = useMemo(
    () => filterCpuHistoryJobs(historyState.history?.jobs, cpuName, 'all'),
    [cpuName, historyState.history?.jobs]
  );
  const filteredJobs = useMemo(
    () => filterCpuHistoryJobs(historyState.history?.jobs, cpuName, jobStatusFilter),
    [cpuName, historyState.history?.jobs, jobStatusFilter]
  );
  const cpuSnapshots = useMemo(
    () => (historyState.history?.snapshots ?? []).filter((snapshot) => snapshot.cpuName === cpuName),
    [cpuName, historyState.history?.snapshots]
  );
  // Busy rate is a network-level ratio: aggregate every CPU sample at each
  // timestamp instead of rendering a misleading 0/100% line for one CPU.
  const busyRatePoints = useMemo(
    () => buildCpuBusyRatePoints(historyState.history?.snapshots),
    [historyState.history?.snapshots]
  );
  const handleCapacityWindowChange = useCallback((value: string) => {
    setCapacityWindow(normalizeCpuCapacityWindow(value));
  }, []);
  const handleJobStatusChange = useCallback((value: string) => {
    setJobStatusFilter(value as CpuHistoryStatusFilter);
  }, []);

  if (!cpu) return null;

  const storageTotal = cpu.usedStorage + cpu.availableStorage;
  const storagePct = storageTotal > 0 ? Math.round((cpu.usedStorage / storageTotal) * 100) : 0;
  const remainingMs = estimateRemainingMs(cpu);
  const coord =
    cpu.x != null && cpu.y != null && cpu.z != null
      ? `${cpu.x}, ${cpu.y}, ${cpu.z}${cpu.dim != null ? ` (D${cpu.dim})` : ''}`
      : cpu.monitorX != null
        ? `${cpu.monitorX}, ${cpu.monitorY}, ${cpu.monitorZ}${cpu.monitorDim != null ? ` (D${cpu.monitorDim})` : ''}`
        : '—';
  const capacity = historyState.capacity;
  const hasUnknownJobs = cpuJobs.some((job) => normalizeCpuHistoryStatus(job.status) === 'unknown');
  const missingValue = t('cpuCapacityUnknownValue');
  const percentText = (value: number | null | undefined) => {
    const percent = boundedCpuPercent(value);
    return percent == null ? missingValue : `${Math.round(percent)}%`;
  };
  const durationText = (value: number | null | undefined) => value == null ? missingValue : formatDuration(value);
  const statusOptions = [
    { value: 'all', label: t(cpuHistoryStatusFilterKey('all')) },
    ...CPU_HISTORY_STATUSES.map((status) => ({ value: status, label: t(cpuHistoryStatusLabelKey(status)) })),
  ];
  const jobColumns = [
    {
      title: t('status'),
      dataIndex: 'status',
      key: 'status',
      width: 105,
      render: (status: string) => {
        const normalized = normalizeCpuHistoryStatus(status);
        const tag = <Tag color={cpuHistoryStatusTone(normalized)}>{t(cpuHistoryStatusLabelKey(normalized))}</Tag>;
        return normalized === 'unknown' ? <Tooltip title={t('cpuHistoryUnknownStatusHint')}>{tag}</Tooltip> : tag;
      },
    },
    {
      title: t('cpuHistoryRecipe'),
      dataIndex: 'recipeKey',
      key: 'recipeKey',
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
      width: 120,
      render: (_: unknown, job: CpuJobHistoryDto) => formatDateTime(job.finishedAt || job.startedAt || job.queuedAt, lang),
    },
    {
      title: t('cpuHistoryProgress'),
      dataIndex: 'progress',
      key: 'progress',
      width: 82,
      render: (value: number | null | undefined) => value == null ? missingValue : `${Math.max(0, Math.min(100, value))}%`,
    },
    {
      title: t('cpuHistoryCoProcessors'),
      dataIndex: 'coProcessors',
      key: 'coProcessors',
      width: 72,
      render: (value: number | null | undefined) => value == null ? missingValue : `×${value}`,
    },
  ];

  return (
    <Drawer
      title={`${t('cpuDetail')}: ${cpu.name}`}
      open={open}
      onClose={onClose}
      width={560}
      destroyOnClose
    >
      <Descriptions column={1} size="small" bordered style={{ marginBottom: 16 }}>
        <Descriptions.Item label={t('cpuName')}>{cpu.name}</Descriptions.Item>
        <Descriptions.Item label={t('coordinates')}>{coord}</Descriptions.Item>
        <Descriptions.Item label={t('network')}>{networkLabel}</Descriptions.Item>
        <Descriptions.Item label={t('status')}>
          {cpu.isBusy ? <Tag color="processing">{t('busy')}</Tag> : <Tag>{t('idle')}</Tag>}
        </Descriptions.Item>
        <Descriptions.Item label={t('coprocessors')}>
          {cpu.coProcessors > 0 ? `×${cpu.coProcessors}` : '—'}
        </Descriptions.Item>
      </Descriptions>

      <Text strong style={{ display: 'block', marginBottom: 8 }}>{t('cpuStorageUsage')}</Text>
      {storageTotal > 0 ? (
        <>
          <Progress percent={storagePct} />
          <Text type="secondary" style={{ fontSize: '0.8rem' }}>
            {formatBytes(cpu.usedStorage)} / {formatBytes(storageTotal)}
          </Text>
        </>
      ) : <Text type="secondary">—</Text>}

      <Text strong style={{ display: 'block', marginTop: 16, marginBottom: 8 }}>{t('currentCraftingTask')}</Text>
      {cpu.isBusy ? (
        <div>
          <div style={{ marginBottom: 8 }}>
            {cpu.finalOutputName ? <span>{cpu.finalOutputName} ×{fmtNum(cpu.finalOutputAmount)}</span> : <Text type="secondary">—</Text>}
          </div>
          <Progress percent={Math.round(cpu.craftingProgress * 100)} />
          <div style={{ marginTop: 8, fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
            <div>{t('elapsedTime')}: {cpu.elapsedTime > 0 ? formatDuration(cpu.elapsedTime) : '—'}</div>
            <div>{t('estimatedRemaining')}: {remainingMs != null ? formatDuration(remainingMs) : '—'}</div>
            {cpu.maxItems > 0 && <div>{t('progress')}: {fmtNum(cpu.storedItems)} / {fmtNum(cpu.maxItems)}</div>}
          </div>
        </div>
      ) : <Text type="secondary">{t('cpuIdleNoTask')}</Text>}

      <Text strong style={{ display: 'block', marginTop: 16, marginBottom: 8 }}>{t('craftHistory')}</Text>
      {historyState.loading && !historyState.history ? (
        <Spin size="small" aria-label={t('loading')} />
      ) : historyState.historyError && !historyState.history ? (
        <Alert type="error" showIcon message={t('cpuHistoryLoadError')} description={t(historyState.historyError)} action={<Button size="small" onClick={historyState.reload}>{t('retry')}</Button>} />
      ) : historyState.history ? (
        <>
          <Space wrap size={[6, 6]} style={{ marginBottom: 8 }}>
            <Tag>{t('cpuHistoryJobCount', { n: cpuJobs.length })}</Tag>
            <Tag>{t('cpuHistorySnapshotCount', { n: cpuSnapshots.length })}</Tag>
            {cpuHistoryIsTruncated(historyState.history) && <Tag color="warning">{t('cpuHistoryTruncated')}</Tag>}
          </Space>
          {cpuHistoryIsTruncated(historyState.history) && <Alert type="warning" showIcon message={t('cpuHistoryTruncated')} style={{ marginBottom: 8 }} />}
          <Space wrap style={{ marginBottom: 8 }}>
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
          {hasUnknownJobs && <Alert type="warning" showIcon message={t('cpuHistoryUnknownStatusHint')} style={{ marginBottom: 8 }} />}
          <Text strong style={{ display: 'block', marginBottom: 6 }}>{t('cpuHistoryBusyRate')}</Text>
          {busyRatePoints.length >= 2 ? (
            <ChartTrendSvg
              series={[{ id: 'busy-rate', label: t('cpuHistoryBusyRate'), points: busyRatePoints, lineColor: '#1677ff', areaColor: 'rgba(22,119,255,0.16)' }]}
              formatValue={(value) => `${Math.round(value)}%`}
              formatTime={(timestamp) => formatDateTime(timestamp, lang)}
              showValueAxis
              showTimeAxis
              yDomain={[0, 100]}
              stretchMode="stretchX"
              className="cpu-history-trend"
            />
          ) : <Text type="secondary">{t('cpuHistoryNoTrend')}</Text>}
          <Table<CpuJobHistoryDto>
            dataSource={filteredJobs}
            columns={jobColumns}
            rowKey={(job, index) => job.jobId || `${job.cpuName}-${job.queuedAt}-${index}`}
            size="small"
            bordered
            scroll={{ x: 660, y: 270 }}
            pagination={{ pageSize: CPU_HISTORY_TABLE_PAGE_SIZE, showSizeChanger: false, hideOnSinglePage: false }}
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('noCraftHistory')} /> }}
          />
        </>
      ) : <Text type="secondary">{t('noCraftHistory')}</Text>}

      <Space align="center" wrap style={{ display: 'flex', justifyContent: 'space-between', marginTop: 18, marginBottom: 8 }}>
        <Text strong>{t('cpuCapacityTitle', { window: t(cpuCapacityWindowLabelKey(capacityWindow)) })}</Text>
        <Select
          size="small"
          value={capacityWindow}
          onChange={handleCapacityWindowChange}
          options={CPU_CAPACITY_WINDOW_OPTIONS.map((window) => ({ value: window, label: t(cpuCapacityWindowLabelKey(window)) }))}
          aria-label={t('cpuCapacityWindow')}
          style={{ minWidth: 120 }}
        />
      </Space>
      {capacity ? (
        <Card size="small">
          <Descriptions column={1} size="small">
            <Descriptions.Item label={t('cpuCapacityWindow')}>{t(cpuCapacityWindowLabelKey(capacity.window))}</Descriptions.Item>
            <Descriptions.Item label={t('cpuCapacityCurrent')}>{capacity.currentCpuCount ?? missingValue}</Descriptions.Item>
            <Descriptions.Item label={t('cpuCapacityPeak')}>{capacity.peakConcurrent ?? missingValue}</Descriptions.Item>
            <Descriptions.Item label={t('cpuCapacityP50Duration')}>{durationText(capacity.p50DurationMs)}</Descriptions.Item>
            <Descriptions.Item label={t('cpuCapacityP95Duration')}>{durationText(capacity.p95DurationMs)}</Descriptions.Item>
            <Descriptions.Item label={t('cpuCapacityP95Queue')}>{durationText(capacity.p95QueueMs)}</Descriptions.Item>
            <Descriptions.Item label={t('cpuCapacityBusyRatio')}>{percentText(capacity.busyRatio)}</Descriptions.Item>
            <Descriptions.Item label={t('cpuCapacityStoragePressure')}>{percentText(capacity.storagePressure)}</Descriptions.Item>
            <Descriptions.Item label={t('cpuCapacityStuck')}>{capacity.stuckCount ?? missingValue}</Descriptions.Item>
            <Descriptions.Item label={t('cpuCapacityCoProcessors')}>{capacity.coProcessorObservedMax ?? missingValue}</Descriptions.Item>
            <Descriptions.Item label={t('cpuCapacityRequired')}>{capacity.requiredCpuCountEstimate ?? missingValue}</Descriptions.Item>
          </Descriptions>
          <div style={{ marginTop: 12 }}>
            <Text type="secondary">{t('cpuCapacityBottlenecks')}</Text>
            <div>
              {(capacity.bottlenecks ?? []).length > 0 ? capacity.bottlenecks.map((code) => (
                <Tag key={code} color="warning">{t(cpuCapacityBottleneckLabelKey(code))}</Tag>
              )) : <Text type="secondary">{t('cpuCapacityNoBottlenecks')}</Text>}
            </div>
          </div>
          <div style={{ marginTop: 12 }}>
            <Text type="secondary">{t('cpuCapacityRecommendations')}</Text>
            <div>
              {(capacity.recommendations ?? []).length > 0 ? capacity.recommendations.map((code) => (
                <div key={code}>• {t(cpuCapacityRecommendationLabelKey(code))}</div>
              )) : <Text type="secondary">{t('cpuCapacityNoRecommendations')}</Text>}
            </div>
          </div>
        </Card>
      ) : historyState.loading ? (
        <Spin size="small" aria-label={t('loading')} />
      ) : historyState.capacityError ? (
        <Alert type="error" showIcon message={t('cpuCapacityUnavailable')} description={t(historyState.capacityError)} action={<Button size="small" onClick={historyState.reload}>{t('retry')}</Button>} />
      ) : <Text type="secondary">{t('cpuCapacityNoData')}</Text>}
    </Drawer>
  );
}

export function CpuPage() {
  const { selectedNetworks, displayMode, networks, isLoggedIn } = useAppContext();
  const { t } = useI18n();
  const { storageMap, loading } = useSnapshotData();
  const cpuColumns = useCpuColumns();
  const [search, setSearch] = useState('');
  const [detailCpu, setDetailCpu] = useState<StorageCpu | null>(null);
  const [activeNetTab, setActiveNetTab] = useState<string>('all');

  useEffect(() => {
    if (!isLoggedIn || (detailCpu != null
      && (detailCpu.networkId == null || !selectedNetworks.includes(detailCpu.networkId)))) {
      setDetailCpu(null);
    }
  }, [detailCpu, isLoggedIn, selectedNetworks]);

  const merged = useMemo(() => {
    const storages = selectedNetworks.map((nid) => storageMap[nid]).filter(Boolean) as StorageDto[];
    if (storages.length === 0) return null;
    return mergeCpusFromStorages(storages, displayMode, selectedNetworks);
  }, [storageMap, selectedNetworks, displayMode]);

  const networkName = (id: number) => {
    const net = networks.find((n) => n.networkId === id);
    if (!net) return `#${id}`;
    return formatNetworkOptionLabel(net, t('networkUnavailable'));
  };

  const filterCpus = (cpus: StorageCpu[]) => {
    if (!search) return cpus;
    const q = search.toLowerCase();
    return cpus.filter(
      (c) =>
        (c.name || '').toLowerCase().includes(q) ||
        (c.finalOutputName || '').toLowerCase().includes(q)
    );
  };

  if (!merged) {
    return (
      <PageShell title={t('cpuPage')}>
        <Card>
          <Empty description={selectedNetworks.length === 0 ? t('selectNetworkFirst') : t('noDataYet')} />
        </Card>
      </PageShell>
    );
  }

  const showNetworkTabs =
    displayMode === 'split' && selectedNetworks.length > 1;

  const tableForCpus = (cpus: StorageCpu[], netId?: number) => {
    const filtered = filterCpus(cpus);
    if (!filtered.length) {
      return <Empty description={t('noCpus')} />;
    }
    return (
      <Table
        dataSource={filtered}
        columns={cpuColumns}
        rowKey={(r) => cpuRowKey(r, netId ?? r.networkId)}
        size="small"
        pagination={filtered.length > 20 ? { pageSize: 20, showSizeChanger: true } : false}
        onRow={(record) => ({
          onClick: () => setDetailCpu(record),
          style: { cursor: 'pointer' },
        })}
      />
    );
  };

  return (
    <PageShell title={t('cpuPage')} description={t('cpuPageDesc')}>
      <div className="data-page-flow">
        <OverviewWidgetGrid
          storageKey={CPU_OVERVIEW_CONFIG_KEY}
          defaultSettings={DEFAULT_CPU_OVERVIEW_SETTINGS}
          snapshot={merged.snapshot}
          dataSources={CPU_OVERVIEW_DATA_SOURCES}
          settingsTitleKey="cpuOverviewSettings"
          sectionTitleKey="cpuOverviewTitle"
          sectionDescriptionKey="cpuOverviewDesc"
          gridClassName="overview-widget-grid cpu-overview-grid"
        />

        <DataPageSection
          title={t('cpuQueueTitle')}
          description={t('cpuQueueDesc')}
          eyebrow={t('dataDetailsEyebrow')}
          icon={<UnorderedListOutlined />}
          variant="details"
        >
          <div className="data-page-toolbar">
            <Input
              placeholder={t('searchCpuPlaceholder')}
              prefix={<SearchOutlined />}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              allowClear
              aria-label={t('searchCpuPlaceholder')}
            />
          </div>

          {loading && !merged.snapshot.cpus.length ? (
            <div className="data-page-loading">
              <Spin aria-label={t('loading')} />
            </div>
          ) : showNetworkTabs ? (
            <Tabs
              activeKey={activeNetTab}
              onChange={setActiveNetTab}
              items={[
                {
                  key: 'all',
                  label: t('allNetworks'),
                  children: tableForCpus(merged.cpus),
                },
                ...merged.perNetwork.map((pn) => ({
                  key: String(pn.id),
                  label: networkName(pn.id),
                  children: tableForCpus(pn.cpus, pn.id),
                })),
              ]}
            />
          ) : (
            tableForCpus(merged.cpus)
          )}
        </DataPageSection>
      </div>

      <CpuDetailDrawer
        cpu={detailCpu}
        networkLabel={
          detailCpu?.networkId != null
            ? networkName(detailCpu.networkId)
            : networkName(selectedNetworks[0] ?? 0)
        }
        open={!!detailCpu}
        onClose={() => setDetailCpu(null)}
      />
    </PageShell>
  );
}
