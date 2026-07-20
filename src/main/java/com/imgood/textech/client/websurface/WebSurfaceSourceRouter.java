package com.imgood.textech.client.websurface;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import com.imgood.textech.client.WebSurfaceClientCache;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Picks a web-surface source: MCEF → CDP → HttpFrame → Snapshot fallback.
 */
@SideOnly(Side.CLIENT)
public final class WebSurfaceSourceRouter {

    private WebSurfaceSourceRouter() {}

    public static ResourceLocation resolveTexture(NBTTagCompound binding, int textureWidth, int bindingIndex, int teX,
        int teY, int teZ) {
        if (binding == null) return null;
        Minecraft mc = Minecraft.getMinecraft();
        double distanceSq = 0.0D;
        boolean inView = true;
        if (mc != null && mc.thePlayer != null) {
            double dx = mc.thePlayer.posX - (teX + 0.5D);
            double dy = mc.thePlayer.posY - (teY + 0.5D);
            double dz = mc.thePlayer.posZ - (teZ + 0.5D);
            distanceSq = dx * dx + dy * dy + dz * dz;
        }

        String mode = binding.getString(TileEntityAdvanceDataMonitor.WEB_SURFACE_MODE_KEY);
        if (TileEntityAdvanceDataMonitor.MODE_DASHBOARD_LIVE.equals(mode)
            || TileEntityAdvanceDataMonitor.MODE_LIVE_URL.equals(mode)) {
            ResourceLocation tex = McefWebSurfaceSource.instance()
                .getTexture(binding, textureWidth, distanceSq, inView);
            if (tex != null) return tex;
            tex = CdpWebSurfaceSource.instance()
                .getTexture(binding, textureWidth, distanceSq, inView);
            if (tex != null) return tex;
            tex = HttpFrameWebSurfaceSource.instance()
                .getTexture(binding, textureWidth, distanceSq, inView);
            if (tex != null) return tex;
            // Optional snapshot payload cold-start
            String hash = binding.getString(TileEntityAdvanceDataMonitor.WEB_DASHBOARD_HASH_KEY);
            if (hash != null && hash.length() == 64 && WebSurfaceClientCache.hasContent(hash)) {
                return WebSurfaceClientCache.getTexture(hash, textureWidth);
            }
            return null;
        }

        // Snapshot mode: ensure content request still happens via renderer.
        return SnapshotWebSurfaceSource.instance()
            .getTexture(binding, textureWidth, distanceSq, inView);
    }
}
