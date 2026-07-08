package com.imgood.textech.webae.worldmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.imgood.textech.Config;
import com.imgood.textech.webae.topology.TopologySnapshot;
import com.imgood.textech.webae.topology.TopologySnapshotStore;
import com.imgood.textech.webae.worldmap.engine.WorldMapBlockPatchRegistry;
import com.imgood.textech.webae.worldmap.engine.WorldMapRenderEngines;
import com.imgood.textech.webae.worldmap.engine.WorldMapServerAtlas;
import com.imgood.textech.webae.worldmap.engine.WorldMapZoomPyramid;

/**
 * Computes per-dimension bounding boxes and chunk counts from world map markers.
 */
public final class WorldMapBoundsBuilder {

    private static final ConcurrentHashMap<String, CachedMeta> META_CACHE = new ConcurrentHashMap<String, CachedMeta>();

    private WorldMapBoundsBuilder() {}

    public static WorldMapMetaDto buildMeta(String ownerUuid, int networkId, TopologySnapshot logical,
        List<WorldMapMarkerDto> markers) {
        return buildMeta(ownerUuid, networkId, logical, markers, null);
    }

    public static WorldMapMetaDto buildMeta(String ownerUuid, int networkId, TopologySnapshot logical,
        List<WorldMapMarkerDto> markers, String actorUuid) {
        return buildMeta(ownerUuid, networkId, logical, markers, actorUuid, null);
    }

    public static WorldMapMetaDto buildMeta(String ownerUuid, int networkId, TopologySnapshot logical,
        List<WorldMapMarkerDto> markers, String actorUuid, WorldMapQualityTier quality) {
        WorldMapQualityTier tier = WorldMapQualityTier.clamp(
            quality != null ? quality : WorldMapQualityTier.fromConfigDefault(),
            WorldMapQualityTier.fromConfigMax());
        WorldMapMetaDto meta = new WorldMapMetaDto();
        meta.hasLogicalSnapshot = logical != null;
        meta.timestamp = logical != null ? logical.timestamp : 0L;
        meta.tilePx = WorldMapRenderSupport.tilePx(tier);
        meta.pxPerBlock = WorldMapRenderSupport.pxPerBlock(tier);
        meta.paddingChunks = Math.max(0, Config.webWorldMapBoundsPaddingChunks);
        meta.maxChunks = Math.max(1, Config.webWorldMapMaxChunks);
        meta.worldMapEnabled = Config.webWorldMapEnabled && Config.webTopologyEnabled;
        meta.cooldownMs = Math.max(1000L, Config.webTopologyCacheTtlMs);
        meta.cooldownRemainingMs = com.imgood.textech.webae.topology.TopologyCache.instance()
            .remainingCooldownMs(ownerUuid, networkId);
        meta.flatRenderEngine = WorldMapRenderEngines.flatEngineId();
        meta.obliqueRenderEngine = WorldMapRenderEngines.obliqueEngineId();
        meta.blockPatchesEnabled = Config.webWorldMapBlockPatchesEnabled;
        meta.aeQualityBoost = Config.webWorldMapAeQualityBoost;
        meta.serverAtlasEnabled = Config.webWorldMapServerAtlasEnabled;
        WorldMapBlockPatchRegistry.ensureLoaded();
        meta.blockPatchEntries = WorldMapBlockPatchRegistry.loadedJsonEntryCount();
        if (Config.webWorldMapServerAtlasEnabled) {
            WorldMapServerAtlas atlas = WorldMapServerAtlas.instance();
            meta.serverAtlasSlots = atlas != null ? atlas.slotCount() : 0;
        } else {
            meta.serverAtlasSlots = 0;
        }
        meta.zoomLevels = buildZoomLevelInfosPublic(tier);
        meta.recommendedZoom = 0;

        if (markers == null) {
            markers = new ArrayList<WorldMapMarkerDto>();
        }
        meta.markerCount = markers.size();

        Map<Integer, DimensionAccumulator> byDim = new HashMap<Integer, DimensionAccumulator>();
        List<WorldMapAePlacementRecord> placements = WorldMapAePlacementSupport.placementsFromSnapshot(logical);
        Map<Integer, WorldMapChunkSetBuilder.DimensionChunkSet> chunkSets;
        if (placements != null && !placements.isEmpty()) {
            chunkSets = WorldMapChunkSetBuilder.buildByPlacements(placements, meta.paddingChunks);
            for (WorldMapAePlacementRecord placement : placements) {
                if (placement == null) {
                    continue;
                }
                DimensionAccumulator acc = byDim.get(placement.dim);
                if (acc == null) {
                    acc = new DimensionAccumulator(placement.dim);
                    byDim.put(placement.dim, acc);
                }
                acc.include(placement.x, placement.z);
                if (!"part".equals(placement.kind)) {
                    acc.markerCount++;
                }
            }
        } else {
            for (WorldMapMarkerDto marker : markers) {
                if (marker == null) {
                    continue;
                }
                DimensionAccumulator acc = byDim.get(marker.dim);
                if (acc == null) {
                    acc = new DimensionAccumulator(marker.dim);
                    byDim.put(marker.dim, acc);
                }
                acc.include(marker.x, marker.z);
                acc.markerCount++;
            }
            chunkSets = WorldMapChunkSetBuilder.buildByDimension(markers, meta.paddingChunks);
        }

        boolean tooLarge = false;
        for (DimensionAccumulator acc : byDim.values()) {
            WorldMapChunkSetBuilder.DimensionChunkSet chunkSet = chunkSets.get(acc.dim);
            WorldMapMetaDto.DimensionInfo info = acc.toInfo(chunkSet, meta.maxChunks);
            if (info.chunkCount > meta.maxChunks) {
                tooLarge = true;
                info.chunkCount = meta.maxChunks;
            }
            meta.dimensions.add(info);
        }
        meta.boundsTooLarge = tooLarge;
        meta.views = WorldMapView.uiViewInfos();
        meta.obliqueDirections = WorldMapView.enabledObliqueDirectionInfos();
        meta.hdAvailable = WorldMapHdSupport.isHdAvailable(ownerUuid, actorUuid);
        meta.maxQualityTier = WorldMapQualityTier.fromConfigMax().id;
        meta.defaultQualityTier = WorldMapQualityTier.fromConfigDefault().id;
        meta.qualityTiers = buildQualityTierInfos();
        return meta;
    }

