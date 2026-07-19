import { useEffect, useMemo, useState } from 'react';
import { Button, Card, Empty, Input, Spin, Table, Typography } from 'antd';
import {
  DatabaseOutlined,
  ExperimentOutlined,
  LineChartOutlined,
  PushpinFilled,
  PushpinOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { useSnapshotData } from '@/hooks/useSnapshotData';
import { useNetworkMetrics } from '@/hooks/useNetworkMetrics';
import { useFluidMetrics, loadPinnedFluids, savePinnedFluids } from '@/hooks/useFluidMetrics';
import { useNumberFormat } from '@/hooks/useNumberFormat';
import { PageShell } from '@/components/Layout/PageShell';
import { DataPageSection } from '@/components/Layout/DataPageSection';
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
      <div className="data-page-flow">
        <DataPageSection
          title={t('fluidOverviewTitle')}
          description={t('fluidOverviewDesc')}
          eyebrow={t('liveOverviewEyebrow')}
          icon={<ExperimentOutlined />}
          variant="overview"
        >
          <div className="data-metric-grid">
            <div className="data-metric-tile">
              <span className="data-metric-tile__icon"><ExperimentOutlined /></span>
              <div>
                <Text type="secondary">{t('fluidTypes')}</Text>
                <strong>{fmtNum(totalTypes)}</strong>
              </div>
            </div>
            <div className="data-metric-tile">
              <span className="data-metric-tile__icon"><DatabaseOutlined /></span>
              <div>
                <Text type="secondary">{t('fluidTotalAmount')}</Text>
                <strong>{fmtNum(totalAmount)} mB</strong>
              </div>
            </div>
          </div>
          <div className="data-insight-chart">
            <div className="data-insight-chart__title">
              <LineChartOutlined />
              <Text strong>{t('dataSource_fluidTotal')}</Text>
            </div>
            {trendPoints.length >= 2 ? (
              <div className="widget-chart-area" style={{ height: 150, minHeight: 100 }}>
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
              <div className="data-insight-chart__empty">
                <Text type="secondary">{t('notEnoughData')}</Text>
              </div>
            )}
          </div>
        </DataPageSection>

        {pinned.length > 0 && (
          <DataPageSection
            title={t('fluidPinnedTrends')}
            description={t('fluidPinnedTrendsDesc')}
            eyebrow={t('insightsEyebrow')}
            icon={<PushpinFilled />}
            variant="insight"
          >
            <div className="fluid-pinned-grid">
              {pinned.map((fluidKey) => {
                const points = seriesMap[fluidKey] ?? [];
                const label = fluidRows.find((f) => f.fluidName.toLowerCase() === fluidKey)?.fluidName ?? fluidKey;
                return (
                  <div className="fluid-pinned-card" key={fluidKey}>
                    <div className="fluid-pinned-card__header">
                      <Icon id={fluidIconId(label)} size={24} alt={label} />
                      <Text strong>{label}</Text>
                    </div>
                    {points.length >= 2 ? (
                      <div className="widget-chart-area" style={{ height: 112 }}>
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
                      <div className="data-insight-chart__empty data-insight-chart__empty--compact">
                        <Text type="secondary">{t('notEnoughData')}</Text>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </DataPageSection>
        )}

        <DataPageSection
          title={t('fluidInventoryTitle')}
          description={t('fluidInventoryDesc')}
          eyebrow={t('dataDetailsEyebrow')}
          icon={<DatabaseOutlined />}
          variant="details"
        >
          <div className="data-page-toolbar">
            <Input
              placeholder={t('searchFluid')}
              prefix={<SearchOutlined />}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              allowClear
              aria-label={t('searchFluid')}
            />
          </div>
          {loading && fluidRows.length === 0 ? (
            <div className="data-page-loading">
              <Spin aria-label={t('loading')} />
            </div>
          ) : filtered.length ? (
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
        </DataPageSection>
      </div>
    </PageShell>
  );
}
