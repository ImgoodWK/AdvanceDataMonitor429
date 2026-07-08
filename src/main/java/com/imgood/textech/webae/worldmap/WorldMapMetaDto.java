package com.imgood.textech.webae.worldmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Response body for {@code GET /api/worldmap/meta}.
 */
public final class WorldMapMetaDto {

    public boolean success = true;
    public boolean hasLogicalSnapshot;
    public long timestamp;
    public List<DimensionInfo> dimensions = new ArrayList<DimensionInfo>();
    public int tilePx;
    public int pxPerBlock;
    public int paddingChunks;
    public int maxChunks;
    public boolean boundsTooLarge;
    public int markerCount;
    public boolean worldMapEnabled;
    public long cooldownRemainingMs;
    public long cooldownMs;
    public String message = "";
    /** UI tile tabs: flat + oblique reference. */
    public List<ViewInfo> views = new ArrayList<ViewInfo>();
    /** Selectable oblique orbit directions (se/sw/ne/nw) for settings. */
    public List<ViewInfo> obliqueDirections = new ArrayList<ViewInfo>();
    /** True when a client HD worker may upload higher-quality tiles (Phase 4). */
    public boolean hdAvailable;
    /** Selectable quality tiers for world map tiles. */
    public List<QualityTierInfo> qualityTiers = new ArrayList<QualityTierInfo>();
    /** Server cap for world map quality tier id. */
    public String maxQualityTier = WorldMapQualityTier.ULTRA.id;
    /** Default world map quality tier id when client has no preference. */
    public String defaultQualityTier = WorldMapQualityTier.MEDIUM.id;
    /** Active flat terrain render engine id (uv or legacy). */
    public String flatRenderEngine = "uv";
    /** Active oblique terrain render engine id (ray or legacy). */
    public String obliqueRenderEngine = "ray";
    /** Configured zoom pyramid levels (z0 …). */
    public List<ZoomLevelInfo> zoomLevels = new ArrayList<ZoomLevelInfo>();
    /** Suggested zoom level at default viewport scale (client may override from viewport). */
    public int recommendedZoom = 0;
    /** Whether block patch models are enabled for oblique ray rendering. */
    public boolean blockPatchesEnabled = true;
    /** Whether AE chunks receive a one-tier terrain quality boost. */
    public boolean aeQualityBoost = true;
    /** Whether server-side texture atlas baking is enabled. */
    public boolean serverAtlasEnabled = true;
    /** Loaded JSON/class/prefix patch rule count (excludes built-in stairs/slabs). */
    public int blockPatchEntries = 0;
    /** Current baked slots in server texture atlas (0 when disabled or cold). */
    public int serverAtlasSlots = 0;

    public static final class ZoomLevelInfo {

        public int level;
        public int chunkSpan;
        public int tilePx;
        public int pxPerBlock;
    }

    public static final class QualityTierInfo {

        public String id;
        public String labelKey;
        public int tilePx;
        public int pxPerBlock;
        public boolean hdCapable;
    }

    public static final class ViewInfo {

        public String id;
        public String labelKey;
    }

    public static final class DimensionInfo {

        public int dim;
        public String name;
        /** Marker block bounds (for marker placement). */
        public int minX;
        public int maxX;
        public int minZ;
        public int maxZ;
        /** Allowed chunk set bounding box (for fitBounds and fallback scope check). */
        public int minChunkX;
        public int maxChunkX;
        public int minChunkZ;
        public int maxChunkZ;
        /** Compact {@code "cx,cz"} list when chunk count &lt;= 256; {@code null} when only bbox applies. */
        public List<String> allowedChunks;
        public int markerCount;
        public int chunkCount;
    }
}
