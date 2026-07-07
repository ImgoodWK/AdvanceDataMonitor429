package com.imgood.textech.webae.worldmap;

import java.io.File;
import java.io.FileOutputStream;

import com.imgood.textech.AdvanceDataMonitor;

/**
 * Disk cache for world map chunk PNG tiles at {@code config/textech/web-map-tiles/{view}/[{ae}/]{dim}/{cx}/{cz}.png}.
 * Legacy flat tiles without a view segment fall back to {@code web-map-tiles/<dim>/<cx>/<cz>.png}.
 */
public final class WorldMapTileCache {

    private static final File ROOT = new File("config/textech/web-map-tiles");
    private static final String FLAT_VIEW = WorldMapView.FLAT.id;

    private WorldMapTileCache() {}

    public static File tileFile(String view, int dim, int chunkX, int chunkZ) {
        return tileFile(view, WorldMapTileLayer.TERRAIN, dim, chunkX, chunkZ);
    }

    public static File tileFile(String view, String layer, int dim, int chunkX, int chunkZ) {
        String cacheView = WorldMapTileLayer.cacheViewPath(view, layer);
        return new File(
            new File(new File(new File(ROOT, cacheView), String.valueOf(dim)), String.valueOf(chunkX)),
            chunkZ + ".png");
    }

    /** @deprecated Use {@link #tileFile(String, String, int, int, int)}. */
    public static File tileFile(int dim, int chunkX, int chunkZ) {
        return tileFile(FLAT_VIEW, dim, chunkX, chunkZ);
    }

    private static File legacyFlatFile(int dim, int chunkX, int chunkZ) {
        return new File(new File(new File(ROOT, String.valueOf(dim)), String.valueOf(chunkX)), chunkZ + ".png");
    }

    public static boolean exists(String view, int dim, int chunkX, int chunkZ) {
        return exists(view, WorldMapTileLayer.TERRAIN, dim, chunkX, chunkZ);
    }

    public static boolean exists(String view, String layer, int dim, int chunkX, int chunkZ) {
        return getExisting(view, layer, dim, chunkX, chunkZ) != null;
    }

    /** @deprecated Use {@link #exists(String, String, int, int, int)}. */
    public static boolean exists(int dim, int chunkX, int chunkZ) {
        return exists(FLAT_VIEW, dim, chunkX, chunkZ);
    }

    public static File getExisting(String view, int dim, int chunkX, int chunkZ) {
        return getExisting(view, WorldMapTileLayer.TERRAIN, dim, chunkX, chunkZ);
    }

    public static File getExisting(String view, String layer, int dim, int chunkX, int chunkZ) {
        File file = tileFile(view, layer, dim, chunkX, chunkZ);
        if (file.isFile() && file.length() >= WorldMapRenderSupport.MIN_VALID_TILE_BYTES) {
            return file;
        }
        if (file.isFile() && file.length() > 0L) {
            deleteIfPresent(file);
            deleteIfPresent(hdMarkerFile(view, layer, dim, chunkX, chunkZ));
        }
        if (WorldMapTileLayer.TERRAIN.equals(WorldMapTileLayer.normalize(layer)) && FLAT_VIEW.equals(normalizeView(view))) {
            File legacy = legacyFlatFile(dim, chunkX, chunkZ);
            if (legacy.isFile() && legacy.length() >= WorldMapRenderSupport.MIN_VALID_TILE_BYTES) {
                return legacy;
            }
            if (legacy.isFile() && legacy.length() > 0L) {
                deleteIfPresent(legacy);
            }
        }
        return null;
    }

    /** @deprecated Use {@link #getExisting(String, String, int, int, int)}. */
    public static File getExisting(int dim, int chunkX, int chunkZ) {
        return getExisting(FLAT_VIEW, dim, chunkX, chunkZ);
    }

    public static void write(String view, int dim, int chunkX, int chunkZ, byte[] png) {
        write(view, WorldMapTileLayer.TERRAIN, dim, chunkX, chunkZ, png);
    }

