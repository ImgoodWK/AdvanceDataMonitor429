import { useEffect, useState } from 'react';

import { Modal, Tabs, Button } from 'antd';

import { RecipeDetailCard } from '@/components/recipes/RecipeDetailCard';
import type { RecipeDto, RecipeItemEntry } from '@/types/dto';
import { primaryOutput } from '@/utils/recipe';

interface RecipeDetailModalProps {
  open: boolean;
  recipes: RecipeDto[];
  initialIndex?: number;
  onClose: () => void;
  onItemClick?: (item: RecipeItemEntry) => void;
  /** When set, shows apply button for the active recipe tab. */
  onApplyRecipe?: (recipe: RecipeDto) => void;
  applyLabel?: string;
  t: (k: string) => string;
}

export function RecipeDetailModal({
  open,
  recipes,
  initialIndex = 0,
  onClose,
  onItemClick,
  onApplyRecipe,
  applyLabel,
  t,
}: RecipeDetailModalProps) {
  const [activeIndex, setActiveIndex] = useState(initialIndex);

  useEffect(() => {
    if (open) setActiveIndex(initialIndex);
  }, [open, initialIndex]);

  if (recipes.length === 0) return null;

  const main = primaryOutput(recipes[0]);
  const title =
    main?.displayName || main?.registryName || recipes[0].handlerName || t('recipes');

  const showTabs = recipes.length > 1;

  return (
    <Modal
      open={open}
      onCancel={onClose}
      footer={
        onApplyRecipe ? (
          <Button
            type="primary"
            onClick={() => {
              onApplyRecipe(recipes[activeIndex]);
              onClose();
            }}
          >
            {applyLabel || t('useRecipe')}
          </Button>
        ) : null
      }
      width={720}
      title={title}
      destroyOnClose
      aria-labelledby="recipe-detail-modal-title"
    >
      {showTabs ? (
        <Tabs
          activeKey={String(activeIndex)}
          onChange={(key) => setActiveIndex(Number(key))}
          items={recipes.map((recipe, idx) => ({
            key: String(idx),
            label: recipe.handlerName,
            children: (
              <RecipeDetailCard recipe={recipe} t={t} onItemClick={onItemClick} />
            ),
          }))}
        />
      ) : (
        <RecipeDetailCard recipe={recipes[0]} t={t} onItemClick={onItemClick} />
      )}
    </Modal>
  );
}
