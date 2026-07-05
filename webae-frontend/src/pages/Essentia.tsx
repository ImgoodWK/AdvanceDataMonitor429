import { useEffect, useMemo, useState } from 'react';
import { Card, Empty, Input, Table, Tag, Typography } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { useSnapshotData } from '@/hooks/useSnapshotData';
import { useNetworkMetrics } from '@/hooks/useNetworkMetrics';
import { useNumberFormat } from '@/hooks/useNumberFormat';
import { PageShell } from '@/components/Layout/PageShell';
import { ChartTrendSvg } from '@/components/dashboard/ChartTrendSvg';
import { formatTime } from '@/utils/format';
import type { StorageEssentia } from '@/types/dto';

const { Text } = Typography;

export function EssentiaPage() {
  const { selectedNetworks, displayMode, consumePageSearchPrefill } = useAppContext();
  const { t } = useI18n();
  const { storageMap, loading } = useSnapshotData();
  const { getHistory } = useNetworkMetrics();
  const fmtNum = useNumberFormat();
  const [search, setSearch] = useState('');

  useEffect(() => {
    const prefill = consumePageSearchPrefill('essentia');
    if (prefill?.query) setSearch(prefill.query);
  }, [consumePageSearchPrefill]);

  const currentNet = selectedNetworks[0] ?? 0;

  const essentiaRows = useMemo((): StorageEssentia[] => {
    const storages = selectedNetworks.map((nid) => storageMap[nid]).filter(Boolean);
    if (storages.length === 0) return [];
    if (displayMode === 'merged') {
      const map = new Map<string, StorageEssentia>();
      for (const s of storages) {
        for (const e of s.essentia || []) {
          const existing = map.get(e.aspect);
          if (existing) existing.amount += e.amount;
          else map.set(e.aspect, { ...e });
        }
      }
      return Array.from(map.values());
    }
    return storages[0]?.essentia || [];
  }, [storageMap, selectedNetworks, displayMode]);

  const filtered = essentiaRows.filter((e) => {
    if (!search.trim()) return true;
    return (e.aspect || '').toLowerCase().includes(search.trim().toLowerCase());
  });

  const trendPoints = getHistory(currentNet, 'essentiaCount');
  const totalAspects = essentiaRows.length;
  const totalAmount = essentiaRows.reduce((sum, e) => sum + (e.amount || 0), 0);

  const columns = [
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

  if (selectedNetworks.length === 0) {
    return (
      <PageShell title={t('essentiaPage')} description={t('essentiaPageDesc')}>
        <Card>
          <Empty description={t('selectNetworkFirst')} />
        </Card>
      </PageShell>
    );
  }

  return (
    <PageShell title={t('essentiaPage')} description={t('essentiaPageDesc')}>
      <Card size="small" style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 24, alignItems: 'flex-start' }}>
          <div>
            <Text type="secondary">{t('essentiaTypes')}</Text>
            <div style={{ fontSize: '1.5rem', fontWeight: 600 }}>{totalAspects}</div>
          </div>
          <div>
            <Text type="secondary">{t('essentiaTotalAmount')}</Text>
            <div style={{ fontSize: '1.5rem', fontWeight: 600 }}>{fmtNum(totalAmount)}</div>
          </div>
        </div>
        <div style={{ marginTop: 16 }}>
          <Text type="secondary" style={{ display: 'block', marginBottom: 8 }}>
            {t('dataSource_essentiaCount')}
          </Text>
          {trendPoints.length >= 2 ? (
            <div className="widget-chart-area" style={{ height: 120, minHeight: 80 }}>
              <ChartTrendSvg
                series={[
                  {
                    id: 'essentiaCount',
                    label: t('dataSource_essentiaCount'),
                    points: trendPoints,
                    lineColor: 'var(--category-essentia, #a855f7)',
                    areaColor: 'rgba(168, 85, 247, 0.15)',
                  },
                ]}
                formatValue={(v) => String(Math.round(v))}
                formatTime={(ts) => formatTime(ts)}
                showValueAxis
                showTimeAxis={false}
                stretchMode="fill"
                colors={{
                  gridColor: 'var(--border-light)',
                  pointColor: '#a855f7',
                }}
              />
            </div>
          ) : (
            <Text type="secondary">{t('notEnoughData')}</Text>
          )}
        </div>
      </Card>

      <Card loading={loading && essentiaRows.length === 0}>
        <Input
          placeholder={t('searchAspect')}
          prefix={<SearchOutlined />}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          allowClear
          style={{ marginBottom: 16 }}
          aria-label={t('searchAspect')}
        />
        {filtered.length ? (
          <Table
            dataSource={filtered}
            columns={columns}
            rowKey="aspect"
            size="small"
            pagination={{ pageSize: 50, showSizeChanger: true, showTotal: (total) => `${t('showing')} ${total}` }}
          />
        ) : (
          <Empty description={t('noEssentia')} />
        )}
      </Card>
    </PageShell>
  );
}
