import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Collapse, Drawer, Empty, Progress, Spin, Table, Tag, Typography } from 'antd';
import { LineChartOutlined, ReloadOutlined } from '@ant-design/icons';
import { getApiClient } from '@/api/client';
import { PageShell } from '@/components/Layout/PageShell';
import { ChartTrendSvg } from '@/components/dashboard/ChartTrendSvg';
import { useI18n } from '@/i18n';
import type {
  MonitorBindingDto,
  MonitorBindingsResponse,
  MonitorDataBindingDto,
  MonitorPreviewDto,
  MonitorPreviewResponse,
} from '@/types/dto';

const { Text } = Typography;

interface PreviewTarget {
  monitor: MonitorBindingDto;
  slot: MonitorDataBindingDto;
}

export function MonitorBindingsPage() {
  const { t } = useI18n();
  const [loading, setLoading] = useState(false);
  const [monitors, setMonitors] = useState<MonitorBindingDto[]>([]);
  const [previewTarget, setPreviewTarget] = useState<PreviewTarget | null>(null);
  const [preview, setPreview] = useState<MonitorPreviewDto | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getApiClient().get<MonitorBindingsResponse>('/api/monitor/bindings');
      setMonitors(data.monitors || []);
    } catch {
      setMonitors([]);
    } finally {
      setLoading(false);
    }
  }, []);

  const openPreview = useCallback(async (monitor: MonitorBindingDto, slot: MonitorDataBindingDto) => {
    setPreviewTarget({ monitor, slot });
    setPreviewLoading(true);
    setPreview(null);
    try {
      const q = new URLSearchParams({
        dim: String(monitor.monitorDim),
        x: String(monitor.monitorX),
        y: String(monitor.monitorY),
        z: String(monitor.monitorZ),
        slot: String(slot.slotIndex),
        width: '480',
      });
      const data = await getApiClient().get<MonitorPreviewResponse>(`/api/monitor/preview?${q.toString()}`);
      setPreview(data.preview ?? null);
    } catch {
      setPreview(null);
    } finally {
      setPreviewLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <PageShell title={t('monitorBindings')} description={t('monitorBindingsDesc')}>
      <Card extra={<a onClick={() => void load()} role="button" tabIndex={0}><ReloadOutlined /> {t('refresh')}</a>}>
        <Spin spinning={loading}>
          {monitors.length === 0 ? (
            <Empty description={t('monitorBindingsEmpty')} />
          ) : (
            <Collapse
              items={monitors.map((m, i) => ({
                key: String(i),
                label: `${t('monitorAt')} D${m.monitorDim} (${m.monitorX}, ${m.monitorY}, ${m.monitorZ}) — ${m.dataBindings?.length ?? 0} + ${m.gtBindings?.length ?? 0} GT`,
                children: (
                  <>
                    <Text type="secondary">{t('monitorBindingsReadOnly')}</Text>
                    <Table
                      size="small"
                      style={{ marginTop: 8 }}
                      pagination={false}
                      rowKey="slotIndex"
                      dataSource={m.dataBindings || []}
                      columns={[
                        { title: '#', dataIndex: 'slotIndex', width: 48 },
                        { title: t('monitorColType'), dataIndex: 'kind' },
                        { title: t('monitorColName'), dataIndex: 'displayName', ellipsis: true },
                        {
                          title: t('monitorColTarget'),
                          key: 'target',
                          render: (_, r) => `D${r.bindDim} (${r.bindX}, ${r.bindY}, ${r.bindZ})`,
                        },
                        {
                          title: t('monitorColEnabled'),
                          dataIndex: 'enabled',
                          render: (v: boolean) => (v ? <Tag color="green">{t('on')}</Tag> : <Tag>{t('off')}</Tag>),
                        },
                        {
                          title: t('monitorPreview'),
                          key: 'preview',
                          width: 100,
                          render: (_, r) => (
                            <Button
                              size="small"
                              icon={<LineChartOutlined />}
                              onClick={() => void openPreview(m, r)}
                              aria-label={t('monitorPreview')}
                            >
                              {t('monitorPreview')}
                            </Button>
                          ),
                        },
                      ]}
                    />
                    {(m.gtBindings?.length ?? 0) > 0 && (
                      <Table
                        size="small"
                        style={{ marginTop: 12 }}
                        pagination={false}
                        rowKey={(r) => `${r.dim}:${r.x}:${r.y}:${r.z}`}
                        dataSource={m.gtBindings}
                        columns={[
                          { title: t('scannerColDim'), dataIndex: 'dim', width: 70 },
                          {
                            title: t('scannerColCoords'),
                            key: 'c',
                            render: (_, r) => `${r.x}, ${r.y}, ${r.z}`,
                          },
                        ]}
                      />
                    )}
                  </>
                ),
              }))}
            />
          )}
        </Spin>
      </Card>

      <Drawer
        title={previewTarget ? `${t('monitorPreview')} — ${previewTarget.slot.displayName || previewTarget.slot.slotIndex}` : t('monitorPreview')}
        open={previewTarget != null}
        onClose={() => {
          setPreviewTarget(null);
          setPreview(null);
        }}
        width={Math.min(560, window.innerWidth - 24)}
      >
        <Spin spinning={previewLoading}>
          {preview ? (
            <MonitorPreviewContent preview={preview} emptyLabel={t('monitorPreviewEmpty')} />
          ) : (
            <Empty description={t('monitorPreviewEmpty')} />
          )}
        </Spin>
      </Drawer>
    </PageShell>
  );
}

function MonitorPreviewContent({ preview, emptyLabel }: { preview: MonitorPreviewDto; emptyLabel: string }) {
  if (preview.previewType === 'scalar' && preview.scalar) {
    const scalar = preview.scalar;
    const progressKind = preview.kind === 'progressBar' || preview.kind === 'gauge';
    if (progressKind && scalar.maxKnown) {
      return (
        <div style={{ display: 'grid', placeItems: 'center', minHeight: 220 }}>
          <Text strong>{preview.title || preview.displayName}</Text>
          <Progress
            type={preview.kind === 'gauge' ? 'dashboard' : 'line'}
            percent={Math.max(0, Math.min(100, scalar.percentage))}
            format={() => `${scalar.value} / ${scalar.max}`}
            style={{ width: '100%' }}
          />
        </div>
      );
    }
    return (
      <Card size="small" title={preview.title || preview.displayName}>
        <div style={{ color: 'var(--accent)', fontSize: 32, textAlign: 'center' }}>{scalar.value}</div>
      </Card>
    );
  }

  if (preview.previewType === 'series') {
    const series = (preview.series?.length ? preview.series : [{ id: 'monitor', label: preview.title, values: preview.values }])
      .filter((entry) => entry.values.length > 0)
      .map((entry, index) => ({
        id: entry.id || String(index),
        label: entry.label || preview.title || `#${preview.slotIndex}`,
        points: entry.values.map((value, pointIndex) => ({ value, ts: pointIndex })),
        lineColor: ['#00ffff', '#52c41a', '#faad14'][index % 3],
        areaColor: ['rgba(0,255,255,0.12)', 'rgba(82,196,26,0.12)', 'rgba(250,173,20,0.12)'][index % 3],
      }));
    return series.length > 0 ? (
      <ChartTrendSvg series={series} formatValue={(v) => String(v)} showValueAxis className="monitor-preview-chart" />
    ) : <Empty description={emptyLabel} />;
  }

  if (preview.previewType === 'categories' && preview.categories?.length > 0) {
    return preview.kind === 'pieChart'
      ? <CategoryPie categories={preview.categories} />
      : <CategoryBars categories={preview.categories} />;
  }

  if (preview.previewType === 'rows' && preview.rows?.length > 0) {
    if (Array.isArray(preview.columns) && preview.columns.length === 0) {
      return <Empty description={emptyLabel} />;
    }
    const keys = preview.columns ?? Object.keys(preview.rows[0]?.cells || {});
    return (
      <Table
        size="small"
        pagination={false}
        rowKey={(_, index) => String(index)}
        dataSource={preview.rows.map((row) => row.cells)}
        columns={keys.map((key) => ({ title: key, dataIndex: key, ellipsis: true }))}
      />
    );
  }
  return <Empty description={emptyLabel} />;
}

function CategoryBars({ categories }: { categories: MonitorPreviewDto['categories'] }) {
  const max = Math.max(1, ...categories.map((category) => Math.abs(category.value)));
  return (
    <div style={{ display: 'flex', alignItems: 'end', gap: 8, minHeight: 240 }}>
      {categories.map((category, index) => (
        <div key={`${category.label}-${index}`} style={{ flex: 1, textAlign: 'center' }}>
          <div style={{ height: 180, display: 'flex', alignItems: 'end' }}>
            <div style={{ width: '100%', minHeight: 2, height: `${Math.abs(category.value) / max * 100}%`, background: category.color || '#00ffff' }} />
          </div>
          <Text type="secondary" ellipsis style={{ display: 'block' }}>{category.label}</Text>
          <Text>{category.value}</Text>
        </div>
      ))}
    </div>
  );
}

function CategoryPie({ categories }: { categories: MonitorPreviewDto['categories'] }) {
  const total = categories.reduce((sum, category) => sum + Math.abs(category.value), 0) || 1;
  let offset = 0;
  const gradient = categories.map((category, index) => {
    const start = offset;
    offset += Math.abs(category.value) / total * 100;
    return `${category.color || ['#00ffff', '#52c41a', '#faad14', '#a66cff'][index % 4]} ${start}% ${offset}%`;
  }).join(', ');
  return (
    <div style={{ display: 'grid', placeItems: 'center', gap: 12 }}>
      <div style={{ width: 220, height: 220, borderRadius: '50%', background: `conic-gradient(${gradient})` }} />
      <SpaceWrap categories={categories} />
    </div>
  );
}

function SpaceWrap({ categories }: { categories: MonitorPreviewDto['categories'] }) {
  return <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>{categories.map((category) => <Tag key={category.label}>{category.label}: {category.value}</Tag>)}</div>;
}
