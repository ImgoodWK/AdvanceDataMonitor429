import { useCallback, useEffect, useMemo, useState, type Key } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Empty,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  DeleteOutlined,
  EyeOutlined,
  LineChartOutlined,
  LinkOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { getApiClient } from '@/api/client';
import { PageShell } from '@/components/Layout/PageShell';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { formatTime } from '@/utils/format';

const { Text, Paragraph } = Typography;

interface SparkProfile {
  id: string;
  status: string;
  initiatedBy: string;
  startedAt: number;
  completedAt: number;
  durationSeconds: number;
  resultUrl?: string;
  error?: string;
  messages: string[];
}

interface SparkResponse {
  success: boolean;
  enabled: boolean;
  available: boolean;
  adminOnly: boolean;
  running: boolean;
  current?: SparkProfile | null;
  history: SparkProfile[];
  defaultDurationSeconds: number;
  maxDurationSeconds: number;
}

function statusColor(status: string): string {
  if (status === 'completed') return 'success';
  if (status === 'failed') return 'error';
  if (status === 'running' || status === 'stopping') return 'processing';
  return 'default';
}

function elapsed(profile: SparkProfile): string {
  const end = profile.completedAt > 0 ? profile.completedAt : Date.now();
  const seconds = Math.max(0, Math.round((end - profile.startedAt) / 1000));
  return `${seconds}s`;
}