    private static List<WorldMapMetaDto.QualityTierInfo> buildQualityTierInfos() {
        return buildQualityTierInfosPublic();
    }

    public static List<WorldMapMetaDto.QualityTierInfo> buildQualityTierInfosPublic() {
        List<WorldMapMetaDto.QualityTierInfo> out = new ArrayList<WorldMapMetaDto.QualityTierInfo>();
        WorldMapQualityTier max = WorldMapQualityTier.fromConfigMax();
        for (WorldMapQualityTier tier : WorldMapQualityTier.values()) {
            if (tier.ordinal() > max.ordinal()) {
                continue;
            }
            WorldMapMetaDto.QualityTierInfo info = new WorldMapMetaDto.QualityTierInfo();
            info.id = tier.id;
            info.labelKey = tier.labelKey;
            info.tilePx = tier == WorldMapQualityTier.ULTRA ? tier.hdTilePx() : tier.serverTilePx();
            info.pxPerBlock = tier == WorldMapQualityTier.ULTRA ? tier.pxPerBlock : tier.serverPxPerBlock();
            info.hdCapable = tier.hdCapable;
            out.add(info);
        }
        return out;
    }

    public static List<WorldMapMetaDto.ZoomLevelInfo> buildZoomLevelInfosPublic(WorldMapQualityTier quality) {
        List<WorldMapMetaDto.ZoomLevelInfo> out = new ArrayList<WorldMapMetaDto.ZoomLevelInfo>();
        int levels = WorldMapZoomPyramid.configuredLevels();
        WorldMapQualityTier tier = quality != null ? quality : WorldMapQualityTier.MEDIUM;
        int tilePx = WorldMapRenderSupport.tilePx(tier);
        for (int level = 0; level < levels; level++) {
            WorldMapMetaDto.ZoomLevelInfo info = new WorldMapMetaDto.ZoomLevelInfo();
            info.level = level;
            info.chunkSpan = WorldMapZoomPyramid.chunkSpan(level);
            info.tilePx = tilePx;
            info.pxPerBlock = WorldMapZoomPyramid.effectivePxPerBlock(tier, level);
            out.add(info);
        }
        return out;
    }

