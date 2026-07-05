import { Button, Empty, Skeleton, Table, Tag } from 'antd';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { useNetworkBalance } from '@/hooks/useNetworkBalance';
import { useNumberFormat } from '@/hooks/useNumberFormat';
import type { NetworkBalanceSuggestionDto } from '@/types/dto';

interface NetworkBalanceTableProps {
  networkIds: number[];
  compact?: boolean;
}

export function NetworkBalanceTable({ networkIds, compact }: NetworkBalanceTableProps) {
  const { t } = useI18n();
  const fmtNum = useNumberFormat();
  const { setActivePage, setPageSearchPrefill, setSelectedNetworks } = useAppContext();
  const { suggestions, loading } = useNetworkBalance(networkIds, networkIds.length >= 2);

  const openStorage = (row: NetworkBalanceSuggestionDto) => {
    setSelectedNetworks([row.needyNetworkId]);
    setPageSearchPrefill({
      page: 'storage',
      query: row.displayName,
      networkId: row.needyNetworkId,
    });
    setActivePage('storage');
  };

  const openOrder = (row: NetworkBalanceSuggestionDto) => {
    setSelectedNetworks([row.needyNetworkId]);
    setPageSearchPrefill({
      page: 'order',
      query: row.displayName,
      networkId: row.needyNetworkId,
    });
    setActivePage('order');
  };

  if (networkIds.length < 2) {
    return (
      <Empty
        description={t('networkBalanceNeedTwoNetworks')}
        image={Empty.PRESENTED_IMAGE_SIMPLE}
      />
    );
  }

  if (loading && suggestions.length === 0) {
    return <Skeleton active paragraph={{ rows: compact ? 2 : 4 }} />;
  }

  if (suggestions.length === 0) {
    return (
      <Empty description={t('networkBalanceEmpty')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
    );
  }

  const columns = [
    {
      title: t('networkBalanceResource'),
      dataIndex: 'displayName',
      key: 'displayName',
      ellipsis: true,
      render: (_: unknown, r: NetworkBalanceSuggestionDto) => (
        <span>
          {r.displayName}
          <Tag style={{ marginLeft: 4 }}>{r.resourceType}</Tag>
        </span>
      ),
    },
    {
      title: t('networkBalanceShort'),
      key: 'short',
      width: compact ? 88 : 110,
      render: (_: unknown, r: NetworkBalanceSuggestionDto) => (
        <span>
          <Tag>{r.needyNetworkId}</Tag> {fmtNum(r.needyAmount)}
        </span>
      ),
    },
    {
      title: t('networkBalanceSurplus'),
      key: 'surplus',
      width: compact ? 88 : 110,
      render: (_: unknown, r: NetworkBalanceSuggestionDto) => (
        <span>
          <Tag color="blue">{r.sourceNetworkId}</Tag> {fmtNum(r.sourceAmount)}
        </span>
      ),
    },
    {
      title: t('networkBalanceGap'),
      dataIndex: 'transferable',
      key: 'transferable',
      width: 72,
      render: (v: number) => fmtNum(v),
    },
    ...(compact
      ? []
      : [
          {
            title: t('actions'),
            key: 'actions',
            width: 140,
            render: (_: unknown, r: NetworkBalanceSuggestionDto) => (
              <>
                <Button type="link" size="small" onClick={() => openStorage(r)}>
                  {t('storage')}
                </Button>
                <Button type="link" size="small" onClick={() => openOrder(r)}>
                  {t('aeOrdering')}
                </Button>
              </>
            ),
          },
        ]),
  ];

  return (
    <Table
      size="small"
      pagination={compact ? false : { pageSize: 8, size: 'small' }}
      dataSource={suggestions.map((s, i) => ({ ...s, key: `${s.itemId}-${i}` }))}
      columns={columns}
      onRow={
        compact
          ? (r) => ({
              onClick: () => openStorage(r),
              style: { cursor: 'pointer' },
            })
          : undefined
      }
    />
  );
}
