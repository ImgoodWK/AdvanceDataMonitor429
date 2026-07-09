import { useCallback, useEffect, useState } from 'react';

import { getApiClient } from '@/api/client';
import type {
  WorldMapMarkerDto,
  WorldMapMarkersResponse,
  WorldMapMetaDto,
  WorldMapSnapshotStatusDto,
} from '@/types/dto';

export interface UseWorldMapDataResult {
  meta: WorldMapMetaDto | null;
  markers: WorldMapMarkerDto[];
  snapshotStatus: WorldMapSnapshotStatusDto | null;
  loading: boolean;
  error: string | null;
  reload: () => Promise<void>;
  /** @deprecated Use requestSnapshotUpdate in client_only mode. */
  invalidateTiles: (views: string, quality?: string) => Promise<void>;
  requestSnapshotUpdate: () => Promise<WorldMapSnapshotStatusDto | null>;
  refreshSnapshotStatus: () => Promise<void>;
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

  const requestSnapshotUpdate = useCallback(async () => {
    if (!enabled) return null;
    try {
      await getApiClient().post<{ success: boolean; requestId?: string; state?: string }>(
        `/api/worldmap/snapshot/request?network=${networkId}`
      );
      await refreshSnapshotStatus();
      return snapshotStatus;
    } catch (e) {
      setError((e as Error).message || 'Failed to request map snapshot update');
      return null;
    }
  }, [enabled, networkId, refreshSnapshotStatus, snapshotStatus]);

  const invalidateTiles = useCallback(
    async (views: string, quality = 'medium') => {
      if (!enabled || !views) return;
      if (meta?.snapshotMode === 'client_only') {
        await requestSnapshotUpdate();
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
    [enabled, meta?.snapshotMode, networkId, requestSnapshotUpdate]
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
