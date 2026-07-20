package com.imgood.textech.client.websurface;

import net.minecraft.util.ResourceLocation;

/**
 * Ready-to-draw web-surface frame: either a managed {@link ResourceLocation} or a raw OpenGL texture id
 * (e.g. MCEF OSR). Exactly one of the two is set when {@link #isReady()}.
 */
public final class WebSurfaceFrame {

    private final ResourceLocation location;
    private final int glTextureId;
    private final boolean flipV;

    private WebSurfaceFrame(ResourceLocation location, int glTextureId, boolean flipV) {
        this.location = location;
        this.glTextureId = glTextureId;
        this.flipV = flipV;
    }

    public static WebSurfaceFrame ofLocation(ResourceLocation location) {
        if (location == null) return null;
        return new WebSurfaceFrame(location, 0, false);
    }

    public static WebSurfaceFrame ofGlTexture(int glTextureId, boolean flipV) {
        if (glTextureId <= 0) return null;
        return new WebSurfaceFrame(null, glTextureId, flipV);
    }

    public boolean isReady() {
        return location != null || glTextureId > 0;
    }

    public boolean hasGlTexture() {
        return glTextureId > 0;
    }

    public ResourceLocation getLocation() {
        return location;
    }

    public int getGlTextureId() {
        return glTextureId;
    }

    public boolean isFlipV() {
        return flipV;
    }
}
