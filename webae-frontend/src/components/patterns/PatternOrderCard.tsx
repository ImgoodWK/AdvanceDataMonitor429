import { memo } from 'react';

import { Card, Checkbox, Tag, Tooltip, Typography, Space } from 'antd';
import { InfoCircleOutlined, PlusOutlined } from '@ant-design/icons';

import { Icon } from '@/components/Icon';
import type { PatternListEntryDto } from '@/types/dto';

const { Text } = Typography;

function entryIconId(entry: { registryName: string; meta?: number; isFluid?: boolean } | null): string | undefined {
  if (!entry || !entry.registryName) return undefined;
  if (entry.isFluid) return 'fluid:' + entry.registryName;
  return entry.meta && entry.meta > 0 ? `${entry.registryName}:${entry.meta}` : entry.registryName;
}

interface PatternOrderCardProps {
  pattern: PatternListEntryDto;
  selected: boolean;
  t: (k: string) => string;
  onToggle: () => void;
  onInfo: () => void;
  onAddToBatch?: () => void;
}

function sourceTagLabel(source: string | undefined, t: (k: string) => string): string | null {
  if (source === 'grid') return t('orderPatternSourceGrid');
  if (source === 'interface') return t('orderPatternSourceInterface');
  return null;
}

export const PatternOrderCard = memo(function PatternOrderCard({
  pattern,
  selected,
  t,
  onToggle,
  onInfo,
  onAddToBatch,
}: PatternOrderCardProps) {
  const primaryOutput = pattern.outputs[0];
  const iconId = entryIconId(primaryOutput);
  const recipeType = pattern.crafting ? t('crafting') : t('processing');
  const sourceLabel = sourceTagLabel(pattern.source, t);

  return (
    <Card
      size="small"
      hoverable
      onClick={onToggle}
      className={`recipe-thumbnail-card pattern-order-card${selected ? ' pattern-order-card-selected' : ''}`}
      styles={{
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
      role="button"
      tabIndex={0}
      aria-pressed={selected}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          onToggle();
        }
      }}
    >
      <Checkbox
        checked={selected}
        onClick={(e) => e.stopPropagation()}
        onChange={onToggle}
        style={{ position: 'absolute', top: 4, left: 4, zIndex: 1 }}
        aria-label={t('orderPatternSelected').replace('{n}', selected ? '1' : '0')}
      />
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
      {onAddToBatch && (
        <Tooltip title={t('orderPatternAddToBatch')}>
          <button
            type="button"
            className="pattern-order-add-btn"
            onClick={(e) => {
              e.stopPropagation();
              onAddToBatch();
            }}
            aria-label={t('orderPatternAddToBatch')}
          >
            <PlusOutlined />
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
    </Card>
  );
});
