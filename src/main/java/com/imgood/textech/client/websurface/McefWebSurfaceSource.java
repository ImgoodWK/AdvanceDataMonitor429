package com.imgood.textech.client.websurface;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;

import com.imgood.textech.Config;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraftforge.event.world.WorldEvent;

/**
 * Soft-dep on montoyo MCEF: reflective off-screen browser for {@code dashboard_live} / {@code live_url}.
 * Falls through (returns null) when MCEF is absent, disabled, or the texture is not ready yet.
 * Pool size and distance limits come from {@link Config}.
 */
@SideOnly(Side.CLIENT)
public final class McefWebSurfaceSource implements WebSurfaceSource {

    private static final McefWebSurfaceSource INSTANCE = new McefWebSurfaceSource();

    private static final Boolean CLASS_PRESENT;
    private static final LinkedHashMap<String, BrowserEntry> BROWSERS = new LinkedHashMap<String, BrowserEntry>(
        8,
        0.75F,
        true);

    private static Method getApiMethod;
    private static Method createBrowserMethod;
    private static Method resizeMethod;
    private static Method getTextureIdMethod;
    private static Method closeMethod;
    private static Object apiInstance;
    private static boolean reflectionReady;
    private static boolean reflectionFailed;
    private static volatile String lastFailure = "";

    static {
        Boolean found = Boolean.FALSE;
        try {
            Class.forName("net.montoyo.mcef.api.MCEFApi");
            found = Boolean.TRUE;
        } catch (Throwable ignored) {
            found = Boolean.FALSE;
        }
        CLASS_PRESENT = found;
    }

    private McefWebSurfaceSource() {}

    public static McefWebSurfaceSource instance() {
        return INSTANCE;
    }

    public static boolean isAvailable() {
        return CLASS_PRESENT.booleanValue() && ensureReflection();
    }

    public static boolean isClassPresent() {
        return CLASS_PRESENT.booleanValue();
    }

    /** Last reflection/create failure code for GUI diagnostics. */
    public static String getLastFailure() {
        return lastFailure != null ? lastFailure : "";
    }

    private static int maxBrowsers() {
        int n = Config.webSurfaceMcefMaxBrowsers;
        if (n < 1) n = 1;
        if (n > 8) n = 8;
        return n;
    }

    private static double closeDistance() {
        double d = Config.webSurfaceMcefCloseDistance;
        return d < 8.0D ? 8.0D : d;
    }

    private static double createDistance() {
        double d = Config.webSurfaceMcefCreateDistance;
        double close = closeDistance();
        return d < close ? close : d;
    }

