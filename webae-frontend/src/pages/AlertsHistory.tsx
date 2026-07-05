import { useCallback, useEffect, useState } from 'react';
import { Card, Empty, Segmented, Space, Spin, Table, Tag, Typography } from 'antd';
import { BellOutlined } from '@ant-design/icons';
import { getApiClient } from '@/api/client';
import { PageShell } from '@/components/Layout/PageShell';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { useVisibilityAwarePolling } from '@/hooks/useVisibilityAwarePolling';
import { formatTime } from '@/utils/format';
import type { AlertHistoryEntryDto, AlertHistoryResponse } from '@/types/dto';

const { Text } = Typography;

type FilterMode = 'all' | 'active' | 'cleared';

const PAGE_SIZE = 50;

function severityColor(severity: string): string {
  if (severity === 'error') return 'error';
  if (severity === 'info') return 'processing';
  return 'warning';
}

function typeLabel(type: string, t: (k: string) => string): string {
  switch (type) {
    case 'inventory_threshold':
      return t('alertTypeInventory');
    case 'cpu_stuck':
      return t('alertTypeCpuStuck');
    case 'gt_error':
      return t('alertTypeGtError');
    case 'order_complete':
      return t('alertTypeOrderComplete');
    case 'channel_overload':
      return t('alertTypeChannelOverload');
    default:
      return type || '—';
  }
}

export function AlertsHistoryPage() {
  const { isLoggedIn, pauseRefreshWhenHidden } = useAppContext();
  const { t } = useI18n();
  const [loading, setLoading] = useState(true);
  const [history, setHistory] = useState<AlertHistoryEntryDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [filter, setFilter] = useState<FilterMode>('all');

  const load = useCallback(async () => {
    if (!isLoggedIn) return;
    setLoading(true);
    try {
      const offset = (page - 1) * PAGE_SIZE;
      const data = await getApiClient().get<AlertHistoryResponse>(
        `/api/alerts/history?offset=${offset}&limit=${PAGE_SIZE}`
      );
      if (data.success) {
        setHistory(data.history ?? []);
        setTotal(data.total ?? 0);
      }
    } catch {
      /* silent */
    } finally {
      setLoading(false);
    }
  }, [isLoggedIn, page]);

  useEffect(() => {
    void load();
  }, [load]);

  useVisibilityAwarePolling(load, isLoggedIn ? 15_000 : null, pauseRefreshWhenHidden);

  const filtered = history.filter((row) => {
    if (filter === 'active') return row.active;
    if (filter === 'cleared') return !row.active;
    return true;
  });

  return (
    <PageShell
      title={t('alertsHistoryTitle')}
      description={t('alertsHistoryDesc')}
      actions={
        <Segmented
          value={filter}
          onChange={(v) => setFilter(v as FilterMode)}
          options={[
            { label: t('alertsHistoryFilterAll'), value: 'all' },
            { label: t('alertsHistoryFilterActive'), value: 'active' },
            { label: t('alertsHistoryFilterCleared'), value: 'cleared' },
          ]}
        />
      }
    >
      <Card>
        <Spin spinning={loading && history.length === 0}>
          {filtered.length === 0 && !loading ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('alertsHistoryEmpty')} />
          ) : (
            <Table
              size="small"
              rowKey="id"
              dataSource={filtered}
              pagination={{
                current: page,
                pageSize: PAGE_SIZE,
                total,
                showSizeChanger: false,
                onChange: (p) => setPage(p),
              }}
              scroll={{ x: 960 }}
              columns={[
                {
                  title: t('alertsHistoryColStatus'),
                  dataIndex: 'active',
                  width: 88,
                  render: (active: boolean) =>
                    active ? (
                      <Tag color="volcano">{t('alertsHistoryActive')}</Tag>
                    ) : (
                      <Tag>{t('alertsHistoryCleared')}</Tag>
                    ),
                },
                {
                  title: t('alertsHistoryColSeverity'),
                  dataIndex: 'severity',
                  width: 88,
                  render: (v: string) => <Tag color={severityColor(v)}>{v}</Tag>,
                },
                {
                  title: t('alertsHistoryColType'),
                  dataIndex: 'type',
                  width: 140,
                  render: (v: string) => typeLabel(v, t),
                },
                {
                  title: t('alertsHistoryColTitle'),
                  dataIndex: 'title',
                  ellipsis: true,
                },
                {
                  title: t('alertsHistoryColMessage'),
                  dataIndex: 'message',
                  ellipsis: true,
                },
                {
                  title: t('alertsHistoryColNetwork'),
                  dataIndex: 'networkId',
                  width: 88,
                  render: (v: number) => (v < 0 ? '—' : v),
                },
                {
                  title: t('alertsHistoryColFirstSeen'),
                  dataIndex: 'firstSeenAt',
                  width: 168,
                  render: (v: number) => <Text type="secondary">{formatTime(v)}</Text>,
                },
                {
                  title: t('alertsHistoryColLastSeen'),
                  dataIndex: 'lastSeenAt',
                  width: 168,
                  render: (v: number) => <Text type="secondary">{formatTime(v)}</Text>,
                },
                {
                  title: t('alertsHistoryColCleared'),
                  dataIndex: 'clearedAt',
                  width: 168,
                  render: (v: number, row) =>
                    row.active || !v ? '—' : <Text type="secondary">{formatTime(v)}</Text>,
                },
              ]}
            />
          )}
        </Spin>
        <Space style={{ marginTop: 12 }}>
          <BellOutlined />
          <Text type="secondary">{t('alertsHistoryFootnote')}</Text>
        </Space>
      </Card>
    </PageShell>
  );
}
