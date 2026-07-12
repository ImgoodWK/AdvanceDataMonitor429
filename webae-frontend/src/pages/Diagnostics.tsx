import { Card, Col, Row, Spin, Table, Tag, Typography } from 'antd';
import { DashboardOutlined } from '@ant-design/icons';
import type { ReactNode } from 'react';
import { PageShell } from '@/components/Layout/PageShell';
import { useServerDiagnostics } from '@/hooks/useServerDiagnostics';
import { useI18n } from '@/i18n';
import { formatTime } from '@/utils/format';
import type { PerfPhaseView, PerfSlowHttpEntry } from '@/types/dto';

const { Text, Paragraph } = Typography;

const PHASE_ORDER = [
  'serverTasks',
  'snapshotScheduler',
  'powerSampler',
  'metricSampler',
  'iconMissingQueue',
  'worldMapTileQueue',
  'worldMapCapture',
  'webAlertEngine',
  'misc',
];

function phaseRows(phases: Record<string, PerfPhaseView> | undefined): Array<{ key: string } & PerfPhaseView> {
  if (!phases) return [];
  const keys = Object.keys(phases);
  keys.sort((a, b) => {
    const ia = PHASE_ORDER.indexOf(a);
    const ib = PHASE_ORDER.indexOf(b);
    if (ia >= 0 && ib >= 0) return ia - ib;
    if (ia >= 0) return -1;
    if (ib >= 0) return 1;
    return a.localeCompare(b);
  });
  return keys.map((key) => ({ key, ...phases[key] }));
}

function msTag(ms: number): ReactNode {
  let color: string = 'default';
  if (ms >= 10) color = 'error';
  else if (ms >= 5) color = 'warning';
  else if (ms > 0) color = 'success';
  return <Tag color={color}>{ms.toFixed(1)} ms</Tag>;
}

