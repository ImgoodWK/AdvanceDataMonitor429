package com.imgood.textech.webae.worldmap;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.imgood.textech.Config;
import com.imgood.textech.webae.topology.TopologySnapshot;
import com.imgood.textech.webae.topology.TopologySnapshotStore;

/**
 * Batch-enqueues terrain + ae tiles for all allowed chunks after invalidate or snapshot.
 */
public final class WorldMapTilePrefetcher {

    private WorldMapTilePrefetcher() {}

    public static int prefetchNetwork(String ownerUuid, int networkId, String viewsCsv, WorldMapQualityTier quality) {
        if (ownerUuid == null || ownerUuid.isEmpty() || networkId < 0) {
            return 0;
        }
        if (!Config.webWorldMapEnabled || !Config.webTopologyEnabled) {
            return 0;
        }
        WorldMapQualityTier tier = WorldMapQualityTier.clamp(
            quality != null ? quality : WorldMapQualityTier.fromConfigDefault(),
            WorldMapQualityTier.fromConfigMax());

        TopologySnapshot logical = TopologySnapshotStore.loadSnapshot(ownerUuid, networkId, "logical");
        if (logical == null) {
            return 0;
        }
        List<WorldMapMarkerDto> markers = WorldMapMarkerBuilder.fromLogicalSnapshot(logical);
        WorldMapMetaDto meta = WorldMapBoundsBuilder.buildMeta(ownerUuid, networkId, logical, markers);
        if (meta == null || meta.dimensions == null || meta.dimensions.isEmpty()) {
            return 0;
        }

        Set<String> viewIds = parseViewIds(viewsCsv);
        int enqueued = 0;
        for (WorldMapMetaDto.DimensionInfo dimInfo : meta.dimensions) {
            if (dimInfo == null) {
                continue;
            }
            for (String viewId : viewIds) {
                WorldMapTileProgressTracker.instance()
                    .beginSession(networkId, viewId, tier, dimInfo.dim);
                WorldMapView view = WorldMapView.fromId(viewId);
                if (view == null || !WorldMapView.isEnabled(view)) {
                    continue;
                }
                enqueued += prefetchDimension(view.id, tier, dimInfo, ownerUuid, networkId);
            }
        }
        return enqueued;
    }

    private static int prefetchDimension(String viewId, WorldMapQualityTier tier, WorldMapMetaDto.DimensionInfo dimInfo,
        String ownerUuid, int networkId) {
        int enqueued = 0;
        if (dimInfo.allowedChunks != null && !dimInfo.allowedChunks.isEmpty()) {
            for (String pair : dimInfo.allowedChunks) {
                if (pair == null || pair.isEmpty()) {
                    continue;
                }
                String[] parts = pair.split(",");
                if (parts.length != 2) {
                    continue;
                }
                try {
                    int chunkX = Integer.parseInt(parts[0].trim());
                    int chunkZ = Integer.parseInt(parts[1].trim());
                    WorldMapPrefetchQueue.instance()
                        .schedule(viewId, tier, dimInfo.dim, chunkX, chunkZ, ownerUuid, networkId);
                    enqueued++;
                } catch (NumberFormatException ignored) {}
            }
            return enqueued;
        }
        if (dimInfo.minChunkX > dimInfo.maxChunkX || dimInfo.minChunkZ > dimInfo.maxChunkZ) {
            return 0;
        }
        int max = Math.max(1, Config.webWorldMapMaxChunks);
        int count = 0;
        outer:
        for (int cx = dimInfo.minChunkX; cx <= dimInfo.maxChunkX; cx++) {
            for (int cz = dimInfo.minChunkZ; cz <= dimInfo.maxChunkZ; cz++) {
                if (count >= max) {
                    break outer;
                }
                WorldMapPrefetchQueue.instance()
                    .schedule(viewId, tier, dimInfo.dim, cx, cz, ownerUuid, networkId);
                enqueued++;
                count++;
            }
        }
        return enqueued;
    }

    private static Set<String> parseViewIds(String viewsCsv) {
        Set<String> out = new HashSet<String>();
        if (viewsCsv == null || viewsCsv.trim()
            .isEmpty()) {
            out.add(WorldMapView.FLAT.id);
            for (WorldMapView view : WorldMapView.enabledViews()) {
                if (view.isOblique()) {
                    out.add(view.id);
                }
            }
            return out;
        }
        String[] parts = viewsCsv.split(",");
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            if ("oblique".equalsIgnoreCase(token)) {
                for (WorldMapView view : WorldMapView.enabledViews()) {
                    if (view.isOblique()) {
                        out.add(view.id);
                    }
                }
                continue;
            }
            WorldMapView parsed = WorldMapView.fromId(token);
            if (parsed != null) {
                out.add(parsed.id);
            } else {
                WorldMapObliqueDirection dir = WorldMapObliqueDirection.fromId(token);
                if (dir != null) {
                    out.add(WorldMapView.obliqueForDirection(dir).id);
                }
            }
        }
        if (out.isEmpty()) {
            out.add(WorldMapView.FLAT.id);
        }
        return out;
    }
}
