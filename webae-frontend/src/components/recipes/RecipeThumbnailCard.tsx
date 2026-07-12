import { memo } from 'react';

import { Tag, Typography } from 'antd';

import { SelectableCard } from '@/components/common/SelectableCard';
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
    <SelectableCard onClick={onClick}>
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
    </SelectableCard>
  );
});
