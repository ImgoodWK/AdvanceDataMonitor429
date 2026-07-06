import { useMemo, useState, useEffect } from 'react';
import { Tabs, Input, Empty, Card, Tag, Button, Alert, Select, Spin } from 'antd';
import type { ColumnType } from 'antd/es/table';
import { SearchOutlined } from '@ant-design/icons';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { useSnapshotData } from '@/hooks/useSnapshotData';
import { useStoragePaged, type StorageSortKey } from '@/hooks/useStoragePaged';
import { useNumberFormat } from '@/hooks/useNumberFormat';
import { Icon } from '@/components/Icon';
import { PageShell } from '@/components/Layout/PageShell';
import { ExportCsvButton } from '@/components/ExportCsvButton';
import { OverviewWidgetGrid } from '@/components/dashboard/OverviewWidgetGrid';
import { VirtualStorageTable } from '@/components/storage/VirtualStorageTable';
import {
  DEFAULT_STORAGE_OVERVIEW_SETTINGS,
  STORAGE_OVERVIEW_CONFIG_KEY,
} from '@/utils/presets';
import { STORAGE_OVERVIEW_DATA_SOURCES } from '@/utils/overviewDataSources';
import type { OverviewSnapshot } from '@/utils/overviewDataSources';
import type { StorageItem, StorageFluid, StorageEssentia } from '@/types/dto';
import { fluidIconId } from '@/utils/icon';

