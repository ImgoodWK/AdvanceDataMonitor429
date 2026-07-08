package com.imgood.textech.webae.worldmap.dynmap;

/**
 * Resolves Dynmap/GWM world directory names for Minecraft dimensions.
 */
public final class WorldMapDynmapWorldNames {

    private WorldMapDynmapWorldNames() {}

    public static String resolveForDimension(int dim) {
        if (!WorldMapDynmapDetector.isDynmapAvailable()) {
            return null;
        }
        String[] candidates = candidatesForDimension(dim);
        for (int i = 0; i < candidates.length; i++) {
            if (WorldMapDynmapTileProvider.hasTiles(candidates[i])) {
                return candidates[i];
            }
        }
        return null;
    }

    private static String[] candidatesForDimension(int dim) {
        if (dim == 0) {
            return new String[] { "world", "DIM0" };
        }
        if (dim == -1) {
            return new String[] { "DIM-1", "DIM_-1" };
        }
        if (dim == 1) {
            return new String[] { "DIM1" };
        }
        return new String[] { "DIM" + dim };
    }
}
