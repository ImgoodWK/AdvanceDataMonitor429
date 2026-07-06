import { useCallback, useEffect, useMemo, useState } from 'react';
import { getApiClient } from '@/api/client';
import { useAppContext } from '@/context/AppContext';
import type {
  StorageBatchResponse,
  StorageResponse,
  StorageDto,
  PowerBatchResponse,
  PowerResponse,
  PowerDto,
  GtMachineBatchResponse,
  GtMachineResponse,
  GtMachineListDto,
} from '@/types/dto';

/**
 * Fetch storage/power/gt data for the selected networks.
 * Re-fetches when refreshTick changes (auto-refresh or manual trigger).
 * Supports both single-network and batch (multi-network) requests.
 */
export function useSnapshotData() {
  const { selectedNetworks, refreshTick, isLoggedIn } = useAppContext();
  const [storageMap, setStorageMap] = useState<Record<number, StorageDto>>({});
  const [powerMap, setPowerMap] = useState<Record<number, PowerDto>>({});
  const [gtMap, setGtMap] = useState<Record<number, GtMachineListDto>>({});
  const [loading, setLoading] = useState(false);

  const fetchAll = useCallback(async () => {
    if (!isLoggedIn || selectedNetworks.length === 0) return;
    setLoading(true);
    try {
      const client = getApiClient();
      // Use batch endpoints when multiple networks selected
      if (selectedNetworks.length > 1) {
        const networksParam = selectedNetworks.join(',');
        const [storageBatch, powerBatch, gtBatch] = await Promise.allSettled([
          client.get<StorageBatchResponse>(
            `/api/storage/batch?networks=${networksParam}`
          ),
          client.get<PowerBatchResponse>(
            `/api/power/batch?networks=${networksParam}`
          ),
          client.get<GtMachineBatchResponse>(
            `/api/gt/machines/batch?networks=${networksParam}`
          ),
        ]);
        if (storageBatch.status === 'fulfilled' && storageBatch.value.success) {
          setStorageMap((prev) => {
            const map = { ...prev };
            for (const r of storageBatch.value.results) {
              if (r.data) map[r.networkId] = r.data;
            }
            return map;
          });
        }
        if (powerBatch.status === 'fulfilled' && powerBatch.value.success) {
          setPowerMap((prev) => {
            const map = { ...prev };
            for (const r of powerBatch.value.results) {
              if (r.data) map[r.networkId] = r.data;
            }
            return map;
          });
        }
        if (gtBatch.status === 'fulfilled' && gtBatch.value.success) {
          setGtMap((prev) => {
            const map = { ...prev };
            for (const r of gtBatch.value.results) {
              if (r.data) map[r.networkId] = r.data;
            }
            return map;
          });
        }
      } else {
        const nid = selectedNetworks[0];
        const [storage, power, gt] = await Promise.allSettled([
          client.get<StorageResponse>(`/api/storage?network=${nid}`),
          client.get<PowerResponse>(`/api/power?network=${nid}`),
          client.get<GtMachineResponse>(`/api/gt/machines?network=${nid}`),
        ]);
        if (storage.status === 'fulfilled' && storage.value.success && storage.value.data) {
          setStorageMap((prev) => ({ ...prev, [nid]: storage.value.data! }));
        }
        if (power.status === 'fulfilled' && power.value.success && power.value.data) {
          setPowerMap((prev) => ({ ...prev, [nid]: power.value.data! }));
        }
        if (gt.status === 'fulfilled' && gt.value.success && gt.value.data) {
          setGtMap((prev) => ({ ...prev, [nid]: gt.value.data! }));
        }
      }
    } catch {
      /* ignore — connection status handled elsewhere */
    } finally {
      setLoading(false);
    }
  }, [isLoggedIn, selectedNetworks]);

  useEffect(() => {
    fetchAll();
  }, [fetchAll, refreshTick]);

  const hasAnyData = useMemo(
    () =>
      selectedNetworks.some(
        (nid) => storageMap[nid] || powerMap[nid] || gtMap[nid]
      ),
    [selectedNetworks, storageMap, powerMap, gtMap]
  );

  /** True when selected networks have cached snapshot rows (stale-while-revalidate). */
  const hasSelectedStorage = useMemo(
    () => selectedNetworks.some((nid) => storageMap[nid] != null),
    [selectedNetworks, storageMap]
  );

  const initialLoading = loading && !hasAnyData;
  const refreshing = loading && hasAnyData;

  return {
    storageMap,
    powerMap,
    gtMap,
    loading: initialLoading,
    initialLoading,
    refreshing,
    hasSelectedStorage,
    refetch: fetchAll,
  };
}
