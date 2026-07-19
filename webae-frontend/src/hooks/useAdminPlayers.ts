import { useCallback, useEffect, useRef, useState } from 'react';
import { getApiClient } from '@/api/client';
import type {
  AdminPlayerSummary,
  AdminPlayersResponse,
  AdminPlayerAccessResponse,
  AdminActionResponse,
} from '@/types/dto';

const POLL_MS = 30_000;

export interface UseAdminPlayersResult {
  players: AdminPlayerSummary[];
  loading: boolean;
  refresh: () => void;
  disablePlayer: (uuid: string) => Promise<boolean>;
  enablePlayer: (uuid: string) => Promise<boolean>;
  clearPlayerCache: (uuid: string) => Promise<boolean>;
  fetchAccess: (uuid: string) => Promise<AdminPlayerAccessResponse | null>;
  suspendNetwork: (ownerUuid: string, networkKey: string, reason?: string) => Promise<boolean>;
  resumeNetwork: (ownerUuid: string, networkKey: string) => Promise<boolean>;
  setAcl: (actorUuid: string, ownerUuid: string, networkKey: string, effect: 'deny' | 'allow') => Promise<boolean>;
  revokeGuestToken: (actorUuid: string, token: string) => Promise<boolean>;
}

export function useAdminPlayers(active = true): UseAdminPlayersResult {
  const [players, setPlayers] = useState<AdminPlayerSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const mountedRef = useRef(true);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const resp = await getApiClient().get<AdminPlayersResponse>('/api/admin/players');
      if (mountedRef.current) {
        setPlayers(resp.players || []);
      }
    } catch {
      // ignore
    }
    if (mountedRef.current) setLoading(false);
  }, []);

  useEffect(() => {
    mountedRef.current = true;
    if (!active) {
      return () => {
        mountedRef.current = false;
      };
    }
    refresh();
    const timer = setInterval(refresh, POLL_MS);
    return () => {
      mountedRef.current = false;
      clearInterval(timer);
    };
  }, [active, refresh]);

  const disablePlayer = useCallback(async (uuid: string) => {
    try {
      const resp = await getApiClient().post<AdminActionResponse>(
        `/api/admin/players/${encodeURIComponent(uuid)}/disable`);
      if (resp.success) {
        await refresh();
        return true;
      }
      return false;
    } catch {
      return false;
    }
  }, [refresh]);

  const enablePlayer = useCallback(async (uuid: string) => {
    try {
      const resp = await getApiClient().post<AdminActionResponse>(
        `/api/admin/players/${encodeURIComponent(uuid)}/enable`);
      if (resp.success) {
        await refresh();
        return true;
      }
      return false;
    } catch {
      return false;
    }
  }, [refresh]);

  const clearPlayerCache = useCallback(async (uuid: string) => {
    try {
      const resp = await getApiClient().post<AdminActionResponse>(
        `/api/admin/players/${encodeURIComponent(uuid)}/clear-cache`);
      if (resp.success) {
        await refresh();
        return true;
      }
      return false;
    } catch {
      return false;
    }
  }, [refresh]);

  const fetchAccess = useCallback(async (uuid: string) => {
    try {
      return await getApiClient().get<AdminPlayerAccessResponse>(
        `/api/admin/players/${encodeURIComponent(uuid)}/access`);
    } catch {
      return null;
    }
  }, []);

  const suspendNetwork = useCallback(async (ownerUuid: string, networkKey: string, reason?: string) => {
    try {
      const resp = await getApiClient().post<AdminActionResponse>(
        `/api/admin/players/${encodeURIComponent(ownerUuid)}/networks/${encodeURIComponent(networkKey)}/suspend`,
        { reason: reason || 'Suspended by admin' },
      );
      return !!resp.success;
    } catch {
      return false;
    }
  }, []);

  const resumeNetwork = useCallback(async (ownerUuid: string, networkKey: string) => {
    try {
      const resp = await getApiClient().post<AdminActionResponse>(
        `/api/admin/players/${encodeURIComponent(ownerUuid)}/networks/${encodeURIComponent(networkKey)}/resume`,
      );
      return !!resp.success;
    } catch {
      return false;
    }
  }, []);

  const setAcl = useCallback(async (
    actorUuid: string,
    ownerUuid: string,
    networkKey: string,
    effect: 'deny' | 'allow',
  ) => {
    try {
      const resp = await getApiClient().post<AdminActionResponse>(
        `/api/admin/players/${encodeURIComponent(actorUuid)}/acl`,
        { ownerUuid, networkKey, effect },
      );
      return !!resp.success;
    } catch {
      return false;
    }
  }, []);

  const revokeGuestToken = useCallback(async (actorUuid: string, token: string) => {
    try {
      const resp = await getApiClient().post<AdminActionResponse>(
        `/api/admin/players/${encodeURIComponent(actorUuid)}/guest-tokens/revoke`,
        { token },
      );
      return !!resp.success;
    } catch {
      return false;
    }
  }, []);

  return {
    players,
    loading,
    refresh,
    disablePlayer,
    enablePlayer,
    clearPlayerCache,
    fetchAccess,
    suspendNetwork,
    resumeNetwork,
    setAcl,
    revokeGuestToken,
  };
}
