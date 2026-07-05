import { useCallback, useEffect, useMemo, useRef, useState, useTransition } from 'react';

import { Card, Space, Spin, Tag, Typography } from 'antd';

import { getApiClient } from '@/api/client';
import { HandlerCategoryFilter } from '@/components/recipes/HandlerCategoryFilter';
import { RecipeDetailModal } from '@/components/recipes/RecipeDetailModal';
import {
  loadRecipeDisplayMode,
  loadRecipeLayoutMode,
  RecipeToolbar,
  saveRecipeDisplayMode,
  saveRecipeLayoutMode,
  type RecipeDisplayMode,
  type RecipeLayoutMode,
  type RecipeSearchMode,
} from '@/components/recipes/RecipeToolbar';
import { RecipeResultList } from '@/components/recipes/RecipeResultList';
import { PageShell } from '@/components/Layout/PageShell';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { debugLog } from '@/utils/debugLog';
import { groupByPrimaryOutput } from '@/utils/recipe';
import { openGtnhWikiSearch } from '@/utils/wiki';
import type {
  RecipeBrowseResponse,
  RecipeCacheStatus,
  RecipeDto,
  RecipeHandlerInfo,
  RecipeHandlersResponse,
  RecipeItemEntry,
  RecipeSearchResponse,
  RecipeStatusResponse,
  RecipeSuggestEntry,
} from '@/types/dto';

const BROWSE_PAGE_SIZE = 24;
const BROWSE_ALL = 'all';

function resolveHandlersParam(browseHandlers: string[]): string | undefined {
  if (browseHandlers.length === 0) return undefined;
  return browseHandlers.join(',');
}

