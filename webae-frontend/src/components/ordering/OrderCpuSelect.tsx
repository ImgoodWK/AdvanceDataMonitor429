import { Select, Space, Typography } from 'antd';
import { useI18n } from '@/i18n';
import type { StorageCpu } from '@/types/dto';
import { formatStorageBytes } from './orderUtils';

const { Text } = Typography;

interface OrderCpuSelectProps {
  selectedCpu: string | undefined;
  onChange: (cpu: string | undefined) => void;
  cpuOptions: Array<{ value: string; label: string; cpu?: StorageCpu }>;
}

export function OrderCpuSelect({ selectedCpu, onChange, cpuOptions }: OrderCpuSelectProps) {
  const { t } = useI18n();

  return (
    <Space wrap align="start">
      <label htmlFor="order-cpu-select" style={{ fontSize: '0.85rem' }}>
        {t('orderCpuSelect')}:
      </label>
      <Select
        id="order-cpu-select"
        style={{ minWidth: 320 }}
        value={selectedCpu ?? ''}
        onChange={(v) => onChange(v || undefined)}
        options={cpuOptions}
        optionRender={(opt) => {
          const cpu = (opt.data as { cpu?: StorageCpu }).cpu;
          if (!cpu) return opt.label;
          return (
            <Space direction="vertical" size={0} style={{ padding: '2px 0' }}>
              <Text strong style={{ fontSize: '0.85rem' }}>{cpu.name}</Text>
              <Text type="secondary" style={{ fontSize: '0.7rem' }}>
                {t('orderCpuStorage')}: {formatStorageBytes(cpu.availableStorage)} ·{' '}
                {t('orderCpuCoProcessors')}: {cpu.coProcessors} ·{' '}
                {t('orderCpuParallelism')}: {Math.max(1, cpu.coProcessors + 1)} ·{' '}
                {cpu.isBusy ? t('busy') : t('orderCpuIdle')}
              </Text>
            </Space>
          );
        }}
      />
    </Space>
  );
}
