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
        String view = normalizeCacheView(viewId);
        if (isAe(layer)) {
            return view + "/" + AE_ID;
        }
        return view;
    }

    private static String normalizeCacheView(String viewId) {
        if (viewId == null) {
            return WorldMapView.FLAT.id;
        }
        String view = viewId.trim()
            .toLowerCase();
        if (WorldMapView.FLAT.id.equals(view) || WorldMapView.OBLIQUE_SE.id.equals(view)
            || WorldMapView.OBLIQUE_SW.id.equals(view) || WorldMapView.OBLIQUE_NE.id.equals(view)
            || WorldMapView.OBLIQUE_NW.id.equals(view) || "oblique".equals(view) || "iso_se".equals(view)) {
            return view;
        }
        return WorldMapView.FLAT.id;
    }
}
