package com.imgood.textech.webae.worldmap.engine;

import com.imgood.textech.webae.worldmap.WorldMapBlockColorResolver;

/**
 * Axis-aligned box within block-local space [0,1]³ for non-cube map geometry.
 */
public final class WorldMapBlockPatch {

    public final double minX;
    public final double minY;
    public final double minZ;
    public final double maxX;
    public final double maxY;
    public final double maxZ;
    public final WorldMapBlockColorResolver.BlockFace textureFace;

    public WorldMapBlockPatch(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
        WorldMapBlockColorResolver.BlockFace textureFace) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.textureFace = textureFace != null ? textureFace : WorldMapBlockColorResolver.BlockFace.TOP;
    }

    public static WorldMapBlockPatch box(double x0, double y0, double z0, double x1, double y1, double z1,
        WorldMapBlockColorResolver.BlockFace face) {
        return new WorldMapBlockPatch(x0, y0, z0, x1, y1, z1, face);
    }
}
