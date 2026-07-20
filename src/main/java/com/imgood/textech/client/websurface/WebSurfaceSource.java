package com.imgood.textech.client.websurface;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Pluggable content source for {@code renderType=web_surface}.
 */
public interface WebSurfaceSource {

    /** Stable key used for texture LRU (hash / displayId / url). */
    String cacheKey(NBTTagCompound binding);

    /** Whether this source can handle the binding. */
    boolean supports(NBTTagCompound binding);

    /**
     * Returns a ready frame or null while loading / when this source cannot serve.
     * May kick off async work. Returning null lets the router try the next source.
     */
    WebSurfaceFrame getFrame(NBTTagCompound binding, int textureWidth, double distanceSq, boolean inView);
}