export function StoragePage() {
  const { selectedNetworks, displayMode, setActivePage, setOrderNavigation, consumePageSearchPrefill } =
    useAppContext();
  const { t } = useI18n();
  const { refreshing, hasSelectedStorage } = useSnapshotData();
  const fmtNum = useNumberFormat();
  const [search, setSearch] = useState('');
  const [activeTab, setActiveTab] = useState('items');
  const [sort, setSort] = useState<StorageSortKey>('amount_desc');

  const merged = displayMode === 'merged';
  const networkIds = selectedNetworks;

  useEffect(() => {
    const prefill = consumePageSearchPrefill('storage');
    if (prefill?.query) setSearch(prefill.query);
  }, [consumePageSearchPrefill]);

  const itemsPaged = useStoragePaged({
    kind: 'items',
    networkIds,
    merged,
    search,
    sort,
    enabled: selectedNetworks.length > 0,
  });

  const fluidsPaged = useStoragePaged({
    kind: 'fluids',
    networkIds,
    merged,
    search,
    sort,
    enabled: selectedNetworks.length > 0,
  });

  const essentiaPaged = useStoragePaged({
    kind: 'essentia',
    networkIds,
    merged,
    search,
    sort,
    enabled: selectedNetworks.length > 0,
  });

  const overviewSnapshot = useMemo((): OverviewSnapshot | null => {
    const summary = itemsPaged.summary;
    if (!summary && !itemsPaged.loading && itemsPaged.totalEstimate === 0) {
      return null;
    }
    return {
      bytesUsed: summary?.bytesUsed ?? 0,
      bytesMax: summary?.bytesMax ?? 0,
      cpus: summary?.cpus ?? [],
      itemCount: itemsPaged.totalEstimate,
      itemTotal: summary?.totalAmountSum,
      fluidCount: fluidsPaged.totalEstimate,
      essentiaCount: essentiaPaged.totalEstimate,
    };
  }, [itemsPaged, fluidsPaged, essentiaPaged]);

  if (selectedNetworks.length === 0) {
    return (
      <PageShell title={t('storage')}>
        <Card>
          <Empty description={t('selectNetworkFirst')} />
        </Card>
      </PageShell>
    );
  }

  const mergeLimited = itemsPaged.mergeLimited;

  const navigateToCraftable = (displayName: string) => {
    setOrderNavigation({ tab: 'patterns', view: 'byProduct', search: displayName });
    setActivePage('order');
  };

  const itemColumns: ColumnType<StorageItem>[] = [
    {
      title: t('item'),
      dataIndex: 'displayName',
      key: 'displayName',
      render: (_: string, record: StorageItem) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Icon item={record} size={32} alt={record.displayName} />
          <span>{record.displayName || record.registryName}</span>
        </div>
      ),
    },
    {
      title: t('registry'),
      dataIndex: 'registryName',
      key: 'registryName',
      ellipsis: true,
      render: (v: string) => <code style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>{v}</code>,
    },
    {
      title: t('amount'),
      dataIndex: 'amount',
      key: 'amount',
      align: 'right' as const,
      render: (v: number, record: StorageItem) =>
        v === 0 ? (
          <Button type="link" size="small" style={{ padding: 0 }} onClick={() => navigateToCraftable(record.displayName || record.registryName || '')}>
            {t('storageCraftable')}
          </Button>
        ) : (
          <strong style={{ color: 'var(--accent)' }}>{fmtNum(v)}</strong>
        ),
    },
  ];

  const fluidColumns: ColumnType<StorageFluid>[] = [
    {
      title: t('fluid'),
      dataIndex: 'fluidName',
      key: 'fluidName',
      render: (v: string) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Icon id={fluidIconId(v)} size={32} alt={v} />
          <span>{v}</span>
        </div>
      ),
    },
    {
      title: t('amount'),
      dataIndex: 'amount',
      key: 'amount',
      align: 'right' as const,
      render: (v: number) => <strong style={{ color: 'var(--accent)' }}>{fmtNum(v)}</strong>,
    },
  ];

  const essentiaColumns: ColumnType<StorageEssentia>[] = [
    {
      title: t('aspect'),
      dataIndex: 'aspect',
      key: 'aspect',
      render: (v: string) => <Tag color="purple">{v}</Tag>,
    },
    {
      title: t('amount'),
      dataIndex: 'amount',
      key: 'amount',
      align: 'right' as const,
      render: (v: number) => <strong style={{ color: 'var(--accent)' }}>{fmtNum(v)}</strong>,
    },
  ];

  const sortOptions = [
    { value: 'amount_desc', label: t('storageSortAmountDesc') },
    { value: 'amount_asc', label: t('storageSortAmountAsc') },
    { value: 'name_asc', label: t('storageSortNameAsc') },
    { value: 'name_desc', label: t('storageSortNameDesc') },
  ];

  const exportRows = (itemsPaged.rows as StorageItem[]).map((item) => [
    item.displayName || '',
    item.registryName || item.itemId || '',
    item.amount,
  ]);

  return (
    <PageShell
      title={t('storage')}
      actions={
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          {(refreshing || hasSelectedStorage) && refreshing && (
            <span style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }} aria-live="polite">
              {t('refreshing')}
            </span>
          )}
          <ExportCsvButton
            filename="storage-items.csv"
            headers={[t('displayName'), 'registry', t('amount')]}
            rows={exportRows}
            disabled={exportRows.length === 0}
          />
        </div>
      }
    >
      {overviewSnapshot && (
        <OverviewWidgetGrid
          storageKey={STORAGE_OVERVIEW_CONFIG_KEY}
          defaultSettings={DEFAULT_STORAGE_OVERVIEW_SETTINGS}
          snapshot={overviewSnapshot}
          dataSources={STORAGE_OVERVIEW_DATA_SOURCES}
          settingsTitleKey="storageOverviewSettings"
          gridClassName="overview-widget-grid storage-overview-grid"
        />
      )}

      <Card style={{ marginTop: 16 }}>
        {mergeLimited && (
          <Alert
            type="warning"
            showIcon
            message={t('storageMergeLimit')}
            style={{ marginBottom: 16 }}
          />
        )}
        <div style={{ display: 'flex', gap: 12, marginBottom: 16, flexWrap: 'wrap' }}>
          <Input
            placeholder={t('searchPlaceholder')}
            prefix={<SearchOutlined />}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            allowClear
            style={{ flex: '1 1 200px' }}
            aria-label={t('searchPlaceholder')}
          />
          <Select
            value={sort}
            onChange={(v) => setSort(v as StorageSortKey)}
            options={sortOptions}
            style={{ minWidth: 160 }}
            aria-label={t('storageSortLabel')}
          />
        </div>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[
            {
              key: 'items',
              label: `${t('items')} (${itemsPaged.totalEstimate})`,
              children: mergeLimited ? (
                <Empty description={t('storageMergeLimit')} />
              ) : itemsPaged.loading && itemsPaged.rows.length === 0 ? (
                <div style={{ textAlign: 'center', padding: 48 }}>
                  <Spin aria-label={t('loading')} />
                </div>
              ) : (
                <VirtualStorageTable
                  rows={itemsPaged.rows as StorageItem[]}
                  columns={itemColumns}
                  rowKey={(r) => r.itemId || r.registryName}
                  totalEstimate={itemsPaged.totalEstimate}
                  loading={itemsPaged.loading}
                  loadingMore={itemsPaged.loadingMore}
                  hasMore={itemsPaged.hasMore}
                  onLoadMore={itemsPaged.loadMore}
                  emptyText={<Empty description={t('noItems')} />}
                />
              ),
            },
            {
              key: 'fluids',
              label: `${t('fluids')} (${fluidsPaged.totalEstimate})`,
              children: mergeLimited ? (
                <Empty description={t('storageMergeLimit')} />
              ) : (
                <VirtualStorageTable
                  rows={fluidsPaged.rows as StorageFluid[]}
                  columns={fluidColumns}
                  rowKey={(r) => r.fluidName}
                  totalEstimate={fluidsPaged.totalEstimate}
                  loading={fluidsPaged.loading}
                  loadingMore={fluidsPaged.loadingMore}
                  hasMore={fluidsPaged.hasMore}
                  onLoadMore={fluidsPaged.loadMore}
                  emptyText={<Empty description={t('noFluids')} />}
                />
              ),
            },
            {
              key: 'essentia',
              label: `${t('essentia')} (${essentiaPaged.totalEstimate})`,
              children: mergeLimited ? (
                <Empty description={t('storageMergeLimit')} />
              ) : (
                <VirtualStorageTable
                  rows={essentiaPaged.rows as StorageEssentia[]}
                  columns={essentiaColumns}
                  rowKey={(r) => r.aspect}
                  totalEstimate={essentiaPaged.totalEstimate}
                  loading={essentiaPaged.loading}
                  loadingMore={essentiaPaged.loadingMore}
                  hasMore={essentiaPaged.hasMore}
                  onLoadMore={essentiaPaged.loadMore}
                  emptyText={<Empty description={t('noEssentia')} />}
                />
              ),
            },
          ]}
        />
      </Card>
    </PageShell>
  );
}