export function RecipesPage() {
  const { refreshTick, consumePageSearchPrefill } = useAppContext();
  const { t } = useI18n();
  const [search, setSearch] = useState('');
  const [searchMode, setSearchMode] = useState<RecipeSearchMode>('query');
  const [browseHandlers, setBrowseHandlers] = useState<string[]>([]);
  const [layoutMode, setLayoutMode] = useState<RecipeLayoutMode>(loadRecipeLayoutMode);
  const [displayMode, setDisplayMode] = useState<RecipeDisplayMode>(loadRecipeDisplayMode);
  const [handlers, setHandlers] = useState<RecipeHandlerInfo[]>([]);
  const [rawResults, setRawResults] = useState<RecipeDto[]>([]);
  const [resultTotal, setResultTotal] = useState(0);
  const [searching, setSearching] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [cacheStatus, setCacheStatus] = useState<RecipeCacheStatus | null>(null);
  const [searched, setSearched] = useState(false);
  const [browsePage, setBrowsePage] = useState(1);
  const [isBrowseMode, setIsBrowseMode] = useState(true);
  const [detailRecipes, setDetailRecipes] = useState<RecipeDto[]>([]);
  const [detailInitialIndex, setDetailInitialIndex] = useState(0);
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const autoLoadedRef = useRef(false);
  const [, startDisplayTransition] = useTransition();

  const results = rawResults;

  const mergedGroups = useMemo(() => groupByPrimaryOutput(results), [results]);

  const fetchHandlers = useCallback(async () => {
    try {
      const data = await getApiClient().get<RecipeHandlersResponse>('/api/recipes/handlers');
      if (data.success) setHandlers(data.handlers || []);
    } catch {
      /* ignore */
    }
  }, []);

  const fetchStatus = useCallback(async () => {
    try {
      const data = await getApiClient().get<RecipeStatusResponse>('/api/recipes/status');
      if (data.success && data.status) setCacheStatus(data.status);
    } catch {
      /* ignore */
    }
  }, []);

  useEffect(() => {
    fetchHandlers();
    fetchStatus();
  }, [fetchHandlers, fetchStatus, refreshTick]);

  const browseHandlerRecipes = useCallback(
    async (page: number, append = false, handlerIds = browseHandlers) => {
      if (append) setLoadingMore(true);
      else setSearching(true);
      setSearched(true);
      setIsBrowseMode(true);
      setBrowsePage(page);
      const handlerParam = resolveHandlersParam(handlerIds);
      try {
        const offset = (page - 1) * BROWSE_PAGE_SIZE;
        const params = new URLSearchParams();
        params.set('offset', String(offset));
        params.set('limit', String(BROWSE_PAGE_SIZE));
        if (handlerParam) {
          params.set('handlers', handlerParam);
        } else {
          params.set('handler', BROWSE_ALL);
        }
        const data = await getApiClient().get<RecipeBrowseResponse>(
          `/api/recipes/browse?${params.toString()}`
        );
        if (data.success) {
          const batch = data.results || [];
          setRawResults((prev) => (append ? [...prev, ...batch] : batch));
          setResultTotal(data.total ?? data.count ?? 0);
        }
      } catch {
        if (!append) {
          setRawResults([]);
          setResultTotal(0);
        }
      } finally {
        setSearching(false);
        setLoadingMore(false);
      }
    },
    [browseHandlers]
  );

  const doSearch = useCallback(
    async (page = 1, append = false, handlerIds = browseHandlers, queryOverride?: string) => {
      const term = (queryOverride ?? search).trim();
      if (!term) return;
      if (append) setLoadingMore(true);
      else setSearching(true);
      setSearched(true);
      setIsBrowseMode(false);
      setBrowsePage(page);
      try {
        const params = new URLSearchParams();
        const offset = (page - 1) * BROWSE_PAGE_SIZE;

        params.set('q', term);
        params.set('offset', String(offset));
        params.set('limit', String(BROWSE_PAGE_SIZE));
        if (searchMode === 'output') {
          params.set('scope', 'output');
        } else if (searchMode === 'input') {
          params.set('scope', 'input');
        } else {
          params.set('scope', 'all');
        }
        const handlerParam = resolveHandlersParam(handlerIds);
        if (handlerParam) params.set('handlers', handlerParam);

        const data = await getApiClient().get<RecipeSearchResponse>(
          `/api/recipes/search?${params.toString()}`
        );
        if (data.success) {
          const batch = data.results || [];
          debugLog(
            'patterns',
            'debug',
            'recipe search response: query={} mode={} handlers={} total={} returned={} offset={}',
            term,
            searchMode,
            handlerIds.join(','),
            data.total ?? data.count ?? 0,
            batch.length,
            offset
          );
          setRawResults((prev) => (append ? [...prev, ...batch] : batch));
          setResultTotal(data.total ?? data.count ?? 0);
        }
      } catch {
        if (!append) {
          setRawResults([]);
          setResultTotal(0);
        }
      } finally {
        setSearching(false);
        setLoadingMore(false);
      }
    },
    [search, searchMode, browseHandlers]
  );

  useEffect(() => {
    const prefill = consumePageSearchPrefill('recipes');
    if (prefill?.query) {
      setSearch(prefill.query);
      setSearchMode('query');
      void doSearch(1, false, browseHandlers, prefill.query);
    }
  }, [consumePageSearchPrefill, doSearch, browseHandlers]);

  const handleSelectSuggest = useCallback(
    (entry: RecipeSuggestEntry) => {
      const label = entry.displayName || entry.registryName;
      setSearch(label);
      setSearchMode('output');
      setSearching(true);
      setSearched(true);
      setIsBrowseMode(false);
      setBrowsePage(1);
      const params = new URLSearchParams();
      params.set('output', entry.registryName);
      params.set('limit', String(BROWSE_PAGE_SIZE));
      const handlerParam = resolveHandlersParam(browseHandlers);
      if (handlerParam) params.set('handlers', handlerParam);
      getApiClient()
        .get<RecipeSearchResponse>(`/api/recipes/search?${params.toString()}`)
        .then((data) => {
          if (data.success) {
            setRawResults(data.results || []);
            setResultTotal(data.total ?? data.count ?? 0);
          }
        })
        .catch(() => {
          setRawResults([]);
          setResultTotal(0);
        })
        .finally(() => setSearching(false));
    },
    [browseHandlers]
  );

  useEffect(() => {
    if (autoLoadedRef.current || !cacheStatus || cacheStatus.recipeCount <= 0) return;
    autoLoadedRef.current = true;
    browseHandlerRecipes(1, false, []);
  }, [cacheStatus, browseHandlerRecipes]);

  const handleLayoutChange = (mode: RecipeLayoutMode) => {
    startDisplayTransition(() => {
      setLayoutMode(mode);
      saveRecipeLayoutMode(mode);
    });
  };

  const handleDisplayModeChange = (mode: RecipeDisplayMode) => {
    startDisplayTransition(() => {
      setDisplayMode(mode);
      saveRecipeDisplayMode(mode);
    });
  };

  const handleBrowseHandlersChange = (ids: string[]) => {
    setBrowseHandlers(ids);
    if (isBrowseMode) {
      browseHandlerRecipes(1, false, ids);
    } else if (search.trim()) {
      doSearch(1, false, ids);
    }
  };

  const handleBrowseAll = () => {
    setBrowseHandlers([]);
    browseHandlerRecipes(1, false, []);
  };

  const handleItemClick = (item: RecipeItemEntry) => {
    openGtnhWikiSearch(item);
  };

  const handleRecipeClick = (recipes: RecipeDto[], initialIndex = 0) => {
    setDetailRecipes(recipes);
    setDetailInitialIndex(initialIndex);
    setDetailModalOpen(true);
  };

  const hasMore = rawResults.length < resultTotal;

  const loadMore = useCallback(() => {
    if (loadingMore || searching || !hasMore) return;
    const nextPage = browsePage + 1;
    if (isBrowseMode) {
      browseHandlerRecipes(nextPage, true, browseHandlers);
    } else if (search.trim()) {
      doSearch(nextPage, true, browseHandlers);
    }
  }, [
    browseHandlers,
    browsePage,
    browseHandlerRecipes,
    doSearch,
    hasMore,
    isBrowseMode,
    loadingMore,
    search,
    searching,
  ]);

  return (
    <PageShell title={t('recipes')} description={t('uploadRecipesHint')}>
      {cacheStatus && (
        <Card size="small">
          <Space wrap>
            <Tag color={cacheStatus.recipeCount > 0 ? 'blue' : 'red'}>
              {cacheStatus.recipeCount} {t('recipesCached')}
            </Tag>
            <Tag>
              {cacheStatus.handlerCount} {t('handlerTypes')}
            </Tag>
            {cacheStatus.lastDiskSave > 0 && (
              <Tag>
                {t('lastDiskSave')}: {new Date(cacheStatus.lastDiskSave).toLocaleString()}
              </Tag>
            )}
          </Space>
          {cacheStatus.recipeCount === 0 && (
            <Typography.Paragraph type="warning" style={{ marginTop: 12, marginBottom: 0 }}>
              {t('recipesEmptyHint')}
              <br />
              {t('recipesSnapshotHint')}
            </Typography.Paragraph>
          )}
        </Card>
      )}

      <Card>
        {handlers.length > 0 && (
          <HandlerCategoryFilter
            handlers={handlers}
            browseHandlers={browseHandlers}
            onBrowseHandlersChange={handleBrowseHandlersChange}
            onBrowseAll={handleBrowseAll}
            t={t}
          />
        )}

        <RecipeToolbar
          search={search}
          onSearchChange={setSearch}
          searchMode={searchMode}
          onSearchModeChange={setSearchMode}
          layoutMode={layoutMode}
          onLayoutModeChange={handleLayoutChange}
          displayMode={displayMode}
          onDisplayModeChange={handleDisplayModeChange}
          searching={searching}
          onSearch={() => doSearch(1, false)}
          onSelectSuggest={handleSelectSuggest}
          t={t}
        />

        {!searched && searching ? (
          <div style={{ textAlign: 'center', padding: 40 }}>
            <Spin tip={t('searching')} />
          </div>
        ) : searched ? (
          <RecipeResultList
            results={results}
            mergedGroups={mergedGroups}
            resultTotal={resultTotal}
            layoutMode={layoutMode}
            displayMode={displayMode}
            loading={searching}
            loadingMore={loadingMore}
            hasMore={hasMore}
            onLoadMore={loadMore}
            onItemClick={handleItemClick}
            onRecipeClick={handleRecipeClick}
            t={t}
          />
        ) : (
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            {t('recipeBrowseHint')}
          </Typography.Paragraph>
        )}
      </Card>

      <RecipeDetailModal
        open={detailModalOpen}
        recipes={detailRecipes}
        initialIndex={detailInitialIndex}
        onClose={() => setDetailModalOpen(false)}
        onItemClick={handleItemClick}
        t={t}
      />
    </PageShell>
  );
}
