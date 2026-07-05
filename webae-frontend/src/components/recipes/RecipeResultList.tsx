import { memo, useCallback, useEffect, useRef } from 'react';

import { Empty, Spin, Typography } from 'antd';

import { RecipeDetailCard } from '@/components/recipes/RecipeDetailCard';
import { RecipeMergedCard } from '@/components/recipes/RecipeMergedCard';
import type { RecipeDisplayMode, RecipeLayoutMode } from '@/components/recipes/RecipeToolbar';
import { RecipeThumbnailCard } from '@/components/recipes/RecipeThumbnailCard';
import type { RecipeDto, RecipeItemEntry } from '@/types/dto';
import type { RecipeMergedGroup } from '@/utils/recipe';
import { recipeKey } from '@/utils/recipe';

const { Text } = Typography;

export interface RecipeResultListProps {
  results: RecipeDto[];
  mergedGroups: RecipeMergedGroup[];
  resultTotal: number;
  layoutMode: RecipeLayoutMode;
  displayMode: RecipeDisplayMode;
  loading: boolean;
  loadingMore: boolean;
  hasMore: boolean;
  onLoadMore: () => void;
  onItemClick: (item: RecipeItemEntry) => void;
  onRecipeClick: (recipes: RecipeDto[], initialIndex?: number) => void;
  t: (key: string) => string;
}

interface CompactPaneProps {
  results: RecipeDto[];
  displayMode: RecipeDisplayMode;
  mergedGroups: RecipeMergedGroup[];
  onRecipeClick: (recipes: RecipeDto[], initialIndex?: number) => void;
  t: (key: string) => string;
}

interface DetailedPaneProps extends CompactPaneProps {
  onItemClick: (item: RecipeItemEntry) => void;
}

const RecipeCompactPane = memo(function RecipeCompactPane({
  results,
  displayMode,
  mergedGroups,
  onRecipeClick,
  t,
}: CompactPaneProps) {
  if (displayMode === 'merged') {
    return (
      <div className="recipe-thumbnail-grid">
        {mergedGroups.map((group) => (
          <RecipeMergedCard
            key={group.primaryOutputKey}
            group={group}
            t={t}
            onClick={() => onRecipeClick(group.recipes, 0)}
          />
        ))}
      </div>
    );
  }
  return (
    <div className="recipe-thumbnail-grid">
      {results.map((recipe) => {
        const key = recipeKey(recipe);
        return (
          <RecipeThumbnailCard
            key={key}
            recipe={recipe}
            t={t}
            onClick={() => onRecipeClick([recipe], 0)}
          />
        );
      })}
    </div>
  );
});

const RecipeDetailedPane = memo(function RecipeDetailedPane({
  results,
  displayMode,
  mergedGroups,
  onRecipeClick,
  onItemClick,
  t,
}: DetailedPaneProps) {
  if (displayMode === 'merged') {
    return (
      <div className="recipe-detail-grid">
        {mergedGroups.map((group) => (
          <div key={group.primaryOutputKey} className="recipe-detail-grid-item">
            <RecipeMergedCard
              group={group}
              t={t}
              onClick={() => onRecipeClick(group.recipes, 0)}
            />
          </div>
        ))}
      </div>
    );
  }
  return (
    <div className="recipe-detail-grid">
      {results.map((recipe) => {
        const key = recipeKey(recipe);
        return (
          <div key={key} className="recipe-detail-grid-item">
            <RecipeDetailCard recipe={recipe} t={t} onItemClick={onItemClick} />
          </div>
        );
      })}
    </div>
  );
});

export function RecipeResultList({
  results,
  mergedGroups,
  resultTotal,
  layoutMode,
  displayMode,
  loading,
  loadingMore,
  hasMore,
  onLoadMore,
  onItemClick,
  onRecipeClick,
  t,
}: RecipeResultListProps) {
  const sentinelRef = useRef<HTMLDivElement | null>(null);
  const suppressLoadMoreUntilRef = useRef(0);
  const prevDisplayModeRef = useRef(displayMode);

  useEffect(() => {
    if (prevDisplayModeRef.current !== displayMode) {
      prevDisplayModeRef.current = displayMode;
      suppressLoadMoreUntilRef.current = Date.now() + 800;
    }
  }, [displayMode]);

  const handleIntersect = useCallback(
    (entries: IntersectionObserverEntry[]) => {
      if (Date.now() < suppressLoadMoreUntilRef.current) return;
      if (entries[0]?.isIntersecting && hasMore && !loading && !loadingMore) {
        onLoadMore();
      }
    },
    [hasMore, loading, loadingMore, onLoadMore]
  );

  useEffect(() => {
    const node = sentinelRef.current;
    if (!node || !hasMore) return;
    const observer = new IntersectionObserver(handleIntersect, { rootMargin: '240px' });
    observer.observe(node);
    return () => observer.disconnect();
  }, [handleIntersect, hasMore, displayMode, results.length]);

  if (loading && results.length === 0) {
    return (
      <div style={{ textAlign: 'center', padding: 40 }}>
        <Spin tip={t('searching')} />
      </div>
    );
  }

  if (results.length === 0) {
    return <Empty description={t('noRecipesFound')} />;
  }

  const shownCount = displayMode === 'merged' ? mergedGroups.length : results.length;
  const countLabel = t('recipeShowingCount')
    .replace('{shown}', String(shownCount))
    .replace('{total}', String(resultTotal));

  return (
    <>
      <Text type="secondary" className="recipe-result-count" style={{ display: 'block', marginBottom: 12 }}>
        {countLabel}
      </Text>
      <div className="recipe-layout-viewport">
        {layoutMode === 'compact' ? (
          <div className="recipe-layout-container">
            <RecipeCompactPane
              results={results}
              displayMode={displayMode}
              mergedGroups={mergedGroups}
              onRecipeClick={onRecipeClick}
              t={t}
            />
          </div>
        ) : (
          <div className="recipe-layout-container">
            <RecipeDetailedPane
              results={results}
              displayMode={displayMode}
              mergedGroups={mergedGroups}
              onRecipeClick={onRecipeClick}
              onItemClick={onItemClick}
              t={t}
            />
          </div>
        )}
      </div>
      <div ref={sentinelRef} className="recipe-load-sentinel" aria-hidden />
      {loadingMore && (
        <div style={{ textAlign: 'center', padding: 16 }}>
          <Spin size="small" tip={t('loadingMoreRecipes')} />
        </div>
      )}
      {!hasMore && results.length > 0 && (
        <Text type="secondary" style={{ display: 'block', textAlign: 'center', padding: 12 }}>
          {t('allRecipesLoaded')}
        </Text>
      )}
    </>
  );
}
