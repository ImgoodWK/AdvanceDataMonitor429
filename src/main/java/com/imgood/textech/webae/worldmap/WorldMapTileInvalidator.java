package com.imgood.textech.webae.worldmap;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.imgood.textech.webae.topology.TopologySnapshot;
import com.imgood.textech.webae.topology.TopologySnapshotStore;

/**
 * Invalidates cached world map tiles for a network scope so the next request re-renders.
 */
public final class WorldMapTileInvalidator {

    private WorldMapTileInvalidator() {}

    public static int invalidateNetwork(String ownerUuid, int networkId, String viewsCsv) {
        return invalidateNetwork(ownerUuid, networkId, viewsCsv, null);
    }

    public static int invalidateNetwork(String ownerUuid, int networkId, String viewsCsv, String layerParam) {
        if (ownerUuid == null || ownerUuid.isEmpty() || networkId < 0) {
            return 0;
        }
        Set<String> viewIds = parseViewIds(viewsCsv);
        if (viewIds.isEmpty()) {
            viewIds.add(WorldMapView.FLAT.id);
            viewIds.add(WorldMapView.OBLIQUE_SE.id);
            viewIds.add(WorldMapView.OBLIQUE_SW.id);
            viewIds.add(WorldMapView.OBLIQUE_NE.id);
            viewIds.add(WorldMapView.OBLIQUE_NW.id);
        }

        Set<String> layers = parseLayers(layerParam);

        TopologySnapshot logical = TopologySnapshotStore.loadSnapshot(ownerUuid, networkId, "logical");
        if (logical == null) {
            return 0;
        }
        List<WorldMapMarkerDto> markers = WorldMapMarkerBuilder.fromLogicalSnapshot(logical);
        WorldMapMetaDto meta = WorldMapBoundsBuilder.buildMeta(ownerUuid, networkId, logical, markers);
        if (meta == null || meta.dimensions == null || meta.dimensions.isEmpty()) {
            return 0;
        }

        WorldMapBoundsBuilder.invalidateCache(ownerUuid, networkId);
        int removed = 0;
        for (WorldMapMetaDto.DimensionInfo dimInfo : meta.dimensions) {
            if (dimInfo == null) {
                continue;
            }
            for (String viewId : viewIds) {
                WorldMapView view = WorldMapView.fromId(viewId);
                if (view == null || !WorldMapView.isEnabled(view)) {
                    continue;
                }
                for (String layer : layers) {
                    removed += invalidateDimension(view.id, layer, dimInfo);
                }
            }
        }
        return removed;
    }

    private static Set<String> parseLayers(String layerParam) {
        Set<String> out = new HashSet<String>();
        if (layerParam == null || layerParam.trim()
            .isEmpty()) {
            out.add(WorldMapTileLayer.TERRAIN);
            out.add(WorldMapTileLayer.AE);
            return out;
        }
        String[] parts = layerParam.split(",");
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String token = part.trim()
                .toLowerCase();
            if (token.isEmpty()) {
                continue;
            }
            if ("all".equals(token)) {
                out.add(WorldMapTileLayer.TERRAIN);
                out.add(WorldMapTileLayer.AE);
            } else if (WorldMapTileLayer.AE.equals(token) || WorldMapTileLayer.TERRAIN.equals(token)) {
                out.add(token);
            }
        }
        if (out.isEmpty()) {
            out.add(WorldMapTileLayer.TERRAIN);
            out.add(WorldMapTileLayer.AE);
        }
        return out;
    }

    private static int invalidateDimension(String viewId, String layer, WorldMapMetaDto.DimensionInfo dimInfo) {
        int removed = 0;
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
                    WorldMapTileCache.invalidateAllTiers(viewId, layer, dimInfo.dim, chunkX, chunkZ);
                    removed++;
                } catch (NumberFormatException ignored) {}
            }
            return removed;
        }

        if (dimInfo.minChunkX > dimInfo.maxChunkX || dimInfo.minChunkZ > dimInfo.maxChunkZ) {
            return 0;
        }
        for (int cx = dimInfo.minChunkX; cx <= dimInfo.maxChunkX; cx++) {
            for (int cz = dimInfo.minChunkZ; cz <= dimInfo.maxChunkZ; cz++) {
                WorldMapTileCache.invalidateAllTiers(viewId, layer, dimInfo.dim, cx, cz);
                removed++;
            }
        }
        return removed;
    }

    private static Set<String> parseViewIds(String viewsCsv) {
        Set<String> out = new HashSet<String>();
        if (viewsCsv == null || viewsCsv.trim()
            .isEmpty()) {
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
        return out;
    }
}
