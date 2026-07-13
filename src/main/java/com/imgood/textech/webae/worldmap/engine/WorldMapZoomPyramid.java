package com.imgood.textech.webae.worldmap.engine;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.imageio.ImageIO;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.worldmap.WorldMapQualityTier;
import com.imgood.textech.webae.worldmap.WorldMapRenderExecutor;
import com.imgood.textech.webae.worldmap.WorldMapRenderSupport;
import com.imgood.textech.webae.worldmap.WorldMapTileCache;

/**
 * Dynmap-style zoom-out pyramid: after z0 chunk tiles are written, parent tiles z1/z2… are
 * synthesized by merging 2×2 child tiles with box downsample.
 */
public final class WorldMapZoomPyramid {

    private static final int MAX_QUEUE = 2048;
    private static final WorldMapZoomPyramid INSTANCE = new WorldMapZoomPyramid();

    private final Deque<ZoomKey> queue = new ArrayDeque<ZoomKey>();
    private final Set<String> queuedKeys = new LinkedHashSet<String>();
    /** Results from worker-thread synthesis tasks, collected on the main tick. */
    private final ConcurrentLinkedQueue<SynthesisTask> synthesisResults = new ConcurrentLinkedQueue<SynthesisTask>();

    private WorldMapZoomPyramid() {}

    public static WorldMapZoomPyramid instance() {
        return INSTANCE;
    }

    /** Number of configured zoom levels (z0 … z{n-1}). */
    public static int configuredLevels() {
        int raw = Config.webWorldMapZoomLevels;
        if (raw < 1) {
            return 1;
        }
        if (raw > 6) {
            return 6;
        }
        return raw;
    }

    /** Chunks spanned by one tile at {@code zoomLevel} (1, 2, 4, …). */
    public static int chunkSpan(int zoomLevel) {
        if (zoomLevel <= 0) {
            return 1;
        }
        if (zoomLevel >= 31) {
            return Integer.MAX_VALUE;
        }
        return 1 << zoomLevel;
    }

    /** Tile index for a chunk coordinate at {@code zoomLevel}. */
    public static int tileIndex(int chunkCoord, int zoomLevel) {
        int span = chunkSpan(zoomLevel);
        if (chunkCoord >= 0) {
            return chunkCoord / span;
        }
        return (chunkCoord - span + 1) / span;
    }

    /** Effective px-per-block at a zoom level (same tilePx, larger world area). */
    public static int effectivePxPerBlock(WorldMapQualityTier quality, int zoomLevel) {
        int base = WorldMapRenderSupport.pxPerBlock(quality);
        int span = chunkSpan(zoomLevel);
        return Math.max(1, base / span);
    }

    public static void enqueueParents(String view, String layer, WorldMapQualityTier quality, int dim, int chunkX,
        int chunkZ) {
        if (configuredLevels() <= 1) {
            return;
        }
        for (int level = 1; level < configuredLevels(); level++) {
            int parentX = tileIndex(chunkX, level);
            int parentZ = tileIndex(chunkZ, level);
            instance().enqueue(view, layer, quality, dim, parentX, parentZ, level);
        }
    }

    public void enqueue(String view, String layer, WorldMapQualityTier quality, int dim, int tileX, int tileZ,
        int zoomLevel) {
        if (!Config.webWorldMapEnabled || zoomLevel <= 0 || zoomLevel >= configuredLevels()) {
            return;
        }
        WorldMapQualityTier tier = WorldMapQualityTier.clamp(
            quality != null ? quality : WorldMapQualityTier.MEDIUM,
            WorldMapQualityTier.fromConfigMax());
        if (WorldMapTileCache.exists(view, layer, tier, dim, tileX, tileZ, zoomLevel)) {
            return;
        }
        String key = zoomKey(view, layer, tier, dim, tileX, tileZ, zoomLevel);
        synchronized (this) {
            if (queuedKeys.contains(key)) {
                return;
            }
            if (queue.size() >= MAX_QUEUE) {
                ZoomKey dropped = queue.pollFirst();
                if (dropped != null) {
                    queuedKeys.remove(dropped.key);
                }
            }
            queue.offerLast(new ZoomKey(view, layer, tier, dim, tileX, tileZ, zoomLevel, key));
            queuedKeys.add(key);
        }
    }

