import { memo } from 'react';

import { Badge, Card, Tag, Typography } from 'antd';

import { Icon } from '@/components/Icon';
import type { RecipeMergedGroup } from '@/utils/recipe';

const { Text } = Typography;

interface RecipeMergedCardProps {
  group: RecipeMergedGroup;
  t: (k: string) => string;
  onClick?: () => void;
}

export const RecipeMergedCard = memo(function RecipeMergedCard({
  group,
  t,
  onClick,
}: RecipeMergedCardProps) {
  const { primaryOutput, recipes } = group;
  const handlerCount = recipes.length;

  return (
    <Card
      size="small"
      hoverable={Boolean(onClick)}
      onClick={onClick}
      className="recipe-thumbnail-card"
      styles={{
        body: {
          padding: 8,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'space-between',
          height: '100%',
        },
      }}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
      onKeyDown={
        onClick
          ? (e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                onClick();
              }
            }
          : undefined
      }
    >
      <div className="recipe-thumbnail-icon">
        <Icon item={primaryOutput} size={48} alt={primaryOutput.displayName} />
      </div>
      <Text strong className="recipe-thumbnail-name" ellipsis={{ tooltip: true }}>
        {primaryOutput.displayName || primaryOutput.registryName}
      </Text>
      <div className="recipe-merged-footer">
        <Badge count={handlerCount} size="small" aria-label={t('recipeTypeCount')}>
          <Tag>{t('recipeTypes')}</Tag>
        </Badge>
      </div>
    </Card>
  );
});