    /**
     * Rebuilds bounds from the persisted logical snapshot (no side effects beyond read).
     */
    public static WorldMapMetaDto rebuild(String ownerUuid, int networkId) {
        String cacheKey = cacheKey(ownerUuid, networkId);
        long ttl = Math.max(1000L, Config.webTopologyCacheTtlMs);
        long now = System.currentTimeMillis();
        CachedMeta cached = META_CACHE.get(cacheKey);
        if (cached != null && now - cached.cachedAt < ttl) {
            return cached.meta;
        }
        TopologySnapshot logical = TopologySnapshotStore.loadSnapshot(ownerUuid, networkId, "logical");
        List<WorldMapMarkerDto> markers = WorldMapMarkerBuilder.fromLogicalSnapshot(logical);
        WorldMapMetaDto meta = buildMeta(ownerUuid, networkId, logical, markers);
        META_CACHE.put(cacheKey, new CachedMeta(meta, now));
        return meta;
    }

    public static void invalidateCache(String ownerUuid, int networkId) {
        META_CACHE.remove(cacheKey(ownerUuid, networkId));
    }

    private static String cacheKey(String ownerUuid, int networkId) {
        return ownerUuid + ":" + networkId;
    }

    private static final class CachedMeta {

        final WorldMapMetaDto meta;
        final long cachedAt;

        CachedMeta(WorldMapMetaDto meta, long cachedAt) {
            this.meta = meta;
            this.cachedAt = cachedAt;
        }
    }

    public static String dimensionName(int dim) {
        if (dim == 0) {
            return "Overworld";
        }
        if (dim == -1) {
            return "The Nether";
        }
        if (dim == 1) {
            return "The End";
        }
        return "Dimension " + dim;
    }

    private static final class DimensionAccumulator {

        final int dim;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int markerCount;

        DimensionAccumulator(int dim) {
            this.dim = dim;
        }

        void include(int x, int z) {
            if (x < minX) {
                minX = x;
            }
            if (x > maxX) {
                maxX = x;
            }
            if (z < minZ) {
                minZ = z;
            }
            if (z > maxZ) {
                maxZ = z;
            }
        }

        WorldMapMetaDto.DimensionInfo toInfo(WorldMapChunkSetBuilder.DimensionChunkSet chunkSet, int maxChunks) {
            WorldMapMetaDto.DimensionInfo info = new WorldMapMetaDto.DimensionInfo();
            info.dim = dim;
            info.name = WorldMapBoundsBuilder.dimensionName(dim);
            info.markerCount = markerCount;

            if (markerCount == 0) {
                info.minX = 0;
                info.maxX = 0;
                info.minZ = 0;
                info.maxZ = 0;
                info.minChunkX = 0;
                info.maxChunkX = 0;
                info.minChunkZ = 0;
                info.maxChunkZ = 0;
                info.chunkCount = 0;
                return info;
            }

            info.minX = minX;
            info.maxX = maxX;
            info.minZ = minZ;
            info.maxZ = maxZ;

            if (chunkSet == null || chunkSet.isEmpty()) {
                info.minChunkX = 0;
                info.maxChunkX = 0;
                info.minChunkZ = 0;
                info.maxChunkZ = 0;
                info.chunkCount = 0;
                return info;
            }

            info.minChunkX = chunkSet.minChunkX;
            info.maxChunkX = chunkSet.maxChunkX;
            info.minChunkZ = chunkSet.minChunkZ;
            info.maxChunkZ = chunkSet.maxChunkZ;
            int count = chunkSet.allowed.size();
            info.chunkCount = count > maxChunks ? maxChunks : count;
            info.allowedChunks = chunkSet.listedChunks();
            return info;
        }
    }
}
