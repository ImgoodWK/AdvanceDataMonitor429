import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { getApiClient } from '@/api/client';
import { useAppContext } from '@/context/AppContext';
import { useSnapshotData } from '@/hooks/useSnapshotData';
import type { GlobalSearchResultDto, GlobalSearchResponse } from '@/types/dto';

const DEBOUNCE_MS = 300;
const MIN_QUERY_LEN = 1;
const FALLBACK_PER_TYPE = 15;

function buildLocalFallback(
  query: string,
  selectedNetworks: number[],
  storageMap: ReturnType<typeof useSnapshotData>['storageMap'],
  gtMap: ReturnType<typeof useSnapshotData>['gtMap']
): GlobalSearchResultDto[] {
  const q = query.trim().toLowerCase();
  if (!q) return [];
  const hits: GlobalSearchResultDto[] = [];

  for (const networkId of selectedNetworks) {
    const storage = storageMap[networkId];
    if (storage) {
      for (const item of storage.items || []) {
        if (hits.filter((h) => h.type === 'storage').length >= FALLBACK_PER_TYPE) break;
        const label = item.displayName || item.registryName || item.itemId || '';
        if (
          !label.toLowerCase().includes(q) &&
          !(item.registryName || '').toLowerCase().includes(q) &&
          !(item.itemId || '').toLowerCase().includes(q)
        ) {
          continue;
        }
        hits.push({
          type: 'storage',
          id: `storage:${networkId}:item:${item.registryName}:${item.meta ?? 0}`,
          label,
          subtitle: `Network ${networkId} · ${item.amount ?? 0}`,
          networkId,
          category: 'item',
          itemId: item.itemId,
          registryName: item.registryName,
          meta: item.meta,
          amount: item.amount,
        });
      }
      for (const fluid of storage.fluids || []) {
        if (hits.filter((h) => h.type === 'storage').length >= FALLBACK_PER_TYPE) break;
        if (!(fluid.fluidName || '').toLowerCase().includes(q)) continue;
        hits.push({
          type: 'storage',
          id: `storage:${networkId}:fluid:${fluid.fluidName}`,
          label: fluid.fluidName,
          subtitle: `Network ${networkId} · ${fluid.amount ?? 0} mB`,
          networkId,
          category: 'fluid',
          registryName: fluid.fluidName,
          amount: fluid.amount,
        });
      }
      for (const ess of storage.essentia || []) {
        if (hits.filter((h) => h.type === 'storage').length >= FALLBACK_PER_TYPE) break;
        if (!(ess.aspect || '').toLowerCase().includes(q)) continue;
        hits.push({
          type: 'storage',
          id: `storage:${networkId}:essentia:${ess.aspect}`,
          label: ess.aspect,
          subtitle: `Network ${networkId} · ${ess.amount ?? 0}`,
          networkId,
          category: 'essentia',
          registryName: ess.aspect,
          amount: ess.amount,
        });
      }
    }

    const gt = gtMap[networkId];
    if (gt?.machines) {
      for (const machine of gt.machines) {
        if (hits.filter((h) => h.type === 'gt').length >= FALLBACK_PER_TYPE) break;
        const label = machine.recipeMapName || machine.currentOutput || 'GT Machine';
        const hay = [
          machine.recipeMapName,
          machine.statusText,
          machine.currentOutput,
          machine.machineMode,
          `${machine.x},${machine.y},${machine.z}`,
        ]
          .filter(Boolean)
          .join(' ')
          .toLowerCase();
        if (!hay.includes(q)) continue;
        hits.push({
          type: 'gt',
          id: `gt:${networkId}:${machine.x}:${machine.y}:${machine.z}:${machine.dim}`,
          label,
          subtitle: `Network ${networkId} · ${machine.x},${machine.y},${machine.z}`,
          networkId,
          x: machine.x,
          y: machine.y,
          z: machine.z,
          dim: machine.dim,
        });
      }
    }
  }
  return hits;
}

export function useGlobalSearch(query: string, enabled: boolean) {
  const { isLoggedIn, selectedNetworks } = useAppContext();
  const { storageMap, gtMap } = useSnapshotData();
  const [apiResults, setApiResults] = useState<GlobalSearchResultDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [usedFallback, setUsedFallback] = useState(false);
  const requestIdRef = useRef(0);

  const trimmed = query.trim();

  const fetchSearch = useCallback(async (q: string, requestId: number) => {
    if (!isLoggedIn) {
      setApiResults([]);
      setUsedFallback(true);
      return;
    }
    setLoading(true);
    try {
      const networkParam =
        selectedNetworks.length === 1 ? `&network=${selectedNetworks[0]}` : '';
      const [data, questData] = await Promise.all([
        getApiClient().get<GlobalSearchResponse>(
          `/api/search?q=${encodeURIComponent(q)}&limit=30${networkParam}`
        ),
        getApiClient()
          .get<{ success: boolean; search: Array<{ questId: string; questName: string; lineName: string; state: string }> }>(
            `/api/quests/search?q=${encodeURIComponent(q)}`
          )
          .catch(() => ({ success: false, search: [] as [] })),
      ]);
      if (requestId !== requestIdRef.current) return;
      const questHits: GlobalSearchResultDto[] = (questData.search ?? []).map((hit) => ({
        type: 'quest' as const,
        id: `quest:${hit.questId}`,
        label: hit.questName,
        subtitle: `${hit.lineName} · ${hit.state}`,
      }));
      if (data.success && data.results) {
        setApiResults([...data.results, ...questHits].slice(0, 30));
        setUsedFallback(false);
      } else if (questHits.length > 0) {
        setApiResults(questHits);
        setUsedFallback(false);
      } else {
        setApiResults([]);
        setUsedFallback(true);
      }
    } catch {
      if (requestId !== requestIdRef.current) return;
      setApiResults([]);
      setUsedFallback(true);
    } finally {
      if (requestId === requestIdRef.current) {
        setLoading(false);
      }
    }
  }, [isLoggedIn, selectedNetworks]);

  useEffect(() => {
    if (!enabled || trimmed.length < MIN_QUERY_LEN) {
      setApiResults([]);
      setUsedFallback(false);
      setLoading(false);
      return;
    }
    const requestId = ++requestIdRef.current;
    const timer = window.setTimeout(() => {
      void fetchSearch(trimmed, requestId);
    }, DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [trimmed, enabled, fetchSearch]);

  const fallbackResults = useMemo(
    () =>
      usedFallback
        ? buildLocalFallback(trimmed, selectedNetworks, storageMap, gtMap)
        : [],
    [usedFallback, trimmed, selectedNetworks, storageMap, gtMap]
  );

  const remoteResults = trimmed.length >= MIN_QUERY_LEN ? apiResults : [];

  return {
    remoteResults,
    fallbackResults,
    loading,
    usedFallback,
    hasQuery: trimmed.length >= MIN_QUERY_LEN,
  };
}
