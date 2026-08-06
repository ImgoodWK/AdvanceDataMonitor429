import { Modal, Space, Tag, Typography, Divider, Button, InputNumber, Table } from 'antd';
import { ShoppingCartOutlined } from '@ant-design/icons';

import { Icon } from '@/components/Icon';
import { patternEntryIconId } from '@/utils/icon';
import type { PatternProductGroup } from '@/utils/patternGroup';

const { Text } = Typography;

interface PatternProductModalProps {
  open: boolean;
  group: PatternProductGroup | null;
  t: (k: string, arg?: string | number) => string;
  onClose: () => void;
  onOrderSingle?: (patternId: string, amount: number) => void;
}

export function PatternProductModal({
  open,
  group,
  t,
  onClose,
  onOrderSingle,
}: PatternProductModalProps) {
  if (!group) return null;

  const title = group.primaryOutput.displayName || group.primaryOutput.registryName || t('orderProductTitle');
  const iconId = patternEntryIconId(group.primaryOutput);

  const columns = [
    {
      title: t('patternSourceInterface'),
      key: 'source',
      render: (_: unknown, p: (typeof group.patterns)[number]) => (
        <Space direction="vertical" size={0}>
          <Text style={{ fontSize: '0.8rem' }}>{p.sourceInterfaceName || p.sourceInterface}</Text>
          <Text type="secondary" style={{ fontSize: '0.7rem' }}>{p.sourceInterface}</Text>
        </Space>
      ),
    },
    {
      title: t('patternSlot'),
      dataIndex: 'slotIndex',
      key: 'slotIndex',
      width: 70,
      render: (v: number) => <span style={{ fontSize: '0.8rem' }}>{v}</span>,
    },
    {
      title: t('patternInputs'),
      key: 'inputs',
      render: (_: unknown, p: (typeof group.patterns)[number]) => (
        <Space wrap size={4}>
          {(p.inputs || []).filter(Boolean).map((entry, idx) => {
            const id = patternEntryIconId(entry);
            return (
              <span
                key={`in-${idx}`}
                style={{ display: 'inline-flex', alignItems: 'center', gap: 2 }}
                title={entry!.displayName}
              >
                {id && <Icon id={id} size={22} alt={entry!.displayName} />}
                <Text style={{ fontSize: '0.7rem' }}>×{entry!.stackSize}</Text>
              </span>
            );
          })}
        </Space>
      ),
    },
    {
      title: t('patternOutputs'),
      key: 'outputs',
      render: (_: unknown, p: (typeof group.patterns)[number]) => (
        <Space wrap size={4}>
          {p.outputs.map((entry, idx) => {
            const id = patternEntryIconId(entry);
            return (
              <span
                key={`out-${idx}`}
                style={{ display: 'inline-flex', alignItems: 'center', gap: 2 }}
                title={entry.displayName}
              >
                {id && <Icon id={id} size={22} alt={entry.displayName} />}
                <Text style={{ fontSize: '0.7rem' }}>×{entry.stackSize}</Text>
              </span>
            );
          })}
        </Space>
      ),
    },
    {
      title: t('crafting'),
      key: 'crafting',
      width: 80,
      render: (_: unknown, p: (typeof group.patterns)[number]) => (
        <Tag className={p.crafting ? 'pattern-tag-crafting' : 'pattern-tag-processing'}>{p.crafting ? t('crafting') : t('processing')}</Tag>
      ),
    },
    {
      title: t('orderProductActions'),
      key: 'actions',
      width: 180,
      render: (_: unknown, p: (typeof group.patterns)[number]) => {
        return (
          <Space size={4} wrap>
            <InputNumber
              min={1}
              defaultValue={1}
              size="small"
              id={`amt-${p.patternId}`}
              style={{ width: 70 }}
              aria-label={t('qty')}
            />
            <Button
              type="primary"
              size="small"
              icon={<ShoppingCartOutlined />}
              onClick={() => {
                const el = document.getElementById(`amt-${p.patternId}`) as HTMLInputElement | null;
                const amt = el ? Math.max(1, parseInt(el.value, 10) || 1) : 1;
                onOrderSingle?.(p.patternId, amt);
              }}
            >
              {t('placeOrder')}
            </Button>
          </Space>
        );
      },
    },
  ];

  return (
    <Modal
      open={open}
      onCancel={onClose}
      footer={null}
      width={860}
      title={
        <Space>
          {iconId && <Icon id={iconId} size={28} alt={title} />}
          <span>{title}</span>
          <Tag color="blue">
            {t('orderProductVariantCount').replace('{n}', String(group.patterns.length))}
          </Tag>
          <Tag>
            {t('orderProductInterfaceCount').replace('{n}', String(group.sourceInterfaces.length))}
          </Tag>
        </Space>
      }
      destroyOnClose
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Text type="secondary" style={{ fontSize: '0.8rem' }}>
          {t('orderProductModalHint')}
        </Text>
        <Divider style={{ margin: '4px 0' }} />
        <Table
          dataSource={group.patterns}
          columns={columns}
          rowKey="patternId"
          size="small"
          pagination={group.patterns.length > 8 ? { pageSize: 8 } : false}
          scroll={{ x: 'max-content' }}
        />
      </Space>
    </Modal>
  );
}
