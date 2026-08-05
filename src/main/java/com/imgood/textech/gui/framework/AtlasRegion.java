package com.imgood.textech.gui.framework;

import net.minecraft.util.ResourceLocation;

/** Exact pixel region in a square texture atlas. */
public final class AtlasRegion {

    private final ResourceLocation texture;
    private final int atlasSize;
    private final int u;
    private final int v;
    private final int width;
    private final int height;

    public AtlasRegion(ResourceLocation texture, int atlasSize, int u, int v, int width, int height) {
        if (texture == null) {
            throw new IllegalArgumentException("texture");
        }
        if (atlasSize <= 0 || u < 0
            || v < 0
            || width <= 0
            || height <= 0
            || u + width > atlasSize
            || v + height > atlasSize) {
            throw new IllegalArgumentException("Atlas region is outside the texture");
        }
        this.texture = texture;
        this.atlasSize = atlasSize;
        this.u = u;
        this.v = v;
        this.width = width;
        this.height = height;
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

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public AtlasRegion subRegion(int localU, int localV, int regionWidth, int regionHeight) {
        if (localU < 0 || localV < 0
            || regionWidth <= 0
            || regionHeight <= 0
            || localU + regionWidth > width
            || localV + regionHeight > height) {
            throw new IllegalArgumentException("Sub-region is outside the parent region");
        }
        return new AtlasRegion(texture, atlasSize, u + localU, v + localV, regionWidth, regionHeight);
    }
}
