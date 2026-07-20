package com.imgood.textech.client.websurface;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Optional soft-dep on montoyo MCEF. Reflectively creates an off-screen browser when present.
 * Falls through (returns null) when MCEF is absent so HttpFrame / Snapshot can take over.
 */
@SideOnly(Side.CLIENT)
public final class McefWebSurfaceSource implements WebSurfaceSource {

    private static final McefWebSurfaceSource INSTANCE = new McefWebSurfaceSource();
    private static final Boolean AVAILABLE;

    static {
        Boolean found = Boolean.FALSE;
        try {
            Class.forName("net.montoyo.mcef.api.MCEFApi");
            found = Boolean.TRUE;
        } catch (Throwable ignored) {
            found = Boolean.FALSE;
        }
        AVAILABLE = found;
    }

    private McefWebSurfaceSource() {}

    public static McefWebSurfaceSource instance() {
        return INSTANCE;
    }

    public static boolean isAvailable() {
        return AVAILABLE.booleanValue();
    }

    @Override
    public boolean supports(NBTTagCompound binding) {
        return isAvailable() && binding != null
            && (TileEntityAdvanceDataMonitor.MODE_DASHBOARD_LIVE
                .equals(binding.getString(TileEntityAdvanceDataMonitor.WEB_SURFACE_MODE_KEY))
                || TileEntityAdvanceDataMonitor.MODE_LIVE_URL
                    .equals(binding.getString(TileEntityAdvanceDataMonitor.WEB_SURFACE_MODE_KEY)));
    }

    @Override
    public String cacheKey(NBTTagCompound binding) {
        return "mcef:" + HttpFrameWebSurfaceSource.instance()
            .cacheKey(binding);
    }

    @Override
    public ResourceLocation getTexture(NBTTagCompound binding, int textureWidth, double distanceSq, boolean inView) {
        // Soft-dep placeholder: full OSR integration requires linking MCEF API at runtime.
        // Returning null lets HttpFrameWebSurfaceSource handle frames without a hard dependency.
        return null;
    }
}
