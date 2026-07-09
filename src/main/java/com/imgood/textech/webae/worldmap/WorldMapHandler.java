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
import com.imgood.textech.webae.worldmap.dynmap.WorldMapDynmapCoordMapper;
import com.imgood.textech.webae.worldmap.dynmap.WorldMapDynmapDetector;
import com.imgood.textech.webae.worldmap.dynmap.WorldMapDynmapTileProvider;
import com.imgood.textech.webae.worldmap.dynmap.WorldMapDynmapTileRoot;
import com.imgood.textech.webae.worldmap.engine.WorldMapRenderEngines;
import com.imgood.textech.webae.worldmap.engine.WorldMapZoomPyramid;

import fi.iki.elonen.NanoHTTPD;

/**
 * REST handler for {@code GET /api/worldmap/meta}, {@code GET /api/worldmap/markers},
 * {@code GET /api/worldmap/progress},
 * {@code GET /api/worldmap/tiles/[view/][ae/]<dim>/<chunkX>/<chunkZ>.png},
 * and {@code GET /api/worldmap/dynmap-tiles/<world>/<zoom>/<x>/<y>.png}.
 */
public final class WorldMapHandler {

    private static final String TILES_PREFIX = "/api/worldmap/tiles/";
    private static final String DYNMAP_TILES_PREFIX = "/api/worldmap/dynmap-tiles/";
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
        WorldMapQualityTier quality = parseQuality(params);

        TopologySnapshot logical = TopologySnapshotStore.loadSnapshot(ownerUuid, networkId, "logical");
        if (logical == null) {
            WorldMapMetaDto empty = new WorldMapMetaDto();
            empty.success = true;
            empty.hasLogicalSnapshot = false;
            empty.worldMapEnabled = Config.webWorldMapEnabled && Config.webTopologyEnabled;
            empty.views = WorldMapView.uiViewInfos();
            empty.obliqueDirections = WorldMapView.enabledObliqueDirectionInfos();
            empty.hdAvailable = WorldMapHdSupport.isHdAvailable(ownerUuid, actorUuid);
            empty.maxQualityTier = WorldMapQualityTier.fromConfigMax().id;
            empty.defaultQualityTier = WorldMapQualityTier.fromConfigDefault().id;
            empty.aeOverlayQualityTier = WorldMapQualityTier.fromConfigAeOverlay().id;
            empty.aeQualityBoost = Config.webWorldMapAeQualityBoost;
            empty.qualityTiers = WorldMapBoundsBuilder.buildQualityTierInfosPublic();
            empty.tilePx = WorldMapRenderSupport.tilePx(quality);
            empty.pxPerBlock = WorldMapRenderSupport.pxPerBlock(quality);
            empty.flatRenderEngine = WorldMapRenderEngines.flatEngineId();
            empty.obliqueRenderEngine = WorldMapRenderEngines.obliqueEngineId();
            empty.zoomLevels = WorldMapBoundsBuilder.buildZoomLevelInfosPublic(quality);
            empty.recommendedZoom = 0;
            empty.message = "No logical topology snapshot yet. Capture one manually.";
            empty.cooldownMs = Math.max(1000L, Config.webTopologyCacheTtlMs);
            empty.cooldownRemainingMs = com.imgood.textech.webae.topology.TopologyCache.instance()
                .remainingCooldownMs(ownerUuid, networkId);
            applyDynmapMeta(empty);
            applyCaptureMeta(empty);
            applySnapshotMeta(empty, ownerUuid, networkId);
            return json(NanoHTTPD.Response.Status.OK, GSON.toJson(empty));
        }

