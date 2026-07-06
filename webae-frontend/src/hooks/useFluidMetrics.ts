import { useCallback, useEffect, useState } from 'react';
import { getApiClient } from '@/api/client';
import { useAppContext } from '@/context/AppContext';
import { useVisibilityAwarePolling } from '@/hooks/useVisibilityAwarePolling';
import type { ChartTrendPoint } from '@/components/dashboard/ChartTrendSvg';
import type { NetworkMetricFluidHistoryResponse } from '@/types/dto';

const PINNED_KEY_PREFIX = 'webae-pinned-fluids-';

export function loadPinnedFluids(networkId: number): string[] {
  try {
    const raw = localStorage.getItem(PINNED_KEY_PREFIX + networkId);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as string[];
    return Array.isArray(parsed) ? parsed.slice(0, 10) : [];
  } catch {
    return [];
  }
}

export function savePinnedFluids(networkId: number, fluids: string[]): void {
  localStorage.setItem(PINNED_KEY_PREFIX + networkId, JSON.stringify(fluids.slice(0, 10)));
}

/**
 * Polls GET /api/network/metrics/fluids for pinned fluid trends (Phase 3.1).
 */
export function useFluidMetrics(networkId: number, pinnedFluids: string[], pollIntervalMs = 10_000) {
  const { isLoggedIn, pauseRefreshWhenHidden } = useAppContext();
  const [seriesMap, setSeriesMap] = useState<Record<string, ChartTrendPoint[]>>({});

  const fetchFluids = useCallback(async () => {
    if (!isLoggedIn || networkId < 0 || pinnedFluids.length === 0) {
      setSeriesMap({});
      return;
    }
    const fluidsParam = pinnedFluids.join(',');
    try {
      const resp = await getApiClient().get<NetworkMetricFluidHistoryResponse>(
        `/api/network/metrics/fluids?network=${networkId}&fluids=${encodeURIComponent(fluidsParam)}`
      );
      if (!resp.success || !resp.history?.fluids) {
        return;
      }
      const next: Record<string, ChartTrendPoint[]> = {};
      for (const [key, series] of Object.entries(resp.history.fluids)) {
        const ts = series.timestamps ?? [];
        const amounts = series.amounts ?? [];
        next[key] = ts.map((t, i) => ({ ts: t, value: amounts[i] ?? 0 }));
      }
      setSeriesMap(next);
    } catch {
      /* keep stale */
    }
  }, [isLoggedIn, networkId, pinnedFluids]);

  useEffect(() => {
    void fetchFluids();
  }, [fetchFluids]);

  useVisibilityAwarePolling(
    fetchFluids,
    isLoggedIn && pinnedFluids.length > 0 ? pollIntervalMs : null,
    pauseRefreshWhenHidden
  );

  return { seriesMap, refetch: fetchFluids };
}
