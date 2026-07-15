import { useCallback, useEffect, useMemo, useRef, useState, useTransition } from 'react';

import { Button, Card, Progress, Space, Spin, Tag, Typography, message } from 'antd';
import { CloudDownloadOutlined, ReloadOutlined, StopOutlined } from '@ant-design/icons';

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
import { useRecipeSync } from '@/hooks/useRecipeSync';
import { useI18n } from '@/i18n';
import { groupByPrimaryOutput } from '@/utils/recipe';
import { formatBytes } from '@/utils/recipeLocalStore';
import { openGtnhWikiSearch } from '@/utils/wiki';
import type { RecipeDto, RecipeItemEntry, RecipeSuggestEntry } from '@/types/dto';

const BROWSE_PAGE_SIZE = 24;

export function RecipesPage() {
  const { consumePageSearchPrefill } = useAppContext();
  const { t } = useI18n();
  const sync = useRecipeSync();
  const [search, setSearch] = useState('');
  const [searchMode, setSearchMode] = useState<RecipeSearchMode>('query');
  const [browseHandlers, setBrowseHandlers] = useState<string[]>([]);
  const [layoutMode, setLayoutMode] = useState<RecipeLayoutMode>(loadRecipeLayoutMode);
  const [displayMode, setDisplayMode] = useState<RecipeDisplayMode>(loadRecipeDisplayMode);
  const [rawResults, setRawResults] = useState<RecipeDto[]>([]);
  const [resultTotal, setResultTotal] = useState(0);
  const [searching, setSearching] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [searched, setSearched] = useState(false);
  const [browsePage, setBrowsePage] = useState(1);
  const [isBrowseMode, setIsBrowseMode] = useState(true);
  const [detailRecipes, setDetailRecipes] = useState<RecipeDto[]>([]);
  const [detailInitialIndex, setDetailInitialIndex] = useState(0);
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const autoLoadedRef = useRef(false);
  const [, startDisplayTransition] = useTransition();

  const handlers = useMemo(() => {
    if (sync.localMeta?.handlers?.length) return sync.localMeta.handlers;
    return sync.serverManifest?.handlers || [];
  }, [sync.localMeta, sync.serverManifest]);

  const results = rawResults;
  const mergedGroups = useMemo(() => groupByPrimaryOutput(results), [results]);

  const hasLocalData = sync.recipes.length > 0;

  const browseHandlerRecipes = useCallback(
    (page: number, append = false, handlerIds = browseHandlers) => {
      if (!hasLocalData) return;
      if (append) setLoadingMore(true);
      else setSearching(true);
      setSearched(true);
      setIsBrowseMode(true);
      setBrowsePage(page);
      const offset = (page - 1) * BROWSE_PAGE_SIZE;
      const { results: batch, total } = sync.browseLocal(handlerIds, offset, BROWSE_PAGE_SIZE);
      setRawResults((prev) => (append ? [...prev, ...batch] : batch));
      setResultTotal(total);
      setSearching(false);
      setLoadingMore(false);
    },
    [browseHandlers, hasLocalData, sync]
  );

  const doSearch = useCallback(
    (page = 1, append = false, handlerIds = browseHandlers, queryOverride?: string) => {
      const term = (queryOverride ?? search).trim();
      if (!term || !hasLocalData) return;
      if (append) setLoadingMore(true);
      else setSearching(true);
      setSearched(true);
      setIsBrowseMode(false);
      setBrowsePage(page);
      const scope = searchMode === 'output' ? 'output' : searchMode === 'input' ? 'input' : 'all';
      const offset = (page - 1) * BROWSE_PAGE_SIZE;
      const { results: batch, total } = sync.searchLocal(
        term,
        scope,
        handlerIds,
        offset,
        BROWSE_PAGE_SIZE
      );
      setRawResults((prev) => (append ? [...prev, ...batch] : batch));
      setResultTotal(total);
      setSearching(false);
      setLoadingMore(false);
    },
    [browseHandlers, hasLocalData, search, searchMode, sync]
  );

  useEffect(() => {
    const prefill = consumePageSearchPrefill('recipes');
    if (prefill?.query && hasLocalData) {
      setSearch(prefill.query);
      setSearchMode('query');
      doSearch(1, false, browseHandlers, prefill.query);
    }
  }, [consumePageSearchPrefill, doSearch, browseHandlers, hasLocalData]);

  // Progressive browse while syncing / when ready — refresh when local recipe count grows
  useEffect(() => {
    if (!hasLocalData) return;
    if (sync.phase !== 'ready' && sync.phase !== 'syncing') return;
    if (!searched || isBrowseMode) {
      autoLoadedRef.current = true;
      browseHandlerRecipes(1, false, browseHandlers);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- intentional: only on recipe count / phase
  }, [hasLocalData, sync.phase, sync.recipes.length]);

  const handleSelectSuggest = useCallback(
    (entry: RecipeSuggestEntry) => {
      const label = entry.displayName || entry.registryName;
      setSearch(label);
      setSearchMode('output');
      setSearching(true);
      setSearched(true);
      setIsBrowseMode(false);
      setBrowsePage(1);
      const { results: batch, total } = sync.searchExactLocal(
        entry.registryName,
        'output',
        browseHandlers,
        BROWSE_PAGE_SIZE
      );
      setRawResults(batch);
      setResultTotal(total);
      setSearching(false);
    },
    [browseHandlers, sync]
  );

  const handleFetchClick = async (forceFull = false) => {
    const result = await sync.startSync(forceFull);
    if (result.reason === 'uptodate') {
      message.success(t('recipesSyncUpToDate'));
    } else if (result.reason === 'synced') {
      message.success(t('recipesSyncDone'));
      autoLoadedRef.current = false;
    } else if (result.reason === 'empty') {
      message.warning(t('recipesEmptyHint'));
    } else if (result.reason === 'cancelled') {
      message.info(t('recipesSyncCancelled'));
    }
  };

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

  const pct =
    sync.progressTotal > 0 ? Math.round((sync.progressDone / sync.progressTotal) * 100) : 0;

  const serverCount = sync.serverManifest?.recipeCount ?? 0;
  const localCount = sync.localMeta?.complete
    ? sync.localMeta.recipeCount
    : sync.recipes.length;

  return (
    <PageShell title={t('recipes')} description={t('uploadRecipesHint')}>
      <Card size="small">
        <Space wrap style={{ width: '100%' }}>
          {sync.phase === 'syncing' && (
            <Tag color="processing">{t('recipesSyncing')}</Tag>
          )}
          {sync.updateAvailable && <Tag color="warning">{t('recipesUpdateAvailable')}</Tag>}
          {sync.ready && <Tag color="success">{t('recipesLocalReady')}</Tag>}
          <Tag color={localCount > 0 ? 'blue' : 'default'}>
            {localCount} {t('recipesLocalCached')}
          </Tag>
          {serverCount > 0 && (
            <Tag>
              {serverCount} {t('recipesServerAvailable')}
            </Tag>
          )}
          {handlers.length > 0 && (
            <Tag>
              {handlers.length} {t('handlerTypes')}
            </Tag>
          )}
          {sync.serverManifest?.estimatedBytes ? (
            <Tag>
              {t('recipesSyncSize')}: {formatBytes(sync.serverManifest.estimatedBytes)}
            </Tag>
          ) : null}
        </Space>

        {sync.phase === 'syncing' && (
          <div style={{ marginTop: 12 }}>
            <Progress
              percent={pct}
              status="active"
              format={() =>
                t('recipesSyncProgress')
                  .replace('{done}', String(sync.progressDone))
                  .replace('{total}', String(sync.progressTotal))
              }
            />
            <Button icon={<StopOutlined />} onClick={() => sync.cancelSync()} size="small">
              {t('recipesSyncCancel')}
            </Button>
          </div>
        )}

        <Space wrap style={{ marginTop: 12 }}>
          {sync.needsFetch && sync.phase !== 'syncing' && (
            <Button
              type="primary"
              icon={<CloudDownloadOutlined />}
              onClick={() => void handleFetchClick(false)}
              loading={sync.phase === 'checking'}
            >
              {sync.localMeta && !sync.localMeta.complete && sync.localMeta.nextChunkIndex > 0
                ? t('recipesSyncResume')
                : t('recipesFetch')}
            </Button>
          )}
          {sync.ready && sync.phase !== 'syncing' && (
            <Button icon={<ReloadOutlined />} onClick={() => void handleFetchClick(false)}>
              {t('recipesCheckUpdate')}
            </Button>
          )}
          {sync.ready && sync.phase !== 'syncing' && (
            <Button onClick={() => void handleFetchClick(true)}>{t('recipesRefetch')}</Button>
          )}
          {!sync.serverManifest && sync.phase !== 'checking' && (
            <Typography.Text type="secondary">{t('recipesEmptyHint')}</Typography.Text>
          )}
        </Space>

        {!hasLocalData && sync.phase !== 'syncing' && serverCount > 0 && (
          <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
            {t('recipesFetchHint')}
          </Typography.Paragraph>
        )}
        {serverCount === 0 && sync.phase !== 'checking' && (
          <Typography.Paragraph type="warning" style={{ marginTop: 12, marginBottom: 0 }}>
            {t('recipesEmptyHint')}
            <br />
            {t('recipesSnapshotHint')}
          </Typography.Paragraph>
        )}
        {sync.error && sync.error !== 'empty' && (
          <Typography.Paragraph type="danger" style={{ marginTop: 8, marginBottom: 0 }}>
            {t('recipesSyncError')}: {sync.error}
          </Typography.Paragraph>
        )}
      </Card>

      <Card>
        {handlers.length > 0 && hasLocalData && (
          <HandlerCategoryFilter
            handlers={handlers}
            browseHandlers={browseHandlers}
            onBrowseHandlersChange={handleBrowseHandlersChange}
            onBrowseAll={handleBrowseAll}
            t={t}
          />
        )}

        {hasLocalData && (
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
            localSuggest={sync.suggestLocal}
            t={t}
          />
        )}

        {!hasLocalData && sync.phase === 'syncing' ? (
          <div style={{ textAlign: 'center', padding: 40 }}>
            <Spin tip={t('recipesSyncing')} />
          </div>
        ) : searched && hasLocalData ? (
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
        ) : hasLocalData ? (
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            {t('recipeBrowseHint')}
          </Typography.Paragraph>
        ) : null}
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
