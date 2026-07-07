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
