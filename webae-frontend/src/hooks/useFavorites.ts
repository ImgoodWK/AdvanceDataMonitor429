import { useCallback, useEffect, useState } from 'react';
import { getApiClient } from '@/api/client';
import type { WebFavoritesDto } from '@/types/dto';

const EMPTY: WebFavoritesDto = { recipes: [], patterns: [], items: [] };

export function recipeFavoriteKey(handlerId: string, recipeIndex: number): string {
  return `${handlerId}#${recipeIndex}`;
}

export function useFavorites() {
  const [favorites, setFavorites] = useState<WebFavoritesDto>(EMPTY);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getApiClient().get<{ success: boolean; favorites?: WebFavoritesDto }>(
        '/api/favorites'
      );
      setFavorites(data.favorites ?? EMPTY);
    } catch {
      setFavorites(EMPTY);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const save = useCallback(async (next: WebFavoritesDto) => {
    const data = await getApiClient().put<{ success: boolean; favorites?: WebFavoritesDto }>(
      '/api/favorites',
      { favorites: next }
    );
    const saved = data.favorites ?? next;
    setFavorites(saved);
    return saved;
  }, []);

  const toggleRecipe = useCallback(
    async (key: string) => {
      const list = [...(favorites.recipes || [])];
      const idx = list.indexOf(key);
      if (idx >= 0) {
        list.splice(idx, 1);
      } else {
        list.unshift(key);
      }
      return save({ ...favorites, recipes: list });
    },
    [favorites, save]
  );

  const togglePattern = useCallback(
    async (patternId: string) => {
      const list = [...(favorites.patterns || [])];
      const idx = list.indexOf(patternId);
      if (idx >= 0) {
        list.splice(idx, 1);
      } else {
        list.unshift(patternId);
      }
      return save({ ...favorites, patterns: list });
    },
    [favorites, save]
  );

  const isRecipeFavorite = useCallback(
    (key: string) => (favorites.recipes || []).includes(key),
    [favorites.recipes]
  );

  const isPatternFavorite = useCallback(
    (patternId: string) => (favorites.patterns || []).includes(patternId),
    [favorites.patterns]
  );

  const favoriteRecipeItems = useCallback((): PaletteFavoriteItem[] => {
    return (favorites.recipes || []).map((key) => ({
      id: `fav-recipe:${key}`,
      kind: 'recipe' as const,
      label: key.split('#')[0] || key,
      subtitle: key,
      favoriteKey: key,
    }));
  }, [favorites.recipes]);

  return {
    favorites,
    loading,
    reload: load,
    toggleRecipe,
    togglePattern,
    isRecipeFavorite,
    isPatternFavorite,
    favoriteRecipeItems,
  };
}

export interface PaletteFavoriteItem {
  id: string;
  kind: 'recipe';
  label: string;
  subtitle?: string;
  favoriteKey: string;
}