export function DiagnosticsPage() {
  const { t } = useI18n();
  const { data, loading, refresh } = useServerDiagnostics(3000);

  const phaseData = phaseRows(data?.phases);
  const collectData = phaseRows(data?.collects);

  return (
    <PageShell
      title={t('diagnosticsPage')}
      description={t('diagnosticsHint')}
      actions={
        <a onClick={() => refresh()} style={{ cursor: 'pointer' }}>
          {t('refresh')}
        </a>
      }
    >
      <Spin spinning={loading && !data}>
        <Paragraph type="secondary" style={{ marginTop: 0 }}>
          <DashboardOutlined /> {t('diagnosticsReadonlyNote')}
        </Paragraph>

        <Row gutter={[16, 16]}>
          <Col xs={12} sm={8} md={4}>
            <Card size="small" title="TPS">
              <Text strong style={{ fontSize: 22 }}>
                {data ? data.tps.toFixed(1) : '—'}
              </Text>
            </Card>
          </Col>
          <Col xs={12} sm={8} md={4}>
            <Card size="small" title="MSPT">
              <Text strong style={{ fontSize: 22 }}>
                {data ? data.mspt.toFixed(1) : '—'}
              </Text>
            </Card>
          </Col>
          <Col xs={12} sm={8} md={4}>
            <Card size="small" title={t('diagQueueDepth')}>
              <Text strong style={{ fontSize: 22 }}>
                {data?.queueDepth ?? '—'}
              </Text>
            </Card>
          </Col>
          <Col xs={12} sm={8} md={4}>
            <Card size="small" title={t('diagTasksThisTick')}>
              <Text strong style={{ fontSize: 22 }}>
                {data?.tasksProcessedThisTick ?? '—'}
              </Text>
            </Card>
          </Col>
          <Col xs={12} sm={8} md={4}>
            <Card size="small" title={t('diagActiveNetworks')}>
              <Text strong style={{ fontSize: 22 }}>
                {data?.activeNetworks ?? '—'}
              </Text>
            </Card>
          </Col>
          <Col xs={12} sm={8} md={4}>
            <Card size="small" title={t('diagSnapshotCache')}>
              <Text strong style={{ fontSize: 22 }}>
                {data?.snapshotCacheSize ?? '—'}
              </Text>
            </Card>
          </Col>
        </Row>

        {data?.config && (
          <Card size="small" title={t('diagConfigSummary')} style={{ marginTop: 16 }}>
            <Text type="secondary">
              refresh={data.config.refreshIntervalMs}ms · gt={data.config.gtRefreshIntervalMs}ms · patternTtl=
              {data.config.patternCacheTtlMs}ms · mapBudget={data.config.worldMapTileBudgetPerTick}/tick · webaePerf=
              {data.config.perfDebugEnabled ? 'on' : 'off'}
            </Text>
          </Card>
        )}

        <Card size="small" title={t('diagTickPhases')} style={{ marginTop: 16 }}>
          <Table
            size="small"
            pagination={false}
            rowKey="key"
            dataSource={phaseData}
            columns={[
              { title: t('diagPhase'), dataIndex: 'key' },
              {
                title: t('diagLastMs'),
                dataIndex: 'lastMs',
                render: (v: number) => msTag(v),
              },
              {
                title: t('diagAvgMs'),
                dataIndex: 'avgMs',
                render: (v: number) => `${Number(v).toFixed(1)}`,
              },
              { title: t('diagMaxMs'), dataIndex: 'maxMs' },
              { title: t('diagCount'), dataIndex: 'count' },
            ]}
          />
        </Card>

        <Card size="small" title={t('diagCollects')} style={{ marginTop: 16 }}>
          <Table
            size="small"
            pagination={false}
            rowKey="key"
            dataSource={collectData}
            locale={{ emptyText: t('diagNoData') }}
            columns={[
              { title: t('diagPhase'), dataIndex: 'key' },
              {
                title: t('diagLastMs'),
                dataIndex: 'lastMs',
                render: (v: number) => msTag(v),
              },
              {
                title: t('diagAvgMs'),
                dataIndex: 'avgMs',
                render: (v: number) => `${Number(v).toFixed(1)}`,
              },
              { title: t('diagMaxMs'), dataIndex: 'maxMs' },
              { title: t('diagCount'), dataIndex: 'count' },
            ]}
          />
        </Card>

        <Card size="small" title={t('diagTopRoutes')} style={{ marginTop: 16 }}>
          <Table
            size="small"
            pagination={false}
            rowKey="route"
            dataSource={data?.topRoutes ?? []}
            locale={{ emptyText: t('diagNoData') }}
            columns={[
              { title: t('diagRoute'), dataIndex: 'route' },
              { title: t('diagCount'), dataIndex: 'count' },
              {
                title: t('diagAvgMs'),
                dataIndex: 'avgMs',
                render: (v: number) => `${Number(v).toFixed(1)}`,
              },
              {
                title: t('diagMaxMs'),
                dataIndex: 'maxMs',
                render: (v: number) => msTag(v),
              },
              { title: t('diagTotalMs'), dataIndex: 'totalMs' },
            ]}
          />
        </Card>

        <Card size="small" title={t('diagSlowHttp')} style={{ marginTop: 16 }}>
          <Table
            size="small"
            pagination={{ pageSize: 10 }}
            rowKey={(r: PerfSlowHttpEntry) => `${r.ts}-${r.route}-${r.durationMs}`}
            dataSource={[...(data?.slowHttp ?? [])].reverse()}
            locale={{ emptyText: t('diagNoData') }}
            columns={[
              {
                title: t('diagTime'),
                dataIndex: 'ts',
                render: (v: number) => formatTime(v),
              },
              { title: t('diagRoute'), dataIndex: 'route' },
              {
                title: t('diagDurationMs'),
                dataIndex: 'durationMs',
                render: (v: number) => msTag(v),
              },
            ]}
          />
        </Card>
      </Spin>
    </PageShell>
  );
}
