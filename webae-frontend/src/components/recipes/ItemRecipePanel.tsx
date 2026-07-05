import { useCallback, useEffect, useState } from 'react';

import { Drawer, Empty, Spin, Tabs } from 'antd';

import { getApiClient } from '@/api/client';
import { RecipeDetailCard } from '@/components/recipes/RecipeDetailCard';
import type { RecipeDto, RecipeItemEntry, RecipeSearchResponse } from '@/types/dto';
import { recipeKey } from '@/utils/recipe';

interface ItemRecipePanelProps {
  item: RecipeItemEntry | null;
  open: boolean;
  onClose: () => void;
  t: (k: string) => string;
  handlerFilter?: string;
}

export function ItemRecipePanel({ item, open, onClose, t, handlerFilter }: ItemRecipePanelProps) {
  const [craftRecipes, setCraftRecipes] = useState<RecipeDto[]>([]);
  const [usageRecipes, setUsageRecipes] = useState<RecipeDto[]>([]);
  const [loading, setLoading] = useState(false);

  const loadRecipes = useCallback(async () => {
    if (!item?.registryName) return;
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.set('output', item.registryName);
      if (handlerFilter && handlerFilter !== 'all') params.set('handler', handlerFilter);
      const craft = await getApiClient().get<RecipeSearchResponse>(
        `/api/recipes/search?${params.toString()}`
      );

      const usageParams = new URLSearchParams();
      usageParams.set('input', item.registryName);
      if (handlerFilter && handlerFilter !== 'all') usageParams.set('handler', handlerFilter);
      const usage = await getApiClient().get<RecipeSearchResponse>(
        `/api/recipes/search?${usageParams.toString()}`
      );

      setCraftRecipes(craft.success ? craft.results || [] : []);
      setUsageRecipes(usage.success ? usage.results || [] : []);
    } catch {
      setCraftRecipes([]);
      setUsageRecipes([]);
    } finally {
      setLoading(false);
    }
  }, [item, handlerFilter]);

  useEffect(() => {
    if (open && item) {
      loadRecipes();
    }
  }, [open, item, loadRecipes]);

  return (
    <Drawer
      title={item ? item.displayName || item.registryName : ''}
      open={open}
      onClose={onClose}
      width={480}
      destroyOnClose
    >
      {loading ? (
        <div style={{ textAlign: 'center', padding: 40 }}>
          <Spin tip={t('searching')} />
        </div>
      ) : (
        <Tabs
          items={[
            {
              key: 'craft',
              label: `${t('craftRecipes')} (${craftRecipes.length})`,
              children:
                craftRecipes.length === 0 ? (
                  <Empty description={t('noRecipesFound')} />
                ) : (
                  craftRecipes.map((recipe, idx) => (
                    <div key={recipeKey(recipe, idx)} style={{ marginBottom: 12 }}>
                      <RecipeDetailCard recipe={recipe} t={t} />
                    </div>
                  ))
                ),
            },
            {
              key: 'usage',
              label: `${t('usageRecipes')} (${usageRecipes.length})`,
              children:
                usageRecipes.length === 0 ? (
                  <Empty description={t('noRecipesFound')} />
                ) : (
                  usageRecipes.map((recipe, idx) => (
                    <div key={recipeKey(recipe, idx)} style={{ marginBottom: 12 }}>
                      <RecipeDetailCard recipe={recipe} t={t} />
                    </div>
                  ))
                ),
            },
          ]}
        />
      )}
    </Drawer>
  );
}