    /** Closes every pooled browser (world unload / dimension change). */
    public static void closeAll() {
        synchronized (BROWSERS) {
            Iterator<Map.Entry<String, BrowserEntry>> it = BROWSERS.entrySet()
                .iterator();
            while (it.hasNext()) {
                Map.Entry<String, BrowserEntry> e = it.next();
                closeBrowserQuietly(e.getValue().browser);
                it.remove();
            }
        }
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
    public WebSurfaceFrame getFrame(NBTTagCompound binding, int textureWidth, double distanceSq, boolean inView) {
        if (!Config.webSurfaceUseMcef || !supports(binding)) return null;

        double dist = Math.sqrt(Math.max(0.0D, distanceSq));
        String key = cacheKey(binding);

        if (dist > closeDistance()) {
            closeBrowser(key);
            return null;
        }
        if (!inView || dist > createDistance()) {
            return peekFrame(key);
        }

        String url = resolveUrl(binding);
        if (url == null || url.isEmpty()) return null;

        int width = normalizeWidth(textureWidth, binding);
        int height = normalizeHeight(width, binding);

        BrowserEntry entry;
        synchronized (BROWSERS) {
            entry = BROWSERS.get(key);
            if (entry != null && !url.equals(entry.url)) {
                closeBrowserQuietly(entry.browser);
                BROWSERS.remove(key);
                entry = null;
            }
            if (entry == null) {
                Object browser = createBrowser(url);
                if (browser == null) return null;
                resizeBrowser(browser, width, height);
                entry = new BrowserEntry(browser, url, width, height);
                BROWSERS.put(key, entry);
                evictOverflow();
                lastFailure = "";
            } else if (entry.width != width || entry.height != height) {
                resizeBrowser(entry.browser, width, height);
                entry.width = width;
                entry.height = height;
            }
            // Touch LRU
            BROWSERS.get(key);
            entry.lastUsedMs = System.currentTimeMillis();
        }

        int texId = getTextureId(entry.browser);
        if (texId <= 0) return null;
        // CEF OSR textures are typically upside-down relative to Minecraft quads.
        return WebSurfaceFrame.ofGlTexture(texId, true);
    }

    private static WebSurfaceFrame peekFrame(String key) {
        synchronized (BROWSERS) {
            BrowserEntry entry = BROWSERS.get(key);
            if (entry == null) return null;
            int texId = getTextureId(entry.browser);
            if (texId <= 0) return null;
            return WebSurfaceFrame.ofGlTexture(texId, true);
        }
    }

    private static void closeBrowser(String key) {
        synchronized (BROWSERS) {
            BrowserEntry entry = BROWSERS.remove(key);
            if (entry != null) closeBrowserQuietly(entry.browser);
        }
    }

    private static void evictOverflow() {
        int max = maxBrowsers();
        while (BROWSERS.size() > max) {
            Iterator<Map.Entry<String, BrowserEntry>> it = BROWSERS.entrySet()
                .iterator();
            if (!it.hasNext()) break;
            Map.Entry<String, BrowserEntry> oldest = it.next();
            closeBrowserQuietly(oldest.getValue().browser);
            it.remove();
        }
    }

    static String resolveUrl(NBTTagCompound binding) {
        if (binding == null) return null;
        String mode = binding.getString(TileEntityAdvanceDataMonitor.WEB_SURFACE_MODE_KEY);
        if (TileEntityAdvanceDataMonitor.MODE_LIVE_URL.equals(mode)) {
            String url = binding.getString(TileEntityAdvanceDataMonitor.WEB_LIVE_URL_KEY);
            if (url == null) return null;
            url = url.trim();
            if (url.startsWith("http://") || url.startsWith("https://")) return url;
            return null;
        }
        String origin = HttpFrameWebSurfaceSource.resolveOrigin(
            binding.getString(TileEntityAdvanceDataMonitor.WEB_ORIGIN_KEY));
        String embedPath = binding.hasKey(TileEntityAdvanceDataMonitor.WEB_EMBED_PATH_KEY)
            ? binding.getString(TileEntityAdvanceDataMonitor.WEB_EMBED_PATH_KEY)
                .trim()
            : "";
        if (!embedPath.isEmpty()) {
            if (embedPath.startsWith("http://") || embedPath.startsWith("https://")) return embedPath;
            if (!embedPath.startsWith("/")) embedPath = "/" + embedPath;
            return origin + embedPath;
        }
        String displayId = binding.getString(TileEntityAdvanceDataMonitor.WEB_DISPLAY_ID_KEY);
        String token = binding.getString(TileEntityAdvanceDataMonitor.WEB_VIEW_TOKEN_KEY);
        if (displayId == null || displayId.isEmpty() || token == null || token.isEmpty()) return null;
        return origin + "/embed/dashboard/" + displayId + "?token=" + token;
    }

    private static int normalizeWidth(int textureWidth, NBTTagCompound binding) {
        int viewport = binding.getInteger("webDashboardViewportWidth");
        if (viewport < 64) viewport = 960;
        int w = textureWidth > 0 ? textureWidth : 512;
        if (w < 256) w = 256;
        if (w > 1024) w = 1024;
        if (viewport > 0 && viewport < w) w = viewport;
        // Prefer mid quality for CEF cost; 960 is a good SPA default when binding says so.
        if (w >= 768 && viewport >= 900) return Math.min(960, viewport);
        if (w >= 768) return 512;
        return w;
    }

    private static int normalizeHeight(int width, NBTTagCompound binding) {
        int vw = Math.max(64, binding.getInteger("webDashboardViewportWidth"));
        int vh = Math.max(64, binding.getInteger("webDashboardViewportHeight"));
        int h = (int) Math.round(width * (double) vh / (double) vw);
        if (h < 64) h = 64;
        if (h > 1200) h = 1200;
        return h;
    }

    private static boolean ensureReflection() {
        if (reflectionReady) return true;
        if (reflectionFailed || !CLASS_PRESENT.booleanValue()) return false;
        try {
            Class<?> apiClass = Class.forName("net.montoyo.mcef.api.MCEFApi");
            getApiMethod = apiClass.getMethod("getAPI");
            Object api = getApiMethod.invoke(null);
            if (api == null) {
                lastFailure = "mcef_api_null";
                reflectionFailed = true;
                return false;
            }
            apiInstance = api;
            Class<?> apiType = api.getClass();
            createBrowserMethod = findMethod(apiType, "createBrowser", String.class, boolean.class);
            if (createBrowserMethod == null) {
                createBrowserMethod = findMethod(apiType, "createBrowser", String.class, boolean.class, boolean.class);
            }
            if (createBrowserMethod == null) {
                for (Class<?> iface : apiType.getInterfaces()) {
                    createBrowserMethod = findMethod(iface, "createBrowser", String.class, boolean.class);
                    if (createBrowserMethod == null) {
                        createBrowserMethod = findMethod(iface, "createBrowser", String.class, boolean.class,
                            boolean.class);
                    }
                    if (createBrowserMethod != null) break;
                }
            }
            if (createBrowserMethod == null) {
                lastFailure = "mcef_no_createBrowser";
                reflectionFailed = true;
                return false;
            }
            Class<?> browserIface = null;
            try {
                browserIface = Class.forName("net.montoyo.mcef.api.IBrowser");
            } catch (Throwable ignored) {}
            Class<?> methodOwner = browserIface != null ? browserIface : null;
            if (methodOwner != null) {
                resizeMethod = findMethod(methodOwner, "resize", int.class, int.class);
                getTextureIdMethod = findMethod(methodOwner, "getTextureID");
                if (getTextureIdMethod == null) {
                    getTextureIdMethod = findMethod(methodOwner, "getTextureId");
                }
                closeMethod = findMethod(methodOwner, "close");
            }
            lastFailure = "";
            reflectionReady = true;
            return true;
        } catch (Throwable t) {
            lastFailure = "mcef_reflection_failed";
            reflectionFailed = true;
            return false;
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params) {
        if (type == null) return null;
        try {
            return type.getMethod(name, params);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object createBrowser(String url) {
        if (!ensureReflection()) return null;
        try {
            Object browser;
            Class<?>[] types = createBrowserMethod.getParameterTypes();
            if (types.length >= 3) {
                // Some builds: createBrowser(url, transparent, disableListeners)
                browser = createBrowserMethod.invoke(apiInstance, url, Boolean.TRUE, Boolean.FALSE);
            } else {
                // montoyo default: createBrowser(url, transparent) — transparent true for OSR surfaces
                browser = createBrowserMethod.invoke(apiInstance, url, Boolean.TRUE);
            }
            if (browser == null) {
                lastFailure = "mcef_create_null";
                return null;
            }
            if (resizeMethod == null) {
                resizeMethod = findMethod(browser.getClass(), "resize", int.class, int.class);
            }
            if (getTextureIdMethod == null) {
                getTextureIdMethod = findMethod(browser.getClass(), "getTextureID");
                if (getTextureIdMethod == null) {
                    getTextureIdMethod = findMethod(browser.getClass(), "getTextureId");
                }
            }
            if (closeMethod == null) {
                closeMethod = findMethod(browser.getClass(), "close");
            }
            lastFailure = "";
            return browser;
        } catch (Throwable t) {
            lastFailure = "mcef_create_failed";
            return null;
        }
    }

    private static void resizeBrowser(Object browser, int width, int height) {
        if (browser == null || resizeMethod == null) return;
        try {
            resizeMethod.invoke(browser, Integer.valueOf(width), Integer.valueOf(height));
        } catch (Throwable ignored) {}
    }

    private static int getTextureId(Object browser) {
        if (browser == null || getTextureIdMethod == null) return 0;
        try {
            Object id = getTextureIdMethod.invoke(browser);
            if (id instanceof Integer) return ((Integer) id).intValue();
            if (id instanceof Number) return ((Number) id).intValue();
            return 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static void closeBrowserQuietly(Object browser) {
        if (browser == null || closeMethod == null) return;
        try {
            closeMethod.invoke(browser);
        } catch (Throwable ignored) {}
    }

    private static final class BrowserEntry {

        final Object browser;
        final String url;
        int width;
        int height;
        long lastUsedMs;

        BrowserEntry(Object browser, String url, int width, int height) {
            this.browser = browser;
            this.url = url;
            this.width = width;
            this.height = height;
            this.lastUsedMs = System.currentTimeMillis();
        }
    }

    /**
     * Client-only world unload hook registered from {@link com.imgood.textech.ClientProxy}.
     */
    @SideOnly(Side.CLIENT)
    public static final class UnloadHandler {

        @SubscribeEvent
        public void onWorldUnload(WorldEvent.Unload event) {
            if (event.world != null && event.world.isRemote) {
                closeAll();
            }
        }
    }
}
