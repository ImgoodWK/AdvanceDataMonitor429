import { useCallback, useEffect, useState } from 'react';

import { AutoComplete, Button, Input, Segmented, Space } from 'antd';
import { SearchOutlined } from '@ant-design/icons';

import { getApiClient } from '@/api/client';
import type { RecipeSuggestEntry, RecipeSuggestResponse } from '@/types/dto';

export type RecipeSearchMode = 'output' | 'input' | 'query';
export type RecipeLayoutMode = 'compact' | 'detailed';
export type RecipeDisplayMode = 'merged' | 'full';

const LAYOUT_STORAGE_KEY = 'webae-recipe-layout';
const DISPLAY_MODE_STORAGE_KEY = 'webae-recipe-display-mode';

interface RecipeToolbarProps {
  search: string;
  onSearchChange: (v: string) => void;
  searchMode: RecipeSearchMode;
  onSearchModeChange: (v: RecipeSearchMode) => void;
  layoutMode: RecipeLayoutMode;
  onLayoutModeChange: (v: RecipeLayoutMode) => void;
  displayMode: RecipeDisplayMode;
  onDisplayModeChange: (v: RecipeDisplayMode) => void;
  searching: boolean;
  onSearch: () => void;
  onSelectSuggest: (entry: RecipeSuggestEntry) => void;
  t: (k: string) => string;
}

export function loadRecipeLayoutMode(): RecipeLayoutMode {
  try {
    const v = localStorage.getItem(LAYOUT_STORAGE_KEY);
    if (v === 'compact' || v === 'detailed') return v;
  } catch {
    /* ignore */
  }
  return 'compact';
}

export function saveRecipeLayoutMode(mode: RecipeLayoutMode) {
  try {
    localStorage.setItem(LAYOUT_STORAGE_KEY, mode);
  } catch {
    /* ignore */
  }
}

export function loadRecipeDisplayMode(): RecipeDisplayMode {
  try {
    const v = localStorage.getItem(DISPLAY_MODE_STORAGE_KEY);
    if (v === 'merged' || v === 'full') return v;
  } catch {
    /* ignore */
  }
  return 'full';
}

export function saveRecipeDisplayMode(mode: RecipeDisplayMode) {
  try {
    localStorage.setItem(DISPLAY_MODE_STORAGE_KEY, mode);
  } catch {
    /* ignore */
  }
}

export function RecipeToolbar({
  search,
  onSearchChange,
  searchMode,
  onSearchModeChange,
  layoutMode,
  onLayoutModeChange,
  displayMode,
  onDisplayModeChange,
  searching,
  onSearch,
  onSelectSuggest,
  t,
}: RecipeToolbarProps) {
  const [options, setOptions] = useState<Array<{ value: string; label: string; entry: RecipeSuggestEntry }>>(
    []
  );

  const fetchSuggest = useCallback(async (q: string) => {
    if (!q.trim()) {
      setOptions([]);
      return;
    }
    try {
      const data = await getApiClient().get<RecipeSuggestResponse>(
        `/api/recipes/suggest?q=${encodeURIComponent(q.trim())}&limit=20`
      );
      if (data.success && data.suggestions) {
        setOptions(
          data.suggestions.map((s) => ({
            value: s.registryName,
            label: `${s.displayName || s.registryName} (${s.registryName})`,
            entry: s,
          }))
        );
      }
    } catch {
      setOptions([]);
    }
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => {
      if (search.trim().length >= 1) fetchSuggest(search);
    }, 300);
    return () => clearTimeout(timer);
  }, [search, fetchSuggest]);

  return (
    <Space style={{ marginBottom: 16, width: '100%' }} wrap>
      <Segmented
        value={searchMode}
        onChange={(v) => onSearchModeChange(v as RecipeSearchMode)}
        options={[
          { label: t('searchByOutput'), value: 'output' },
          { label: t('searchByInput'), value: 'input' },
          { label: t('searchByQuery'), value: 'query' },
        ]}
        aria-label={t('recipeSearchMode')}
      />
      <AutoComplete
        style={{ width: 320 }}
        options={options}
        value={search}
        onChange={onSearchChange}
        onSelect={(_, opt) => {
          if (opt && 'entry' in opt && opt.entry) {
            onSelectSuggest(opt.entry as RecipeSuggestEntry);
          }
        }}
      >
        <Input
          placeholder={t('searchRecipePlaceholder')}
          prefix={<SearchOutlined />}
          onPressEnter={onSearch}
          allowClear
          aria-label={t('searchRecipePlaceholder')}
        />
      </AutoComplete>
      <Segmented
        value={displayMode}
        onChange={(v) => onDisplayModeChange(v as RecipeDisplayMode)}
        options={[
          { label: t('displayFull'), value: 'full' },
          { label: t('displayMerged'), value: 'merged' },
        ]}
        aria-label={t('recipeDisplayMode')}
      />
      <Segmented
        value={layoutMode}
        onChange={(v) => onLayoutModeChange(v as RecipeLayoutMode)}
        options={[
          { label: t('layoutCompact'), value: 'compact' },
          { label: t('layoutDetailed'), value: 'detailed' },
        ]}
        aria-label={t('recipeLayoutMode')}
      />
      <Button type="primary" icon={<SearchOutlined />} loading={searching} onClick={onSearch}>
        {t('search')}
      </Button>
    </Space>
  );
}
