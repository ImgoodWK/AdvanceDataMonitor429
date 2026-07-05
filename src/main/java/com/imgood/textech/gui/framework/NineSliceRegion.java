package com.imgood.textech.gui.framework;

import net.minecraft.util.ResourceLocation;

/**
 * Describes a 9-slice region within a texture atlas.
 * {@code regionW/regionH} are the pixel size of the source slice template on the atlas;
 * {@code borderPx} is the non-stretching edge width on each side of that template.
 */
public final class NineSliceRegion {

    private final ResourceLocation texture;
    private final int atlasSize;
    private final int u;
    private final int v;
    private final int regionW;
    private final int regionH;
    private final int borderPx;

    public NineSliceRegion(ResourceLocation texture, int atlasSize, int u, int v, int regionW, int regionH,
        int borderPx) {
        this.texture = texture;
        this.atlasSize = atlasSize;
        this.u = u;
        this.v = v;
        this.regionW = regionW;
        this.regionH = regionH;
        this.borderPx = borderPx;
    }

    public ResourceLocation texture() {
        return texture;
    }

    public int atlasSize() {
        return atlasSize;
    }

    public int u() {
        return u;
    }

    public int v() {
        return v;
    }

    public int regionW() {
        return regionW;
    }

    public int regionH() {
        return regionH;
    }

    public int borderPx() {
        return borderPx;
    }

    /** Source width of the stretchable center strip. */
    public int srcMidW() {
        return Math.max(1, regionW - borderPx * 2);
    }

    /** Source height of the stretchable center strip. */
    public int srcMidH() {
        return Math.max(1, regionH - borderPx * 2);
    }
}
