import { useCallback, useEffect, useRef, useState } from 'react';

import { getApiClient } from '@/api/client';
import { useAppContext } from '@/context/AppContext';
import { useVisibilityAwarePolling } from '@/hooks/useVisibilityAwarePolling';
import type {
  PlayersResponse,
  PlayerDto,
  PlayerOnlineHistoryResponse,
  PlayerOnlineHistoryPoint,
} from '@/types/dto';

/**
 * 轮询玩家列表 + 在线人数趋势历史的 hook（p2-dashboard）。
 *
 * - 玩家列表每 {@link PLAYER_POLL_MS} 拉取一次（合并后端 online+offline 为 players）。
 * - 在线人数趋势每 {@link HISTORY_POLL_MS} 拉取一次（后端 30s 采样一次，前端 15s 拉一次足够）。
 * - Tab 不可见时可在 Settings 暂停轮询（见 {@link useVisibilityAwarePolling}）。
 */
export function usePlayers(playerPollMs = 10000, historyPollMs = 15000) {
  const { pauseRefreshWhenHidden } = useAppContext();
  const [players, setPlayers] = useState<PlayerDto[]>([]);
  const [onlineCount, setOnlineCount] = useState(0);
  const [history, setHistory] = useState<PlayerOnlineHistoryPoint[]>([]);
  const [loading, setLoading] = useState(true);
  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const pollPlayers = useCallback(async () => {
    try {
      const data = await getApiClient().get<PlayersResponse>('/api/players');
      if (!data.success) return;
      if (data.players && data.players.length > 0) {
        if (!mountedRef.current) return;
        setPlayers(data.players);
        setOnlineCount(data.players.filter((p) => p.online).length);
        return;
      }
      const online = data.online || [];
      const offline = data.offline || [];
      if (!mountedRef.current) return;
      const merged = [...online, ...offline];
      setPlayers(merged);
      setOnlineCount(online.length);
    } catch {
      /* ignore */
    } finally {
      if (mountedRef.current) setLoading(false);
    }
  }, []);

  const pollHistory = useCallback(async () => {
    try {
      const data = await getApiClient().get<PlayerOnlineHistoryResponse>(
        '/api/players/online/history'
      );
      if (data.success && data.history && mountedRef.current) {
        setHistory(data.history);
        if (data.history.length > 0) {
          setOnlineCount(data.history[data.history.length - 1].count);
        }
      }
    } catch {
      /* ignore */
    }
  }, []);

  useVisibilityAwarePolling(pollPlayers, playerPollMs, pauseRefreshWhenHidden);
  useVisibilityAwarePolling(pollHistory, historyPollMs, pauseRefreshWhenHidden);

  return { players, onlineCount, history, loading, refresh: pollPlayers };
}
