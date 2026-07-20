package com.imgood.textech.client.websurface;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

/**
 * Pluggable content source for {@code renderType=web_surface}.
 */
public interface WebSurfaceSource {

    /** Stable key used for texture LRU (hash / displayId / url). */
    String cacheKey(NBTTagCompound binding);

    /** Whether this source can handle the binding. */
    boolean supports(NBTTagCompound binding);

    /**
     * Returns a ready texture or null while loading. May kick off async work.
     */
    ResourceLocation getTexture(NBTTagCompound binding, int textureWidth, double distanceSq, boolean inView);
}
