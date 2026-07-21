package com.imgood.textech.client.websurface;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;

import com.imgood.textech.Config;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Picks a web-surface source: MCEF (optional) → CDP → HttpFrame.
 * Live modes never silently fall back to AWT snapshot (that is not real web content).
 */
@SideOnly(Side.CLIENT)
public final class WebSurfaceSourceRouter {

    public static final String SOURCE_NONE = "none";
    public static final String SOURCE_MCEF = "mcef";
    public static final String SOURCE_SPA_JPEG = "spa-jpeg";
    public static final String SOURCE_SNAPSHOT = "snapshot";

    private static volatile String lastSource = SOURCE_NONE;
    private static volatile String lastDetail = "";

    private WebSurfaceSourceRouter() {}

    public static String getLastSource() {
        return lastSource;
    }

    /** Short machine-readable detail for GUI (e.g. browser_not_found, waiting). */
    public static String getLastDetail() {
        return lastDetail != null ? lastDetail : "";
    }

    public static WebSurfaceFrame resolveFrame(NBTTagCompound binding, int textureWidth, int bindingIndex, int teX,
        int teY, int teZ) {
        if (binding == null) {
            lastSource = SOURCE_NONE;
            lastDetail = "no_binding";
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
                    lastDetail = "";
                    return frame;
                }
            }
            WebSurfaceFrame frame = CdpWebSurfaceSource.instance()
                .getFrame(binding, textureWidth, distanceSq, inView);
            if (frame != null && frame.isReady()) {
                lastSource = SOURCE_SPA_JPEG;
                lastDetail = "";
                return frame;
            }
            frame = HttpFrameWebSurfaceSource.instance()
                .getFrame(binding, textureWidth, distanceSq, inView);
            if (frame != null && frame.isReady()) {
                lastSource = SOURCE_SPA_JPEG;
                lastDetail = "";
                return frame;
            }

            // Live mode: never paint AWT snapshot as a successful "web" frame.
            if (Config.webSurfaceAllowLiveSnapshotFallback) {
                String hash = binding.getString(TileEntityAdvanceDataMonitor.WEB_DASHBOARD_HASH_KEY);
                if (hash != null && hash.length() == 64
                    && com.imgood.textech.client.WebSurfaceClientCache.hasContent(hash)) {
                    lastSource = SOURCE_SNAPSHOT;
                    lastDetail = "legacy_snapshot_fallback";
                    return WebSurfaceFrame.ofLocation(
                        com.imgood.textech.client.WebSurfaceClientCache.getTexture(hash, textureWidth));
                }
            }

            lastSource = SOURCE_NONE;
            lastDetail = resolveLivePendingDetail(mode, binding);
            return null;
        }

        lastSource = SOURCE_SNAPSHOT;
        lastDetail = "dashboard_snapshot";
        return SnapshotWebSurfaceSource.instance()
            .getFrame(binding, textureWidth, distanceSq, inView);
    }

    private static String resolveLivePendingDetail(String mode, NBTTagCompound binding) {
        if (TileEntityAdvanceDataMonitor.MODE_LIVE_URL.equals(mode)) {
            if (!Config.webSurfaceUseMcef || !McefWebSurfaceSource.isClassPresent()) {
                return "live_url_requires_mcef";
            }
            if (!McefWebSurfaceSource.isAvailable()) {
                return "mcef_unavailable";
            }
            return "mcef_pending";
        }
        String httpErr = HttpFrameWebSurfaceSource.getLastError(
            HttpFrameWebSurfaceSource.instance()
                .cacheKey(binding));
        if (httpErr != null && !httpErr.isEmpty()) {
            return httpErr;
        }
        if (Config.webSurfaceUseMcef && McefWebSurfaceSource.isClassPresent() && McefWebSurfaceSource.isAvailable()) {
            return "mcef_pending";
        }
        if (Config.webSurfaceUseMcef && McefWebSurfaceSource.isClassPresent() && !McefWebSurfaceSource.isAvailable()) {
            String fail = McefWebSurfaceSource.getLastFailure();
            return fail != null && !fail.isEmpty() ? fail : "mcef_unavailable";
        }
        return "waiting_spa_jpeg";
    }
}