        List<WorldMapMarkerDto> markers = WorldMapMarkerBuilder.fromLogicalSnapshot(logical);
        WorldMapMetaDto meta = WorldMapBoundsBuilder.buildMeta(ownerUuid, networkId, logical, markers, actorUuid,
            quality);
        applyDynmapMeta(meta);
        applyCaptureMeta(meta);
        applySnapshotMeta(meta, ownerUuid, networkId);
        return json(NanoHTTPD.Response.Status.OK, GSON.toJson(meta));
    }

    private static void applyCaptureMeta(WorldMapMetaDto meta) {
        if (meta == null) {
            return;
        }
        meta.clientCaptureMode = WorldMapClientCaptureMode.normalized();
        meta.progressiveFallback = Config.webWorldMapProgressiveFallback;
        meta.snapshotMode = WorldMapSnapshotMode.normalized();
        meta.journeyMapPreferred = Config.worldMapJourneyMapEnabled;
        if (WorldMapSnapshotMode.isClientOnly()) {
            meta.terrainSource = "snapshot";
            meta.progressiveFallback = false;
        }
    }

    private static void applySnapshotMeta(WorldMapMetaDto meta, String ownerUuid, int networkId) {
        if (meta == null) {
            return;
        }
        meta.snapshotVersion = WorldMapSnapshotStore.currentVersion(ownerUuid, networkId);
        WorldMapSnapshotManifest manifest = WorldMapSnapshotStore.loadCurrentManifest(ownerUuid, networkId);
        if (manifest != null) {
            meta.snapshotSource = manifest.source != null ? manifest.source : "";
            if (manifest.tilePx > 0) {
                meta.tilePx = manifest.tilePx;
            }
        }
    }

    public static NanoHTTPD.Response handleSnapshotManifest(Map<String, String> params, String ownerUuid) {
        NanoHTTPD.Response disabled = checkEnabled();
        if (disabled != null) {
            return disabled;
        }
        int networkId = parseNetworkId(params);
        if (networkId < 0) {
            return parseNetworkError(networkId);
        }
        WorldMapSnapshotManifest manifest = WorldMapSnapshotStore.loadCurrentManifest(ownerUuid, networkId);
        if (manifest == null) {
            return json(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"hasSnapshot\":false}");
        }
        return json(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"hasSnapshot\":true,\"manifest\":" + GSON.toJson(manifest) + "}");
    }

    public static NanoHTTPD.Response handleSnapshotStatus(Map<String, String> params, String ownerUuid) {
        NanoHTTPD.Response disabled = checkEnabled();
        if (disabled != null) {
            return disabled;
        }
        int networkId = parseNetworkId(params);
        if (networkId < 0) {
            return parseNetworkError(networkId);
        }
        WorldMapSnapshotStatusDto status = WorldMapCaptureCoordinator.instance()
            .buildStatus(ownerUuid, networkId);
        return json(NanoHTTPD.Response.Status.OK, GSON.toJson(status));
    }

    public static NanoHTTPD.Response handleSnapshotRequest(Map<String, String> params, String ownerUuid,
        String actorUuid, String actorName) {
        NanoHTTPD.Response disabled = checkEnabled();
        if (disabled != null) {
            return disabled;
        }
        int networkId = parseNetworkId(params);
        if (networkId < 0) {
            return parseNetworkError(networkId);
        }
        boolean ownerIsRequester = ownerUuid != null && ownerUuid.equals(actorUuid);
        String requestId = WorldMapCaptureCoordinator.instance()
            .requestSnapshot(ownerUuid, networkId, actorUuid, actorName, false, ownerIsRequester);
        if (requestId == null) {
            return json(
                NanoHTTPD.Response.Status.CONFLICT,
                "{\"success\":false,\"message\":\"No nearby online player or cooldown active. Use /admweb worldmap upload in-game.\"}");
        }
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"requestId\":\"" + requestId + "\",\"state\":\"awaiting_consent\"}");
    }

    /**
     * Populates the {@code terrainSource}, {@code dynmapAvailable}, {@code dynmapWorldName},
     * and {@code dynmapTileUrlTemplate} fields on a meta DTO based on auto-detection or config.
     */
    private static void applyDynmapMeta(WorldMapMetaDto meta) {
        String source = Config.worldMapTerrainSource;
        if (source == null || source.trim().isEmpty()) {
            source = "auto";
        }
        source = source.trim().toLowerCase();

        boolean dynmapAvailable = WorldMapDynmapDetector.isDynmapAvailable();
        String dynmapWorldName = resolveDynmapWorldName();

        if ("dynmap".equals(source)) {
            meta.terrainSource = "dynmap";
            meta.dynmapAvailable = true;
            meta.dynmapWorldName = dynmapWorldName;
            meta.dynmapTileUrlTemplate = "/api/worldmap/dynmap-tiles/"
                + (dynmapWorldName != null ? dynmapWorldName : "world")
                + "/{z}/{x}/{y}.png";
            meta.dynmapMaxZoom = 6;
        } else if ("self".equals(source)) {
            meta.terrainSource = "self";
            meta.dynmapAvailable = dynmapAvailable;
        } else {
            // auto
            if (dynmapAvailable && dynmapWorldName != null) {
                meta.terrainSource = "dynmap";
                meta.dynmapAvailable = true;
                meta.dynmapWorldName = dynmapWorldName;
                meta.dynmapTileUrlTemplate = "/api/worldmap/dynmap-tiles/" + dynmapWorldName + "/{z}/{x}/{y}.png";
                meta.dynmapMaxZoom = 6;
            } else {
                meta.terrainSource = "self";
                meta.dynmapAvailable = dynmapAvailable;
            }
        }
    }

    /**
     * Resolves the Dynmap world name by checking which worlds have tiles available.
     */
    private static String resolveDynmapWorldName() {
        if (!WorldMapDynmapDetector.isDynmapAvailable()) {
            return null;
        }
        // Check common world names
        String[] candidates = { "world", "DIM0", "DIM-1", "DIM1" };
        for (String candidate : candidates) {
            if (WorldMapDynmapTileProvider.hasTiles(candidate)) {
                return candidate;
            }
        }
        // Fallback: return the first world name that has tiles
        java.nio.file.Path root = WorldMapDynmapTileRoot.getTileRoot();
        if (root != null) {
            File[] dirs = root.toFile().listFiles();
            if (dirs != null) {
                for (File dir : dirs) {
                    if (dir.isDirectory() && WorldMapDynmapTileProvider.hasTiles(dir.getName())) {
                        return dir.getName();
                    }
                }
            }
        }
        return "world";
    }

    public static NanoHTTPD.Response handleProgress(Map<String, String> params) {
        NanoHTTPD.Response disabled = checkEnabled();
        if (disabled != null) {
            return disabled;
        }
        WorldMapQualityTier quality = parseQuality(params);
        String view = params.get("view");
        if (view == null || view.isEmpty()) {
            view = DEFAULT_VIEW;
        }
        int dim = 0;
        String dimStr = params.get("dim");
        if (dimStr != null && !dimStr.isEmpty()) {
            try {
                dim = Integer.parseInt(dimStr.trim());
            } catch (NumberFormatException ignored) {}
        }
        int networkId = parseNetworkId(params);
        if (networkId < 0) {
            networkId = 0;
        }
        String body = WorldMapTileProgressTracker.instance()
            .toJson(networkId, view, quality, dim);
        return json(NanoHTTPD.Response.Status.OK, body);
    }

    public static NanoHTTPD.Response handleInvalidate(Map<String, String> params, String ownerUuid) {
        NanoHTTPD.Response disabled = checkEnabled();
        if (disabled != null) {
            return disabled;
        }
        if (WorldMapSnapshotMode.isClientOnly()) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Use POST /api/worldmap/snapshot/request for manual snapshot updates\"}");
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
        WorldMapQualityTier quality = parseQuality(params);
        int removed = WorldMapTileInvalidator.invalidateNetwork(ownerUuid, networkId, views, layer);
        int prefetched = WorldMapTilePrefetcher.prefetchNetwork(ownerUuid, networkId, views, quality);
        String body = "{\"success\":true,\"invalidatedTiles\":" + removed + ",\"prefetchedChunks\":" + prefetched
            + ",\"quality\":\"" + quality.id + "\"}";
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
        return handleTile(uri, params, ownerUuid, null);
    }

    public static NanoHTTPD.Response handleTile(String uri, Map<String, String> params, String ownerUuid,
        String actorUuid) {
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
            return serveTransparentTile(layer, WorldMapQualityTier.MEDIUM);
        }

        WorldMapView parsedView = WorldMapView.fromId(view);
        if (parsedView == null || !WorldMapView.isEnabled(parsedView)) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Unknown or disabled world map view\"}");
        }

        WorldMapQualityTier quality = parseQuality(params);
        int zoomLevel = parseZoom(params);

        if (Config.webWorldMapRequireNetworkScope) {
            int networkId = parseNetworkId(params);
            if (networkId < 0) {
                return parseNetworkError(networkId);
            }
            if (!isChunkAllowed(ownerUuid, networkId, dim, chunkX, chunkZ, zoomLevel)) {
                return serveTransparentTile(layer, quality);
            }
            return serveOrEnqueueTile(parsedView, layer, quality, dim, chunkX, chunkZ, zoomLevel, ownerUuid, networkId,
                actorUuid);
        }

        int networkId = parseNetworkId(params);
        if (networkId < 0) {
            networkId = 0;
        }
        return serveOrEnqueueTile(parsedView, layer, quality, dim, chunkX, chunkZ, zoomLevel, ownerUuid, networkId,
            actorUuid);
    }

    /**
     * Serves a pre-rendered Dynmap HD tile from local disk, proxied through WebAE auth.
     * Expected URI: {@code /api/worldmap/dynmap-tiles/<world>/<zoom>/<x>/<y>.png}
     */
    public static NanoHTTPD.Response handleDynmapTile(String uri, Map<String, String> params) {
        NanoHTTPD.Response disabled = checkEnabled();
        if (disabled != null) {
            return disabled;
        }

        if (uri == null || !uri.startsWith(DYNMAP_TILES_PREFIX) || !uri.endsWith(".png")) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Invalid dynmap tile path\"}");
        }

        String pathPart = uri.substring(DYNMAP_TILES_PREFIX.length(), uri.length() - 4);
        String[] parts = pathPart.split("/");
        if (parts.length != 4) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Expected /api/worldmap/dynmap-tiles/<world>/<zoom>/<x>/<y>.png\"}");
        }

        String worldName = parts[0].trim();
        int zoom;
        int tileX;
        int tileY;
        try {
            zoom = Integer.parseInt(parts[1].trim());
            tileX = Integer.parseInt(parts[2].trim());
            tileY = Integer.parseInt(parts[3].trim());
        } catch (NumberFormatException e) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Invalid dynmap tile coordinates\"}");
        }

        // Determine perspective from the active view (flat as default)
        String viewId = params.get("view");
        if (viewId == null || viewId.trim().isEmpty()) {
            viewId = "flat";
        }
        String perspective = WorldMapDynmapCoordMapper.toDynmapPerspective(viewId);

        byte[] png = WorldMapDynmapTileProvider.getTile(worldName, perspective, zoom, tileX, tileY);
        if (png == null) {
            return json(
                NanoHTTPD.Response.Status.NOT_FOUND,
                "{\"success\":false,\"message\":\"Dynmap tile not found\"}");
        }

        NanoHTTPD.Response resp = NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "image/png",
            new ByteArrayInputStream(png),
            png.length);
        resp.addHeader("Cache-Control", "public, max-age=3600");
        resp.addHeader("X-WorldMap-Tile-Source", "dynmap");
        return resp;
    }

    private static NanoHTTPD.Response serveOrEnqueueTile(WorldMapView parsedView, String layer,
        WorldMapQualityTier quality, int dim, int chunkX, int chunkZ, int zoomLevel, String ownerUuid, int networkId,
        String actorUuid) {
        if (WorldMapSnapshotMode.isClientOnly()) {
            return serveSnapshotTile(layer, dim, chunkX, chunkZ, ownerUuid, networkId);
        }
        boolean aeChunk = hasAeInChunk(ownerUuid, networkId, dim, chunkX, chunkZ);
        WorldMapQualityTier layerTier = resolveLayerTier(layer, quality, aeChunk);

        if (zoomLevel > 0) {
            File cachedZoom = WorldMapTileCache.getExisting(parsedView.id, layer, layerTier, dim, chunkX, chunkZ,
                zoomLevel);
            if (cachedZoom != null) {
                return servePngFile(cachedZoom, !WorldMapTileCache.isEmpty(parsedView.id, layer, layerTier, dim,
                    chunkX, chunkZ, zoomLevel), "standard", layer, layerTier, zoomLevel);
            }
            WorldMapZoomPyramid.instance()
                .enqueue(parsedView.id, layer, layerTier, dim, chunkX, chunkZ, zoomLevel);
            if (WorldMapTileLayer.isAe(layer)) {
                return serveTransparentTile(false, layer, layerTier);
            }
            return servePngBytes(
                WorldMapFlatRenderer.stripePlaceholder(WorldMapRenderSupport.tilePx(layerTier)),
                false,
                "standard",
                layer,
                layerTier,
                zoomLevel);
        }

        File cached = WorldMapTileCache.getExisting(parsedView.id, layer, layerTier, dim, chunkX, chunkZ, 0);
        if (cached != null) {
            boolean emptyTile = WorldMapTileCache.isEmpty(parsedView.id, layer, layerTier, dim, chunkX, chunkZ, 0);
            String renderQuality = WorldMapTileCache.isHd(parsedView.id, layer, layerTier, dim, chunkX, chunkZ) ? "hd"
                : "standard";
            return servePngFile(cached, !emptyTile, renderQuality, layer, layerTier, 0);
        }

        if (WorldMapTileLayer.isAe(layer)) {
            List<WorldMapAePlacementRecord> inChunk = WorldMapAePlacementSupport.filterChunk(
                WorldMapAePlacementSupport.loadForNetwork(ownerUuid, networkId),
                dim,
                chunkX,
                chunkZ);
            if (inChunk.isEmpty()) {
                WorldMapTileProgressTracker.instance()
                    .markEmpty(networkId, parsedView.id, layerTier, dim, chunkX, chunkZ, layer);
                return serveEmptyTile(layer, layerTier);
            }
        }

        WorldMapTileQueue.instance()
            .enqueueChunkPair(parsedView.id, quality, dim, chunkX, chunkZ, ownerUuid, networkId, actorUuid);

        if (WorldMapTileLayer.isAe(layer)) {
            return serveTransparentTile(false, layer, layerTier);
        }

        WorldMapTerrainFallback.Result fallback = WorldMapTerrainFallback.find(
            parsedView.id,
            layer,
            layerTier,
            dim,
            chunkX,
            chunkZ);
        if (fallback != null && fallback.png != null && fallback.png.length > 0) {
            String renderQuality = "dynmap_crop".equals(fallback.source) ? "dynmap" : "standard";
            String status = fallback.upgrading ? "upgrading" : "cached";
            return servePngBytes(fallback.png, false, renderQuality, layer, fallback.servedTier, 0, status);
        }

        return servePngBytes(
            WorldMapFlatRenderer.stripePlaceholder(WorldMapRenderSupport.tilePx(layerTier)),
            false,
            "standard",
            layer,
            layerTier,
            0);
    }

    private static boolean hasAeInChunk(String ownerUuid, int networkId, int dim, int chunkX, int chunkZ) {
        List<WorldMapAePlacementRecord> inChunk = WorldMapAePlacementSupport.filterChunk(
            WorldMapAePlacementSupport.loadForNetwork(ownerUuid, networkId),
            dim,
            chunkX,
            chunkZ);
        return !inChunk.isEmpty();
    }

    private static WorldMapQualityTier resolveLayerTier(String layer, WorldMapQualityTier requested, boolean aeChunk) {
        if (WorldMapTileLayer.isAe(layer)) {
            return WorldMapQualityTier.clamp(
                WorldMapQualityTier.fromConfigAeOverlay(),
                WorldMapQualityTier.fromConfigMax());
        }
        return WorldMapQualitySupport.effectiveTier(requested, aeChunk);
    }

    private static boolean isChunkAllowed(String ownerUuid, int networkId, int dim, int chunkX, int chunkZ,
        int zoomLevel) {
        WorldMapMetaDto meta = WorldMapBoundsBuilder.rebuild(ownerUuid, networkId);
        if (meta == null || meta.dimensions == null) {
            return false;
        }
        int span = WorldMapZoomPyramid.chunkSpan(Math.max(0, zoomLevel));
        for (WorldMapMetaDto.DimensionInfo info : meta.dimensions) {
            if (info == null || info.dim != dim) {
                continue;
            }
            if (span <= 1) {
                return WorldMapChunkSetBuilder.containsChunk(info, chunkX, chunkZ);
            }
            int baseX = chunkX * span;
            int baseZ = chunkZ * span;
            for (int dz = 0; dz < span; dz++) {
                for (int dx = 0; dx < span; dx++) {
                    if (WorldMapChunkSetBuilder.containsChunk(info, baseX + dx, baseZ + dz)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return false;
    }

    /** @deprecated scope check without zoom — treats coords as z0 chunk indices. */
    private static boolean isChunkAllowed(String ownerUuid, int networkId, int dim, int chunkX, int chunkZ) {
        return isChunkAllowed(ownerUuid, networkId, dim, chunkX, chunkZ, 0);
    }

    private static WorldMapQualityTier parseQuality(Map<String, String> params) {
        if (params == null) {
            return WorldMapQualityTier.fromConfigDefault();
        }
        String raw = params.get("quality");
        return WorldMapQualityTier.resolveEffective(raw);
    }

    private static int parseZoom(Map<String, String> params) {
        if (params == null) {
            return 0;
        }
        String raw = params.get("zoom");
        if (raw == null || raw.trim()
            .isEmpty()) {
            return 0;
        }
        try {
            int level = Integer.parseInt(raw.trim());
            if (level < 0) {
                return 0;
            }
            if (level >= WorldMapZoomPyramid.configuredLevels()) {
                return WorldMapZoomPyramid.configuredLevels() - 1;
            }
            return level;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static NanoHTTPD.Response serveEmptyTile(String layer, WorldMapQualityTier quality) {
        return servePngBytes(
            WorldMapFlatRenderer.transparentPlaceholder(),
            true,
            "standard",
            layer,
            quality,
            0,
            "empty");
    }

    private static NanoHTTPD.Response serveTransparentTile(String layer, WorldMapQualityTier quality) {
        return serveTransparentTile(true, layer, quality);
    }

    private static NanoHTTPD.Response serveTransparentTile(boolean longCache, String layer,
        WorldMapQualityTier quality) {
        return servePngBytes(WorldMapFlatRenderer.transparentPlaceholder(), longCache, "standard", layer, quality, 0);
    }

    private static NanoHTTPD.Response serveSnapshotTile(String layer, int dim, int chunkX, int chunkZ,
        String ownerUuid, int networkId) {
        File cached = WorldMapSnapshotStore.getCurrentTile(ownerUuid, networkId, layer, dim, chunkX, chunkZ);
        if (cached != null) {
            return servePngFile(cached, true, "snapshot", layer, WorldMapQualityTier.ULTRA, 0, "cached");
        }
        return servePngBytes(
            WorldMapFlatRenderer.stripePlaceholder(128),
            false,
            "snapshot",
            layer,
            WorldMapQualityTier.MEDIUM,
            0,
            "missing");
    }

    private static NanoHTTPD.Response servePngFile(File file, boolean longCache, String quality, String layer,
        WorldMapQualityTier tier, int zoomLevel) {
        return servePngFile(file, longCache, quality, layer, tier, zoomLevel, longCache ? "cached" : "pending");
    }

    private static NanoHTTPD.Response servePngFile(File file, boolean longCache, String quality, String layer,
        WorldMapQualityTier tier, int zoomLevel, String tileStatus) {
        try {
            FileInputStream fis = new FileInputStream(file);
            NanoHTTPD.Response resp = NanoHTTPD.newChunkedResponse(NanoHTTPD.Response.Status.OK, "image/png", fis);
            if (longCache) {
                resp.addHeader("Cache-Control", "public, max-age=3600");
                resp.addHeader("X-WorldMap-Tile-Status", tileStatus != null ? tileStatus : "cached");
            } else {
                resp.addHeader("Cache-Control", "public, max-age=60");
            }
            addTileHeaders(resp, quality, layer, tier, zoomLevel);
            return resp;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to serve world map tile {}", file.getAbsolutePath(), e);
            return json(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "{\"success\":false,\"message\":\"Failed to read tile\"}");
        }
    }

    private static NanoHTTPD.Response servePngBytes(byte[] png, boolean longCache, String quality, String layer,
        WorldMapQualityTier tier, int zoomLevel) {
        return servePngBytes(png, longCache, quality, layer, tier, zoomLevel, longCache ? "cached" : "pending");
    }

    private static NanoHTTPD.Response servePngBytes(byte[] png, boolean longCache, String quality, String layer,
        WorldMapQualityTier tier, int zoomLevel, String tileStatus) {
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
        } else {
            resp.addHeader("Cache-Control", "public, max-age=60");
        }
        if (tileStatus != null && !tileStatus.isEmpty()) {
            resp.addHeader("X-WorldMap-Tile-Status", tileStatus);
        }
        addTileHeaders(resp, quality, layer, tier, zoomLevel);
        return resp;
    }

    private static void addTileHeaders(NanoHTTPD.Response resp, String quality, String layer, WorldMapQualityTier tier,
        int zoomLevel) {
        resp.addHeader("X-WorldMap-Tile-Quality", quality != null ? quality : "standard");
        resp.addHeader("X-WorldMap-Tile-Layer", WorldMapTileLayer.normalize(layer));
        if (tier != null) {
            resp.addHeader("X-WorldMap-Tile-Quality-Tier", tier.id);
        }
        resp.addHeader("X-WorldMap-Tile-Zoom", String.valueOf(Math.max(0, zoomLevel)));
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
