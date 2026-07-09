package com.imgood.textech.webae.worldmap;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

import javax.imageio.ImageIO;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.TeXTechDataDir;

/**
 * Side-neutral JourneyMap tile filesystem reader (no Minecraft client dependency).
 */
public final class WorldMapJourneyMapFsReader {

    private static final int CHUNK_BLOCKS = 16;
    private static final WorldMapJourneyMapFsReader INSTANCE = new WorldMapJourneyMapFsReader();

    private WorldMapJourneyMapFsReader() {}

    public static WorldMapJourneyMapFsReader instance() {
        return INSTANCE;
    }

    public static boolean isDataRootAvailable() {
        if (!Config.worldMapJourneyMapCaptureEnabled && !Config.worldMapJourneyMapEnabled) {
            return false;
        }
        File root = resolveDataRoot();
        return root != null && root.isDirectory();
    }

    public byte[] readChunkTerrain(boolean multiplayer, String worldName, int dim, int chunkX, int chunkZ, int tilePx) {
        File worldRoot = resolveWorldRoot(multiplayer, worldName);
        if (worldRoot == null) {
            return null;
        }
        File dimRoot = resolveDimRoot(worldRoot, dim);
        if (dimRoot == null || !dimRoot.isDirectory()) {
            return null;
        }
        File dayRoot = new File(dimRoot, "day");
        if (!dayRoot.isDirectory()) {
            dayRoot = dimRoot;
        }
        ZoomInfo zoom = findHighestZoom(dayRoot);
        if (zoom == null) {
            return null;
        }
        int blockX = chunkX * CHUNK_BLOCKS;
        int blockZ = chunkZ * CHUNK_BLOCKS;
        int tileX = floorDiv(blockX, zoom.blocksPerTile);
        int tileZ = floorDiv(blockZ, zoom.blocksPerTile);
        File tileFile = resolveTileFile(zoom.folder, tileX, tileZ);
        if (tileFile == null) {
            return null;
        }
        try {
            BufferedImage source = ImageIO.read(tileFile);
            if (source == null) {
                return null;
            }
            int localBlockX = blockX - tileX * zoom.blocksPerTile;
            int localBlockZ = blockZ - tileZ * zoom.blocksPerTile;
            int srcPxPerBlock = Math.max(1, source.getWidth() / zoom.blocksPerTile);
            int srcX = localBlockX * srcPxPerBlock;
            int srcY = localBlockZ * srcPxPerBlock;
            int srcSize = CHUNK_BLOCKS * srcPxPerBlock;
            if (srcX + srcSize > source.getWidth() || srcY + srcSize > source.getHeight()) {
                return null;
            }
            BufferedImage chunk = source.getSubimage(srcX, srcY, srcSize, srcSize);
            if (tilePx > 0 && (chunk.getWidth() != tilePx || chunk.getHeight() != tilePx)) {
                BufferedImage scaled = new BufferedImage(tilePx, tilePx, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = scaled.createGraphics();
                g.drawImage(chunk, 0, 0, tilePx, tilePx, null);
                g.dispose();
                chunk = scaled;
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(chunk, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            AdvanceDataMonitor.LOG.debug("[WebAE] JourneyMap FS tile read failed dim={} cx={} cz={}", dim, chunkX, chunkZ);
            return null;
        }
    }

    public File resolveWorldRoot(boolean multiplayer, String worldName) {
        if (worldName == null || worldName.trim().isEmpty()) {
            worldName = "world";
        }
        File dataRoot = resolveDataRoot();
        if (dataRoot == null) {
            return null;
        }
        File modeRoot = new File(dataRoot, multiplayer ? "mp" : "sp");
        return findWorldFolder(modeRoot, worldName.trim());
    }

    public static File resolveDataRoot() {
        if (Config.worldMapJourneyMapDataRoot != null && !Config.worldMapJourneyMapDataRoot.trim().isEmpty()) {
            File custom = new File(Config.worldMapJourneyMapDataRoot.trim());
            if (custom.isDirectory()) {
                return custom;
            }
        }
        File jm = new File(TeXTechDataDir.instanceRoot(), "journeymap/data");
        if (jm.isDirectory()) {
            return jm;
        }
        return null;
    }

    private static File findWorldFolder(File modeRoot, String worldName) {
        if (modeRoot == null || !modeRoot.isDirectory()) {
            return null;
        }
        File direct = new File(modeRoot, worldName);
        if (direct.isDirectory()) {
            return direct;
        }
        File[] children = modeRoot.listFiles();
        if (children == null) {
            return null;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                return child;
            }
        }
        return null;
    }

    private static File resolveDimRoot(File worldRoot, int dim) {
        String[] names = dimNames(dim);
        for (String name : names) {
            File candidate = new File(worldRoot, name);
            if (candidate.isDirectory()) {
                return candidate;
            }
        }
        return new File(worldRoot, names[0]);
    }

    private static String[] dimNames(int dim) {
        if (dim == -1) {
            return new String[] { "DIM-1", "Nether", "dim-1", "-1" };
        }
        if (dim == 1) {
            return new String[] { "DIM1", "End", "dim1", "1" };
        }
        return new String[] { "DIM0", "Overworld", "overworld", "0", "world" };
    }

    private static ZoomInfo findHighestZoom(File dayRoot) {
        if (dayRoot == null || !dayRoot.isDirectory()) {
            return null;
        }
        File[] children = dayRoot.listFiles();
        if (children == null || children.length == 0) {
            if (containsPng(dayRoot)) {
                ZoomInfo info = new ZoomInfo();
                info.folder = dayRoot;
                info.blocksPerTile = 512;
                return info;
            }
            return null;
        }
        Arrays.sort(children, new Comparator<File>() {

            @Override
            public int compare(File a, File b) {
                int za = parseZoomName(a.getName());
                int zb = parseZoomName(b.getName());
                return Integer.compare(zb, za);
            }
        });
        for (File child : children) {
            if (!child.isDirectory()) {
                continue;
            }
            if (containsPng(child) || hasNumericSubdirs(child)) {
                ZoomInfo info = new ZoomInfo();
                info.folder = child;
                info.blocksPerTile = Math.max(16, 512 >> Math.max(0, parseZoomName(child.getName())));
                return info;
            }
        }
        if (containsPng(dayRoot)) {
            ZoomInfo info = new ZoomInfo();
            info.folder = dayRoot;
            info.blocksPerTile = 512;
            return info;
        }
        return null;
    }

    private static boolean hasNumericSubdirs(File dir) {
        File[] children = dir.listFiles();
        if (children == null) {
            return false;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                try {
                    Integer.parseInt(child.getName());
                    return true;
                } catch (NumberFormatException ignored) {}
            }
        }
        return false;
    }

    private static boolean containsPng(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return false;
        }
        for (File f : files) {
            if (f.isFile() && f.getName().endsWith(".png")) {
                return true;
            }
        }
        return false;
    }

    private static int parseZoomName(String name) {
        if (name == null) {
            return 0;
        }
        try {
            return Integer.parseInt(name.replace("z", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int floorDiv(int a, int b) {
        if (b <= 0) {
            return 0;
        }
        return a >= 0 ? a / b : (a - b + 1) / b;
    }

    private static File resolveTileFile(File zoomFolder, int tileX, int tileZ) {
        if (zoomFolder == null) {
            return null;
        }
        File nested = new File(new File(new File(zoomFolder, String.valueOf(tileX)), String.valueOf(tileZ)), "tile.png");
        if (nested.isFile()) {
            return nested;
        }
        File flatZ = new File(new File(zoomFolder, String.valueOf(tileX)), tileZ + ".png");
        if (flatZ.isFile()) {
            return flatZ;
        }
        File underscore = new File(zoomFolder, tileX + "_" + tileZ + ".png");
        if (underscore.isFile()) {
            return underscore;
        }
        File comma = new File(zoomFolder, tileX + "," + tileZ + ".png");
        if (comma.isFile()) {
            return comma;
        }
        return null;
    }

    private static final class ZoomInfo {

        File folder;
        int blocksPerTile = 512;
    }
}
