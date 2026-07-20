package com.imgood.textech.client.websurface;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;

import com.imgood.textech.Config;
import com.imgood.textech.client.WebSurfaceClientCache;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Picks a web-surface source: MCEF (optional) → CDP → HttpFrame → Snapshot fallback.
 */
@SideOnly(Side.CLIENT)
public final class WebSurfaceSourceRouter {

    public static final String SOURCE_NONE = "none";
    public static final String SOURCE_MCEF = "mcef";
    public static final String SOURCE_SPA_JPEG = "spa-jpeg";
    public static final String SOURCE_SNAPSHOT = "snapshot";

    private static volatile String lastSource = SOURCE_NONE;

    private WebSurfaceSourceRouter() {}

    public static String getLastSource() {
        return lastSource;
    }

    public static WebSurfaceFrame resolveFrame(NBTTagCompound binding, int textureWidth, int bindingIndex, int teX,
        int teY, int teZ) {
        if (binding == null) {
            lastSource = SOURCE_NONE;
            return null;
        }
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
            if (Config.webSurfaceUseMcef) {
                WebSurfaceFrame frame = McefWebSurfaceSource.instance()
                    .getFrame(binding, textureWidth, distanceSq, inView);
                if (frame != null && frame.isReady()) {
                    lastSource = SOURCE_MCEF;
                    return frame;
                }
            }
            WebSurfaceFrame frame = CdpWebSurfaceSource.instance()
                .getFrame(binding, textureWidth, distanceSq, inView);
            if (frame != null && frame.isReady()) {
                lastSource = SOURCE_SPA_JPEG;
                return frame;
            }
            frame = HttpFrameWebSurfaceSource.instance()
                .getFrame(binding, textureWidth, distanceSq, inView);
            if (frame != null && frame.isReady()) {
                lastSource = SOURCE_SPA_JPEG;
                return frame;
            }
            String hash = binding.getString(TileEntityAdvanceDataMonitor.WEB_DASHBOARD_HASH_KEY);
            if (hash != null && hash.length() == 64 && WebSurfaceClientCache.hasContent(hash)) {
                lastSource = SOURCE_SNAPSHOT;
                return WebSurfaceFrame.ofLocation(WebSurfaceClientCache.getTexture(hash, textureWidth));
            }
            lastSource = SOURCE_NONE;
            return null;
        }

        lastSource = SOURCE_SNAPSHOT;
        return SnapshotWebSurfaceSource.instance()
            .getFrame(binding, textureWidth, distanceSq, inView);
    }
}
