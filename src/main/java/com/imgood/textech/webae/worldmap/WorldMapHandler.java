package com.imgood.textech.webae.worldmap;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.topology.TopologySnapshot;
import com.imgood.textech.webae.topology.TopologySnapshotStore;

import fi.iki.elonen.NanoHTTPD;

/**
 * REST handler for {@code GET /api/worldmap/meta}, {@code GET /api/worldmap/markers}, and
 * {@code GET /api/worldmap/tiles/[view/][ae/]<dim>/<chunkX>/<chunkZ>.png}.
 */
public final class WorldMapHandler {

    private static final String TILES_PREFIX = "/api/worldmap/tiles/";
    private static final String DEFAULT_VIEW = "flat";

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private WorldMapHandler() {}

    public static NanoHTTPD.Response handleMeta(Map<String, String> params, String ownerUuid) {
        return handleMeta(params, ownerUuid, null);
    }

    public static NanoHTTPD.Response handleMeta(Map<String, String> params, String ownerUuid, String actorUuid) {
        NanoHTTPD.Response disabled = checkEnabled();
        if (disabled != null) {
            return disabled;
        }
        int networkId = parseNetworkId(params);
        if (networkId < 0) {
            return parseNetworkError(networkId);
        }

        TopologySnapshot logical = TopologySnapshotStore.loadSnapshot(ownerUuid, networkId, "logical");
        if (logical == null) {
            WorldMapMetaDto empty = new WorldMapMetaDto();
            empty.success = true;
            empty.hasLogicalSnapshot = false;
            empty.worldMapEnabled = Config.webWorldMapEnabled && Config.webTopologyEnabled;
            empty.views = WorldMapView.uiViewInfos();
            empty.obliqueDirections = WorldMapView.enabledObliqueDirectionInfos();
            empty.hdAvailable = WorldMapHdSupport.isHdAvailable(ownerUuid, actorUuid);
            empty.message = "No logical topology snapshot yet. Capture one manually.";
            empty.cooldownMs = Math.max(1000L, Config.webTopologyCacheTtlMs);
            empty.cooldownRemainingMs = com.imgood.textech.webae.topology.TopologyCache.instance()
                .remainingCooldownMs(ownerUuid, networkId);
            return json(NanoHTTPD.Response.Status.OK, GSON.toJson(empty));
        }

        List<WorldMapMarkerDto> markers = WorldMapMarkerBuilder.fromLogicalSnapshot(logical);
        WorldMapMetaDto meta = WorldMapBoundsBuilder.buildMeta(ownerUuid, networkId, logical, markers, actorUuid);
        return json(NanoHTTPD.Response.Status.OK, GSON.toJson(meta));
    }

    public static NanoHTTPD.Response handleInvalidate(Map<String, String> params, String ownerUuid) {
        NanoHTTPD.Response disabled = checkEnabled();
        if (disabled != null) {
            return disabled;
        }
        int networkId = parseNetworkId(params);
        if (networkId < 0) {
            return parseNetworkError(networkId);
        }
        String views = params.get("views");
        if (views == null || views.isEmpty()) {
            views = params.get("view");
        }
        String layer = params.get("layer");
        int removed = WorldMapTileInvalidator.invalidateNetwork(ownerUuid, networkId, views, layer);
        String body = "{\"success\":true,\"invalidatedTiles\":" + removed + "}";
        return json(NanoHTTPD.Response.Status.OK, body);
    }

    public static NanoHTTPD.Response handleMarkers(Map<String, String> params, String ownerUuid) {
        NanoHTTPD.Response disabled = checkEnabled();
        if (disabled != null) {
            return disabled;
        }
        int networkId = parseNetworkId(params);
        if (networkId < 0) {
            return parseNetworkError(networkId);
        }

        TopologySnapshot logical = TopologySnapshotStore.loadSnapshot(ownerUuid, networkId, "logical");
        if (logical == null) {
            return json(
                NanoHTTPD.Response.Status.OK,
                "{\"success\":false,\"hasLogicalSnapshot\":false,\"code\":\"no_logical_snapshot\",\"message\":\"No logical topology snapshot yet. Capture one via POST /api/network/topology/snapshot?mode=logical\"}");
        }

        List<WorldMapMarkerDto> markers = WorldMapMarkerBuilder.fromLogicalSnapshot(logical);
        String body = "{\"success\":true,\"hasLogicalSnapshot\":true,\"markers\":" + GSON.toJson(markers) + "}";
        return json(NanoHTTPD.Response.Status.OK, body);
    }

