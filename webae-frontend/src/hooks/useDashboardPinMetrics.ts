import { useCallback, useEffect, useMemo, useState } from 'react';
import { getApiClient } from '@/api/client';
import { useAppContext } from '@/context/AppContext';
import { useVisibilityAwarePolling } from '@/hooks/useVisibilityAwarePolling';
import type { ChartTrendPoint } from '@/components/dashboard/ChartTrendSvg';
import type {
  NetworkMetricEntityHistoryResponse,
  NetworkMetricFluidHistoryResponse,
  NetworkMetricItemHistoryResponse,
} from '@/types/dto';
import type { DashboardPin, DashboardWidgetConfig } from '@/utils/presets';
import { entityApiKey } from '@/utils/dashboardPins';
import { flattenWidgets } from '@/utils/dashboardTree';

export interface PinSeriesMap {
  items: Record<string, ChartTrendPoint[]>;
  fluids: Record<string, ChartTrendPoint[]>;
  entities: Record<string, ChartTrendPoint[]>;
  error?: string;
}

function collectPins(widgets: DashboardWidgetConfig[]): DashboardPin[] {
  const out: DashboardPin[] = [];
  const seen = new Set<string>();
  for (const w of flattenWidgets(widgets)) {
    for (const p of w.pins || []) {
      const k = `${p.kind}:${p.id}:${p.metricField || ''}`;
      if (seen.has(k)) continue;
      seen.add(k);
      out.push(p);
    }
  }
  return out;
}

/**
 * Merges pin history requests across all dashboard widgets for one network.
 */
export function useDashboardPinMetrics(
  networkId: number,
  widgets: DashboardWidgetConfig[],
  pollIntervalMs = 10_000
): PinSeriesMap & { refetch: () => Promise<void> } {
  const { isLoggedIn, pauseRefreshWhenHidden, serverConfig, notify } = useAppContext();
  const [series, setSeries] = useState<PinSeriesMap>({ items: {}, fluids: {}, entities: {} });

  const pins = useMemo(() => collectPins(widgets), [widgets]);
  const maxPerWidget = serverConfig?.dashboardMaxTracksPerWidget ?? 10;

  const itemIds = useMemo(
    () => pins.filter((p) => p.kind === 'item').map((p) => p.id).slice(0, maxPerWidget * 4),
    [pins, maxPerWidget]
  );
  const fluidIds = useMemo(
    () => pins.filter((p) => p.kind === 'fluid').map((p) => p.id).slice(0, maxPerWidget * 4),
    [pins, maxPerWidget]
  );
  const entityPins = useMemo(
    () => pins.filter((p) => p.kind === 'cpu' || p.kind === 'gt').slice(0, maxPerWidget * 4),
    [pins, maxPerWidget]
  );

  const fetchAll = useCallback(async () => {
    if (!isLoggedIn || networkId < 0) {
      setSeries({ items: {}, fluids: {}, entities: {} });
      return;
    }
    if (itemIds.length === 0 && fluidIds.length === 0 && entityPins.length === 0) {
      setSeries({ items: {}, fluids: {}, entities: {} });
      return;
    }
    const client = getApiClient();
    let error: string | undefined;
    const next: PinSeriesMap = { items: {}, fluids: {}, entities: {} };

    try {
      if (itemIds.length > 0) {
        const resp = await client.get<NetworkMetricItemHistoryResponse>(
          `/api/network/metrics/items?network=${networkId}&items=${encodeURIComponent(itemIds.join(','))}`
        );
        if (!resp.success) {
          error = resp.message || 'item tracks failed';
        } else if (resp.history?.items) {
          for (const [key, s] of Object.entries(resp.history.items)) {
            const ts = s.timestamps ?? [];
            const amounts = s.amounts ?? [];
            next.items[key] = ts.map((t, i) => ({ ts: t, value: amounts[i] ?? 0 }));
          }
        }
      }
      if (fluidIds.length > 0) {
        const resp = await client.get<NetworkMetricFluidHistoryResponse>(
          `/api/network/metrics/fluids?network=${networkId}&fluids=${encodeURIComponent(fluidIds.join(','))}`
        );
        if (!resp.success) {
          error = (resp as { message?: string }).message || error || 'fluid tracks failed';
        } else if (resp.history?.fluids) {
          for (const [key, s] of Object.entries(resp.history.fluids)) {
            const ts = s.timestamps ?? [];
            const amounts = s.amounts ?? [];
            next.fluids[key] = ts.map((t, i) => ({ ts: t, value: amounts[i] ?? 0 }));
          }
        }
      }
      if (entityPins.length > 0) {
        const keys = entityPins.map((p) => entityApiKey(p)!).filter(Boolean);
        const fields = entityPins.map((p) => p.metricField || (p.kind === 'cpu' ? 'craftingProgress' : 'progressPercent'));
        const resp = await client.get<NetworkMetricEntityHistoryResponse>(
          `/api/network/metrics/entities?network=${networkId}&entities=${encodeURIComponent(keys.join(','))}&fields=${encodeURIComponent(fields.join(','))}`
        );
        if (!resp.success) {
          error = resp.message || error || 'entity tracks failed';
        } else if (resp.history?.entities) {
          for (const [key, s] of Object.entries(resp.history.entities)) {
            const ts = s.timestamps ?? [];
            const values = s.values ?? [];
            next.entities[key] = ts.map((t, i) => ({ ts: t, value: values[i] ?? 0 }));
          }
        }
      }
    } catch {
      /* keep stale */
    }

    next.error = error;
    setSeries(next);
    if (error) {
      notify(error, 'warning');
    }
  }, [isLoggedIn, networkId, itemIds, fluidIds, entityPins, notify]);

  useEffect(() => {
    void fetchAll();
  }, [fetchAll]);

  const active =
    isLoggedIn && (itemIds.length > 0 || fluidIds.length > 0 || entityPins.length > 0);
  useVisibilityAwarePolling(fetchAll, active ? pollIntervalMs : null, pauseRefreshWhenHidden);

  return { ...series, refetch: fetchAll };
}

export function lookupPinSeries(
  pin: DashboardPin,
  map: PinSeriesMap
): ChartTrendPoint[] {
  if (pin.kind === 'item') {
    return map.items[pin.id] || [];
  }
  if (pin.kind === 'fluid') {
    const key = pin.id.toLowerCase();
    return map.fluids[key] || map.fluids[pin.id] || [];
  }
  if (pin.kind === 'cpu' || pin.kind === 'gt') {
    const k = entityApiKey(pin);
    return (k && map.entities[k]) || [];
  }
  return [];
}
