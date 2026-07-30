import { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Empty, Input, Select, Space, Spin, Tag, Typography } from 'antd';
import { InfoCircleOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { getApiClient } from '@/api/client';
import { Icon } from '@/components/Icon';
import { RecipeDetailModal } from '@/components/recipes/RecipeDetailModal';
import { resolveIconItemId } from '@/utils/recipe';
import type {
  RecipeBrowseResponse,
  RecipeDto,
  RecipeHandlersResponse,
  RecipeSearchResponse,
} from '@/types/dto';

const { Text } = Typography;
const PAGE_SIZE = 30;

interface PatternRecipeSidebarProps {
  t: (key: string) => string;
  onUseRecipe: (recipe: RecipeDto) => void;
}

export function PatternRecipeSidebar({ t, onUseRecipe }: PatternRecipeSidebarProps) {
  const [query, setQuery] = useState('');
  const [handler, setHandler] = useState('all');
  const [handlers, setHandlers] = useState<RecipeHandlersResponse['handlers']>([]);
  const [results, setResults] = useState<RecipeDto[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<RecipeDto | null>(null);

  useEffect(() => {
    getApiClient()
      .get<RecipeHandlersResponse>('/api/recipes/handlers')
      .then((data) => setHandlers(data.success ? data.handlers || [] : []))
      .catch(() => setHandlers([]));
  }, []);

  const load = useCallback(async (offset = 0, append = false) => {
    setLoading(true);
    try {
      const trimmed = query.trim();
      const handlerParam = handler !== 'all' ? `&handler=${encodeURIComponent(handler)}` : '';
      if (trimmed) {
        const data = await getApiClient().get<RecipeSearchResponse>(
          `/api/recipes/search?q=${encodeURIComponent(trimmed)}&scope=all&offset=${offset}&limit=${PAGE_SIZE}${handlerParam}`
        );
        if (data.success) {
          setResults((prev) => (append ? [...prev, ...(data.results || [])] : data.results || []));
          setTotal(data.total ?? data.results?.length ?? 0);
        }
      } else {
        const data = await getApiClient().get<RecipeBrowseResponse>(
          `/api/recipes/browse?offset=${offset}&limit=${PAGE_SIZE}${handlerParam}`
        );
        if (data.success) {
          setResults((prev) => (append ? [...prev, ...(data.results || [])] : data.results || []));
          setTotal(data.total ?? data.results?.length ?? 0);
        }
      }
    } catch {
      if (!append) {
        setResults([]);
        setTotal(0);
      }
    } finally {
      setLoading(false);
    }
  }, [handler, query]);

  useEffect(() => {
    const timer = window.setTimeout(() => load(0, false), query.trim() ? 500 : 0);
    return () => window.clearTimeout(timer);
  }, [load, query, handler]);

  const handlerOptions = useMemo(
    () => [
      { value: 'all', label: t('patternRecipeAllTypes') },
      ...handlers.map((entry) => ({
        value: entry.handlerId,
        label: `${entry.handlerName || entry.handlerId} (${entry.recipeCount})`,
      })),
    ],
    [handlers, t]
  );

  return (
    <section className="webae-pattern-rail" aria-label={t('patternRecipeLibrary')}>
      <div className="webae-pattern-rail-header">
        <Text strong>{t('patternRecipeLibrary')}</Text>
        <Text type="secondary" className="webae-text-2xs">
          {t('patternRecipeLibraryHint')}
        </Text>
      </div>
      <Input
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        prefix={<SearchOutlined />}
        placeholder={t('patternRecipeSearchPlaceholder')}
        allowClear
        aria-label={t('patternRecipeSearchPlaceholder')}
      />
      <Select
        value={handler}
        onChange={setHandler}
        options={handlerOptions}
        showSearch
        optionFilterProp="label"
        aria-label={t('patternRecipeTypeFilter')}
      />

      <div className="webae-pattern-recipe-list webae-scroll-panel">
        {loading && results.length === 0 ? (
          <div className="webae-pattern-centered"><Spin /></div>
        ) : results.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('patternRecipeEmpty')} />
        ) : (
          results.map((recipe) => {
            const output = recipe.outputs?.[0];
            const nonConsumableCount = (recipe.inputs || []).filter(
              (input) => input.nonConsumable || input.stackSize <= 0
            ).length;
            return (
              <article className="webae-pattern-recipe-row" key={`${recipe.handlerId}:${recipe.recipeIndex}`}>
                {output ? (
                  <Icon id={resolveIconItemId(output)} size={34} alt={output.displayName || output.registryName} />
                ) : (
                  <div className="webae-icon-placeholder-32" aria-hidden />
                )}
                <div className="webae-pattern-recipe-copy">
                  <Text strong ellipsis={{ tooltip: true }}>
                    {output?.displayName || output?.registryName || recipe.handlerName}
                  </Text>
                  <Space size={4} wrap>
                    <Tag>{recipe.handlerName || recipe.handlerId}</Tag>
                    {nonConsumableCount > 0 && (
                      <Tag color="purple">{t('patternNonConsumableCount').replace('{n}', String(nonConsumableCount))}</Tag>
                    )}
                  </Space>
                </div>
                <Space size={4}>
                  <Button
                    size="small"
                    icon={<InfoCircleOutlined />}
                    aria-label={t('patternRecipeDetail')}
                    onClick={() => setDetail(recipe)}
                  />
                  <Button
                    size="small"
                    type="primary"
                    icon={<PlusOutlined />}
                    onClick={() => onUseRecipe(recipe)}
                  >
                    {t('patternRecipeAdd')}
                  </Button>
                </Space>
              </article>
            );
          })
        )}
      </div>
      {results.length < total && (
        <Button block loading={loading} onClick={() => load(results.length, true)}>
          {t('patternRecipeLoadMore')}
        </Button>
      )}
      <RecipeDetailModal
        open={Boolean(detail)}
        recipes={detail ? [detail] : []}
        onClose={() => setDetail(null)}
        onApplyRecipe={(recipe) => {
          onUseRecipe(recipe);
          setDetail(null);
        }}
        applyLabel={t('useRecipe')}
        t={t}
      />
    </section>
  );
}
