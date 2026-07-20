package com.imgood.textech.client.websurface;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Optional local Chrome/Edge CDP accelerator. Currently delegates discovery only;
 * production frames use {@link HttpFrameWebSurfaceSource} against the WebAE host.
 */
@SideOnly(Side.CLIENT)
public final class CdpWebSurfaceSource implements WebSurfaceSource {

    private static final CdpWebSurfaceSource INSTANCE = new CdpWebSurfaceSource();

    private CdpWebSurfaceSource() {}

    public static CdpWebSurfaceSource instance() {
        return INSTANCE;
    }

    @Override
    public boolean supports(NBTTagCompound binding) {
        return binding != null
            && (TileEntityAdvanceDataMonitor.MODE_DASHBOARD_LIVE
                .equals(binding.getString(TileEntityAdvanceDataMonitor.WEB_SURFACE_MODE_KEY))
                || TileEntityAdvanceDataMonitor.MODE_LIVE_URL
                    .equals(binding.getString(TileEntityAdvanceDataMonitor.WEB_SURFACE_MODE_KEY)));
    }

    @Override
    public String cacheKey(NBTTagCompound binding) {
        return "cdp:" + HttpFrameWebSurfaceSource.instance()
            .cacheKey(binding);
    }

    @Override
    public ResourceLocation getTexture(NBTTagCompound binding, int textureWidth, double distanceSq, boolean inView) {
        // Client-local CDP is optional; prefer shared WebAE capture for multiplayer consistency.
        return null;
    }
}
