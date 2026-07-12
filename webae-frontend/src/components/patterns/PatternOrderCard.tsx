import { memo } from 'react';

import { Tag, Tooltip, Typography, Space } from 'antd';
import { InfoCircleOutlined, ShoppingCartOutlined } from '@ant-design/icons';

import { Icon } from '@/components/Icon';
import { SelectableCard } from '@/components/common/SelectableCard';
import { patternEntryIconId } from '@/utils/icon';
import type { PatternListEntryDto } from '@/types/dto';

const { Text } = Typography;

interface PatternOrderCardProps {
  pattern: PatternListEntryDto;
  t: (k: string) => string;
  onInfo: () => void;
  onOrder?: () => void;
}

function sourceTagLabel(source: string | undefined, t: (k: string) => string): string | null {
  if (source === 'grid') return t('orderPatternSourceGrid');
  if (source === 'interface') return t('orderPatternSourceInterface');
  return null;
}

export const PatternOrderCard = memo(function PatternOrderCard({
  pattern,
  t,
  onInfo,
  onOrder,
}: PatternOrderCardProps) {
  const primaryOutput = pattern.outputs[0];
  const iconId = patternEntryIconId(primaryOutput);
  const recipeType = pattern.crafting ? t('crafting') : t('processing');
  const sourceLabel = sourceTagLabel(pattern.source, t);

  return (
    <SelectableCard
      selected={false}
      hoverable
      onClick={onInfo}
      className="pattern-order-card"
      cardStyles={{
        body: {
          padding: 8,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'space-between',
          height: '100%',
          position: 'relative',
        },
      }}
    >
      <Tooltip title={t('orderPatternDetail')}>
        <button
          type="button"
          className="pattern-order-info-btn"
          onClick={(e) => {
            e.stopPropagation();
            onInfo();
          }}
          aria-label={t('orderPatternDetail')}
        >
          <InfoCircleOutlined />
        </button>
      </Tooltip>
      {onOrder && (
        <Tooltip title={t('placeOrder')}>
          <button
            type="button"
            className="pattern-order-add-btn"
            onClick={(e) => {
              e.stopPropagation();
              onOrder();
            }}
            aria-label={t('placeOrder')}
          >
            <ShoppingCartOutlined />
          </button>
        </Tooltip>
      )}
      <div className="recipe-thumbnail-icon">
        {iconId ? (
          <Icon
            id={iconId}
            item={{
              registryName: primaryOutput?.registryName,
              displayName: primaryOutput?.displayName,
              meta: primaryOutput?.meta,
            }}
            size={48}
            alt={primaryOutput?.displayName || ''}
          />
        ) : (
          <div className="recipe-thumbnail-icon-placeholder" aria-hidden />
        )}
      </div>
      <Text strong className="recipe-thumbnail-name" ellipsis={{ tooltip: true }}>
        {primaryOutput?.displayName || primaryOutput?.registryName || `#${pattern.slotIndex}`}
      </Text>
      <Text type="secondary" className="pattern-order-interface" ellipsis={{ tooltip: true }} style={{ fontSize: '0.65rem', maxWidth: '100%' }}>
        {pattern.sourceInterfaceName || pattern.sourceInterface}
      </Text>
      <Space size={4} wrap style={{ justifyContent: 'center' }}>
        <Tag className={`recipe-thumbnail-tag ${pattern.crafting ? 'pattern-tag-crafting' : 'pattern-tag-processing'}`}>{recipeType}</Tag>
        {sourceLabel && <Tag color={pattern.source === 'grid' ? 'blue' : 'green'}>{sourceLabel}</Tag>}
      </Space>
    </SelectableCard>
  );
});
