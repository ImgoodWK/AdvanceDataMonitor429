import { useCallback, useEffect, useMemo, useState } from 'react';
import { Input, Modal, List, Tag, Spin } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { useAppContext, type PageId } from '@/context/AppContext';
import { useGlobalSearch } from '@/hooks/useGlobalSearch';
import { useFavorites } from '@/hooks/useFavorites';
import { useI18n } from '@/i18n';
import { ALL_PAGES } from '@/components/Layout/navConfig';
import type { GlobalSearchResultDto } from '@/types/dto';

interface CommandPaletteProps {
  open: boolean;
  onClose: () => void;
}

type PaletteKind = 'page' | 'favorite' | GlobalSearchResultDto['type'];

interface PaletteItem {
  id: string;
  kind: PaletteKind;
  label: string;
  subtitle?: string;
  keywords: string;
  pageId?: PageId;
  searchResult?: GlobalSearchResultDto;
  favoriteKey?: string;
}

const TAG_COLORS: Record<PaletteKind, string> = {
  page: 'blue',
  favorite: 'gold',
  storage: 'green',
  recipe: 'orange',
  gt: 'purple',
  pattern: 'cyan',
  quest: 'geekblue',
};

export function CommandPalette({ open, onClose }: CommandPaletteProps) {
  const { setActivePage, setPageSearchPrefill, setSelectedNetworks, selectedNetworks } =
    useAppContext();
  const { t } = useI18n();
  const [query, setQuery] = useState('');
  const { remoteResults, fallbackResults, loading, usedFallback, hasQuery } = useGlobalSearch(
    query,
    open
  );
  const { favoriteRecipeItems } = useFavorites();

  useEffect(() => {
    if (open) setQuery('');
  }, [open]);

  const pageItems = useMemo<PaletteItem[]>(() => {
    return ALL_PAGES.map((p) => ({
      id: 'page:' + p.id,
      kind: 'page' as const,
      pageId: p.id,
      label: t(p.labelKey),
      keywords: `${p.id} ${t(p.labelKey)}`.toLowerCase(),
    }));
  }, [t]);

  const favoriteItems = useMemo<PaletteItem[]>(() => {
    return favoriteRecipeItems().map((fav) => ({
      id: fav.id,
      kind: 'favorite' as const,
      label: fav.label,
      subtitle: fav.subtitle,
      keywords: `favorite ${fav.label} ${fav.subtitle ?? ''}`.toLowerCase(),
      favoriteKey: fav.favoriteKey,
    }));
  }, [favoriteRecipeItems]);

  const searchItems = useMemo<PaletteItem[]>(() => {
    const source = usedFallback ? fallbackResults : remoteResults;
    return source.map((result) => ({
      id: result.id,
      kind: result.type,
      label: result.label,
      subtitle: result.subtitle,
      keywords: `${result.type} ${result.label} ${result.subtitle ?? ''}`.toLowerCase(),
      searchResult: result,
    }));
  }, [remoteResults, fallbackResults, usedFallback]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    const favs = !q
      ? favoriteItems
      : favoriteItems.filter((item) => item.keywords.includes(q));
    const pages = !q ? pageItems : pageItems.filter((item) => item.keywords.includes(q));
    if (!hasQuery) {
      return [...favs, ...pages];
    }
    const merged = [
      ...favs,
      ...pages.filter((p) => q && p.keywords.includes(q)),
      ...searchItems,
    ];
    const seen = new Set<string>();
    const deduped: PaletteItem[] = [];
    for (const item of merged) {
      if (seen.has(item.id)) continue;
      seen.add(item.id);
      deduped.push(item);
    }
    return deduped;
  }, [pageItems, favoriteItems, searchItems, query, hasQuery]);

  const activate = useCallback(
    (item: PaletteItem) => {
      if (item.kind === 'page' && item.pageId) {
        setActivePage(item.pageId);
        onClose();
        return;
      }
      if (item.kind === 'favorite' && item.favoriteKey) {
        setPageSearchPrefill({ page: 'recipes', query: item.label });
        setActivePage('recipes');
        onClose();
        return;
      }
      const result = item.searchResult;
      if (!result) {
        onClose();
        return;
      }
      if (result.networkId != null && result.networkId >= 0) {
        if (!selectedNetworks.includes(result.networkId)) {
          setSelectedNetworks([result.networkId]);
        }
      }
      switch (result.type) {
        case 'storage':
          if (result.category === 'essentia') {
            setPageSearchPrefill({ page: 'essentia', query: result.label, networkId: result.networkId });
            setActivePage('essentia');
          } else if (result.category === 'fluid') {
            setPageSearchPrefill({ page: 'fluids', query: result.label, networkId: result.networkId });
            setActivePage('fluids');
          } else {
            setPageSearchPrefill({ page: 'storage', query: result.label, networkId: result.networkId });
            setActivePage('storage');
          }
          break;
        case 'recipe':
          setPageSearchPrefill({ page: 'recipes', query: result.label });
          setActivePage('recipes');
          break;
        case 'gt':
          setPageSearchPrefill({
            page: 'gtmachines',
            query: result.label,
            networkId: result.networkId,
          });
          setActivePage('gtmachines');
          break;
        case 'pattern':
          setPageSearchPrefill({ page: 'pattern', query: result.label, networkId: result.networkId });
          setActivePage('pattern');
          break;
        case 'quest':
          setPageSearchPrefill({ page: 'quests', query: result.label });
          setActivePage('quests');
          break;
        default:
          break;
      }
      onClose();
    },
    [setActivePage, setPageSearchPrefill, setSelectedNetworks, selectedNetworks, onClose]
  );

  const kindLabel = useCallback(
    (kind: PaletteKind) => {
      switch (kind) {
        case 'page':
          return t('commandPalettePage');
        case 'favorite':
          return t('commandPaletteFavorite');
        case 'storage':
          return t('commandPaletteStorage');
        case 'recipe':
          return t('commandPaletteRecipe');
        case 'gt':
          return t('commandPaletteGt');
        case 'pattern':
          return t('commandPalettePattern');
        case 'quest':
          return t('questsPage');
        default:
          return kind;
      }
    },
    [t]
  );

  return (
    <Modal
      title={t('commandPaletteTitle')}
      open={open}
      onCancel={onClose}
      footer={null}
      width={520}
      destroyOnClose
      aria-label={t('commandPaletteTitle')}
    >
      <Input
        prefix={<SearchOutlined />}
        placeholder={t('globalSearchPlaceholder')}
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        autoFocus
        aria-label={t('globalSearchPlaceholder')}
        onKeyDown={(e) => {
          if (e.key === 'Enter' && filtered.length > 0) {
            activate(filtered[0]);
          }
        }}
      />
      {loading && hasQuery ? (
        <div style={{ marginTop: 8, textAlign: 'center' }}>
          <Spin size="small" />
        </div>
      ) : null}
      {usedFallback && hasQuery ? (
        <div style={{ marginTop: 6, fontSize: '0.72rem', color: 'var(--text-dim)' }}>
          {t('commandPaletteOfflineFallback')}
        </div>
      ) : null}
      <List
        style={{ marginTop: 12, maxHeight: 360, overflow: 'auto' }}
        dataSource={filtered}
        locale={{ emptyText: t('commandPaletteEmpty') }}
        renderItem={(item) => (
          <List.Item
            style={{ cursor: 'pointer', padding: '8px 4px' }}
            onClick={() => activate(item)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') activate(item);
            }}
            tabIndex={0}
            role="button"
          >
            <Tag color={TAG_COLORS[item.kind]}>{kindLabel(item.kind)}</Tag>
            <span>
              {item.label}
              {item.subtitle ? (
                <span style={{ marginLeft: 8, color: 'var(--text-dim)', fontSize: '0.85em' }}>
                  {item.subtitle}
                </span>
              ) : null}
            </span>
          </List.Item>
        )}
      />
      <div style={{ marginTop: 8, fontSize: '0.72rem', color: 'var(--text-dim)' }}>
        {t('commandPaletteHint')}
      </div>
    </Modal>
  );
}

/** Global Ctrl+K / Cmd+K shortcut to open the command palette. */
export function useCommandPaletteShortcut(onOpen: () => void): void {
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        onOpen();
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [onOpen]);
}
