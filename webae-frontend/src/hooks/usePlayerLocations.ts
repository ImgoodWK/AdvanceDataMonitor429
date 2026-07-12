import { useCallback, useEffect, useState } from 'react';
import { getApiClient } from '@/api/client';
import { useAppContext } from '@/context/AppContext';
import { useVisibilityAwarePolling } from '@/hooks/useVisibilityAwarePolling';
import type { PlayerLocationDto, PlayerLocationsResponse } from '@/types/dto';

/**
 * Polls player locations for topology world map overlays.
 * Only mounted on the topology page; pauses when the browser tab is hidden.
 */
export function usePlayerLocations(refreshIntervalMs = 10000, enabled = true) {
  const { pauseRefreshWhenHidden } = useAppContext();
  const [locations, setLocations] = useState<PlayerLocationDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getApiClient().get<PlayerLocationsResponse>('/api/players/locations');
      if (data.success && data.locations) {
        setLocations(data.locations);
        setError(null);
      } else {
        setError('Failed to load player locations');
      }
    } catch (e) {
      setError((e as Error).message || 'Failed to load player locations');
    } finally {
      setLoading(false);
    }
  }, []);

  const delay = enabled && refreshIntervalMs > 0 ? refreshIntervalMs : null;
  useVisibilityAwarePolling(refresh, delay, pauseRefreshWhenHidden);

  useEffect(() => {
    if (!enabled) return;
    void refresh();
  }, [enabled, refresh]);

  return { locations, loading, error, refresh };
}
