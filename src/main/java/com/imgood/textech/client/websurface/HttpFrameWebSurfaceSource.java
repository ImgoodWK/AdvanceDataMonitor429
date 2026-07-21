package com.imgood.textech.client.websurface;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import com.imgood.textech.Config;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Pulls JPEG frames from WebAE {@code /api/display/{id}/frame.jpg} with distance-based refresh.
 * Tracks last capture/network error codes for GUI diagnostics.
 */
@SideOnly(Side.CLIENT)
public final class HttpFrameWebSurfaceSource implements WebSurfaceSource {

    private static final HttpFrameWebSurfaceSource INSTANCE = new HttpFrameWebSurfaceSource();
    private static final Object LOCK = new Object();
    private static final int MAX_TEXTURES = 8;
    private static final LinkedHashMap<String, TextureEntry> TEXTURES = new LinkedHashMap<String, TextureEntry>(
        16,
        0.75F,
        true);
    private static final Map<String, BufferedImage> READY = new HashMap<String, BufferedImage>();
    private static final Map<String, String> ETAGS = new HashMap<String, String>();
    private static final Map<String, Long> LAST_FETCH = new HashMap<String, Long>();
    private static final Map<String, Boolean> IN_FLIGHT = new HashMap<String, Boolean>();
    private static final Map<String, String> LAST_ERROR = new HashMap<String, String>();
    private static final Map<String, String> LAST_FRAME_SOURCE = new HashMap<String, String>();

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(new ThreadFactory() {

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "TeXTech-WebSurface-HttpFrame");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        }
    });

    private HttpFrameWebSurfaceSource() {}

    public static HttpFrameWebSurfaceSource instance() {
        return INSTANCE;
    }

    /** Last machine-readable error for a cache key (may be empty). */
    public static String getLastError(String cacheKey) {
        if (cacheKey == null) return "";
        synchronized (LOCK) {
            String err = LAST_ERROR.get(cacheKey);
            return err != null ? err : "";
        }
    }

    /** Last {@code X-WebAE-Frame} source label (browser-jpeg / spa-jpeg / server-html). */
    public static String getLastFrameSource(String cacheKey) {
        if (cacheKey == null) return "";
        synchronized (LOCK) {
            String src = LAST_FRAME_SOURCE.get(cacheKey);
            return src != null ? src : "";
        }
    }

    private static void setError(String key, String code) {
        synchronized (LOCK) {
            if (code == null || code.isEmpty()) {
                LAST_ERROR.remove(key);
            } else {
                LAST_ERROR.put(key, code);
            }
        }
    }

    private static void setFrameSource(String key, String source) {
        synchronized (LOCK) {
            if (source == null || source.isEmpty()) {
                LAST_FRAME_SOURCE.remove(key);
            } else {
                LAST_FRAME_SOURCE.put(key, source);
            }
        }
    }

    @Override
    public boolean supports(NBTTagCompound binding) {
        if (binding == null) return false;
        String mode = binding.getString(TileEntityAdvanceDataMonitor.WEB_SURFACE_MODE_KEY);
        return TileEntityAdvanceDataMonitor.MODE_DASHBOARD_LIVE.equals(mode)
            || TileEntityAdvanceDataMonitor.MODE_LIVE_URL.equals(mode);
    }

    @Override
    public String cacheKey(NBTTagCompound binding) {
        String mode = binding.getString(TileEntityAdvanceDataMonitor.WEB_SURFACE_MODE_KEY);
        if (TileEntityAdvanceDataMonitor.MODE_LIVE_URL.equals(mode)) {
            return "url:" + binding.getString(TileEntityAdvanceDataMonitor.WEB_LIVE_URL_KEY);
        }
        return "live:" + binding.getString(TileEntityAdvanceDataMonitor.WEB_DISPLAY_ID_KEY);
    }

    @Override
    public WebSurfaceFrame getFrame(NBTTagCompound binding, int textureWidth, double distanceSq, boolean inView) {
        if (!supports(binding) || !inView) {
            return WebSurfaceFrame.ofLocation(peekTexture(cacheKey(binding), textureWidth));
        }
        String key = cacheKey(binding);
        long interval = refreshIntervalMs(distanceSq);
        if (interval < 0) return WebSurfaceFrame.ofLocation(peekTexture(key, textureWidth));
        maybeFetch(binding, textureWidth, key, interval);
        return WebSurfaceFrame.ofLocation(peekTexture(key, textureWidth));
    }

    private static long refreshIntervalMs(double distanceSq) {
        double dist = Math.sqrt(Math.max(0.0D, distanceSq));
        if (dist > 64.0D) return -1L;
        if (dist <= 16.0D) return 2000L;
        if (dist <= 32.0D) return 4000L;
        return 8000L;
    }

    private void maybeFetch(final NBTTagCompound binding, final int textureWidth, final String key, long interval) {
        synchronized (LOCK) {
            Long last = LAST_FETCH.get(key);
            long now = System.currentTimeMillis();
            if (last != null && now - last.longValue() < interval) return;
            if (IN_FLIGHT.containsKey(key)) return;
            IN_FLIGHT.put(key, Boolean.TRUE);
            LAST_FETCH.put(key, Long.valueOf(now));
        }
        WORKER.execute(new Runnable() {

            @Override
            public void run() {
                try {
                    fetchFrame(binding, textureWidth, key);
                } finally {
                    synchronized (LOCK) {
                        IN_FLIGHT.remove(key);
                    }
                }
            }
        });
    }

    private void fetchFrame(NBTTagCompound binding, int textureWidth, String key) {
        String mode = binding.getString(TileEntityAdvanceDataMonitor.WEB_SURFACE_MODE_KEY);
        String origin = resolveOrigin(binding.getString(TileEntityAdvanceDataMonitor.WEB_ORIGIN_KEY));
        String url;
        if (TileEntityAdvanceDataMonitor.MODE_LIVE_URL.equals(mode)) {
            setError(key, "live_url_requires_mcef");
            return;
        }
        String displayId = binding.getString(TileEntityAdvanceDataMonitor.WEB_DISPLAY_ID_KEY);
        String token = binding.getString(TileEntityAdvanceDataMonitor.WEB_VIEW_TOKEN_KEY);
        if (displayId == null || displayId.isEmpty() || token == null || token.isEmpty()) {
            setError(key, "missing_display_token");
            return;
        }
        if (origin == null || origin.isEmpty()) {
            setError(key, "origin_missing");
            return;
        }
        int width = textureWidth <= 256 ? 256 : (textureWidth <= 512 ? 512 : 1024);
        url = origin + "/api/display/" + displayId + "/frame.jpg?token=" + token + "&width=" + width;
        String etag;
        synchronized (LOCK) {
            etag = ETAGS.get(key);
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Accept", "image/jpeg");
            if (etag != null) conn.setRequestProperty("If-None-Match", etag);
            // Touch capture liveness
            touchCapture(origin, displayId, token, width);
            int code = conn.getResponseCode();
            String newEtag = conn.getHeaderField("ETag");
            String frameHeader = conn.getHeaderField("X-WebAE-Frame");
            String captureError = conn.getHeaderField("X-WebAE-Capture-Error");
            if ("not-modified".equals(frameHeader) || code == 304) {
                if (newEtag != null) {
                    synchronized (LOCK) {
                        ETAGS.put(key, newEtag);
                    }
                }
                if (frameHeader != null && !"not-modified".equals(frameHeader)) {
                    setFrameSource(key, frameHeader);
                }
                setError(key, "");
                return;
            }
            if (code != 200) {
                String err = captureError;
                if (err == null || err.isEmpty()) {
                    err = readJsonErrorCode(conn, code);
                }
                if (err == null || err.isEmpty()) {
                    if (code == 503) err = "capture_pending";
                    else if (code == 404) err = "display_not_found";
                    else if (code == 401 || code == 403) err = "token_denied";
                    else err = "http_" + code;
                }
                setError(key, err);
                // Back off harder on capture_pending / overload so we do not stampede Chrome.
                if (code == 503 || code == 429) {
                    synchronized (LOCK) {
                        LAST_FETCH.put(key, Long.valueOf(System.currentTimeMillis() + 4000L));
                    }
                }
                return;
            }
            InputStream in = conn.getInputStream();
            byte[] bytes = readAll(in, 512 * 1024);
            if (bytes == null || bytes.length < 32) {
                setError(key, "empty_frame");
                return;
            }
            // JSON not-modified body
            if (bytes[0] == '{') {
                setError(key, "json_frame_body");
                return;
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                setError(key, "jpeg_decode_failed");
                return;
            }
            synchronized (LOCK) {
                READY.put(key, image);
                if (newEtag != null) ETAGS.put(key, newEtag);
            }
            if (frameHeader != null && !frameHeader.isEmpty() && !"not-modified".equals(frameHeader)) {
                setFrameSource(key, frameHeader);
            } else {
                setFrameSource(key, "spa-jpeg");
            }
            setError(key, "");
        } catch (java.net.UnknownHostException e) {
            setError(key, "origin_unreachable");
        } catch (java.net.ConnectException e) {
            setError(key, "origin_unreachable");
        } catch (java.net.SocketTimeoutException e) {
            setError(key, "origin_timeout");
        } catch (Exception e) {
            setError(key, "fetch_failed");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readJsonErrorCode(HttpURLConnection conn, int httpCode) {
        InputStream errStream = null;
        try {
            errStream = conn.getErrorStream();
            if (errStream == null && httpCode >= 400) {
                try {
                    errStream = conn.getInputStream();
                } catch (Exception ignored) {}
            }
            if (errStream == null) return null;
            byte[] bytes = readAll(errStream, 4096);
            if (bytes == null || bytes.length == 0 || bytes[0] != '{') return null;
            String body = new String(bytes, "UTF-8");
            // Prefer message field used by DisplayHandler (often the capture error code).
            String message = extractJsonString(body, "message");
            if (message != null && !message.isEmpty() && message.indexOf(' ') < 0) {
                return message;
            }
            String code = extractJsonString(body, "code");
            if ("capture_unavailable".equals(code) && message != null && !message.isEmpty()) {
                return message.indexOf(' ') < 0 ? message : code;
            }
            return code;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (errStream != null) {
                try {
                    errStream.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private static String extractJsonString(String json, String key) {
        if (json == null || key == null) return null;
        String needle = "\"" + key + "\"";
        int idx = json.indexOf(needle);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + needle.length());
        if (colon < 0) return null;
        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }

    private static void touchCapture(String origin, String displayId, String token, int width) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(origin + "/api/display/" + displayId + "/touch?token=" + token + "&width=" + width);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(1500);
            conn.getResponseCode();
        } catch (Exception ignored) {} finally {
            if (conn != null) conn.disconnect();
        }
    }

    private ResourceLocation peekTexture(String key, int textureWidth) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getTextureManager() == null) return null;
        synchronized (LOCK) {
            BufferedImage ready = READY.remove(key);
            if (ready != null) {
                TextureEntry old = TEXTURES.remove(key);
                if (old != null) mc.getTextureManager()
                    .deleteTexture(old.location);
                DynamicTexture dyn = new DynamicTexture(ready);
                ResourceLocation loc = mc.getTextureManager()
                    .getDynamicTextureLocation("webae_live_" + Integer.toHexString(key.hashCode()), dyn);
                TEXTURES.put(key, new TextureEntry(loc, ready.getWidth() * ready.getHeight()));
                evict(mc);
            }
            TextureEntry entry = TEXTURES.get(key);
            return entry == null ? null : entry.location;
        }
    }

    private static void evict(Minecraft mc) {
        while (TEXTURES.size() > MAX_TEXTURES) {
            Iterator<Map.Entry<String, TextureEntry>> it = TEXTURES.entrySet()
                .iterator();
            if (!it.hasNext()) break;
            Map.Entry<String, TextureEntry> e = it.next();
            it.remove();
            mc.getTextureManager()
                .deleteTexture(e.getValue().location);
        }
    }

    public static String resolveOrigin(String configured) {
        if (configured != null && !configured.trim()
            .isEmpty()) {
            String o = configured.trim();
            if (o.endsWith("/")) o = o.substring(0, o.length() - 1);
            return o;
        }
        String host = Config.webConsoleBindAddress;
        if (host == null || host.isEmpty() || "0.0.0.0".equals(host)) host = "127.0.0.1";
        return "http://" + host + ":" + Config.webConsolePort;
    }

    private static byte[] readAll(InputStream in, int max) throws Exception {
        byte[] buf = new byte[Math.min(max, 65536)];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int n;
        int total = 0;
        while ((n = in.read(buf)) >= 0) {
            total += n;
            if (total > max) return null;
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static final class TextureEntry {

        final ResourceLocation location;
        final int pixels;

        TextureEntry(ResourceLocation location, int pixels) {
            this.location = location;
            this.pixels = pixels;
        }
    }
}
