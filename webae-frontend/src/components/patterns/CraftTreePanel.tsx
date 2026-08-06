import { useCallback, useMemo, useState } from 'react';
import { Button, Empty, Input, InputNumber, Space, Spin, Table, Tag, Typography } from 'antd';
import { SearchOutlined, NodeExpandOutlined } from '@ant-design/icons';
import { getApiClient } from '@/api/client';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { Icon } from '@/components/Icon';
import type { CraftTreeNodeDto, CraftTreeResponse } from '@/types/dto';

const { Text } = Typography;

interface FlatRow {
  key: string;
  node: CraftTreeNodeDto;
  depth: number;
}

function flattenTree(node: CraftTreeNodeDto | undefined, depth = 0, prefix = '0'): FlatRow[] {
  if (!node) return [];
  const rows: FlatRow[] = [{ key: prefix, node, depth }];
  const children = node.children || [];
  children.forEach((child, i) => {
    rows.push(...flattenTree(child, depth + 1, `${prefix}-${i}`));
  });
  return rows;
}

interface CraftTreePanelProps {
  networkId: number;
}

export function CraftTreePanel({ networkId }: CraftTreePanelProps) {
  const { notify } = useAppContext();
  const { t } = useI18n();
  const [itemQuery, setItemQuery] = useState('');
  const [amount, setAmount] = useState(1);
  const [loading, setLoading] = useState(false);
  const [tree, setTree] = useState<CraftTreeNodeDto | null>(null);

  const loadTree = useCallback(async () => {
    const item = itemQuery.trim();
    if (!item) {
      notify(t('craftTreeItemRequired'), 'warning');
      return;
    }
    setLoading(true);
    try {
      const data = await getApiClient().get<CraftTreeResponse>(
        `/api/craft/tree?item=${encodeURIComponent(item)}&amount=${amount}&network=${networkId}&maxDepth=8`
      );
      if (!data.success || !data.tree) {
        notify(data.message || t('craftTreeLoadFailed'), 'error');
        setTree(null);
        return;
      }
      setTree(data.tree);
    } catch (e) {
      notify((e as Error).message || t('craftTreeLoadFailed'), 'error');
      setTree(null);
    } finally {
      setLoading(false);
    }
  }, [itemQuery, amount, networkId, notify, t]);

  const rows = useMemo(() => flattenTree(tree ?? undefined), [tree]);

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Space wrap>
        <Input
          style={{ minWidth: 220 }}
          placeholder={t('craftTreeItemPlaceholder')}
          value={itemQuery}
          onChange={(e) => setItemQuery(e.target.value)}
          onPressEnter={() => void loadTree()}
          prefix={<SearchOutlined />}
          aria-label={t('craftTreeItemPlaceholder')}
        />
        <InputNumber min={1} max={1_000_000} value={amount} onChange={(v) => setAmount(v ?? 1)} />
        <Button type="primary" icon={<NodeExpandOutlined />} loading={loading} onClick={() => void loadTree()}>
          {t('craftTreeCalculate')}
        </Button>
      </Space>
      <Text type="secondary">{t('craftTreeHint')}</Text>
      <Spin spinning={loading}>
        {rows.length === 0 ? (
          <Empty description={t('craftTreeEmpty')} />
        ) : (
          <Table
            size="small"
            pagination={false}
            rowKey="key"
            dataSource={rows}
            columns={[
              {
                title: t('craftTreeColItem'),
                key: 'item',
                render: (_, r) => (
                  <Space style={{ paddingLeft: r.depth * 16 }}>
                    <Icon id={r.node.itemId || r.node.registryName} size={20} />
                    <span>{r.node.displayName || r.node.registryName || r.node.itemId}</span>
                    {r.node.leaf ? <Tag>{t('craftTreeLeaf')}</Tag> : null}
                    {r.node.patternId ? <Tag color="cyan">{t('craftTreeHasPattern')}</Tag> : null}
                  </Space>
                ),
              },
              { title: t('craftTreeColRequired'), dataIndex: ['node', 'required'], width: 100 },
              {
                title: t('craftTreeColAvailable'),
                key: 'inStock',
                width: 100,
                render: (_, r) => r.node.inStock ?? r.node.available,
              },
              {
                title: t('craftTreeColMissing'),
                key: 'missing',
                width: 100,
                render: (_, r) => {
                  const gap = r.node.toCraft ?? r.node.missing;
                  return gap > 0 ? <Text type="danger">{gap}</Text> : <Text type="success">0</Text>;
                },
              },
            ]}
          />
        )}
      </Spin>
    </Space>
  );
}