    /**
     * Serves a cached chunk PNG or enqueues rendering and returns a stripe placeholder.
     * Supports legacy {@code /tiles/<dim>/<cx>/<cz>.png}, {@code /tiles/<view>/<dim>/<cx>/<cz>.png},
     * and {@code /tiles/<view>/ae/<dim>/<cx>/<cz>.png}.
     */
    public static NanoHTTPD.Response handleTile(String uri, Map<String, String> params, String ownerUuid) {
        NanoHTTPD.Response disabled = checkEnabled();
        if (disabled != null) {
            return disabled;
        }

        if (uri == null || !uri.startsWith(TILES_PREFIX) || !uri.endsWith(".png")) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Invalid world map tile path\"}");
        }

        String pathPart = uri.substring(TILES_PREFIX.length(), uri.length() - 4);
        String[] parts = pathPart.split("/");
        String view;
        String layer = WorldMapTileLayer.TERRAIN;
        int dim;
        int chunkX;
        int chunkZ;
        if (parts.length == 3) {
            view = DEFAULT_VIEW;
            try {
                dim = Integer.parseInt(parts[0].trim());
                chunkX = Integer.parseInt(parts[1].trim());
                chunkZ = Integer.parseInt(parts[2].trim());
            } catch (NumberFormatException e) {
                return json(
                    NanoHTTPD.Response.Status.BAD_REQUEST,
                    "{\"success\":false,\"message\":\"Invalid tile coordinates\"}");
            }
        } else if (parts.length == 4) {
            view = parts[0].trim();
            if (view.isEmpty()) {
                view = DEFAULT_VIEW;
            }
            try {
                dim = Integer.parseInt(parts[1].trim());
                chunkX = Integer.parseInt(parts[2].trim());
                chunkZ = Integer.parseInt(parts[3].trim());
            } catch (NumberFormatException e) {
                return json(
                    NanoHTTPD.Response.Status.BAD_REQUEST,
                    "{\"success\":false,\"message\":\"Invalid tile coordinates\"}");
            }
        } else if (parts.length == 5 && WorldMapTileLayer.AE.equalsIgnoreCase(parts[1].trim())) {
            view = parts[0].trim();
            if (view.isEmpty()) {
                view = DEFAULT_VIEW;
            }
            layer = WorldMapTileLayer.AE;
            try {
                dim = Integer.parseInt(parts[2].trim());
                chunkX = Integer.parseInt(parts[3].trim());
                chunkZ = Integer.parseInt(parts[4].trim());
            } catch (NumberFormatException e) {
                return json(
                    NanoHTTPD.Response.Status.BAD_REQUEST,
                    "{\"success\":false,\"message\":\"Invalid tile coordinates\"}");
            }
        } else {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Expected /api/worldmap/tiles/[view/][ae/]<dim>/<chunkX>/<chunkZ>.png\"}");
        }

        if (WorldMapTileLayer.isAe(layer) && !Config.webWorldMapAeOverlayEnabled) {
            return serveTransparentTile();
        }

        WorldMapView parsedView = WorldMapView.fromId(view);
        if (parsedView == null || !WorldMapView.isEnabled(parsedView)) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Unknown or disabled world map view\"}");
        }

        if (Config.webWorldMapRequireNetworkScope) {
            int networkId = parseNetworkId(params);
            if (networkId < 0) {
                return parseNetworkError(networkId);
            }
            if (!isChunkAllowed(ownerUuid, networkId, dim, chunkX, chunkZ)) {
                return serveTransparentTile();
            }
            return serveOrEnqueueTile(parsedView, layer, dim, chunkX, chunkZ, ownerUuid, networkId);
        }

        int networkId = parseNetworkId(params);
        if (networkId < 0) {
            networkId = 0;
        }
        return serveOrEnqueueTile(parsedView, layer, dim, chunkX, chunkZ, ownerUuid, networkId);
    }

    private static NanoHTTPD.Response serveOrEnqueueTile(WorldMapView parsedView, String layer, int dim, int chunkX,
        int chunkZ, String ownerUuid, int networkId) {
        File cached = WorldMapTileCache.getExisting(parsedView.id, layer, dim, chunkX, chunkZ);
        if (cached != null) {
            String quality = WorldMapTileCache.isHd(parsedView.id, layer, dim, chunkX, chunkZ) ? "hd" : "standard";
            return servePngFile(cached, true, quality);
        }

        if (WorldMapTileLayer.isAe(layer)) {
            List<WorldMapAePlacementRecord> inChunk = WorldMapAePlacementSupport.filterChunk(
                WorldMapAePlacementSupport.loadForNetwork(ownerUuid, networkId),
                dim,
                chunkX,
                chunkZ);
            if (inChunk.isEmpty()) {
                return serveTransparentTile();
            }
        }

        WorldMapTileQueue.instance()
            .enqueue(parsedView.id, layer, dim, chunkX, chunkZ, ownerUuid, networkId);

        if (WorldMapTileLayer.isAe(layer)) {
            return serveTransparentTile(false);
        }
        return servePngBytes(
            WorldMapFlatRenderer.stripePlaceholder(Math.max(16, Config.webWorldMapTilePx)),
            false,
            "standard");
    }

    private static boolean isChunkAllowed(String ownerUuid, int networkId, int dim, int chunkX, int chunkZ) {
        WorldMapMetaDto meta = WorldMapBoundsBuilder.rebuild(ownerUuid, networkId);
        if (meta == null || meta.dimensions == null) {
            return false;
        }
        for (WorldMapMetaDto.DimensionInfo info : meta.dimensions) {
            if (info == null || info.dim != dim) {
                continue;
            }
            return WorldMapChunkSetBuilder.containsChunk(info, chunkX, chunkZ);
        }
        return false;
    }

    private static NanoHTTPD.Response serveTransparentTile() {
        return serveTransparentTile(true);
    }

    private static NanoHTTPD.Response serveTransparentTile(boolean longCache) {
        return servePngBytes(WorldMapFlatRenderer.transparentPlaceholder(), longCache, "standard");
    }

    private static NanoHTTPD.Response servePngFile(File file, boolean longCache, String quality) {
        try {
            FileInputStream fis = new FileInputStream(file);
            NanoHTTPD.Response resp = NanoHTTPD.newChunkedResponse(NanoHTTPD.Response.Status.OK, "image/png", fis);
            if (longCache) {
                resp.addHeader("Cache-Control", "public, max-age=3600");
                resp.addHeader("X-WorldMap-Tile-Status", "cached");
            } else {
                resp.addHeader("Cache-Control", "public, max-age=60");
            }
            resp.addHeader("X-WorldMap-Tile-Quality", quality);
            return resp;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to serve world map tile {}", file.getAbsolutePath(), e);
            return json(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "{\"success\":false,\"message\":\"Failed to read tile\"}");
        }
    }

    private static NanoHTTPD.Response servePngBytes(byte[] png, boolean longCache, String quality) {
        if (png == null || png.length == 0) {
            return json(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "{\"success\":false,\"message\":\"Tile placeholder unavailable\"}");
        }
        NanoHTTPD.Response resp = NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "image/png",
            new ByteArrayInputStream(png),
            png.length);
        if (longCache) {
            resp.addHeader("Cache-Control", "public, max-age=3600");
            resp.addHeader("X-WorldMap-Tile-Status", "cached");
        } else {
            resp.addHeader("Cache-Control", "public, max-age=60");
            resp.addHeader("X-WorldMap-Tile-Status", "pending");
        }
        resp.addHeader("X-WorldMap-Tile-Quality", quality);
        return resp;
    }

    private static NanoHTTPD.Response checkEnabled() {
        if (!Config.webTopologyEnabled) {
            return json(
                NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE,
                "{\"success\":false,\"message\":\"World map requires topology API enabled\",\"code\":\"topology_disabled\"}");
        }
        if (!Config.webWorldMapEnabled) {
            return json(
                NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE,
                "{\"success\":false,\"message\":\"World map API is disabled\",\"code\":\"worldmap_disabled\"}");
        }
        return null;
    }

    private static int parseNetworkId(Map<String, String> params) {
        String networkStr = params.get("network");
        if (networkStr == null || networkStr.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(networkStr.trim());
        } catch (NumberFormatException e) {
            return -2;
        }
    }

    private static NanoHTTPD.Response parseNetworkError(int id) {
        if (id == -1) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing 'network' parameter\"}");
        }
        return json(
            NanoHTTPD.Response.Status.BAD_REQUEST,
            "{\"success\":false,\"message\":\"Invalid 'network' parameter\"}");
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
