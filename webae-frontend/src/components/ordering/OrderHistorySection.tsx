import { Button, Checkbox, Empty, Space, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useI18n } from '@/i18n';
import type { OrderStatus } from '@/types/dto';

interface OrderHistorySectionProps {
  activeOrders: OrderStatus[];
  orderHistory: OrderStatus[];
  autoRefreshOrders: boolean;
  onAutoRefreshChange: (v: boolean) => void;
  onCancelAll: () => void;
  columns: ColumnsType<OrderStatus>;
  historyColumns?: ColumnsType<OrderStatus>;
}

export function OrderHistorySection({
  activeOrders,
  orderHistory,
  autoRefreshOrders,
  onAutoRefreshChange,
  onCancelAll,
  columns,
  historyColumns,
}: OrderHistorySectionProps) {
  const { t } = useI18n();
  const histCols = historyColumns || columns;

  return (
    <>
      <div style={{ marginTop: 24 }} aria-live="polite">
        <Space style={{ marginBottom: 12 }}>
          <strong>{t('activeOrders')}</strong>
          <Checkbox checked={autoRefreshOrders} onChange={(e) => onAutoRefreshChange(e.target.checked)}>
            {t('autoRefresh')}
          </Checkbox>
          <span style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>{t('orderRefreshHint')}</span>
          <Button size="small" danger onClick={onCancelAll} disabled={activeOrders.length === 0}>
            {t('cancelAll')}
          </Button>
        </Space>
        {activeOrders.length > 0 ? (
          <Table dataSource={activeOrders} columns={columns} rowKey="craftJobId" size="small" pagination={false} />
        ) : (
          <Empty description={t('noActiveOrders')} />
        )}
      </div>

      <div style={{ marginTop: 24 }}>
        <strong style={{ display: 'block', marginBottom: 12 }}>{t('orderHistory')}</strong>
        {orderHistory.length > 0 ? (
          <Table dataSource={orderHistory} columns={histCols} rowKey="craftJobId" size="small" pagination={{ pageSize: 10 }} />
        ) : (
          <Empty description={t('noOrderHistory')} />
        )}
      </div>
    </>
  );
}
