import { useEffect, useMemo, useState } from 'react';
import { Button, Card, Empty, Input, Table, Typography } from 'antd';
import { PushpinFilled, PushpinOutlined, SearchOutlined } from '@ant-design/icons';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { useSnapshotData } from '@/hooks/useSnapshotData';
import { useNetworkMetrics } from '@/hooks/useNetworkMetrics';
import { useFluidMetrics, loadPinnedFluids, savePinnedFluids } from '@/hooks/useFluidMetrics';
import { useNumberFormat } from '@/hooks/useNumberFormat';
import { PageShell } from '@/components/Layout/PageShell';
import { ChartTrendSvg } from '@/components/dashboard/ChartTrendSvg';
import { Icon } from '@/components/Icon';
import { formatTime } from '@/utils/format';
import { fluidIconId } from '@/utils/icon';
import type { StorageFluid } from '@/types/dto';

const { Text } = Typography;

export function FluidsPage() {
  const { selectedNetworks, displayMode, consumePageSearchPrefill } = useAppContext();
  const { t } = useI18n();
  const { storageMap, loading } = useSnapshotData();
  const fmtNum = useNumberFormat();
  const [search, setSearch] = useState('');

  const currentNet = selectedNetworks[0] ?? 0;
  const [pinned, setPinned] = useState<string[]>(() => loadPinnedFluids(currentNet));

  useEffect(() => {
    setPinned(loadPinnedFluids(currentNet));
  }, [currentNet]);

  useEffect(() => {
    const prefill = consumePageSearchPrefill('fluids');
    if (prefill?.query) setSearch(prefill.query);
  }, [consumePageSearchPrefill]);

  const { getHistory } = useNetworkMetrics();
  const { seriesMap } = useFluidMetrics(currentNet, pinned);

  const fluidRows = useMemo((): StorageFluid[] => {
    const storages = selectedNetworks.map((nid) => storageMap[nid]).filter(Boolean);
    if (storages.length === 0) return [];
    if (displayMode === 'merged') {
      const map = new Map<string, StorageFluid>();
      for (const s of storages) {
        for (const f of s.fluids || []) {
          const key = (f.fluidName || '').toLowerCase();
          const existing = map.get(key);
          if (existing) existing.amount += f.amount;
          else map.set(key, { ...f });
        }
      }
      return Array.from(map.values());
    }
    return storages[0]?.fluids || [];
  }, [storageMap, selectedNetworks, displayMode]);

  const filtered = fluidRows.filter((f) => {
    if (!search.trim()) return true;
    return (f.fluidName || '').toLowerCase().includes(search.trim().toLowerCase());
  });

  const trendPoints = getHistory(currentNet, 'fluidTotal');
  const totalTypes = fluidRows.length;
  const totalAmount = fluidRows.reduce((sum, f) => sum + (f.amount || 0), 0);

  const togglePin = (fluidName: string) => {
    const key = fluidName.toLowerCase();
    setPinned((prev) => {
      let next: string[];
      if (prev.includes(key)) {
        next = prev.filter((p) => p !== key);
      } else if (prev.length >= 10) {
        next = prev;
      } else {
        next = [...prev, key];
      }
      savePinnedFluids(currentNet, next);
      return next;
    });
  };

  const columns = [
    {
      title: '',
      key: 'pin',
      width: 40,
      render: (_: unknown, row: StorageFluid) => {
        const key = (row.fluidName || '').toLowerCase();
        const isPinned = pinned.includes(key);
        return (
          <Button
            type="text"
            size="small"
            icon={isPinned ? <PushpinFilled /> : <PushpinOutlined />}
            aria-label={isPinned ? t('fluidUnpin') : t('fluidPin')}
            onClick={() => togglePin(row.fluidName)}
          />
        );
      },
    },
    {
      title: t('fluidName'),
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
      render: (v: number) => <strong style={{ color: 'var(--accent)' }}>{fmtNum(v)} mB</strong>,
    },
  ];

  if (selectedNetworks.length === 0) {
    return (
      <PageShell title={t('fluidsPage')} description={t('fluidsPageDesc')}>
        <Card>
          <Empty description={t('selectNetworkFirst')} />
        </Card>
      </PageShell>
    );
  }

  return (
    <PageShell title={t('fluidsPage')} description={t('fluidsPageDesc')}>
      <Card size="small" style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 24, alignItems: 'flex-start' }}>
          <div>
            <Text type="secondary">{t('fluidTypes')}</Text>
            <div style={{ fontSize: '1.5rem', fontWeight: 600 }}>{totalTypes}</div>
          </div>
          <div>
            <Text type="secondary">{t('fluidTotalAmount')}</Text>
            <div style={{ fontSize: '1.5rem', fontWeight: 600 }}>{fmtNum(totalAmount)} mB</div>
          </div>
        </div>
        <div style={{ marginTop: 16 }}>
          <Text type="secondary" style={{ display: 'block', marginBottom: 8 }}>
            {t('dataSource_fluidTotal')}
          </Text>
          {trendPoints.length >= 2 ? (
            <div className="widget-chart-area" style={{ height: 120, minHeight: 80 }}>
              <ChartTrendSvg
                series={[
                  {
                    id: 'fluidTotal',
                    label: t('dataSource_fluidTotal'),
                    points: trendPoints,
                    lineColor: 'var(--category-fluid, #06b6d4)',
                    areaColor: 'rgba(6, 182, 212, 0.15)',
                  },
                ]}
                formatValue={(v) => `${fmtNum(v)} mB`}
                formatTime={(ts) => formatTime(ts)}
                showValueAxis
                showTimeAxis={false}
                stretchMode="fill"
                colors={{
                  gridColor: 'var(--border-light)',
                  pointColor: '#06b6d4',
                }}
              />
            </div>
          ) : (
            <Text type="secondary">{t('notEnoughData')}</Text>
          )}
        </div>
      </Card>

      {pinned.length > 0 && (
        <Card size="small" title={t('fluidPinnedTrends')} style={{ marginBottom: 16 }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {pinned.map((fluidKey) => {
              const points = seriesMap[fluidKey] ?? [];
              const label = fluidRows.find((f) => f.fluidName.toLowerCase() === fluidKey)?.fluidName ?? fluidKey;
              return (
                <div key={fluidKey}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                    <Icon id={fluidIconId(label)} size={24} alt={label} />
                    <Text strong>{label}</Text>
                  </div>
                  {points.length >= 2 ? (
                    <div className="widget-chart-area" style={{ height: 100 }}>
                      <ChartTrendSvg
                        series={[
                          {
                            id: fluidKey,
                            label,
                            points,
                            lineColor: '#06b6d4',
                            areaColor: 'rgba(6, 182, 212, 0.12)',
                          },
                        ]}
                        formatValue={(v) => `${fmtNum(v)} mB`}
                        formatTime={(ts) => formatTime(ts)}
                        showValueAxis
                        showTimeAxis={false}
                        stretchMode="fill"
                        colors={{ gridColor: 'var(--border-light)', pointColor: '#06b6d4' }}
                      />
                    </div>
                  ) : (
                    <Text type="secondary">{t('notEnoughData')}</Text>
                  )}
                </div>
              );
            })}
          </div>
        </Card>
      )}

      <Card loading={loading && fluidRows.length === 0}>
        <Input
          placeholder={t('searchFluid')}
          prefix={<SearchOutlined />}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          allowClear
          style={{ marginBottom: 16 }}
          aria-label={t('searchFluid')}
        />
        {filtered.length ? (
          <Table
            dataSource={filtered}
            columns={columns}
            rowKey="fluidName"
            size="small"
            pagination={{ pageSize: 50, showSizeChanger: true, showTotal: (total) => `${t('showing')} ${total}` }}
          />
        ) : (
          <Empty description={t('noFluids')} />
        )}
      </Card>
    </PageShell>
  );
}
