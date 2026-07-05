import { useMemo, useState, useEffect } from 'react';
import { Tabs, Table, Input, Empty, Card, Tag, Button } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { useSnapshotData } from '@/hooks/useSnapshotData';
import { useNumberFormat } from '@/hooks/useNumberFormat';
import { Icon } from '@/components/Icon';
import { PageShell } from '@/components/Layout/PageShell';
import { ExportCsvButton } from '@/components/ExportCsvButton';
import { OverviewWidgetGrid } from '@/components/dashboard/OverviewWidgetGrid';
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
  const { storageMap, loading } = useSnapshotData();
  const fmtNum = useNumberFormat();
  const [search, setSearch] = useState('');
  const [activeTab, setActiveTab] = useState('items');

  useEffect(() => {
    const prefill = consumePageSearchPrefill('storage');
    if (prefill?.query) setSearch(prefill.query);
  }, [consumePageSearchPrefill]);

  const allData = useMemo((): OverviewSnapshot | null => {
    const storages = selectedNetworks.map((nid) => storageMap[nid]).filter(Boolean);
    if (storages.length === 0) return null;
    if (displayMode === 'merged') {
      const itemMap = new Map<string, StorageItem>();
      const fluidMap = new Map<string, StorageFluid>();
      const essentiaMap = new Map<string, StorageEssentia>();
      let bytesUsed = 0;
      let bytesMax = 0;
      const allCpus: OverviewSnapshot['cpus'] = [];
      for (const s of storages) {
        bytesUsed += s.bytesUsed || 0;
        bytesMax += s.bytesMax || 0;
        allCpus.push(...(s.cpus || []));
        for (const item of s.items || []) {
          const key = item.itemId || item.registryName;
          const existing = itemMap.get(key);
          if (existing) existing.amount += item.amount;
          else itemMap.set(key, { ...item });
        }
        for (const fluid of s.fluids || []) {
          const existing = fluidMap.get(fluid.fluidName);
          if (existing) existing.amount += fluid.amount;
          else fluidMap.set(fluid.fluidName, { ...fluid });
        }
        for (const e of s.essentia || []) {
          const existing = essentiaMap.get(e.aspect);
          if (existing) existing.amount += e.amount;
          else essentiaMap.set(e.aspect, { ...e });
        }
      }
      return {
        items: Array.from(itemMap.values()),
        fluids: Array.from(fluidMap.values()),
        essentia: Array.from(essentiaMap.values()),
        bytesUsed,
        bytesMax,
        cpus: allCpus,
      };
    }
    const first = storages[0];
    return {
      items: first.items,
      fluids: first.fluids,
      essentia: first.essentia,
      bytesUsed: first.bytesUsed || 0,
      bytesMax: first.bytesMax || 0,
      cpus: first.cpus || [],
    };
  }, [storageMap, selectedNetworks, displayMode]);

  if (!allData) {
    return (
      <PageShell title={t('storage')}>
        <Card>
          <Empty description={selectedNetworks.length === 0 ? t('selectNetworkFirst') : t('noDataYet')} />
        </Card>
      </PageShell>
    );
  }

  const filteredItems = (allData.items || []).filter((item) => {
    if (!search) return true;
    const q = search.toLowerCase();
    return (
      (item.displayName || '').toLowerCase().includes(q) ||
      (item.registryName || '').toLowerCase().includes(q)
    );
  });
  const filteredFluids = (allData.fluids || []).filter((f) => {
    if (!search) return true;
    return (f.fluidName || '').toLowerCase().includes(search.toLowerCase());
  });
  const filteredEssentia = (allData.essentia || []).filter((e) => {
    if (!search) return true;
    return (e.aspect || '').toLowerCase().includes(search.toLowerCase());
  });

  const navigateToCraftable = (displayName: string) => {
    setOrderNavigation({ tab: 'patterns', view: 'byProduct', search: displayName });
    setActivePage('order');
  };

  const itemColumns = [
    {
      title: t('item'),
      dataIndex: 'displayName',
      key: 'displayName',
      sorter: (a: StorageItem, b: StorageItem) =>
        (a.displayName || '').localeCompare(b.displayName || ''),
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
      sorter: (a: StorageItem, b: StorageItem) => a.amount - b.amount,
      defaultSortOrder: 'descend' as const,
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

  const fluidColumns = [
    {
      title: t('fluid'),
      dataIndex: 'fluidName',
      key: 'fluidName',
      sorter: (a: StorageFluid, b: StorageFluid) =>
        (a.fluidName || '').localeCompare(b.fluidName || ''),
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
      sorter: (a: StorageFluid, b: StorageFluid) => a.amount - b.amount,
      defaultSortOrder: 'descend' as const,
      render: (v: number) => <strong style={{ color: 'var(--accent)' }}>{fmtNum(v)}</strong>,
    },
  ];

  const essentiaColumns = [
    {
      title: t('aspect'),
      dataIndex: 'aspect',
      key: 'aspect',
      sorter: (a: StorageEssentia, b: StorageEssentia) =>
        (a.aspect || '').localeCompare(b.aspect || ''),
      render: (v: string) => <Tag color="purple">{v}</Tag>,
    },
    {
      title: t('amount'),
      dataIndex: 'amount',
      key: 'amount',
      align: 'right' as const,
      sorter: (a: StorageEssentia, b: StorageEssentia) => a.amount - b.amount,
      defaultSortOrder: 'descend' as const,
      render: (v: number) => <strong style={{ color: 'var(--accent)' }}>{fmtNum(v)}</strong>,
    },
  ];

  return (
    <PageShell
      title={t('storage')}
      actions={
        <ExportCsvButton
          filename="storage-items.csv"
          headers={[t('displayName'), 'registry', t('amount')]}
          rows={filteredItems.map((item) => [
            item.displayName || '',
            item.registryName || item.itemId || '',
            item.amount,
          ])}
          disabled={filteredItems.length === 0}
        />
      }
    >
      <OverviewWidgetGrid
        storageKey={STORAGE_OVERVIEW_CONFIG_KEY}
        defaultSettings={DEFAULT_STORAGE_OVERVIEW_SETTINGS}
        snapshot={allData}
        dataSources={STORAGE_OVERVIEW_DATA_SOURCES}
        settingsTitleKey="storageOverviewSettings"
        gridClassName="overview-widget-grid storage-overview-grid"
      />

      <Card loading={loading && !allData} style={{ marginTop: 16 }}>
        <Input
          placeholder={t('searchPlaceholder')}
          prefix={<SearchOutlined />}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          allowClear
          style={{ marginBottom: 16 }}
          aria-label={t('searchPlaceholder')}
        />
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[
            {
              key: 'items',
              label: `${t('items')} (${filteredItems.length})`,
              children: filteredItems.length ? (
                <Table
                  dataSource={filteredItems}
                  columns={itemColumns}
                  rowKey={(r) => r.itemId || r.registryName}
                  size="small"
                  pagination={{ pageSize: 50, showSizeChanger: true, showTotal: (total) => `${t('showing')} ${total}` }}
                />
              ) : (
                <Empty description={t('noItems')} />
              ),
            },
            {
              key: 'fluids',
              label: `${t('fluids')} (${filteredFluids.length})`,
              children: filteredFluids.length ? (
                <Table
                  dataSource={filteredFluids}
                  columns={fluidColumns}
                  rowKey="fluidName"
                  size="small"
                  pagination={{ pageSize: 50, showTotal: (total) => `${t('showing')} ${total}` }}
                />
              ) : (
                <Empty description={t('noFluids')} />
              ),
            },
            {
              key: 'essentia',
              label: `${t('essentia')} (${filteredEssentia.length})`,
              children: filteredEssentia.length ? (
                <Table
                  dataSource={filteredEssentia}
                  columns={essentiaColumns}
                  rowKey="aspect"
                  size="small"
                  pagination={{ pageSize: 50 }}
                />
              ) : (
                <Empty description={t('noEssentia')} />
              ),
            },
          ]}
        />
      </Card>
    </PageShell>
  );
}
