import { useCallback, useEffect, useState } from 'react';

import { ApiClientError, getApiClient } from '@/api/client';import type {
  WorldMapMarkerDto,
  WorldMapMarkersResponse,
  WorldMapMetaDto,
  WorldMapSnapshotStatusDto,
} from '@/types/dto';
import { purgeOldSnapshotVersions } from '@/utils/worldMapIdbCache';

export interface UseWorldMapDataResult {
  meta: WorldMapMetaDto | null;
  markers: WorldMapMarkerDto[];
  snapshotStatus: WorldMapSnapshotStatusDto | null;
  loading: boolean;
  error: string | null;
  reload: () => Promise<void>;
  /** @deprecated Use requestSnapshotUpdate in client_only mode. */
  invalidateTiles: (views: string, quality?: string) => Promise<void>;
  requestSnapshotUpdate: () => Promise<{ ok: boolean; error?: string }>;  refreshSnapshotStatus: () => Promise<void>;
}

export function useWorldMapData(networkId: number, enabled: boolean, quality?: string): UseWorldMapDataResult {
  const [meta, setMeta] = useState<WorldMapMetaDto | null>(null);
  const [markers, setMarkers] = useState<WorldMapMarkerDto[]>([]);
  const [snapshotStatus, setSnapshotStatus] = useState<WorldMapSnapshotStatusDto | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refreshSnapshotStatus = useCallback(async () => {
    if (!enabled) {
      setSnapshotStatus(null);
      return;
    }
    try {
      const status = await getApiClient().get<WorldMapSnapshotStatusDto>(
        `/api/worldmap/snapshot/status?network=${networkId}`
      );
      setSnapshotStatus(status);
    } catch {
      setSnapshotStatus(null);
    }
  }, [enabled, networkId]);

  const reload = useCallback(async () => {
    if (!enabled) {
      setMeta(null);
      setMarkers([]);
      setSnapshotStatus(null);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const client = getApiClient();
      const qualityParam = quality ? `&quality=${encodeURIComponent(quality)}` : '';
      const [metaRes, markersRes, statusRes] = await Promise.all([
        client.get<WorldMapMetaDto>(`/api/worldmap/meta?network=${networkId}${qualityParam}`),
        client.get<WorldMapMarkersResponse>(`/api/worldmap/markers?network=${networkId}`),
        client.get<WorldMapSnapshotStatusDto>(`/api/worldmap/snapshot/status?network=${networkId}`),
      ]);
      setMeta(metaRes);
      setSnapshotStatus(statusRes);
      if (metaRes.snapshotMode === 'client_only' && (metaRes.snapshotVersion ?? 0) > 0) {
        const keep = [metaRes.snapshotVersion ?? 0, metaRes.previousSnapshotVersion ?? 0];
        void purgeOldSnapshotVersions('owner', networkId, keep);
      }
      if (markersRes.success && markersRes.markers) {
        setMarkers(markersRes.markers);
      } else if (metaRes.hasLogicalSnapshot === false) {
        setMarkers([]);
      } else {
        setMarkers([]);
        if (markersRes.message) setError(markersRes.message);
      }
    } catch (e) {
      setMeta(null);
      setMarkers([]);
      setError((e as Error).message || 'Failed to load world map data');
    } finally {
      setLoading(false);
    }
  }, [enabled, networkId, quality]);

  const requestSnapshotUpdate = useCallback(async (): Promise<{ ok: boolean; error?: string }> => {
    if (!enabled) return { ok: false, error: 'disabled' };
    setError(null);
    try {
      await getApiClient().post<{ success: boolean; requestId?: string; state?: string }>(
        `/api/worldmap/snapshot/request?network=${networkId}`
      );
      await refreshSnapshotStatus();
      return { ok: true };
    } catch (e) {
      let message = 'Failed to request map snapshot update';
      if (e instanceof ApiClientError) {
        if (e.status === 409) {
          message = 'No nearby player online or cooldown active. Ask a player in-game to run /admweb wm y';
        } else if (e.status === 400) {
          message = e.message || 'Invalid world map request';
        } else {
          message = e.message || message;
        }
      } else if (e instanceof Error) {
        message = e.message;
      }
      setError(message);
      return { ok: false, error: message };
    }
  }, [enabled, networkId, refreshSnapshotStatus]);

  const invalidateTiles = useCallback(
    async (views: string, quality = 'medium') => {
      if (!enabled || !views) return;
      if (meta?.snapshotMode === 'client_only') {
        return;
      }
      try {
        await getApiClient().post<{ success: boolean; invalidatedTiles?: number; prefetchedChunks?: number }>(
          `/api/worldmap/invalidate?network=${networkId}&views=${encodeURIComponent(views)}&quality=${encodeURIComponent(quality)}`
        );
      } catch {
        // Non-fatal
      }
    },
    [enabled, meta?.snapshotMode, networkId]
  );
  useEffect(() => {
    void reload();
  }, [reload]);

  useEffect(() => {
    if (!enabled || !snapshotStatus) return;
    const state = snapshotStatus.captureState;
    if (state !== 'awaiting_consent' && state !== 'capturing') return;
    const timer = window.setInterval(() => {
      void refreshSnapshotStatus();
    }, 3000);
    return () => window.clearInterval(timer);
  }, [enabled, snapshotStatus?.captureState, refreshSnapshotStatus]);

  return {
    meta,
    markers,
    snapshotStatus,
    loading,
    error,
    reload,
    invalidateTiles,
    requestSnapshotUpdate,
    refreshSnapshotStatus,
  };
}
