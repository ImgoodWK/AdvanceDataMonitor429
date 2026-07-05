package com.imgood.textech.webae.icon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Shared helpers and counters for icon render strategies during one upload session.
 */
@SideOnly(Side.CLIENT)
public final class IconRenderContext {

    final IconAtlasSampler atlasSampler;
    final IconGlFallback glFallback;
    int atlasSampleCount;
    int glFallbackCount;
    int placeholderCount;

    IconRenderContext(IconAtlasSampler atlasSampler, IconGlFallback glFallback) {
        this.atlasSampler = atlasSampler;
        this.glFallback = glFallback;
    }
}
