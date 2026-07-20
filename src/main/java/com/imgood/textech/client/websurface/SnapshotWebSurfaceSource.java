package com.imgood.textech.client.websurface;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import com.imgood.textech.client.WebSurfaceClientCache;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Legacy AWT drawing-list snapshot source.
 */
@SideOnly(Side.CLIENT)
public final class SnapshotWebSurfaceSource implements WebSurfaceSource {

    private static final SnapshotWebSurfaceSource INSTANCE = new SnapshotWebSurfaceSource();

    private SnapshotWebSurfaceSource() {}

    public static SnapshotWebSurfaceSource instance() {
        return INSTANCE;
    }

    @Override
    public boolean supports(NBTTagCompound binding) {
        if (binding == null) return false;
        String mode = binding.getString(TileEntityAdvanceDataMonitor.WEB_SURFACE_MODE_KEY);
        return mode.isEmpty() || TileEntityAdvanceDataMonitor.MODE_DASHBOARD_SNAPSHOT.equals(mode);
    }

    @Override
    public String cacheKey(NBTTagCompound binding) {
        return binding.getString(TileEntityAdvanceDataMonitor.WEB_DASHBOARD_HASH_KEY);
    }

    @Override
    public ResourceLocation getTexture(NBTTagCompound binding, int textureWidth, double distanceSq, boolean inView) {
        String hash = cacheKey(binding);
        if (hash == null || hash.length() != 64) return null;
        return WebSurfaceClientCache.getTexture(hash, textureWidth);
    }
}
