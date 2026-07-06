import { useCallback, useEffect, useState } from 'react';
import { getApiClient } from '@/api/client';
import type { PlayerLocationDto, PlayerLocationsResponse } from '@/types/dto';

export function usePlayerLocations(refreshIntervalMs = 10000) {
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

  useEffect(() => {
    void refresh();
    if (refreshIntervalMs <= 0) return;
    const id = window.setInterval(() => void refresh(), refreshIntervalMs);
    return () => window.clearInterval(id);
  }, [refresh, refreshIntervalMs]);

  return { locations, loading, error, refresh };
}
