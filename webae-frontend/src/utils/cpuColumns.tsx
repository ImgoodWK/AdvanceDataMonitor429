import { Progress, Tag, Tooltip } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useI18n } from '@/i18n';
import { useNumberFormat } from '@/hooks/useNumberFormat';
import { formatBytes, formatDuration } from '@/utils/format';
import type { StorageCpu } from '@/types/dto';

export function useCpuColumns(): ColumnsType<StorageCpu> {
  const { t } = useI18n();
  const fmtNum = useNumberFormat();

  return [
    {
      title: t('cpu'),
      dataIndex: 'name',
      key: 'name',
      render: (v: string) => <strong>{v}</strong>,
    },
    {
      title: t('status'),
      dataIndex: 'isBusy',
      key: 'isBusy',
      render: (v: boolean) =>
        v ? (
          <Tag color="processing">{t('crafting')}</Tag>
        ) : (
          <Tag color="default">{t('idle')}</Tag>
        ),
    },
    {
      title: t('coprocessors'),
      dataIndex: 'coProcessors',
      key: 'coProcessors',
      align: 'right' as const,
      render: (v: number) =>
        v > 0 ? (
          <strong style={{ color: 'var(--accent)' }}>×{v}</strong>
        ) : (
          <span style={{ color: 'var(--text-dim)' }}>—</span>
        ),
    },
    {
      title: t('progress'),
      dataIndex: 'craftingProgress',
      key: 'craftingProgress',
      render: (v: number, record: StorageCpu) =>
        record.isBusy ? (
          <Progress percent={Math.round(v * 100)} size="small" />
        ) : (
          <span style={{ color: 'var(--text-dim)' }}>-</span>
        ),
    },
    {
      title: t('stored'),
      key: 'stored',
      align: 'right' as const,
      render: (_: unknown, record: StorageCpu) => (
        <span>
          {fmtNum(record.storedItems)} / {fmtNum(record.maxItems)}
        </span>
      ),
    },
    {
      title: t('output'),
      key: 'output',
      render: (_: unknown, record: StorageCpu) =>
        record.isBusy && record.finalOutputName ? (
          <span style={{ fontSize: '0.8rem' }}>
            {record.finalOutputName} ×{fmtNum(record.finalOutputAmount)}
          </span>
        ) : (
          <span style={{ color: 'var(--text-dim)' }}>—</span>
        ),
    },
    {
      title: t('cpuStorage'),
      key: 'cpuStorage',
      align: 'right' as const,
      render: (_: unknown, record: StorageCpu) => {
        const total = record.usedStorage + record.availableStorage;
        if (total <= 0) return <span style={{ color: 'var(--text-dim)' }}>—</span>;
        const pct = Math.round((record.usedStorage / total) * 100);
        return (
          <Tooltip title={`${formatBytes(record.usedStorage)} / ${formatBytes(total)}`}>
            <div style={{ minWidth: 80 }}>
              <div style={{ fontSize: '0.7rem', color: 'var(--text-dim)' }}>
                {formatBytes(record.usedStorage)} / {formatBytes(total)}
              </div>
              <Progress percent={pct} size="small" />
            </div>
          </Tooltip>
        );
      },
    },
    {
      title: t('elapsedTime'),
      dataIndex: 'elapsedTime',
      key: 'elapsedTime',
      render: (v: number, record: StorageCpu) =>
        record.isBusy && v > 0 ? (
          <span style={{ fontSize: '0.75rem' }}>{formatDuration(v)}</span>
        ) : (
          <span style={{ color: 'var(--text-dim)' }}>—</span>
        ),
    },
  ];
}

export function cpuRowKey(cpu: StorageCpu, networkId?: number): string {
  const net = networkId ?? cpu.networkId ?? 0;
  return `${net}:${cpu.name}`;
}

export function estimateRemainingMs(cpu: StorageCpu): number | null {
  if (!cpu.isBusy || cpu.elapsedTime <= 0 || cpu.craftingProgress <= 0) return null;
  const progress = cpu.craftingProgress;
  if (progress >= 1) return 0;
  const totalEstimate = cpu.elapsedTime / progress;
  return Math.max(0, Math.round(totalEstimate - cpu.elapsedTime));
}
