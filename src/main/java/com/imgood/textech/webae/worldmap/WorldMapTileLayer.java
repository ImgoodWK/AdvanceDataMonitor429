package com.imgood.textech.webae.worldmap;

/**
 * World map tile layer ids. Terrain uses the legacy cache path; AE overlay uses {@code {view}/ae/...}.
 */
public final class WorldMapTileLayer {

    public static final String TERRAIN = "terrain";
    /** AE overlay layer (API segment {@code /ae/}). */
    public static final String AE = "ae";
    /** Cache subdirectory for category-ID AE tiles (distinct from legacy colored {@code /ae/}). */
    public static final String AE_ID = "ae-id";

    private WorldMapTileLayer() {}

    public static String normalize(String layer) {
        if (layer == null || layer.trim()
            .isEmpty()) {
            return TERRAIN;
        }
        String id = layer.trim()
            .toLowerCase();
        if (AE.equals(id)) {
            return AE;
        }
        return TERRAIN;
    }

    public static boolean isAe(String layer) {
        return AE.equals(normalize(layer));
    }

    /** Cache directory segment under {@code map-tiles/}. */
    public static String cacheViewPath(String viewId, String layer) {
        String view = viewId != null ? viewId.trim() : WorldMapView.FLAT.id;
        if (isAe(layer)) {
            return view + "/" + AE_ID;
        }
        return view;
    }
}
