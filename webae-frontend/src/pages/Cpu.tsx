import { useMemo, useState } from 'react';
import { Tabs, Table, Input, Empty, Card, Drawer, Descriptions, Progress, Tag, Typography } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { useSnapshotData } from '@/hooks/useSnapshotData';
import { useNumberFormat } from '@/hooks/useNumberFormat';
import { PageShell } from '@/components/Layout/PageShell';
import { OverviewWidgetGrid } from '@/components/dashboard/OverviewWidgetGrid';
import {
  DEFAULT_CPU_OVERVIEW_SETTINGS,
  CPU_OVERVIEW_CONFIG_KEY,
} from '@/utils/presets';
import { CPU_OVERVIEW_DATA_SOURCES } from '@/utils/overviewDataSources';
import type { OverviewSnapshot } from '@/utils/overviewDataSources';
import type { StorageCpu, StorageDto } from '@/types/dto';
import { useCpuColumns, cpuRowKey, estimateRemainingMs } from '@/utils/cpuColumns';
import { formatBytes, formatDuration } from '@/utils/format';

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
  const { t } = useI18n();
  const fmtNum = useNumberFormat();

  if (!cpu) return null;

  const storageTotal = cpu.usedStorage + cpu.availableStorage;
  const storagePct =
    storageTotal > 0 ? Math.round((cpu.usedStorage / storageTotal) * 100) : 0;
  const remainingMs = estimateRemainingMs(cpu);
  const coord =
    cpu.x != null && cpu.y != null && cpu.z != null
      ? `${cpu.x}, ${cpu.y}, ${cpu.z}${cpu.dim != null ? ` (D${cpu.dim})` : ''}`
      : cpu.monitorX != null
        ? `${cpu.monitorX}, ${cpu.monitorY}, ${cpu.monitorZ}${cpu.monitorDim != null ? ` (D${cpu.monitorDim})` : ''}`
        : '—';

  return (
    <Drawer
      title={`${t('cpuDetail')}: ${cpu.name}`}
      open={open}
      onClose={onClose}
      width={480}
      destroyOnClose
    >
      <Descriptions column={1} size="small" bordered style={{ marginBottom: 16 }}>
        <Descriptions.Item label={t('cpuName')}>{cpu.name}</Descriptions.Item>
        <Descriptions.Item label={t('coordinates')}>{coord}</Descriptions.Item>
        <Descriptions.Item label={t('network')}>{networkLabel}</Descriptions.Item>
        <Descriptions.Item label={t('status')}>
          {cpu.isBusy ? (
            <Tag color="processing">{t('crafting')}</Tag>
          ) : (
            <Tag>{t('idle')}</Tag>
          )}
        </Descriptions.Item>
        <Descriptions.Item label={t('coprocessors')}>
          {cpu.coProcessors > 0 ? `×${cpu.coProcessors}` : '—'}
        </Descriptions.Item>
      </Descriptions>

      <Text strong style={{ display: 'block', marginBottom: 8 }}>
        {t('cpuStorageUsage')}
      </Text>
      {storageTotal > 0 ? (
        <>
          <Progress percent={storagePct} />
          <Text type="secondary" style={{ fontSize: '0.8rem' }}>
            {formatBytes(cpu.usedStorage)} / {formatBytes(storageTotal)}
          </Text>
        </>
      ) : (
        <Text type="secondary">—</Text>
      )}

      <Text strong style={{ display: 'block', marginTop: 16, marginBottom: 8 }}>
        {t('currentCraftingTask')}
      </Text>
      {cpu.isBusy ? (
        <div>
          <div style={{ marginBottom: 8 }}>
            {cpu.finalOutputName ? (
              <span>
                {cpu.finalOutputName} ×{fmtNum(cpu.finalOutputAmount)}
              </span>
            ) : (
              <Text type="secondary">—</Text>
            )}
          </div>
          <Progress percent={Math.round(cpu.craftingProgress * 100)} />
          <div style={{ marginTop: 8, fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
            <div>
              {t('elapsedTime')}: {cpu.elapsedTime > 0 ? formatDuration(cpu.elapsedTime) : '—'}
            </div>
            <div>
              {t('estimatedRemaining')}:{' '}
              {remainingMs != null ? formatDuration(remainingMs) : '—'}
            </div>
            {cpu.maxItems > 0 && (
              <div>
                {t('progress')}: {fmtNum(cpu.storedItems)} / {fmtNum(cpu.maxItems)}
              </div>
            )}
          </div>
        </div>
      ) : (
        <Text type="secondary">{t('cpuIdleNoTask')}</Text>
      )}

      <Text strong style={{ display: 'block', marginTop: 16, marginBottom: 8 }}>
        {t('craftHistory')}
      </Text>
      <Text type="secondary">{t('noCraftHistory')}</Text>
    </Drawer>
  );
}

export function CpuPage() {
  const { selectedNetworks, displayMode, networks } = useAppContext();
  const { t } = useI18n();
  const { storageMap, loading } = useSnapshotData();
  const cpuColumns = useCpuColumns();
  const [search, setSearch] = useState('');
  const [detailCpu, setDetailCpu] = useState<StorageCpu | null>(null);
  const [activeNetTab, setActiveNetTab] = useState<string>('all');

  const merged = useMemo(() => {
    const storages = selectedNetworks.map((nid) => storageMap[nid]).filter(Boolean) as StorageDto[];
    if (storages.length === 0) return null;
    return mergeCpusFromStorages(storages, displayMode, selectedNetworks);
  }, [storageMap, selectedNetworks, displayMode]);

  const networkName = (id: number) =>
    networks.find((n) => n.networkId === id)?.name ?? `#${id}`;

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
    <PageShell title={t('cpuPage')}>
      <OverviewWidgetGrid
        storageKey={CPU_OVERVIEW_CONFIG_KEY}
        defaultSettings={DEFAULT_CPU_OVERVIEW_SETTINGS}
        snapshot={merged.snapshot}
        dataSources={CPU_OVERVIEW_DATA_SOURCES}
        settingsTitleKey="cpuOverviewSettings"
        gridClassName="overview-widget-grid cpu-overview-grid"
      />

      <Card loading={loading && !merged.snapshot.cpus.length} style={{ marginTop: 16 }}>
        <Input
          placeholder={t('searchCpuPlaceholder')}
          prefix={<SearchOutlined />}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          allowClear
          style={{ marginBottom: 16 }}
          aria-label={t('searchCpuPlaceholder')}
        />

        {showNetworkTabs ? (
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
      </Card>

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
