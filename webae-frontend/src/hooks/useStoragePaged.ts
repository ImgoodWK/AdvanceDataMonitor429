import { useCallback, useEffect, useRef, useState } from 'react';
import { getApiClient, ApiClientError } from '@/api/client';
import type {
  StorageEssentia,
  StorageFluid,
  StorageItem,
  StoragePagedResponse,
} from '@/types/dto';

export type StoragePagedKind = 'items' | 'fluids' | 'essentia';
export type StorageSortKey = 'amount_desc' | 'amount_asc' | 'name_asc' | 'name_desc';

const PAGE_LIMIT = 200;
const SEARCH_DEBOUNCE_MS = 300;

function endpointForKind(kind: StoragePagedKind): string {
  return `/api/storage/${kind}`;
}

function mergeItems(rows: StorageItem[]): StorageItem[] {
  const map = new Map<string, StorageItem>();
  for (const item of rows) {
    const key = item.itemId || item.registryName || '';
    if (!key) continue;
    const existing = map.get(key);
    if (existing) {
      existing.amount += item.amount;
    } else {
      map.set(key, { ...item });
    }
  }
  return Array.from(map.values());
}

function mergeFluids(rows: StorageFluid[]): StorageFluid[] {
  const map = new Map<string, StorageFluid>();
  for (const fluid of rows) {
    const key = fluid.fluidName || '';
    if (!key) continue;
    const existing = map.get(key);
    if (existing) {
      existing.amount += fluid.amount;
    } else {
      map.set(key, { ...fluid });
    }
  }
  return Array.from(map.values());
}

function mergeEssentia(rows: StorageEssentia[]): StorageEssentia[] {
  const map = new Map<string, StorageEssentia>();
  for (const entry of rows) {
    const key = entry.aspect || '';
    if (!key) continue;
    const existing = map.get(key);
    if (existing) {
      existing.amount += entry.amount;
    } else {
      map.set(key, { ...entry });
    }
  }
  return Array.from(map.values());
}

function extractRows(
  kind: StoragePagedKind,
  resp: StoragePagedResponse
): StorageItem[] | StorageFluid[] | StorageEssentia[] {
  if (kind === 'items') return resp.items || [];
  if (kind === 'fluids') return resp.fluids || [];
  return resp.essentia || [];
}

export interface UseStoragePagedOptions {
  kind: StoragePagedKind;
  networkIds: number[];
  merged: boolean;
  search: string;
  sort: StorageSortKey;
  enabled?: boolean;
}

/**
 * Cursor-paginated storage fetch with 300ms search debounce and infinite-scroll loadMore.
 * Single network: server pagination. Merged (≤3 networks): parallel page fetch + client merge.
 */
