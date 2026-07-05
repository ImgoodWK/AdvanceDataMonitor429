import { useMemo, useState, useEffect } from 'react';
import { Card, Empty, Input, Progress, Select, Space, Table, Tag } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { useSnapshotData } from '@/hooks/useSnapshotData';
import { useNumberFormat } from '@/hooks/useNumberFormat';
import { PageShell } from '@/components/Layout/PageShell';
import { ExportCsvButton } from '@/components/ExportCsvButton';
import { GtSummaryCharts } from '@/components/gt/GtSummaryCharts';
import { isGtMachineErrorRow } from '@/utils/gtChartData';
import type { GtMachineDto } from '@/types/dto';

export function GtMachinesPage() {
  const { selectedNetworks, consumePageSearchPrefill } = useAppContext();
  const { t } = useI18n();
  const { gtMap, loading } = useSnapshotData();
  const fmtNum = useNumberFormat();
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [recipeMapFilter, setRecipeMapFilter] = useState<string>('all');

  useEffect(() => {
    const prefill = consumePageSearchPrefill('gtmachines');
    if (prefill?.query) setSearch(prefill.query);
  }, [consumePageSearchPrefill]);

  // Merge machines across selected networks
  const allMachines = useMemo(() => {
    const machines: Array<GtMachineDto & { _net: number }> = [];
    for (const nid of selectedNetworks) {
      const data = gtMap[nid];
      if (data?.machines) {
        for (const m of data.machines) {
          machines.push({ ...m, _net: nid });
        }
      }
    }
    return machines;
  }, [gtMap, selectedNetworks]);

  const recipeMaps = useMemo(() => {
    const set = new Set<string>();
    for (const m of allMachines) {
      if (m.recipeMapName) set.add(m.recipeMapName);
    }
    return Array.from(set);
  }, [allMachines]);

  const filtered = allMachines.filter((m) => {
    if (statusFilter !== 'all' && m.statusText !== statusFilter) return false;
    if (recipeMapFilter !== 'all' && m.recipeMapName !== recipeMapFilter) return false;
    if (search) {
      const q = search.toLowerCase();
      return (
        (m.recipeMapName || '').toLowerCase().includes(q) ||
        (m.machineMode || '').toLowerCase().includes(q) ||
        (m.currentOutput || '').toLowerCase().includes(q) ||
        `${m.x},${m.y},${m.z}`.includes(q)
      );
    }
    return true;
  });

  const statusColor: Record<string, string> = {
    Running: 'success',
    Idle: 'default',
    Error: 'error',
    Problem: 'warning',
    Maintenance: 'warning',
  };

  const columns = [
    {
      title: t('networkId'),
      dataIndex: '_net',
      key: '_net',
      width: 70,
      render: (v: number) => <Tag>{v}</Tag>,
    },
    {
      title: 'XYZ',
      key: 'xyz',
      render: (_: unknown, r: GtMachineDto) => (
        <code style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>
          {r.x},{r.y},{r.z} (dim {r.dim})
        </code>
      ),
    },
    {
      title: t('status'),
      dataIndex: 'statusText',
      key: 'statusText',
      render: (v: string) => <Tag color={statusColor[v] || 'default'}>{v || '-'}</Tag>,
      filters: [
        { text: t('running'), value: 'Running' },
        { text: t('idle'), value: 'Idle' },
        { text: t('error'), value: 'Error' },
        { text: t('problem'), value: 'Problem' },
      ],
      onFilter: (value: unknown, record: GtMachineDto) => record.statusText === value,
    },
    {
      title: t('recipes'),
      dataIndex: 'recipeMapName',
      key: 'recipeMapName',
      ellipsis: true,
      render: (v: string) => v || '-',
    },
    {
      title: t('progress'),
      key: 'progress',
      width: 160,
      render: (_: unknown, r: GtMachineDto) =>
        r.maxProgressTime > 0 ? (
          <div style={{ minWidth: 120 }}>
            <Progress
              percent={Math.round(r.progressPercent)}
              size="small"
              status={isGtMachineErrorRow(r) ? 'exception' : 'active'}
              format={(pct) => `${pct}%`}
            />
            <span style={{ fontSize: '0.65rem', color: 'var(--text-dim)' }}>
              {r.progressTime}/{r.maxProgressTime}t
            </span>
          </div>
        ) : (
          <span style={{ color: 'var(--text-dim)' }}>-</span>
        ),
    },
    {
      title: t('voltage'),
      key: 'voltage',
      render: (_: unknown, r: GtMachineDto) => (
        <span style={{ fontSize: '0.8rem' }}>
          {r.inputVoltage > 0 ? `IN ${fmtNum(r.inputVoltage)}` : ''}
          {r.inputVoltage > 0 && r.outputVoltage > 0 ? ' / ' : ''}
          {r.outputVoltage > 0 ? `OUT ${fmtNum(r.outputVoltage)}` : ''}
          {!r.inputVoltage && !r.outputVoltage ? '-' : ''}
        </span>
      ),
    },
    {
      title: t('parallel'),
      dataIndex: 'parallelCount',
      key: 'parallelCount',
      align: 'right' as const,
      render: (v: number) => (v > 1 ? <Tag color="blue">×{v}</Tag> : v || '-'),
    },
    {
      title: t('output'),
      dataIndex: 'currentOutput',
      key: 'currentOutput',
      ellipsis: true,
      render: (v: string) => v || '-',
    },
  ];

  if (allMachines.length === 0) {
    return (
      <PageShell title={t('gtMachines')}>
        <Card>
          <Empty
            description={
              selectedNetworks.length === 0 ? t('selectNetworkFirst') : t('noGTData')
            }
          />
        </Card>
      </PageShell>
    );
  }

  return (
    <PageShell
      title={t('gtMachines')}
      actions={
        <ExportCsvButton
          filename="gt-machines.csv"
          headers={[
            t('networkId'),
            'X',
            'Y',
            'Z',
            t('recipeMap'),
            t('status'),
            t('progress'),
            t('currentOutput'),
          ]}
          rows={filtered.map((m) => [
            m._net,
            m.x,
            m.y,
            m.z,
            m.recipeMapName || '',
            m.statusText || '',
            m.progressPercent,
            m.currentOutput || '',
          ])}
          disabled={filtered.length === 0}
        />
      }
    >
      <GtSummaryCharts machines={allMachines} t={t} fmtNum={fmtNum} />
      <Card
        title={
          <Space wrap>
            <span>{t('gtMachines')}</span>
            <Tag>{allMachines.length}</Tag>
          </Space>
        }
        loading={loading && allMachines.length === 0}
      >
      <Space style={{ marginBottom: 16 }} wrap>
        <Input
          placeholder={t('searchGt')}
          prefix={<SearchOutlined />}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          allowClear
          style={{ width: 200 }}
        />
        <Select
          value={statusFilter}
          onChange={setStatusFilter}
          style={{ width: 140 }}
          options={[
            { label: t('allStatus'), value: 'all' },
            { label: t('running'), value: 'Running' },
            { label: t('idle'), value: 'Idle' },
            { label: t('error'), value: 'Error' },
            { label: t('problem'), value: 'Problem' },
            { label: t('maintenance'), value: 'Maintenance' },
          ]}
        />
        <Select
          value={recipeMapFilter}
          onChange={setRecipeMapFilter}
          style={{ width: 180 }}
          options={[
            { label: t('allRecipeMaps'), value: 'all' },
            ...recipeMaps.map((r) => ({ label: r, value: r })),
          ]}
        />
      </Space>
      <Table
        dataSource={filtered}
        columns={columns}
        rowKey={(r) => `${r._net}_${r.x}_${r.y}_${r.z}`}
        size="small"
        rowClassName={(r) => (isGtMachineErrorRow(r) ? 'gt-row-error' : '')}
        pagination={{ pageSize: 50, showSizeChanger: true, showTotal: (total) => `${t('showing')} ${total}` }}
      />
      </Card>
    </PageShell>
  );
}