export function SparkPage() {
  const { t } = useI18n();
  const { isAdmin, isLoggedIn, serverConfig, notify } = useAppContext();
  const [data, setData] = useState<SparkResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [duration, setDuration] = useState(serverConfig?.sparkDefaultDurationSeconds ?? 30);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [viewing, setViewing] = useState<SparkProfile | null>(null);
  const [compareOpen, setCompareOpen] = useState(false);

  const load = useCallback(async () => {
    if (!isLoggedIn) return;
    try {
      const response = await getApiClient().get<SparkResponse>('/api/spark');
      if (response.success) {
        setData(response);
        setDuration((current) => current || response.defaultDurationSeconds);
      }
    } catch {
      setData(null);
    } finally {
      setLoading(false);
    }
  }, [isLoggedIn]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!data?.running) return undefined;
    const timer = window.setInterval(() => void load(), 3000);
    return () => window.clearInterval(timer);
  }, [data?.running, load]);

  const selectedProfiles = useMemo(
    () => selectedIds.map((id) => data?.history.find((profile) => profile.id === id)).filter(Boolean) as SparkProfile[],
    [data?.history, selectedIds]
  );

  const start = async () => {
    try {
      await getApiClient().post('/api/spark/profile', { durationSeconds: duration });
      notify(t('sparkStarted'), 'success');
      await load();
    } catch {
      notify(t('sparkStartFailed'), 'error');
    }
  };

  const stop = async () => {
    try {
      await getApiClient().post('/api/spark/stop');
      notify(t('sparkStopRequested'), 'success');
      await load();
    } catch {
      notify(t('sparkStopFailed'), 'error');
    }
  };

  const remove = async (id: string) => {
    try {
      await getApiClient().delete(`/api/spark/history/${encodeURIComponent(id)}`);
      setSelectedIds((ids) => ids.filter((value) => value !== id));
      await load();
    } catch {
      notify(t('sparkDeleteFailed'), 'error');
    }
  };

  const columns = [
    {
      title: t('sparkStatus'),
      dataIndex: 'status',
      width: 120,
      render: (value: string) => <Tag color={statusColor(value)}>{t(`sparkStatus_${value}`)}</Tag>,
    },
    {
      title: t('sparkStartedAt'),
      dataIndex: 'startedAt',
      width: 180,
      render: (value: number) => <Text type="secondary">{formatTime(value)}</Text>,
    },
    { title: t('sparkInitiatedBy'), dataIndex: 'initiatedBy', width: 140 },
    {
      title: t('sparkDuration'),
      key: 'duration',
      width: 110,
      render: (_: unknown, row: SparkProfile) => elapsed(row),
    },
    {
      title: t('sparkResult'),
      key: 'result',
      render: (_: unknown, row: SparkProfile) =>
        row.resultUrl ? (
          <a href={row.resultUrl} target="_blank" rel="noreferrer">
            <LinkOutlined /> {t('sparkOpenViewer')}
          </a>
        ) : (
          <Text type="secondary">{row.error || t('sparkNoResult')}</Text>
        ),
    },
    {
      title: t('actions'),
      key: 'actions',
      width: 150,
      render: (_: unknown, row: SparkProfile) => (
        <Space size="small">
          <Button size="small" icon={<EyeOutlined />} onClick={() => setViewing(row)}>
            {t('view')}
          </Button>
          {isAdmin && (
            <Popconfirm title={t('sparkDeleteConfirm')} onConfirm={() => void remove(row.id)}>
              <Button size="small" danger icon={<DeleteOutlined />} aria-label={t('delete')} />
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  if (!serverConfig?.sparkEnabled) {
    return (
      <PageShell title={t('sparkPage')} description={t('sparkDescription')}>
        <Alert type="warning" showIcon message={t('sparkUnavailable')} />
      </PageShell>
    );
  }

  return (
    <PageShell
      title={t('sparkPage')}
      description={t('sparkDescription')}
      actions={<Button onClick={() => void load()}>{t('refresh')}</Button>}
    >
      <Spin spinning={loading && !data}>
        {!data ? (
          <Alert type="warning" showIcon message={t('sparkUnavailable')} />
        ) : (
          <>
            <Alert
              type="info"
              showIcon
              icon={<LineChartOutlined />}
              message={t('sparkSafetyNote')}
              description={isAdmin ? t('sparkAdminHint') : t('sparkReadOnlyHint')}
              style={{ marginBottom: 16 }}
            />

            <Row gutter={[16, 16]}>
              <Col xs={24} md={8}>
                <Card size="small" title={t('sparkRunCard')}>
                  <Space direction="vertical" style={{ width: '100%' }}>
                    <Text type="secondary">{t('sparkDurationHint', { max: data.maxDurationSeconds })}</Text>
                    <InputNumber
                      min={5}
                      max={data.maxDurationSeconds}
                      value={duration}
                      onChange={(value) => setDuration(value ?? data.defaultDurationSeconds)}
                      addonAfter="s"
                      style={{ width: '100%' }}
                      disabled={!isAdmin || data.running}
                      aria-label={t('sparkDuration')}
                    />
                    <Space>
                      <Button type="primary" onClick={() => void start()} disabled={!isAdmin || data.running}>
                        {t('sparkStart')}
                      </Button>
                      <Button icon={<StopOutlined />} onClick={() => void stop()} disabled={!isAdmin || !data.running}>
                        {t('sparkStop')}
                      </Button>
                    </Space>
                  </Space>
                </Card>
              </Col>
              <Col xs={12} md={8}>
                <Card size="small" title={t('sparkCurrentRun')}>
                  {data.current ? (
                    <Space direction="vertical">
                      <Tag color={statusColor(data.current.status)}>{t(`sparkStatus_${data.current.status}`)}</Tag>
                      <Text>{t('sparkElapsed')}: {elapsed(data.current)}</Text>
                    </Space>
                  ) : (
                    <Text type="secondary">{t('sparkIdle')}</Text>
                  )}
                </Card>
              </Col>
              <Col xs={12} md={8}>
                <Card size="small" title={t('sparkHistoryCount')}>
                  <Text strong style={{ fontSize: 24 }}>{data.history.length}</Text>
                </Card>
              </Col>
            </Row>

            <Card
              size="small"
              title={t('sparkHistory')}
              style={{ marginTop: 16 }}
              extra={
                <Button disabled={selectedProfiles.length !== 2} onClick={() => setCompareOpen(true)}>
                  {t('sparkCompare')} ({selectedProfiles.length}/2)
                </Button>
              }
            >
              {data.history.length === 0 ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('sparkHistoryEmpty')} />
              ) : (
                <Table
                  size="small"
                  rowKey="id"
                  dataSource={data.history}
                  columns={columns}
                  scroll={{ x: 920 }}
                  rowSelection={{
                    selectedRowKeys: selectedIds,
                    onChange: (keys: Key[]) => setSelectedIds(keys.slice(-2) as string[]),
                  }}
                  pagination={{ pageSize: 20, showSizeChanger: false }}
                />
              )}
            </Card>
          </>
        )}
      </Spin>

      <Modal
        open={!!viewing}
        title={t('sparkDetail')}
        onCancel={() => setViewing(null)}
        footer={null}
        width={760}
      >
        {viewing && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <Space wrap>
              <Tag color={statusColor(viewing.status)}>{t(`sparkStatus_${viewing.status}`)}</Tag>
              <Text>{formatTime(viewing.startedAt)}</Text>
              <Text type="secondary">{viewing.initiatedBy}</Text>
            </Space>
            {viewing.resultUrl && (
              <Button type="link" href={viewing.resultUrl} target="_blank" rel="noreferrer" icon={<LinkOutlined />}>
                {t('sparkOpenViewer')}
              </Button>
            )}
            {viewing.error && <Alert type="error" showIcon message={viewing.error} />}
            <Paragraph code copyable={{ text: (viewing.messages || []).join('\n') }} style={{ maxHeight: 360, overflow: 'auto', whiteSpace: 'pre-wrap' }}>
              {(viewing.messages || []).join('\n') || t('sparkNoOutput')}
            </Paragraph>
          </Space>
        )}
      </Modal>

      <Modal
        open={compareOpen}
        title={t('sparkCompare')}
        onCancel={() => setCompareOpen(false)}
        footer={null}
        width={760}
      >
        <Table
          size="small"
          pagination={false}
          rowKey="metric"
          dataSource={[
            { metric: t('sparkCompareStatus'), a: selectedProfiles[0]?.status, b: selectedProfiles[1]?.status },
            { metric: t('sparkCompareStartedAt'), a: selectedProfiles[0] ? formatTime(selectedProfiles[0].startedAt) : '—', b: selectedProfiles[1] ? formatTime(selectedProfiles[1].startedAt) : '—' },
            { metric: t('sparkCompareElapsed'), a: selectedProfiles[0] ? elapsed(selectedProfiles[0]) : '—', b: selectedProfiles[1] ? elapsed(selectedProfiles[1]) : '—' },
            { metric: t('sparkCompareMessages'), a: selectedProfiles[0]?.messages?.length ?? 0, b: selectedProfiles[1]?.messages?.length ?? 0 },
            { metric: t('sparkCompareViewer'), a: selectedProfiles[0]?.resultUrl || '—', b: selectedProfiles[1]?.resultUrl || '—' },
          ]}
          columns={[
            { title: t('sparkCompareMetric'), dataIndex: 'metric', width: 180 },
            { title: t('sparkCompareA'), dataIndex: 'a', ellipsis: true },
            { title: t('sparkCompareB'), dataIndex: 'b', ellipsis: true },
          ]}
        />
      </Modal>
    </PageShell>
  );
}
