import { memo } from 'react';

import { Card, Tag, Typography } from 'antd';

import { Icon } from '@/components/Icon';
import type { RecipeDto } from '@/types/dto';
import { primaryOutput } from '@/utils/recipe';

const { Text } = Typography;

interface RecipeThumbnailCardProps {
  recipe: RecipeDto;
  t: (k: string) => string;
  onClick?: () => void;
}

export const RecipeThumbnailCard = memo(function RecipeThumbnailCard({
  recipe,
  t,
  onClick,
}: RecipeThumbnailCardProps) {
  const main = primaryOutput(recipe);

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
        {main ? (
          <Icon item={main} size={48} alt={main.displayName} />
        ) : (
          <div className="recipe-thumbnail-icon-placeholder" aria-hidden />
        )}
      </div>
      <Text strong className="recipe-thumbnail-name" ellipsis={{ tooltip: true }}>
        {main?.displayName || main?.registryName || t('output')}
      </Text>
      <Tag className="recipe-thumbnail-tag">{recipe.handlerName}</Tag>
    </Card>
  );
});
