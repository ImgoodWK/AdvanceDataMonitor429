package com.imgood.textech.webae.worldmap;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        return root != null;
    }

    public byte[] readChunkTerrain(boolean multiplayer, String worldName, int dim, int chunkX, int chunkZ, int tilePx) {
        return readChunkTerrain(resolveDataRoot(), multiplayer, worldName, dim, chunkX, chunkZ, tilePx);
    }

    byte[] readChunkTerrain(File dataRoot, boolean multiplayer, String worldName, int dim, int chunkX, int chunkZ,
        int tilePx) {
        if (!WorldMapPacketAuthorization.isValidChunk(dim, chunkX, chunkZ)
            || !WorldMapPacketAuthorization.isValidTilePx(tilePx)) {
            return null;
        }
        File worldRoot = resolveWorldRoot(dataRoot, multiplayer, worldName);
        if (worldRoot == null) {
            return null;
        }
        File dimRoot = resolveDimRoot(worldRoot, dim);
        if (dimRoot == null || !dimRoot.isDirectory()) {
            return null;
        }
        File dayRoot = safeDirectory(dimRoot, new File(dimRoot, "day"));
        if (dayRoot == null) {
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
            // This is a bounded PNG header check only.  It deliberately runs
            // before ImageIO is allowed to inflate the local cache file.
            if (!WorldMapRenderSupport.isValidTilePng(tileFile)) {
                return null;
            }
            BufferedImage source = ImageIO.read(tileFile);
            if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0
                || source.getWidth() > WorldMapPacketAuthorization.MAX_TILE_PX
                || source.getHeight() > WorldMapPacketAuthorization.MAX_TILE_PX) {
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
            if (!ImageIO.write(chunk, "png", baos)) {
                return null;
            }
            byte[] png = baos.toByteArray();
            return WorldMapRenderSupport.isValidTilePng(png) ? png : null;
        } catch (IOException | RuntimeException e) {
            AdvanceDataMonitor.LOG
                .debug("[WebAE] JourneyMap FS tile read failed dim={} cx={} cz={}", dim, chunkX, chunkZ);
            return null;
        }
    }

    public File resolveWorldRoot(boolean multiplayer, String worldName) {
        return resolveWorldRoot(resolveDataRoot(), multiplayer, worldName);
    }

    static File resolveWorldRoot(File dataRoot, boolean multiplayer, String worldName) {
        dataRoot = canonicalDataRoot(dataRoot);
        if (dataRoot == null) {
            return null;
        }
        String exactWorldName = worldName == null ? null : worldName.trim();
        if (!isSafePathSegment(exactWorldName)) {
            return null;
        }
        File modeRoot = safeDirectory(dataRoot, new File(dataRoot, multiplayer ? "mp" : "sp"));
        return findWorldFolder(modeRoot, exactWorldName);
    }

    public static File resolveDataRoot() {
        if (Config.worldMapJourneyMapDataRoot != null && !Config.worldMapJourneyMapDataRoot.trim()
            .isEmpty()) {
            File custom = canonicalDataRoot(new File(Config.worldMapJourneyMapDataRoot.trim()));
            if (custom != null) {
                return custom;
            }
        }
        return canonicalDataRoot(new File(TeXTechDataDir.instanceRoot(), "journeymap/data"));
    }

    private static File findWorldFolder(File modeRoot, String worldName) {
        if (modeRoot == null || !isSafePathSegment(worldName)) {
            return null;
        }
        // Exact match only.  Falling back to an arbitrary cache directory can
        // cross worlds and disclose terrain from an unrelated server/save.
        return safeDirectory(modeRoot, new File(modeRoot, worldName));
    }

    private static File resolveDimRoot(File worldRoot, int dim) {
        String[] names = dimNames(dim);
        for (String name : names) {
            File candidate = safeDirectory(worldRoot, new File(worldRoot, name));
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
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
            File safeChild = safeDirectory(dayRoot, child);
            if (safeChild == null) {
                continue;
            }
            if (containsPng(safeChild) || hasNumericSubdirs(safeChild)) {
                ZoomInfo info = new ZoomInfo();
                info.folder = safeChild;
                info.blocksPerTile = Math.max(16, 512 >> Math.max(0, parseZoomName(safeChild.getName())));
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
            if (safeDirectory(dir, child) != null) {
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
            if (safeFile(dir, f) != null && f.getName()
                .endsWith(".png")) {
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
            return Integer.parseInt(
                name.replace("z", "")
                    .trim());
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
        File nested = new File(
            new File(new File(zoomFolder, String.valueOf(tileX)), String.valueOf(tileZ)),
            "tile.png");
        File safe = safeFile(zoomFolder, nested);
        if (safe != null) {
            return safe;
        }
        File flatZ = new File(new File(zoomFolder, String.valueOf(tileX)), tileZ + ".png");
        safe = safeFile(zoomFolder, flatZ);
        if (safe != null) {
            return safe;
        }
        File underscore = new File(zoomFolder, tileX + "_" + tileZ + ".png");
        safe = safeFile(zoomFolder, underscore);
        if (safe != null) {
            return safe;
        }
        File comma = new File(zoomFolder, tileX + "," + tileZ + ".png");
        safe = safeFile(zoomFolder, comma);
        if (safe != null) {
            return safe;
        }
        return null;
    }

    private static File canonicalDataRoot(File root) {
        if (root == null || !root.isDirectory() || Files.isSymbolicLink(root.toPath())) {
            return null;
        }
        try {
            File canonical = root.getCanonicalFile();
            return canonical.isDirectory() && !Files.isSymbolicLink(canonical.toPath()) ? canonical : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static File safeDirectory(File anchor, File candidate) {
        return safeExistingPath(anchor, candidate, true);
    }

    private static File safeFile(File anchor, File candidate) {
        return safeExistingPath(anchor, candidate, false);
    }

    private static File safeExistingPath(File anchor, File candidate, boolean directory) {
        if (anchor == null || candidate == null) {
            return null;
        }
        try {
            File canonicalAnchor = anchor.getCanonicalFile();
            Path root = canonicalAnchor.toPath();
            Path lexicalCandidate = candidate.getAbsoluteFile()
                .toPath()
                .normalize();
            if (!lexicalCandidate.startsWith(root) || Files.isSymbolicLink(root)) {
                return null;
            }
            Path current = root;
            Path relative = root.relativize(lexicalCandidate);
            for (Path part : relative) {
                current = current.resolve(part);
                if (Files.isSymbolicLink(current)) {
                    return null;
                }
            }
            File canonicalCandidate = candidate.getCanonicalFile();
            Path resolved = canonicalCandidate.toPath();
            if (!resolved.startsWith(root)) {
                return null;
            }
            if (directory ? !canonicalCandidate.isDirectory() : !canonicalCandidate.isFile()) {
                return null;
            }
            return canonicalCandidate;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static boolean isSafePathSegment(String value) {
        if (value == null || value.isEmpty() || ".".equals(value) || "..".equals(value)) {
            return false;
        }
        return value.indexOf('/') < 0 && value.indexOf('\\') < 0 && value.indexOf('\0') < 0
            && new File(value).getName()
                .equals(value);
    }

    private static final class ZoomInfo {

        File folder;
        int blocksPerTile = 512;
    }
}
