package com.imgood.textech.webae.worldmap;

/**
 * 
 * @deprecated Side views replaced by mineshot-style {@link WorldMapObliqueDirection} oblique orbit.
 * 
 */

@Deprecated

public final class WorldMapSideRenderer {

    private WorldMapSideRenderer() {}

    /** @deprecated No longer used; returns {@code null}. */

    @Deprecated

    public static byte[] renderTerrain(WorldMapView view, int dim, int chunkX, int chunkZ) {

        return null;

    }

}