    public static void write(String view, String layer, int dim, int chunkX, int chunkZ, byte[] png) {
        if (!WorldMapRenderSupport.isValidTilePng(png)) {
            return;
        }
        File file = tileFile(view, layer, dim, chunkX, chunkZ);
        writeFile(file, view, layer, dim, chunkX, chunkZ, png);
    }

    /** Writes a client-uploaded HD tile and marks it for {@code X-WorldMap-Tile-Quality: hd}. */
    public static void writeHd(String view, int dim, int chunkX, int chunkZ, byte[] png) {
        writeHd(view, WorldMapTileLayer.TERRAIN, dim, chunkX, chunkZ, png);
    }

    public static void writeHd(String view, String layer, int dim, int chunkX, int chunkZ, byte[] png) {
        write(view, layer, dim, chunkX, chunkZ, png);
        markHd(view, layer, dim, chunkX, chunkZ);
    }

    public static boolean isHd(String view, int dim, int chunkX, int chunkZ) {
        return isHd(view, WorldMapTileLayer.TERRAIN, dim, chunkX, chunkZ);
    }

    public static boolean isHd(String view, String layer, int dim, int chunkX, int chunkZ) {
        File marker = hdMarkerFile(view, layer, dim, chunkX, chunkZ);
        return marker.isFile();
    }

    /** @deprecated Use {@link #write(String, String, int, int, int, byte[])}. */
    public static void write(int dim, int chunkX, int chunkZ, byte[] png) {
        write(FLAT_VIEW, dim, chunkX, chunkZ, png);
    }

    public static void invalidate(String view, int dim, int chunkX, int chunkZ) {
        invalidate(view, WorldMapTileLayer.TERRAIN, dim, chunkX, chunkZ);
    }

    public static void invalidate(String view, String layer, int dim, int chunkX, int chunkZ) {
        File file = tileFile(view, layer, dim, chunkX, chunkZ);
        deleteIfPresent(file);
        deleteIfPresent(hdMarkerFile(view, layer, dim, chunkX, chunkZ));
        if (WorldMapTileLayer.TERRAIN.equals(WorldMapTileLayer.normalize(layer)) && FLAT_VIEW.equals(normalizeView(view))) {
            deleteIfPresent(legacyFlatFile(dim, chunkX, chunkZ));
        }
    }

    /** @deprecated Use {@link #invalidate(String, String, int, int, int)}. */
    public static void invalidate(int dim, int chunkX, int chunkZ) {
        invalidate(FLAT_VIEW, dim, chunkX, chunkZ);
    }

    private static void writeFile(File file, String view, String layer, int dim, int chunkX, int chunkZ, byte[] png) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                AdvanceDataMonitor.LOG.warn("[WebAE] Failed to create world map tile dir: {}", parent.getAbsolutePath());
            }
        }
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            fos.write(png);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error(
                "[WebAE] Failed to write world map tile view={} layer={} dim={} cx={} cz={}",
                view,
                layer,
                dim,
                chunkX,
                chunkZ,
                e);
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private static void deleteIfPresent(File file) {
        if (file.isFile()) {
            if (!file.delete()) {
                AdvanceDataMonitor.LOG.debug("[WebAE] Could not delete stale world map tile {}", file.getAbsolutePath());
            }
        }
    }

    private static File hdMarkerFile(String view, String layer, int dim, int chunkX, int chunkZ) {
        return new File(tileFile(view, layer, dim, chunkX, chunkZ).getParentFile(), chunkZ + ".hd");
    }

    private static void markHd(String view, String layer, int dim, int chunkX, int chunkZ) {
        File marker = hdMarkerFile(view, layer, dim, chunkX, chunkZ);
        File parent = marker.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                AdvanceDataMonitor.LOG.warn("[WebAE] Failed to create world map HD marker dir: {}", parent.getAbsolutePath());
            }
        }
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(marker);
            fos.write(1);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.debug(
                "[WebAE] Failed to write world map HD marker view={} layer={} dim={} cx={} cz={}",
                view,
                layer,
                dim,
                chunkX,
                chunkZ,
                e);
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private static String normalizeView(String view) {
        if (view == null || view.trim()
            .isEmpty()) {
            return FLAT_VIEW;
        }
        return view.trim()
            .toLowerCase();
    }
}
