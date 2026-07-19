package com.imgood.textech.client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.network.packet.PacketMonitorWebSurface;
import com.imgood.textech.utils.WebDashboardSnapshotCodec;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-only content and texture cache for passive web surfaces.
 * Rasterization runs on one daemon worker; GL upload and LRU eviction remain on the render thread.
 */
@SideOnly(Side.CLIENT)
public final class WebSurfaceClientCache {

    private static final Object LOCK = new Object();
    private static final int MAX_CONTENTS = 24;
    private static final int MAX_TEXTURES = 8;
    private static final long MAX_TEXTURE_PIXELS = 6L * 1024L * 1024L;
    private static final long REQUEST_RETRY_MS = 2500L;

    private static final LinkedHashMap<String, byte[]> CONTENT = new LinkedHashMap<String, byte[]>(32, 0.75F, true);
    private static final LinkedHashMap<String, TextureEntry> TEXTURES = new LinkedHashMap<String, TextureEntry>(
        16,
        0.75F,
        true);
    private static final Map<String, BufferedImage> READY_IMAGES = new HashMap<String, BufferedImage>();
    private static final Set<String> PENDING_RASTERS = new HashSet<String>();
    private static final Map<String, Long> LAST_REQUEST = new HashMap<String, Long>();
    private static final Map<String, Boolean> UPLOAD_ACK = new HashMap<String, Boolean>();
    private static final Map<String, String> ERRORS = new HashMap<String, String>();