    public void onServerTick() {
        if (!Config.webWorldMapEnabled || configuredLevels() <= 1) {
            return;
        }
        int budget = Config.webWorldMapZoomBudgetPerTick;
        if (budget <= 0) {
            budget = 1;
        }

        // --- Collect completed synthesis results from worker threads ---
        SynthesisTask synthResult;
        while ((synthResult = synthesisResults.poll()) != null) {
            if (synthResult.result == SynthesisResult.NEED_CHILDREN) {
                enqueueChildDependencies(synthResult.key);
            }
            if (synthResult.result != SynthesisResult.DONE) {
                requeueBack(synthResult.key);
            }
        }

        // --- Submit synthesis tasks to the render executor ---
        for (int i = 0; i < budget; i++) {
            ZoomKey next = pollNext();
            if (next == null) {
                break;
            }
            if (WorldMapTileCache.exists(next.view, next.layer, next.quality, next.dim, next.tileX, next.tileZ,
                next.zoomLevel)) {
                continue;
            }
            final ZoomKey captured = next;
            WorldMapRenderExecutor.instance()
                .submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            SynthesisResult result = trySynthesize(captured.view, captured.layer, captured.quality,
                                captured.dim, captured.tileX, captured.tileZ, captured.zoomLevel);
                            synthesisResults.offer(new SynthesisTask(captured, result));
                        } catch (Throwable t) {
                            AdvanceDataMonitor.LOG.error(
                                "[WebAE] World map zoom synthesis failed view={} layer={} tier={} dim={} tile=({}, {}) z={}",
                                captured.view, captured.layer, captured.quality.id, captured.dim,
                                captured.tileX, captured.tileZ, captured.zoomLevel, t);
                            synthesisResults.offer(new SynthesisTask(captured, SynthesisResult.FAILED));
                        }
                    }
                });
        }
    }

    public static SynthesisResult trySynthesize(String view, String layer, WorldMapQualityTier quality, int dim,
        int tileX, int tileZ, int zoomLevel) {
        if (zoomLevel <= 0 || zoomLevel >= configuredLevels()) {
            return SynthesisResult.FAILED;
        }
        WorldMapQualityTier tier = quality != null ? quality : WorldMapQualityTier.MEDIUM;
        int childLevel = zoomLevel - 1;
        int outPx = WorldMapRenderSupport.tilePx(tier);
        int childPx = outPx;
        BufferedImage canvas = new BufferedImage(childPx * 2, childPx * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setBackground(new java.awt.Color(0, 0, 0, 0));
            g.clearRect(0, 0, childPx * 2, childPx * 2);
            int found = 0;
            for (int dz = 0; dz < 2; dz++) {
                for (int dx = 0; dx < 2; dx++) {
                    int childTileX = tileX * 2 + dx;
                    int childTileZ = tileZ * 2 + dz;
                    BufferedImage child = loadChildTile(view, layer, tier, dim, childTileX, childTileZ, childLevel);
                    if (child != null) {
                        g.drawImage(child, dx * childPx, dz * childPx, null);
                        found++;
                    }
                }
            }
            if (found == 0) {
                return SynthesisResult.NEED_CHILDREN;
            }
            BufferedImage out = downsampleBox(canvas, outPx, outPx);
            byte[] png = WorldMapRenderSupport.toPng(out);
            if (!WorldMapRenderSupport.isValidTilePng(png)) {
                return SynthesisResult.FAILED;
            }
            WorldMapTileCache.write(view, layer, tier, dim, tileX, tileZ, zoomLevel, png);
            if (zoomLevel + 1 < configuredLevels()) {
                instance().enqueue(view, layer, tier, dim, tileX, tileZ, zoomLevel + 1);
            }
            return SynthesisResult.DONE;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.debug(
                "[WebAE] World map zoom synthesis failed view={} layer={} tier={} dim={} tile=({}, {}) z={}",
                view,
                layer,
                tier.id,
                dim,
                tileX,
                tileZ,
                zoomLevel,
                e);
            return SynthesisResult.FAILED;
        } finally {
            g.dispose();
        }
    }

    private static BufferedImage loadChildTile(String view, String layer, WorldMapQualityTier tier, int dim,
        int childTileX, int childTileZ, int childLevel) {
        if (childLevel == 0) {
            int chunkX = childTileX;
            int chunkZ = childTileZ;
            File file = WorldMapTileCache.getExisting(view, layer, tier, dim, chunkX, chunkZ, 0);
            return readPng(file);
        }
        File file = WorldMapTileCache.getExisting(view, layer, tier, dim, childTileX, childTileZ, childLevel);
        return readPng(file);
    }

    private static BufferedImage readPng(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            return ImageIO.read(in);
        } catch (Exception e) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private static BufferedImage downsampleBox(BufferedImage src, int outW, int outH) {
        int srcW = src.getWidth();
        int srcH = src.getHeight();
        BufferedImage out = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < outH; y++) {
            for (int x = 0; x < outW; x++) {
                int x0 = x * srcW / outW;
                int x1 = Math.max(x0 + 1, (x + 1) * srcW / outW);
                int y0 = y * srcH / outH;
                int y1 = Math.max(y0 + 1, (y + 1) * srcH / outH);
                long a = 0L;
                long r = 0L;
                long g = 0L;
                long b = 0L;
                int count = 0;
                for (int sy = y0; sy < y1; sy++) {
                    for (int sx = x0; sx < x1; sx++) {
                        int argb = src.getRGB(sx, sy);
                        int alpha = (argb >>> 24) & 0xFF;
                        if (alpha == 0) {
                            continue;
                        }
                        a += alpha;
                        r += (argb >>> 16) & 0xFF;
                        g += (argb >>> 8) & 0xFF;
                        b += argb & 0xFF;
                        count++;
                    }
                }
                if (count == 0) {
                    out.setRGB(x, y, 0);
                } else {
                    int aa = (int) (a / count);
                    int rr = (int) (r / count);
                    int gg = (int) (g / count);
                    int bb = (int) (b / count);
                    out.setRGB(x, y, (aa << 24) | (rr << 16) | (gg << 8) | bb);
                }
            }
        }
        return out;
    }

    private void enqueueChildDependencies(ZoomKey key) {
        int childLevel = key.zoomLevel - 1;
        for (int dz = 0; dz < 2; dz++) {
            for (int dx = 0; dx < 2; dx++) {
                int childTileX = key.tileX * 2 + dx;
                int childTileZ = key.tileZ * 2 + dz;
                if (childLevel == 0) {
                    if (!WorldMapTileCache.exists(key.view, key.layer, key.quality, key.dim, childTileX, childTileZ,
                        0)) {
                        com.imgood.textech.webae.worldmap.WorldMapTileQueue.instance()
                            .enqueue(key.view, key.layer, key.quality, key.dim, childTileX, childTileZ, null, -1);
                    }
                } else if (!WorldMapTileCache.exists(key.view, key.layer, key.quality, key.dim, childTileX,
                    childTileZ, childLevel)) {
                    enqueue(key.view, key.layer, key.quality, key.dim, childTileX, childTileZ, childLevel);
                }
            }
        }
    }

    private ZoomKey pollNext() {
        synchronized (this) {
            ZoomKey next = queue.pollFirst();
            if (next != null) {
                queuedKeys.remove(next.key);
            }
            return next;
        }
    }

    private void requeueBack(ZoomKey key) {
        if (key == null) {
            return;
        }
        synchronized (this) {
            if (!queuedKeys.contains(key.key)) {
                queue.offerLast(key);
                queuedKeys.add(key.key);
            }
        }
    }

    private static String zoomKey(String view, String layer, WorldMapQualityTier tier, int dim, int tileX, int tileZ,
        int zoomLevel) {
        return view + ":" + layer + ":" + tier.id + ":" + dim + ":" + tileX + ":" + tileZ + ":z" + zoomLevel;
    }

    public enum SynthesisResult {
        DONE,
        NEED_CHILDREN,
        FAILED
    }

    /** Result container for worker-thread synthesis tasks — collected on the main tick. */
    private static final class SynthesisTask {
        final ZoomKey key;
        final SynthesisResult result;

        SynthesisTask(ZoomKey key, SynthesisResult result) {
            this.key = key;
            this.result = result;
        }
    }

    private static final class ZoomKey {

        final String view;
        final String layer;
        final WorldMapQualityTier quality;
        final int dim;
        final int tileX;
        final int tileZ;
        final int zoomLevel;
        final String key;

        ZoomKey(String view, String layer, WorldMapQualityTier quality, int dim, int tileX, int tileZ, int zoomLevel,
            String key) {
            this.view = view;
            this.layer = com.imgood.textech.webae.worldmap.WorldMapTileLayer.normalize(layer);
            this.quality = quality != null ? quality : WorldMapQualityTier.MEDIUM;
            this.dim = dim;
            this.tileX = tileX;
            this.tileZ = tileZ;
            this.zoomLevel = zoomLevel;
            this.key = key;
        }
    }
}
