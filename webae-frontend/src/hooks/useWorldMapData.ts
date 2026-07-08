import { useCallback, useEffect, useState } from 'react';

import { getApiClient } from '@/api/client';
import type { WorldMapMarkerDto, WorldMapMarkersResponse, WorldMapMetaDto } from '@/types/dto';

export interface UseWorldMapDataResult {
  meta: WorldMapMetaDto | null;
  markers: WorldMapMarkerDto[];
  loading: boolean;
  error: string | null;
  reload: () => Promise<void>;
  invalidateTiles: (views: string, quality?: string) => Promise<void>;
}

export function useWorldMapData(networkId: number, enabled: boolean, quality?: string): UseWorldMapDataResult {
  const [meta, setMeta] = useState<WorldMapMetaDto | null>(null);
  const [markers, setMarkers] = useState<WorldMapMarkerDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    if (!enabled) {
      setMeta(null);
      setMarkers([]);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const client = getApiClient();
      const qualityParam = quality ? `&quality=${encodeURIComponent(quality)}` : '';
      const [metaRes, markersRes] = await Promise.all([
        client.get<WorldMapMetaDto>(`/api/worldmap/meta?network=${networkId}${qualityParam}`),
        client.get<WorldMapMarkersResponse>(`/api/worldmap/markers?network=${networkId}`),
      ]);
      setMeta(metaRes);
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

  const invalidateTiles = useCallback(
    async (views: string, quality = 'medium') => {
      if (!enabled || !views) return;
      try {
        await getApiClient().post<{ success: boolean; invalidatedTiles?: number; prefetchedChunks?: number }>(
          `/api/worldmap/invalidate?network=${networkId}&views=${encodeURIComponent(views)}&quality=${encodeURIComponent(quality)}`
        );
      } catch {
        // Non-fatal: tiles will eventually refresh from cache miss
      }
    },
    [enabled, networkId]
  );

  useEffect(() => {
    void reload();
  }, [reload]);

  return { meta, markers, loading, error, reload, invalidateTiles };
}
