import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Collapse, Drawer, Empty, Spin, Table, Tag, Typography } from 'antd';
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

  const previewSeries =
    preview && preview.values.length > 0
      ? [
          {
            id: 'monitor',
            label: preview.displayName || `#${preview.slotIndex}`,
            points: preview.values.map((value, i) => ({ value, ts: i })),
            lineColor: '#00ffff',
            areaColor: 'rgba(0,255,255,0.12)',
          },
        ]
      : [];

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
                        { title: t('monitorColType'), dataIndex: 'dataType' },
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
          {previewSeries.length > 0 ? (
            <ChartTrendSvg
              series={previewSeries}
              formatValue={(v) => String(v)}
              showValueAxis
              className="monitor-preview-chart"
            />
          ) : (
            <Empty description={t('monitorPreviewEmpty')} />
          )}
        </Spin>
      </Drawer>
    </PageShell>
  );
}
