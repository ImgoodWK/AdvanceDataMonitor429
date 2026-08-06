import { Button, Empty, Input, InputNumber, Segmented, Space, Typography } from 'antd';
import { SearchOutlined, ShoppingCartOutlined } from '@ant-design/icons';
import { SelectableListRow } from '@/components/common/SelectableListRow';
import { Icon } from '@/components/Icon';
import { useI18n } from '@/i18n';
import type { QueryHit, QueryScope } from './orderUtils';

const { Text } = Typography;

interface OrderQueryTabProps {
  queryScope: QueryScope;
  onQueryScopeChange: (scope: QueryScope) => void;
  search: string;
  onSearchChange: (v: string) => void;
  queryHits: QueryHit[];
  amount: number;
  onAmountChange: (v: number) => void;
  submitting: boolean;
  onPlaceOrder: (hit: QueryHit, qty: number) => void;
}

export function OrderQueryTab({
  queryScope,
  onQueryScopeChange,
  search,
  onSearchChange,
  queryHits,
  amount,
  onAmountChange,
  submitting,
  onPlaceOrder,
}: OrderQueryTabProps) {
  const { t } = useI18n();

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Segmented
        value={queryScope}
        onChange={(v) => onQueryScopeChange(v as QueryScope)}
        options={[
          { label: t('orderQueryByOutput'), value: 'output' },
          { label: t('orderQueryByInput'), value: 'input' },
        ]}
        aria-label={t('orderQueryScope')}
      />
      <Input
        placeholder={t('orderQueryPlaceholder')}
        prefix={<SearchOutlined />}
        value={search}
        onChange={(e) => onSearchChange(e.target.value)}
        allowClear
        style={{ width: '100%' }}
      />
      {search.trim() && queryHits.length > 0 && (
        <div className="webae-section-card" style={{ maxHeight: 280, overflow: 'auto', padding: 0 }}>
          {queryHits.map((hit) => (
            <SelectableListRow
              key={hit.key}
              as="div"
              onClick={() => onSearchChange(hit.orderName)}
              leading={
                hit.iconId ? (
                  <Icon id={hit.iconId} item={hit.item} size={28} alt={hit.label} />
                ) : (
                  <span style={{ width: 28, height: 28 }} aria-hidden />
                )
              }
              trailing={
                <Button
                  type="link"
                  size="small"
                  icon={<ShoppingCartOutlined />}
                  loading={submitting}
                  onClick={(e) => {
                    e.stopPropagation();
                    onPlaceOrder(hit, amount);
                  }}
                >
                  {t('placeOrder')}
                </Button>
              }
            >
              <div style={{ fontSize: '0.85rem' }}>{hit.label}</div>
              {hit.subLabel ? (
                <Text type="secondary" style={{ fontSize: '0.7rem' }} ellipsis>
                  {hit.subLabel}
                </Text>
              ) : null}
            </SelectableListRow>
          ))}
        </div>
      )}
      {search.trim() && queryHits.length === 0 && (
        <Empty description={t('orderQueryNoMatch')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
      )}
      <Space>
        <InputNumber min={1} value={amount} onChange={(v) => onAmountChange(v || 1)} aria-label={t('qty')} />
        <Button
          type="primary"
          icon={<ShoppingCartOutlined />}
          loading={submitting}
          onClick={() => {
            const hit = queryHits.find((h) => h.orderName === search || h.label === search);
            if (hit) onPlaceOrder(hit, amount);
            else if (search.trim()) {
              onPlaceOrder({ key: 'manual', label: search, orderName: search, kind: 'item' }, amount);
            }
          }}
          disabled={!search.trim()}
        >
          {submitting ? t('submitting') : t('placeOrder')}
        </Button>
      </Space>
    </Space>
  );
}
