package com.imgood.textech.webae.worldmap;

/**
 * World map tile layer ids. Terrain uses the legacy cache path; AE overlay uses {@code {view}/ae/...}.
 */
public final class WorldMapTileLayer {

    public static final String TERRAIN = "terrain";
    public static final String AE = "ae";

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

    /** Cache directory segment under {@code web-map-tiles/}. */
    public static String cacheViewPath(String viewId, String layer) {
        String view = viewId != null ? viewId.trim() : WorldMapView.FLAT.id;
        if (isAe(layer)) {
            return view + "/ae";
        }
        return view;
    }
}