    private static final ExecutorService RASTER_WORKER = Executors.newSingleThreadExecutor(new ThreadFactory() {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "TeXTech-WebSurface-Raster");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        }
    });

    private static int worldDimension = Integer.MIN_VALUE;
    private static long generation;

    private WebSurfaceClientCache() {}

    public static void acceptContent(String expectedHash, byte[] payload) {
        if (expectedHash == null || payload == null) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        synchronized (LOCK) {
            if (minecraft.theWorld != null) {
                int dimension = minecraft.theWorld.provider.dimensionId;
                if (worldDimension == Integer.MIN_VALUE) worldDimension = dimension;
                if (worldDimension != dimension) return;
            }
        }
        try {
            WebDashboardSnapshotCodec.DecodedSnapshot decoded = WebDashboardSnapshotCodec.decode(payload);
            if (!expectedHash.equals(decoded.hash)) return;
            synchronized (LOCK) {
                CONTENT.put(expectedHash, payload.clone());
                ERRORS.remove(expectedHash);
                trimContentLocked();
            }
        } catch (WebDashboardSnapshotCodec.SnapshotException e) {
            synchronized (LOCK) {
                ERRORS.put(expectedHash, e.getMessage());
            }
        }
    }

    public static byte[] getContent(String hash) {
        synchronized (LOCK) {
            byte[] payload = CONTENT.get(hash);
            return payload == null ? null : payload.clone();
        }
    }

    public static boolean hasContent(String hash) {
        synchronized (LOCK) {
            return CONTENT.containsKey(hash);
        }
    }

    public static void requestContentIfNeeded(int dimension, int x, int y, int z, int index, String hash) {
        if (hash == null || hash.length() != 64) return;
        ensureWorld(dimension);
        long now = System.currentTimeMillis();
        String requestKey = dimension + ":" + x + ":" + y + ":" + z + ":" + index + ":" + hash;
        synchronized (LOCK) {
            if (CONTENT.containsKey(hash)) return;
            Long last = LAST_REQUEST.get(requestKey);
            if (last != null && now - last.longValue() < REQUEST_RETRY_MS) return;
            LAST_REQUEST.put(requestKey, Long.valueOf(now));
            if (LAST_REQUEST.size() > 128) {
                Iterator<Map.Entry<String, Long>> iterator = LAST_REQUEST.entrySet().iterator();
                while (iterator.hasNext()) {
                    if (now - iterator.next().getValue().longValue() > 30000L) iterator.remove();
                }
            }
        }
        AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketMonitorWebSurface.request(x, y, z, index, hash));
    }

    public static void recordUploadAck(String hash, boolean success) {
        synchronized (LOCK) {
            UPLOAD_ACK.put(hash, Boolean.valueOf(success));
        }
    }

    public static Boolean consumeUploadAck(String hash) {
        synchronized (LOCK) {
            return UPLOAD_ACK.remove(hash);
        }
    }

    public static String getError(String hash) {
        synchronized (LOCK) {
            return ERRORS.get(hash);
        }
    }

    /** Returns an uploaded GL texture, or null while content/rasterization is pending. */
    public static ResourceLocation getTexture(String hash, int requestedWidth) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld == null || hash == null || hash.length() != 64) return null;
        ensureWorld(minecraft.theWorld.provider.dimensionId);
        int width = normalizeWidth(requestedWidth);
        final String textureKey = hash + "@" + width;
        BufferedImage ready = null;
        synchronized (LOCK) {
            TextureEntry cached = TEXTURES.get(textureKey);
            if (cached != null) return cached.location;
            ready = READY_IMAGES.remove(textureKey);
            if (ready == null && !PENDING_RASTERS.contains(textureKey)) {
                byte[] content = CONTENT.get(hash);
                if (content != null) {
                    final byte[] payload = content.clone();
                    final long jobGeneration = generation;
                    PENDING_RASTERS.add(textureKey);
                    RASTER_WORKER.execute(new Runnable() {

                        @Override
                        public void run() {
                            rasterizeAsync(textureKey, payload, width, jobGeneration);
                        }
                    });
                }
            }
        }
        if (ready == null) return null;

        DynamicTexture texture = new DynamicTexture(ready);
        ResourceLocation location = minecraft.getTextureManager()
            .getDynamicTextureLocation("textech_web_surface_" + hash.substring(0, 12) + "_" + width, texture);
        synchronized (LOCK) {
            TextureEntry entry = new TextureEntry(location, texture, ready.getWidth() * ready.getHeight());
            TEXTURES.put(textureKey, entry);
            trimTexturesLocked();
        }
        return location;
    }

    private static void rasterizeAsync(String textureKey, byte[] payload, int width, long jobGeneration) {
        BufferedImage image = null;
        String error = null;
        try {
            image = rasterize(WebDashboardSnapshotCodec.decode(payload), width);
        } catch (Throwable t) {
            error = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        }
        synchronized (LOCK) {
            PENDING_RASTERS.remove(textureKey);
            if (jobGeneration != generation) return;
            if (image != null) {
                READY_IMAGES.put(textureKey, image);
            } else if (error != null) {
                int separator = textureKey.indexOf('@');
                ERRORS.put(separator > 0 ? textureKey.substring(0, separator) : textureKey, error);
            }
        }
    }

    private static BufferedImage rasterize(WebDashboardSnapshotCodec.DecodedSnapshot snapshot, int requestedWidth) {
        int width = Math.max(64, Math.min(1024, requestedWidth));
        int height = Math.max(64, Math.min(768, Math.round(width * snapshot.height / (float) snapshot.width)));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setColor(new Color(snapshot.background, true));
            graphics.fillRect(0, 0, width, height);
            graphics.scale(width / (double) snapshot.width, height / (double) snapshot.height);

            for (WebDashboardSnapshotCodec.Primitive primitive : snapshot.primitives) {
                drawPrimitive(graphics, primitive);
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void drawPrimitive(Graphics2D graphics, WebDashboardSnapshotCodec.Primitive primitive) {
        if ("polyline".equals(primitive.kind)) {
            Path2D.Float path = new Path2D.Float();
            path.moveTo(primitive.points[0], primitive.points[1]);
            for (int i = 2; i < primitive.points.length; i += 2) {
                path.lineTo(primitive.points[i], primitive.points[i + 1]);
            }
            graphics.setColor(new Color(primitive.color, true));
            graphics.setStroke(new BasicStroke(primitive.lineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.draw(path);
            return;
        }

        if ("text".equals(primitive.kind)) {
            drawText(graphics, primitive);
            return;
        }

        Shape shape = "ellipse".equals(primitive.kind)
            ? new Ellipse2D.Float(primitive.x, primitive.y, primitive.width, primitive.height)
            : new RoundRectangle2D.Float(
                primitive.x,
                primitive.y,
                primitive.width,
                primitive.height,
                primitive.radius * 2.0F,
                primitive.radius * 2.0F);
        if (primitive.hasFill && ((primitive.fill >>> 24) & 0xFF) > 0) {
            graphics.setColor(new Color(primitive.fill, true));
            graphics.fill(shape);
        }
        if (primitive.hasStroke && ((primitive.stroke >>> 24) & 0xFF) > 0) {
            graphics.setColor(new Color(primitive.stroke, true));
            graphics.setStroke(new BasicStroke(primitive.lineWidth));
            graphics.draw(shape);
        }
    }

    private static void drawText(Graphics2D graphics, WebDashboardSnapshotCodec.Primitive primitive) {
        int style = primitive.fontWeight >= 600 ? Font.BOLD : Font.PLAIN;
        Font font = new Font("SansSerif", style, Math.max(5, Math.round(primitive.fontSize)));
        graphics.setFont(font);
        graphics.setColor(new Color(primitive.color, true));
        FontMetrics metrics = graphics.getFontMetrics(font);
        float x = primitive.x;
        int textWidth = metrics.stringWidth(primitive.text);
        if ("center".equals(primitive.align)) {
            x += Math.max(0.0F, (primitive.width - textWidth) / 2.0F);
        } else if ("right".equals(primitive.align)) {
            x += Math.max(0.0F, primitive.width - textWidth);
        }
        float y = primitive.y + Math.max(metrics.getAscent(), (primitive.height - metrics.getHeight()) / 2.0F
            + metrics.getAscent());
        Shape oldClip = graphics.getClip();
        graphics.clip(new java.awt.geom.Rectangle2D.Float(primitive.x, primitive.y, primitive.width, primitive.height));
        graphics.drawString(primitive.text, x, y);
        graphics.setClip(oldClip);
    }

    private static void ensureWorld(int dimension) {
        synchronized (LOCK) {
            if (worldDimension == dimension) return;
            worldDimension = dimension;
            generation++;
            for (TextureEntry texture : TEXTURES.values()) texture.texture.deleteGlTexture();
            TEXTURES.clear();
            READY_IMAGES.clear();
            PENDING_RASTERS.clear();
            CONTENT.clear();
            LAST_REQUEST.clear();
            UPLOAD_ACK.clear();
            ERRORS.clear();
        }
    }

    private static void trimContentLocked() {
        while (CONTENT.size() > MAX_CONTENTS) {
            Iterator<Map.Entry<String, byte[]>> iterator = CONTENT.entrySet().iterator();
            if (!iterator.hasNext()) break;
            iterator.next();
            iterator.remove();
        }
    }

    private static void trimTexturesLocked() {
        long pixels = 0L;
        for (TextureEntry entry : TEXTURES.values()) pixels += entry.pixels;
        Iterator<Map.Entry<String, TextureEntry>> iterator = TEXTURES.entrySet().iterator();
        while ((TEXTURES.size() > MAX_TEXTURES || pixels > MAX_TEXTURE_PIXELS) && iterator.hasNext()) {
            TextureEntry entry = iterator.next().getValue();
            pixels -= entry.pixels;
            entry.texture.deleteGlTexture();
            iterator.remove();
        }
    }

    private static int normalizeWidth(int width) {
        if (width >= 768) return 1024;
        if (width >= 384) return 512;
        return 256;
    }

    private static final class TextureEntry {

        private final ResourceLocation location;
        private final DynamicTexture texture;
        private final int pixels;

        private TextureEntry(ResourceLocation location, DynamicTexture texture, int pixels) {
            this.location = location;
            this.texture = texture;
            this.pixels = pixels;
        }
    }
}
