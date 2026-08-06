package com.imgood.textech.webae.worldmap.dynmap;

/**
 * Maps between Minecraft block coordinates and Dynmap HD tile indices.
 *
 * <p>
 * Dynmap uses a quadtree/zoom scheme where each tile covers 128&times;128 blocks
 * at zoom level 0. The tile origin is at the Dynmap world border (usually 0,0 in
 * the image, corresponding to world spawn offset).
 * </p>
 *
 * <p>
 * Perspective prefix mapping (WebAE view &rarr; GWM perspective):
 * <table>
 * <tr>
 * <td>{@code flat}</td>
 * <td>&rarr; {@code flat}</td>
 * </tr>
 * <tr>
 * <td>{@code oblique_se}</td>
 * <td>&rarr; {@code iso_SE_30_hires}</td>
 * </tr>
 * </table>
 * </p>
 */
public final class WorldMapDynmapCoordMapper {

    /** Dynmap tiles per side at zoom 0, covering 128 blocks each. */
    public static final int TILE_BLOCKS_Z0 = 128;

    private WorldMapDynmapCoordMapper() {}

    // ---- Perspective prefix mapping ----

    /**
     * Maps a WebAE view id to the Dynmap perspective prefix used in tile directory names.
     */
    public static String toDynmapPerspective(String webaeViewId) {
        if (webaeViewId == null || webaeViewId.isEmpty()) {
            return "flat";
        }
        switch (webaeViewId.trim()
            .toLowerCase()) {
            case "flat":
                return "flat";
            case "oblique":
            case "oblique_se":
            case "iso_se":
                return "iso_SE_30_hires";
            default:
                return "flat";
        }
    }

    /** Returns whether a WebAE view has an explicit Dynmap perspective mapping. */
    public static boolean isSupportedWebaeView(String webaeViewId) {
        if (webaeViewId == null || webaeViewId.trim()
            .isEmpty()) {
            return true;
        }
        String id = webaeViewId.trim()
            .toLowerCase();
        return "flat".equals(id) || "oblique".equals(id) || "oblique_se".equals(id) || "iso_se".equals(id);
    }

    // ---- Block coordinate mapping ----

    /**
     * Converts Minecraft world X to Dynmap tile X at the given zoom level.
     *
     * @param worldX world block coordinate
     * @param zoom   zoom level (0 = native, higher = parent merges)
     * @return tile X index
     */
    public static int worldToTileX(int worldX, int zoom) {
        int span = tileBlockSpan(zoom);
        // Dynmap uses floor division for negative coordinates
        return Math.floorDiv(worldX, span);
    }

    /**
     * Converts Minecraft world Z to Dynmap tile Z at the given zoom level.
     *
     * @param worldZ world block coordinate
     * @param zoom   zoom level (0 = native, higher = parent merges)
     * @return tile Z index
     */
    public static int worldToTileZ(int worldZ, int zoom) {
        int span = tileBlockSpan(zoom);
        return Math.floorDiv(worldZ, span);
    }

    /**
     * Returns the block span of a single tile at the given zoom level.
     * Each zoom level doubles the span of the previous level.
     *
     * @param zoom zoom level (0 = native 128-block tiles)
     * @return block span per tile side
     */
    public static int tileBlockSpan(int zoom) {
        if (zoom < 0) {
            zoom = 0;
        }
        return TILE_BLOCKS_Z0 << zoom;
    }

    /**
     * Converts tile coordinates back to the world block coordinate of the tile origin (top-left/min corner).
     *
     * @param tileX tile X index
     * @param tileZ tile Z index
     * @param zoom  zoom level
     * @return world X of tile origin
     */
    public static int tileToWorldX(int tileX, int zoom) {
        return tileX * tileBlockSpan(zoom);
    }

    /**
     * @see #tileToWorldX(int, int)
     */
    public static int tileToWorldZ(int tileZ, int zoom) {
        return tileZ * tileBlockSpan(zoom);
    }

    // ---- Tile key construction ----

    /**
     * Builds a Dynmap tile key usable for file lookup.
     * Format: {@code perspective/zoomPrefix/x_z.png}
     */
    public static String tileKey(String perspective, int zoom, int tileX, int tileZ) {
        String zoomPrefix = zoomPrefix(zoom);
        return perspective + "/" + zoomPrefix + "/" + tileX + "_" + tileZ + ".png";
    }

    /**
     * Returns the zoom directory prefix.
     * z0 &rarr; {@code z_0}, z1 &rarr; {@code z_1}, etc.
     * Some Dynmap versions use {@code zz_} prefix.
     */
    public static String zoomPrefix(int zoom) {
        return "z_" + Math.max(0, zoom);
    }

    /**
     * Returns the alternative zoom prefix {@code zz_} used by some Dynmap versions.
     */
    public static String altZoomPrefix(int zoom) {
        return "zz_" + Math.max(0, zoom);
    }
}
