import { memo } from 'react';

import { Card, Space, Tag, Typography } from 'antd';

import { Icon } from '@/components/Icon';
import { RecipeGrid } from '@/components/recipes/RecipeGrid';
import type { RecipeDto, RecipeItemEntry } from '@/types/dto';
import { isFluidEntry, splitEntries } from '@/utils/recipe';

const { Text } = Typography;

interface RecipeDetailCardProps {
  recipe: RecipeDto;
  t: (k: string) => string;
  onItemClick?: (item: RecipeItemEntry) => void;
}

function EntryList({
  entries,
  label,
  color,
  onItemClick,
}: {
  entries: RecipeItemEntry[];
  label: string;
  color: string;
  onItemClick?: (item: RecipeItemEntry) => void;
}) {
  if (entries.length === 0) return null;
  return (
    <div style={{ marginBottom: 8 }}>
      <Text strong style={{ fontSize: '0.8rem', color }}>
        {label}:
      </Text>
      <div style={{ marginTop: 4 }}>
        {entries.map((entry, i) => (
          <div
            key={`${entry.registryName}_${i}`}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              margin: '2px 0',
              cursor: onItemClick ? 'pointer' : 'default',
            }}
            onClick={() => onItemClick?.(entry)}
            role={onItemClick ? 'button' : undefined}
            tabIndex={onItemClick ? 0 : undefined}
            onKeyDown={(e) => {
              if (onItemClick && (e.key === 'Enter' || e.key === ' ')) {
                e.preventDefault();
                onItemClick(entry);
              }
            }}
          >
            <Icon item={entry} size={24} alt={entry.displayName} />
            <span style={{ fontSize: '0.8rem' }}>
              {entry.displayName || entry.registryName}
              {isFluidEntry(entry) ? ` (${entry.stackSize} mB)` : ` ×${entry.stackSize}`}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

export const RecipeDetailCard = memo(function RecipeDetailCard({
  recipe,
  t,
  onItemClick,
}: RecipeDetailCardProps) {
  const inputSplit = splitEntries(recipe.inputs || []);
  const outputSplit = splitEntries(recipe.outputs || []);
  const hasGt =
    recipe.euPerTick != null || recipe.durationTicks != null || Boolean(recipe.voltageTier);

  return (
    <Card size="small" title={recipe.handlerName} extra={<Tag>#{recipe.recipeIndex}</Tag>}>
      {hasGt && (
        <Space wrap style={{ marginBottom: 8 }}>
          {recipe.voltageTier != null && (
            <Tag color="purple">
              {t('gtVoltage')}: {recipe.voltageTier}
            </Tag>
          )}
          {recipe.euPerTick != null && (
            <Tag color="gold">
              {t('gtEu')}: {recipe.euPerTick} EU/t
            </Tag>
          )}
          {recipe.durationTicks != null && (
            <Tag color="blue">
              {t('gtDuration')}: {recipe.durationTicks} t
            </Tag>
          )}
        </Space>
      )}

      <RecipeGrid recipe={recipe} onItemClick={onItemClick} />

      <EntryList
        entries={outputSplit.items}
        label={t('outputs')}
        color="var(--success)"
        onItemClick={onItemClick}
      />
      <EntryList
        entries={outputSplit.fluids}
        label={t('fluidOutputs')}
        color="var(--info)"
        onItemClick={onItemClick}
      />
      <EntryList
        entries={inputSplit.items}
        label={t('inputs')}
        color="var(--warning)"
        onItemClick={onItemClick}
      />
      <EntryList
        entries={inputSplit.fluids}
        label={t('fluidInputs')}
        color="var(--info)"
        onItemClick={onItemClick}
      />
    </Card>
  );
});
