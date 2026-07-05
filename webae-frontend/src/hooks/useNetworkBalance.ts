import { useCallback, useEffect, useState } from 'react';
import { getApiClient } from '@/api/client';
import type { NetworkBalanceResponse, NetworkBalanceSuggestionDto } from '@/types/dto';

export function useNetworkBalance(networkIds: number[], enabled = true) {
  const [suggestions, setSuggestions] = useState<NetworkBalanceSuggestionDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [timestamp, setTimestamp] = useState(0);

  const refresh = useCallback(async () => {
    if (!enabled || networkIds.length < 2) {
      setSuggestions([]);
      return;
    }
    setLoading(true);
    try {
      const qs = `networks=${networkIds.join(',')}&limit=30`;
      const data = await getApiClient().get<NetworkBalanceResponse>(`/api/network/balance?${qs}`);
      if (data.success) {
        setSuggestions(data.suggestions || []);
        setTimestamp(data.timestamp || 0);
      } else {
        setSuggestions([]);
      }
    } catch {
      setSuggestions([]);
    } finally {
      setLoading(false);
    }
  }, [enabled, networkIds]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return { suggestions, loading, timestamp, refresh };
}