export function useStoragePaged({
  kind,
  networkIds,
  merged,
  search,
  sort,
  enabled = true,
}: UseStoragePagedOptions) {
  const [rows, setRows] = useState<(StorageItem | StorageFluid | StorageEssentia)[]>([]);
  const [totalEstimate, setTotalEstimate] = useState(0);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [fromCache, setFromCache] = useState(false);
  const [cacheAgeMs, setCacheAgeMs] = useState(0);
  const [summary, setSummary] = useState<{
    bytesUsed: number;
    bytesMax: number;
    cpus: StoragePagedResponse['cpus'];
    totalAmountSum?: number;
  } | null>(null);

  const cursorsRef = useRef<Record<number, string | null>>({});
  const debouncedSearchRef = useRef(search);
  const [debouncedSearch, setDebouncedSearch] = useState(search);
  const fetchGenRef = useRef(0);

  useEffect(() => {
    const timer = setTimeout(() => {
      debouncedSearchRef.current = search;
      setDebouncedSearch(search);
    }, SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [search]);

  const fetchPage = useCallback(
    async (
      networkId: number,
      cursor: string | null
    ): Promise<StoragePagedResponse | null> => {
      const client = getApiClient();
      const params = new URLSearchParams({
        network: String(networkId),
        limit: String(PAGE_LIMIT),
        sort,
      });
      if (debouncedSearchRef.current) {
        params.set('search', debouncedSearchRef.current);
      }
      if (cursor) {
        params.set('cursor', cursor);
      }
      try {
        const resp = await client.get<StoragePagedResponse>(
          `${endpointForKind(kind)}?${params.toString()}`
        );
        if (!resp.success) return null;
        return resp;
      } catch (err: unknown) {
        if (err instanceof ApiClientError && err.status === 409) {
          return fetchPage(networkId, null);
        }
        return null;
      }
    },
    [kind, sort]
  );

  const resetAndLoad = useCallback(async () => {
    if (!enabled || networkIds.length === 0) {
      setRows([]);
      setTotalEstimate(0);
      setHasMore(false);
      setSummary(null);
      return;
    }

    if (merged && networkIds.length > 3) {
      setRows([]);
      setTotalEstimate(0);
      setHasMore(false);
      return;
    }

    const gen = ++fetchGenRef.current;
    setLoading(true);
    cursorsRef.current = {};

    try {
      const responses = await Promise.all(
        networkIds.map((nid) => fetchPage(nid, null))
      );

      if (gen !== fetchGenRef.current) return;

      let combined: (StorageItem | StorageFluid | StorageEssentia)[] = [];
      let total = 0;
      let anyHasMore = false;
      let cached = false;
      let ageMs = 0;

      for (let i = 0; i < networkIds.length; i++) {
        const resp = responses[i];
        if (!resp) continue;

        cursorsRef.current[networkIds[i]] = resp.nextCursor ?? null;
        cached = cached || resp.fromCache;
        ageMs = Math.max(ageMs, resp.cacheAgeMs);

        if (i === 0 && resp.cpus) {
          setSummary({
            bytesUsed: resp.bytesUsed || 0,
            bytesMax: resp.bytesMax || 0,
            cpus: resp.cpus,
            totalAmountSum: resp.totalAmountSum,
          });
        }

        const pageRows = extractRows(kind, resp);
        combined = combined.concat(pageRows);
        total += resp.totalEstimate;
        if (resp.nextCursor) anyHasMore = true;
      }

      if (merged && networkIds.length > 1) {
        if (kind === 'items') {
          combined = mergeItems(combined as StorageItem[]);
        } else if (kind === 'fluids') {
          combined = mergeFluids(combined as StorageFluid[]);
        } else {
          combined = mergeEssentia(combined as StorageEssentia[]);
        }
        total = combined.length;
      }

      setRows(combined);
      setTotalEstimate(total);
      setHasMore(anyHasMore);
      setFromCache(cached);
      setCacheAgeMs(ageMs);
    } finally {
      if (gen === fetchGenRef.current) {
        setLoading(false);
      }
    }
  }, [enabled, networkIds, merged, kind, fetchPage]);

  const loadMore = useCallback(async () => {
    if (!enabled || loadingMore || !hasMore || networkIds.length === 0) return;
    if (merged && networkIds.length > 3) return;

    setLoadingMore(true);
    const gen = fetchGenRef.current;

    try {
      const responses = await Promise.all(
        networkIds.map((nid) => {
          const cursor = cursorsRef.current[nid];
          if (!cursor) return Promise.resolve(null);
          return fetchPage(nid, cursor);
        })
      );

      if (gen !== fetchGenRef.current) return;

      let append: (StorageItem | StorageFluid | StorageEssentia)[] = [];
      let anyHasMore = false;

      for (let i = 0; i < networkIds.length; i++) {
        const resp = responses[i];
        if (!resp) continue;

        cursorsRef.current[networkIds[i]] = resp.nextCursor ?? null;
        append = append.concat(extractRows(kind, resp));
        if (resp.nextCursor) anyHasMore = true;
      }

      if (append.length === 0) {
        setHasMore(false);
        return;
      }

      setRows((prev) => {
        let mergedRows = prev.concat(append);
        if (merged && networkIds.length > 1) {
          if (kind === 'items') {
            mergedRows = mergeItems(mergedRows as StorageItem[]);
          } else if (kind === 'fluids') {
            mergedRows = mergeFluids(mergedRows as StorageFluid[]);
          } else {
            mergedRows = mergeEssentia(mergedRows as StorageEssentia[]);
          }
        }
        return mergedRows;
      });
      setHasMore(anyHasMore);
    } finally {
      setLoadingMore(false);
    }
  }, [enabled, loadingMore, hasMore, networkIds, merged, kind, fetchPage]);

  useEffect(() => {
    resetAndLoad();
  }, [resetAndLoad, debouncedSearch, sort]);

  return {
    rows,
    totalEstimate,
    loading,
    loadingMore,
    hasMore,
    fromCache,
    cacheAgeMs,
    summary,
    loadMore,
    refetch: resetAndLoad,
    mergeLimited: merged && networkIds.length > 3,
  };
}
