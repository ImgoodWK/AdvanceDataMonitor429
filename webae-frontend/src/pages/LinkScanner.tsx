import { useCallback, useEffect, useMemo, useState } from 'react';
import { Card, Empty, Input, Select, Space, Spin, Table, Tag } from 'antd';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { getApiClient } from '@/api/client';
import { PageShell } from '@/components/Layout/PageShell';
import { useI18n } from '@/i18n';
import type { LinkScannerBlockDto, LinkScannerResponse } from '@/types/dto';

const TYPE_OPTIONS = [
  { value: '', labelKey: 'scannerAllTypes' },
  { value: 'data_monitor', labelKey: 'scannerTypeDataMonitor' },
  { value: 'network_link', labelKey: 'scannerTypeNetworkLink' },
];

export function LinkScannerPage() {
  const { t } = useI18n();
  const [loading, setLoading] = useState(false);
  const [blocks, setBlocks] = useState<LinkScannerBlockDto[]>([]);
  const [typeFilter, setTypeFilter] = useState('');
  const [query, setQuery] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      if (typeFilter) params.set('type', typeFilter);
      if (query.trim()) params.set('q', query.trim());
      const qs = params.toString();
      const data = await getApiClient().get<LinkScannerResponse>(
        '/api/scanner/blocks' + (qs ? '?' + qs : '')
      );
      setBlocks(data.blocks || []);
    } catch {
      setBlocks([]);
    } finally {
      setLoading(false);
    }
  }, [typeFilter, query]);

  useEffect(() => {
    void load();
  }, [load]);

  const columns = useMemo(
    () => [
      { title: t('scannerColType'), dataIndex: 'blockTypeId', key: 'type', render: (v: string) => <Tag>{v}</Tag> },
      { title: t('scannerColDim'), dataIndex: 'dimension', key: 'dim', width: 70 },
      {
        title: t('scannerColCoords'),
        key: 'coords',
        render: (_: unknown, r: LinkScannerBlockDto) => `${r.x}, ${r.y}, ${r.z}`,
      },
      { title: t('scannerColOwner'), dataIndex: 'owner', key: 'owner', ellipsis: true },
    ],
    [t]
  );

  return (
    <PageShell title={t('linkScanner')} description={t('linkScannerDesc')}>
      <Card>
        <Space wrap style={{ marginBottom: 16 }}>
          <Select
            style={{ minWidth: 180 }}
            value={typeFilter}
            onChange={setTypeFilter}
            options={TYPE_OPTIONS.map((o) => ({ value: o.value, label: t(o.labelKey) }))}
          />
          <Input
            prefix={<SearchOutlined />}
            placeholder={t('scannerSearchPlaceholder')}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onPressEnter={() => void load()}
            style={{ width: 260 }}
          />
          <a onClick={() => void load()} role="button" tabIndex={0}>
            <ReloadOutlined /> {t('refresh')}
          </a>
        </Space>
        <Spin spinning={loading}>
          {blocks.length === 0 ? (
            <Empty description={t('scannerEmpty')} />
          ) : (
            <Table rowKey="locationKey" size="small" pagination={{ pageSize: 20 }} columns={columns} dataSource={blocks} />
          )}
        </Spin>
      </Card>
    </PageShell>
  );
}
