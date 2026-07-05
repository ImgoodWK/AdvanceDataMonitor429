import { useCallback, useState } from 'react';
import { getApiClient } from '@/api/client';
import { useAppContext } from '@/context/AppContext';
import { useVisibilityAwarePolling } from '@/hooks/useVisibilityAwarePolling';
import type {
  ChartTrendPoint,
} from '@/components/dashboard/ChartTrendSvg';
import type {
  NetworkMetricHistory,
  NetworkMetricHistoryResponse,
} from '@/types/dto';

/**
 * Polls `/api/network/metrics?network=<id>` for each selected network and
 * exposes the rolling-window history. Sampling cadence on the server is
 * {@code Config.webMetricSampleIntervalMs} (default 10s); we poll at the same
 * cadence so the chart advances roughly one point per request.
 *
 * <p>Also exposes {@link getHistory} which converts a named scalar data source
 * (e.g. {@code itemCount}, {@code cpuBusyRatio}) into a {@link ChartTrendPoint}
 * array suitable for {@code ChartTrendSvg}.</p>
 */
export function useNetworkMetrics(pollIntervalMs = 10_000) {
  const { selectedNetworks, isLoggedIn, pauseRefreshWhenHidden } = useAppContext();
  const [metricsMap, setMetricsMap] = useState<Record<number, NetworkMetricHistory>>({});

  const fetchAll = useCallback(async () => {
    if (!isLoggedIn || selectedNetworks.length === 0) return;
    const client = getApiClient();
    const results = await Promise.allSettled(
      selectedNetworks.map((nid) =>
        client.get<NetworkMetricHistoryResponse>(`/api/network/metrics?network=${nid}`)
      )
    );
    setMetricsMap((prev) => {
      const next = { ...prev };
      results.forEach((res, i) => {
        const nid = selectedNetworks[i];
        if (res.status === 'fulfilled' && res.value.success && res.value.history) {
          next[nid] = res.value.history;
        }
      });
      return next;
    });
  }, [isLoggedIn, selectedNetworks]);

  useVisibilityAwarePolling(
    fetchAll,
    isLoggedIn && selectedNetworks.length > 0 ? pollIntervalMs : null,
    pauseRefreshWhenHidden
  );

  /**
   * Resolve a named data source to a trend point array for the given network.
   * Returns an empty array when the network has no history yet.
   */
  const getHistory = useCallback(
    (networkId: number, dataSource: string): ChartTrendPoint[] => {
      const h = metricsMap[networkId];
      if (!h || !h.timestamps || h.timestamps.length === 0) return [];
      const values = pickHistoryArray(h, dataSource);
      if (!values) return [];
      return h.timestamps.map((ts, i) => ({
        value: values[i],
        ts,
      }));
    },
    [metricsMap]
  );

  return { metricsMap, getHistory, refetch: fetchAll };
}

/**
 * Map a dashboard data source name to its history array inside
 * {@link NetworkMetricHistory}. Returns {@code undefined} for data sources
 * that have no server-side history (e.g. {@code topItems}, {@code powerHistory}
 * — those are handled separately by their own samplers).
 */
function pickHistoryArray(
  h: NetworkMetricHistory,
  dataSource: string
): number[] | undefined {
  switch (dataSource) {
    case 'itemCount': return h.itemCountHistory;
    case 'fluidCount': return h.fluidCountHistory;
    case 'essentiaCount': return h.essentiaCountHistory;
    case 'bytesUsed': return h.bytesUsedHistory;
    case 'bytesMax': return h.bytesMaxHistory;
    case 'bytesPercent': return h.bytesPercentHistory;
    case 'itemTotal': return h.itemTotalHistory;
    case 'fluidTotal': return h.fluidTotalHistory;
    case 'activeCpu': return h.activeCpuHistory;
    case 'busyCpu': return h.busyCpuHistory;
    case 'cpuBusyRatio': return h.cpuBusyRatioHistory;
    case 'gtMachineCount': return h.gtMachineCountHistory;
    case 'gtActiveCount': return h.gtActiveCountHistory;
    default: return undefined;
  }
}
