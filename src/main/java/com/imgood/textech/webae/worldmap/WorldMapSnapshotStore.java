package com.imgood.textech.webae.worldmap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.TeXTechDataDir;

/**
 * Disk store for per-network world map snapshots at
 * {@code TeXTech/WebAE/map-snapshots/{ownerUuid}/{networkId}/{version}/}.
 */
public final class WorldMapSnapshotStore {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();
    private static final String MANIFEST = "manifest.json";
    private static final String LOGICAL_INDEX = "logical-index.json";
    private static final String CURRENT = "current.json";
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int MAX_MANIFEST_BYTES = 8 * 1024 * 1024;
    private static final int MAX_CURRENT_BYTES = 64 * 1024;
    private static final int MAX_DIMENSIONS = 128;
    private static final int MAX_TOTAL_CHUNKS = 100_000;
    private static final int MAX_TILES = 200_000;
    private static final int MAX_MISSING_CHUNKS = 100_000;
    private static final int MAX_SOURCE_STATS = 32;
    private static final int MAX_MANIFEST_KEY_BYTES = 128;
    private static final int MAX_LOGICAL_INDEX_BYTES = 8 * 1024 * 1024;

    private WorldMapSnapshotStore() {}

    public static File snapshotsRoot() {
        return TeXTechDataDir.webAeDir("map-snapshots");
    }

    public static File networkDir(String ownerUuid, int networkId) {
        String canonicalOwner = WorldMapPacketAuthorization.canonicalOwnerUuid(ownerUuid);
        if (canonicalOwner == null || !WorldMapPacketAuthorization.isValidNetworkId(networkId)) {
            return null;
        }
        File result = new File(new File(snapshotsRoot(), canonicalOwner), String.valueOf(networkId));
        return isWithinSnapshotsRoot(result) ? result : null;
    }

    public static File versionDir(String ownerUuid, int networkId, int version) {
        File network = networkDir(ownerUuid, networkId);
        if (network == null || !WorldMapPacketAuthorization.isValidSnapshotVersion(version)) {
            return null;
        }
        File result = new File(network, "v" + version);
        return isWithinSnapshotsRoot(result) ? result : null;
    }

    public static File currentPointerFile(String ownerUuid, int networkId) {
        File network = networkDir(ownerUuid, networkId);
        if (network == null) {
            return null;
        }
        File result = new File(network, CURRENT);
        return isWithinSnapshotsRoot(result) ? result : null;
    }

    public static File manifestFile(String ownerUuid, int networkId, int version) {
        File versionDirectory = versionDir(ownerUuid, networkId, version);
        if (versionDirectory == null) {
            return null;
        }
        File result = new File(versionDirectory, MANIFEST);
        return isWithinSnapshotsRoot(result) ? result : null;
    }

    /** Path to the optional logical topology sidecar for one snapshot version. */
    public static File logicalIndexFile(String ownerUuid, int networkId, int version) {
        File versionDirectory = versionDir(ownerUuid, networkId, version);
        if (versionDirectory == null) {
            return null;
        }
        File result = new File(versionDirectory, LOGICAL_INDEX);
        return isWithinSnapshotsRoot(result) ? result : null;
    }

    public static File tileFile(String ownerUuid, int networkId, int version, String layer, int dim, int chunkX,
        int chunkZ) {
        if (!WorldMapPacketAuthorization.isValidLayer(layer)
            || !WorldMapPacketAuthorization.isValidChunk(dim, chunkX, chunkZ)) {
            return null;
        }
        File versionDirectory = versionDir(ownerUuid, networkId, version);
        if (versionDirectory == null) {
            return null;
        }
        String layerId = WorldMapTileLayer.isAe(layer) ? WorldMapTileLayer.AE : WorldMapTileLayer.TERRAIN;
        File result = new File(
            new File(new File(new File(versionDirectory, layerId), String.valueOf(dim)), String.valueOf(chunkX)),
            chunkZ + ".png");
        return isWithinSnapshotsRoot(result) ? result : null;
    }

    public static WorldMapSnapshotCurrentPointer loadCurrent(String ownerUuid, int networkId) {
        File file = currentPointerFile(ownerUuid, networkId);
        if (file == null || !file.isFile() || Files.isSymbolicLink(file.toPath())) {
            return null;
        }
        try {
            byte[] bytes = readFileLimited(file, MAX_CURRENT_BYTES);
            if (bytes == null) {
                return null;
            }
            WorldMapSnapshotCurrentPointer pointer = GSON
                .fromJson(new String(bytes, UTF8), WorldMapSnapshotCurrentPointer.class);
            if (!isValidCurrentPointer(pointer)) {
                return null;
            }
            WorldMapSnapshotManifest manifest = loadManifest(ownerUuid, networkId, pointer.version);
            return manifest != null && isValidManifest(manifest, ownerUuid, networkId, pointer.version, false) ? pointer
                : null;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to read snapshot current pointer {}", file, e);
            return null;
        }
    }

    public static int currentVersion(String ownerUuid, int networkId) {
        WorldMapSnapshotCurrentPointer ptr = loadCurrent(ownerUuid, networkId);
        return ptr != null ? ptr.version : 0;
    }

    public static boolean saveCurrent(String ownerUuid, int networkId, WorldMapSnapshotCurrentPointer pointer) {
        File target = currentPointerFile(ownerUuid, networkId);
        if (target == null || !isValidCurrentPointer(pointer)) {
            return false;
        }
        File dir = networkDir(ownerUuid, networkId);
        try {
            byte[] bytes = GSON.toJson(pointer)
                .getBytes(UTF8);
            if (bytes.length > MAX_CURRENT_BYTES) {
                return false;
            }
            if (dir == null || (!dir.exists() && !dir.mkdirs())
                || !dir.isDirectory()
                || !isWithinSnapshotsRoot(target)) {
                return false;
            }
            WorldMapSnapshotManifest manifest = loadManifest(ownerUuid, networkId, pointer.version);
            if (manifest == null || !isValidManifest(manifest, ownerUuid, networkId, pointer.version, false)) {
                return false;
            }
            writeAtomically(target, bytes);
            return true;
        } catch (IOException e) {
            AdvanceDataMonitor.LOG
                .error("[WebAE] Failed to write snapshot current pointer owner={} network={}", ownerUuid, networkId, e);
            return false;
        }
    }

    public static WorldMapSnapshotManifest loadManifest(String ownerUuid, int networkId, int version) {
        File file = manifestFile(ownerUuid, networkId, version);
        if (file == null || !file.isFile() || Files.isSymbolicLink(file.toPath())) {
            return null;
        }
        try {
            byte[] bytes = readFileLimited(file, MAX_MANIFEST_BYTES);
            if (bytes == null) {
                return null;
            }
            WorldMapSnapshotManifest manifest = GSON.fromJson(new String(bytes, UTF8), WorldMapSnapshotManifest.class);
            return isValidManifest(manifest, ownerUuid, networkId, version, true) ? manifest : null;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to read snapshot manifest {}", file, e);
            return null;
        }
    }

    public static WorldMapSnapshotManifest loadCurrentManifest(String ownerUuid, int networkId) {
        int version = currentVersion(ownerUuid, networkId);
        if (version <= 0) {
            return null;
        }
        return loadManifest(ownerUuid, networkId, version);
    }

    /**
     * Loads a logical sidecar. A missing, malformed, or invalid sidecar is
     * deliberately represented as an unavailable index rather than falling
     * back to the live/current topology.
     */
    public static WorldMapLogicalIndex loadLogicalIndex(String ownerUuid, int networkId, int version) {
        File file = logicalIndexFile(ownerUuid, networkId, version);
        if (file == null || !file.isFile() || Files.isSymbolicLink(file.toPath())) {
            return unavailableLogicalIndex(version);
        }
        try {
            byte[] bytes = readFileLimited(file, MAX_LOGICAL_INDEX_BYTES);
            if (bytes == null) {
                return unavailableLogicalIndex(version);
            }
            WorldMapLogicalIndex index = GSON.fromJson(new String(bytes, UTF8), WorldMapLogicalIndex.class);
            if (!isValidLogicalIndex(index, version)) {
                return unavailableLogicalIndex(version);
            }
            return WorldMapLogicalIndex.copyOf(index);
        } catch (Exception e) {
            // A sidecar is optional and may be malformed independently of the
            // terrain snapshot. Keep this pure fallback free of mod bootstrap
            // logging: touching AdvanceDataMonitor.LOG here initializes Forge
            // when the store is exercised from a standalone backend/test JVM.
            return unavailableLogicalIndex(version);
        }
    }

    /** Writes an optional logical sidecar; callers may ignore a false result. */
    public static boolean saveLogicalIndex(String ownerUuid, int networkId, int version, WorldMapLogicalIndex index) {
        String canonicalOwner = WorldMapPacketAuthorization.canonicalOwnerUuid(ownerUuid);
        if (canonicalOwner == null || !WorldMapPacketAuthorization.isValidNetworkId(networkId)
            || !WorldMapPacketAuthorization.isValidSnapshotVersion(version)
            || index == null
            || !isValidLogicalIndex(index, version)) {
            return false;
        }
        File dir = versionDir(canonicalOwner, networkId, version);
        File target = logicalIndexFile(canonicalOwner, networkId, version);
        if (dir == null || target == null) {
            return false;
        }
        WorldMapLogicalIndex payload = WorldMapLogicalIndex.copyOf(index);
        payload.version = version;
        try {
            byte[] bytes = GSON.toJson(payload)
                .getBytes(UTF8);
            if (bytes.length <= 0 || bytes.length > MAX_LOGICAL_INDEX_BYTES) {
                return false;
            }
            if ((!dir.exists() && !dir.mkdirs()) || !dir.isDirectory() || !isWithinSnapshotsRoot(target)) {
                return false;
            }
            writeAtomically(target, bytes);
            return true;
        } catch (Exception e) {
            // Optional sidecar persistence must remain usable from the pure
            // backend without initializing the Forge mod entry point.
            return false;
        }
    }

    /** Package-visible helper for diff code to inspect pointer validity without manifest guessing. */
    static WorldMapSnapshotCurrentPointer loadCurrentPointerUnchecked(String ownerUuid, int networkId) {
        File file = currentPointerFile(ownerUuid, networkId);
        if (file == null || !file.isFile() || Files.isSymbolicLink(file.toPath())) {
            return null;
        }
        try {
            byte[] bytes = readFileLimited(file, MAX_CURRENT_BYTES);
            if (bytes == null) {
                return null;
            }
            WorldMapSnapshotCurrentPointer pointer = GSON
                .fromJson(new String(bytes, UTF8), WorldMapSnapshotCurrentPointer.class);
            return isValidCurrentPointer(pointer) ? pointer : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Loads a retained manifest only when it has left the pending capture state. */
    static WorldMapSnapshotManifest loadFinalizedManifest(String ownerUuid, int networkId, int version) {
        File file = manifestFile(ownerUuid, networkId, version);
        if (file == null || !file.isFile() || Files.isSymbolicLink(file.toPath())) {
            return null;
        }
        try {
            byte[] bytes = readFileLimited(file, MAX_MANIFEST_BYTES);
            if (bytes == null) {
                return null;
            }
            WorldMapSnapshotManifest manifest = GSON.fromJson(new String(bytes, UTF8), WorldMapSnapshotManifest.class);
            return isValidManifest(manifest, ownerUuid, networkId, version, false) ? manifest : null;
        } catch (Exception e) {
            // Retained manifests are untrusted disk input. Diff callers need
            // an explicit unknown result, not Forge bootstrap as a side effect.
            return null;
        }
    }

    public static boolean saveManifest(WorldMapSnapshotManifest manifest) {
        if (manifest == null
            || !isValidManifest(manifest, manifest.ownerUuid, manifest.networkId, manifest.version, true)) {
            return false;
        }
        manifest.ownerUuid = WorldMapPacketAuthorization.canonicalOwnerUuid(manifest.ownerUuid);
        File dir = versionDir(manifest.ownerUuid, manifest.networkId, manifest.version);
        File target = manifestFile(manifest.ownerUuid, manifest.networkId, manifest.version);
        if (dir == null || target == null) {
            return false;
        }
        try {
            byte[] bytes = GSON.toJson(manifest)
                .getBytes(UTF8);
            if (bytes.length > MAX_MANIFEST_BYTES) {
                AdvanceDataMonitor.LOG.warn(
                    "[WebAE] Refusing oversized snapshot manifest owner={} network={} version={}",
                    manifest.ownerUuid,
                    manifest.networkId,
                    manifest.version);
                return false;
            }
            if ((!dir.exists() && !dir.mkdirs()) || !dir.isDirectory() || !isWithinSnapshotsRoot(target)) {
                return false;
            }
            writeAtomically(target, bytes);
            return true;
        } catch (IOException e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to write snapshot manifest v{}", manifest.version, e);
            return false;
        }
    }

    public static int allocateNextVersion(String ownerUuid, int networkId) {
        return Math.max(1, currentVersion(ownerUuid, networkId) + 1);
    }

    public static boolean writeTile(String ownerUuid, int networkId, int version, String layer, int dim, int chunkX,
        int chunkZ, byte[] png) {
        if (!WorldMapRenderSupport.isValidTilePng(png)) {
            return false;
        }
        File out = tileFile(ownerUuid, networkId, version, layer, dim, chunkX, chunkZ);
        if (out == null) {
            return false;
        }
        File parent = out.getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs())
            || !parent.isDirectory()
            || !isWithinSnapshotsRoot(out)) {
            return false;
        }
        File temp = null;
        FileOutputStream fos = null;
        try {
            temp = File.createTempFile("snapshot-tile-", ".tmp", parent);
            fos = new FileOutputStream(temp);
            fos.write(png);
            fos.flush();
            fos.close();
            fos = null;
            if (!isWithinSnapshotsRoot(out)) {
                return false;
            }
            moveAtomically(temp, out);
            temp = null;
            return true;
        } catch (IOException e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to write snapshot tile {}", out, e);
            return false;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ignored) {}
            }
            if (temp != null && temp.exists()) {
                temp.delete();
            }
        }
    }

    public static File getExistingTile(String ownerUuid, int networkId, int version, String layer, int dim, int chunkX,
        int chunkZ) {
        File file = tileFile(ownerUuid, networkId, version, layer, dim, chunkX, chunkZ);
        if (file == null || !file.isFile() || Files.isSymbolicLink(file.toPath())) {
            return null;
        }
        return WorldMapRenderSupport.isValidTilePng(file) ? file : null;
    }

    public static File getCurrentTile(String ownerUuid, int networkId, String layer, int dim, int chunkX, int chunkZ) {
        int version = currentVersion(ownerUuid, networkId);
        if (version <= 0) {
            return null;
        }
        return getExistingTile(ownerUuid, networkId, version, layer, dim, chunkX, chunkZ);
    }

    /**
     * Current snapshot tile, or previous version when the current chunk is not ready yet.
     */
    public static File getTileWithFallback(String ownerUuid, int networkId, String layer, int dim, int chunkX,
        int chunkZ) {
        WorldMapSnapshotCurrentPointer ptr = loadCurrent(ownerUuid, networkId);
        if (ptr == null || ptr.version <= 0) {
            return null;
        }
        File current = getExistingTile(ownerUuid, networkId, ptr.version, layer, dim, chunkX, chunkZ);
        if (current != null) {
            return current;
        }
        if (ptr.previousVersion > 0 && ptr.previousVersion != ptr.version) {
            return getExistingTile(ownerUuid, networkId, ptr.previousVersion, layer, dim, chunkX, chunkZ);
        }
        return null;
    }

    public static boolean finalizeSnapshot(WorldMapSnapshotManifest manifest) {
        if (manifest == null
            || !isValidManifest(manifest, manifest.ownerUuid, manifest.networkId, manifest.version, false)) {
            return false;
        }
        if (!saveManifest(manifest)) {
            return false;
        }
        WorldMapSnapshotCurrentPointer oldPtr = loadCurrent(manifest.ownerUuid, manifest.networkId);
        int oldVersion = oldPtr != null ? oldPtr.version : 0;
        WorldMapSnapshotCurrentPointer ptr = new WorldMapSnapshotCurrentPointer();
        ptr.version = manifest.version;
        if (oldVersion > 0 && oldVersion != manifest.version) {
            ptr.previousVersion = oldVersion;
        } else if (oldPtr != null && oldPtr.previousVersion > 0 && oldPtr.previousVersion != manifest.version) {
            ptr.previousVersion = oldPtr.previousVersion;
        } else {
            ptr.previousVersion = 0;
        }
        ptr.timestamp = manifest.timestamp;
        ptr.source = manifest.source;
        ptr.tilePx = manifest.tilePx;
        if (!saveCurrent(manifest.ownerUuid, manifest.networkId, ptr)) {
            return false;
        }
        pruneOldVersions(manifest.ownerUuid, manifest.networkId, ptr.version, ptr.previousVersion);
        return true;
    }

    /** Deletes an active job's unpublished version without touching current/previous snapshots. */
    public static boolean discardUnpublishedSnapshot(String ownerUuid, int networkId, int version) {
        File dir = versionDir(ownerUuid, networkId, version);
        if (dir == null || !dir.exists() || Files.isSymbolicLink(dir.toPath()) || !isWithinSnapshotsRoot(dir)) {
            return dir != null && !dir.exists();
        }
        WorldMapSnapshotCurrentPointer current = loadCurrent(ownerUuid, networkId);
        if (current != null && (current.version == version || current.previousVersion == version)) {
            return false;
        }
        deleteRecursive(dir);
        return !dir.exists();
    }

    /**
     * Keeps only {@code keepCurrent} and {@code keepPrevious} version directories under a network.
     */
    public static void pruneOldVersions(String ownerUuid, int networkId, int keepCurrent, int keepPrevious) {
        File dir = networkDir(ownerUuid, networkId);
        if (dir == null || !dir.isDirectory() || !isWithinSnapshotsRoot(dir)) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child == null || !child.isDirectory()) {
                continue;
            }
            if (Files.isSymbolicLink(child.toPath()) || !isWithinSnapshotsRoot(child)) {
                continue;
            }
            String name = child.getName();
            if (name == null || !name.startsWith("v")) {
                continue;
            }
            int version;
            try {
                version = Integer.parseInt(name.substring(1));
            } catch (NumberFormatException e) {
                continue;
            }
            if (version == keepCurrent) {
                continue;
            }
            if (keepPrevious > 0 && version == keepPrevious) {
                continue;
            }
            deleteRecursive(child);
        }
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists() || Files.isSymbolicLink(file.toPath()) || !isWithinSnapshotsRoot(file)) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        if (!file.delete()) {
            AdvanceDataMonitor.LOG.debug("[WebAE] Failed to delete snapshot path {}", file.getAbsolutePath());
        }
    }

    public static void registerTileInManifest(WorldMapSnapshotManifest manifest, String layer, int dim, int chunkX,
        int chunkZ, byte[] png) {
        if (manifest == null || !WorldMapPacketAuthorization.isValidLayer(layer)
            || !WorldMapPacketAuthorization.isValidChunk(dim, chunkX, chunkZ)
            || !WorldMapRenderSupport.isValidTilePng(png)) {
            return;
        }
        if (manifest.tiles == null) {
            manifest.tiles = new java.util.HashMap<String, WorldMapSnapshotManifest.TileEntry>();
        }
        if (manifest.tiles.size() >= MAX_TILES
            && !manifest.tiles.containsKey(WorldMapSnapshotManifest.tileKey(layer, dim, chunkX, chunkZ))) {
            return;
        }
        WorldMapSnapshotManifest.TileEntry entry = new WorldMapSnapshotManifest.TileEntry();
        entry.size = png.length;
        entry.sha256 = sha256Hex(png);
        manifest.tiles.put(WorldMapSnapshotManifest.tileKey(layer, dim, chunkX, chunkZ), entry);
    }

    public static void markMissingChunk(WorldMapSnapshotManifest manifest, int dim, int chunkX, int chunkZ) {
        if (manifest == null || !WorldMapPacketAuthorization.isValidChunk(dim, chunkX, chunkZ)) {
            return;
        }
        if (manifest.missingChunks == null) {
            manifest.missingChunks = new ArrayList<String>();
        }
        if (manifest.missingChunks.size() >= MAX_MISSING_CHUNKS) {
            return;
        }
        String key = WorldMapSnapshotManifest.chunkKey(dim, chunkX, chunkZ);
        if (!manifest.missingChunks.contains(key)) {
            manifest.missingChunks.add(key);
        }
    }

    public static List<String> buildChunkList(WorldMapMetaDto meta) {
        List<String> chunks = new ArrayList<String>();
        if (meta == null || meta.dimensions == null || meta.dimensions.size() > MAX_DIMENSIONS) {
            return chunks;
        }
        for (WorldMapMetaDto.DimensionInfo dimInfo : meta.dimensions) {
            if (dimInfo == null || Math.abs((long) dimInfo.dim) > WorldMapPacketAuthorization.MAX_DIMENSION) {
                return new ArrayList<String>();
            }
            if (dimInfo.allowedChunks != null && !dimInfo.allowedChunks.isEmpty()) {
                if (dimInfo.allowedChunks.size() > MAX_TOTAL_CHUNKS - chunks.size()) {
                    return new ArrayList<String>();
                }
                for (String pair : dimInfo.allowedChunks) {
                    int[] coords = parseChunkPair(pair);
                    if (coords == null) {
                        return new ArrayList<String>();
                    }
                    chunks.add(dimInfo.dim + ":" + coords[0] + "," + coords[1]);
                }
                continue;
            }
            if (dimInfo.minChunkX <= dimInfo.maxChunkX && dimInfo.minChunkZ <= dimInfo.maxChunkZ
                && WorldMapPacketAuthorization.isValidChunk(dimInfo.dim, dimInfo.minChunkX, dimInfo.minChunkZ)
                && WorldMapPacketAuthorization.isValidChunk(dimInfo.dim, dimInfo.maxChunkX, dimInfo.maxChunkZ)) {
                long width = (long) dimInfo.maxChunkX - dimInfo.minChunkX + 1L;
                long height = (long) dimInfo.maxChunkZ - dimInfo.minChunkZ + 1L;
                long count = width * height;
                if (count > MAX_TOTAL_CHUNKS - chunks.size()) {
                    return new ArrayList<String>();
                }
                for (int cx = dimInfo.minChunkX; cx <= dimInfo.maxChunkX; cx++) {
                    for (int cz = dimInfo.minChunkZ; cz <= dimInfo.maxChunkZ; cz++) {
                        chunks.add(dimInfo.dim + ":" + cx + "," + cz);
                    }
                }
            }
        }
        return chunks;
    }

    private static int[] parseChunkPair(String pair) {
        if (pair == null || pair.length() > MAX_MANIFEST_KEY_BYTES) {
            return null;
        }
        String[] parts = pair.split(",", -1);
        if (parts.length != 2) {
            return null;
        }
        try {
            int chunkX = Integer.parseInt(parts[0].trim());
            int chunkZ = Integer.parseInt(parts[1].trim());
            return WorldMapPacketAuthorization.isValidChunk(0, chunkX, chunkZ) ? new int[] { chunkX, chunkZ } : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isValidCurrentPointer(WorldMapSnapshotCurrentPointer pointer) {
        if (pointer == null || !WorldMapPacketAuthorization.isValidSnapshotVersion(pointer.version)) {
            return false;
        }
        if (pointer.previousVersion < 0 || pointer.previousVersion > WorldMapPacketAuthorization.MAX_SNAPSHOT_VERSION
            || pointer.previousVersion == pointer.version) {
            return false;
        }
        if (pointer.tilePx < 0 || pointer.tilePx > WorldMapPacketAuthorization.MAX_TILE_PX) {
            return false;
        }
        return pointer.source == null || pointer.source.isEmpty()
            || WorldMapPacketAuthorization.isValidSource(pointer.source);
    }

    private static boolean isValidManifest(WorldMapSnapshotManifest manifest, String ownerUuid, int networkId,
        int version, boolean allowPendingSource) {
        if (manifest == null || !WorldMapPacketAuthorization.isValidOwnerUuid(ownerUuid)
            || !WorldMapPacketAuthorization.isValidNetworkId(networkId)
            || !WorldMapPacketAuthorization.isValidSnapshotVersion(version)
            || manifest.version != version
            || manifest.networkId != networkId
            || !WorldMapPacketAuthorization.isValidTilePx(manifest.tilePx)) {
            return false;
        }
        String canonicalExpected = WorldMapPacketAuthorization.canonicalOwnerUuid(ownerUuid);
        String canonicalActual = WorldMapPacketAuthorization.canonicalOwnerUuid(manifest.ownerUuid);
        if (canonicalExpected == null || !canonicalExpected.equals(canonicalActual)) {
            return false;
        }
        boolean sourceValid = allowPendingSource ? WorldMapPacketAuthorization.isValidManifestSource(manifest.source)
            : WorldMapPacketAuthorization.isValidSource(manifest.source);
        if (!sourceValid || manifest.layers == null || manifest.layers.size() > 2) {
            return false;
        }
        Set<String> layerSet = new HashSet<String>();
        for (String layer : manifest.layers) {
            if (!WorldMapPacketAuthorization.isValidLayer(layer)) {
                return false;
            }
            String normalized = WorldMapTileLayer.isAe(layer) ? WorldMapTileLayer.AE : WorldMapTileLayer.TERRAIN;
            if (!layerSet.add(normalized)) {
                return false;
            }
        }
        if (manifest.sourceStats == null || manifest.sourceStats.size() > MAX_SOURCE_STATS) {
            return false;
        }
        for (Map.Entry<String, Integer> stat : manifest.sourceStats.entrySet()) {
            if (stat.getKey() == null || stat.getKey()
                .length() > MAX_MANIFEST_KEY_BYTES
                || stat.getValue() == null
                || stat.getValue() < 0
                || stat.getValue() > MAX_TOTAL_CHUNKS) {
                return false;
            }
        }
        if (manifest.dimensions == null || manifest.dimensions.size() > MAX_DIMENSIONS) {
            return false;
        }
        int totalChunks = 0;
        for (WorldMapSnapshotManifest.DimensionEntry dimension : manifest.dimensions) {
            if (dimension == null || Math.abs((long) dimension.dim) > WorldMapPacketAuthorization.MAX_DIMENSION
                || dimension.chunks == null
                || dimension.chunks.size() > MAX_TOTAL_CHUNKS - totalChunks) {
                return false;
            }
            for (String pair : dimension.chunks) {
                int[] coords = parseChunkPair(pair);
                if (coords == null || !WorldMapPacketAuthorization.isValidChunk(dimension.dim, coords[0], coords[1])) {
                    return false;
                }
                totalChunks++;
            }
        }
        if (manifest.tiles == null || manifest.tiles.size() > MAX_TILES) {
            return false;
        }
        for (Map.Entry<String, WorldMapSnapshotManifest.TileEntry> tile : manifest.tiles.entrySet()) {
            if (tile.getKey() == null || tile.getKey()
                .length() > MAX_MANIFEST_KEY_BYTES
                || !isValidTileKey(tile.getKey())
                || !isValidTileEntry(tile.getValue())) {
                return false;
            }
        }
        if (manifest.missingChunks == null || manifest.missingChunks.size() > MAX_MISSING_CHUNKS) {
            return false;
        }
        for (String key : manifest.missingChunks) {
            if (!isValidChunkKey(key)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidTileKey(String key) {
        String[] parts = key.split(":", -1);
        if (parts.length != 4 || !WorldMapPacketAuthorization.isValidLayer(parts[0])) {
            return false;
        }
        try {
            int dim = Integer.parseInt(parts[1]);
            int chunkX = Integer.parseInt(parts[2]);
            int chunkZ = Integer.parseInt(parts[3]);
            return WorldMapPacketAuthorization.isValidChunk(dim, chunkX, chunkZ);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isValidTileEntry(WorldMapSnapshotManifest.TileEntry entry) {
        if (entry == null || entry.size < 0
            || entry.size > WorldMapRenderSupport.MAX_VALID_TILE_BYTES
            || entry.sha256 == null
            || entry.sha256.length() > 64) {
            return false;
        }
        if (entry.source != null && !entry.source.isEmpty()
            && !WorldMapPacketAuthorization.isValidManifestSource(entry.source)) {
            return false;
        }
        if (!entry.sha256.isEmpty() && entry.sha256.length() != 64) {
            return false;
        }
        for (int i = 0; i < entry.sha256.length(); i++) {
            char c = entry.sha256.charAt(i);
            if (!(c >= '0' && c <= '9') && !(c >= 'a' && c <= 'f') && !(c >= 'A' && c <= 'F')) {
                return false;
            }
        }
        return true;
    }

    private static WorldMapLogicalIndex unavailableLogicalIndex(int version) {
        WorldMapLogicalIndex unavailable = new WorldMapLogicalIndex();
        unavailable.version = version;
        unavailable.logicalAvailable = false;
        return unavailable;
    }

    /** Validates the sidecar independently from live topology and terrain manifest data. */
    private static boolean isValidLogicalIndex(WorldMapLogicalIndex index, int version) {
        if (index == null || !WorldMapPacketAuthorization.isValidSnapshotVersion(version)
            || index.version != version
            || index.timestamp < 0L) {
            return false;
        }
        if (!index.logicalAvailable) {
            return (index.markers == null || index.markers.isEmpty())
                && (index.aePlacements == null || index.aePlacements.isEmpty());
        }
        if (index.markers == null || index.markers.size() > WorldMapLogicalIndex.MAX_MARKERS
            || index.aePlacements == null
            || index.aePlacements.size() > WorldMapLogicalIndex.MAX_AE_PLACEMENTS) {
            return false;
        }
        for (WorldMapMarkerDto marker : index.markers) {
            if (!isValidMarker(marker)) {
                return false;
            }
        }
        for (WorldMapAePlacementRecord placement : index.aePlacements) {
            if (!isValidPlacement(placement)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidMarker(WorldMapMarkerDto marker) {
        if (marker == null || !isValidCoordinate(marker.dim, marker.x, marker.y, marker.z)) {
            return false;
        }
        String expectedId = WorldMapMarkerBuilder.markerId(marker.dim, marker.x, marker.y, marker.z);
        if (marker.id == null || !expectedId.equals(marker.id) || marker.id.length() > 128) {
            return false;
        }
        return isValidString(marker.nodeId, WorldMapLogicalIndex.MAX_STRING_LENGTH)
            && isValidString(marker.type, WorldMapLogicalIndex.MAX_STRING_LENGTH)
            && isValidString(marker.subtype, WorldMapLogicalIndex.MAX_STRING_LENGTH)
            && isValidString(marker.displayName, WorldMapLogicalIndex.MAX_STRING_LENGTH)
            && isValidString(marker.iconItemId, WorldMapLogicalIndex.MAX_STRING_LENGTH);
    }

    private static boolean isValidPlacement(WorldMapAePlacementRecord placement) {
        if (placement == null || !isValidCoordinate(placement.dim, placement.x, placement.y, placement.z)) {
            return false;
        }
        return isValidString(placement.kind, WorldMapLogicalIndex.MAX_STRING_LENGTH)
            && isValidString(placement.className, WorldMapLogicalIndex.MAX_STRING_LENGTH)
            && isValidString(placement.iconItemId, WorldMapLogicalIndex.MAX_STRING_LENGTH)
            && isValidString(placement.displayName, WorldMapLogicalIndex.MAX_STRING_LENGTH);
    }

    private static boolean isValidCoordinate(int dim, int x, int y, int z) {
        return Math.abs((long) dim) <= WorldMapPacketAuthorization.MAX_DIMENSION
            && Math.abs((long) x) <= WorldMapLogicalIndex.MAX_BLOCK_COORDINATE
            && Math.abs((long) y) <= WorldMapLogicalIndex.MAX_BLOCK_COORDINATE
            && Math.abs((long) z) <= WorldMapLogicalIndex.MAX_BLOCK_COORDINATE;
    }

    private static boolean isValidString(String value, int maxLength) {
        return value == null || value.length() <= maxLength;
    }

    private static boolean isValidChunkKey(String key) {
        if (key == null || key.length() > MAX_MANIFEST_KEY_BYTES) {
            return false;
        }
        String[] parts = key.split(":", -1);
        if (parts.length != 3) {
            return false;
        }
        try {
            return WorldMapPacketAuthorization
                .isValidChunk(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static byte[] readFileLimited(File file, int maxBytes) throws IOException {
        if (file == null || maxBytes <= 0
            || !isWithinSnapshotsRoot(file)
            || Files.isSymbolicLink(file.toPath())
            || file.length() <= 0
            || file.length() > maxBytes) {
            return null;
        }
        int expected = (int) file.length();
        byte[] bytes = new byte[expected];
        FileInputStream input = null;
        try {
            input = new FileInputStream(file);
            int offset = 0;
            while (offset < expected) {
                int read = input.read(bytes, offset, expected - offset);
                if (read < 0) {
                    return null;
                }
                if (read == 0) {
                    continue;
                }
                offset += read;
            }
            return bytes;
        } finally {
            closeQuietly(input);
        }
    }

    private static void writeAtomically(File target, byte[] bytes) throws IOException {
        if (target == null || bytes == null || !isWithinSnapshotsRoot(target)) {
            throw new IOException("Snapshot target path is outside the snapshot root");
        }
        File parent = target.getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs())
            || !parent.isDirectory()
            || !isWithinSnapshotsRoot(parent)) {
            throw new IOException("Snapshot target directory unavailable");
        }
        File temp = File.createTempFile(target.getName(), ".tmp", parent);
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(temp);
            output.write(bytes);
            output.flush();
            output.close();
            output = null;
            moveAtomically(temp, target);
            temp = null;
        } finally {
            if (output != null) {
                closeQuietly(output);
            }
            if (temp != null && temp.exists()) {
                temp.delete();
            }
        }
    }

    private static void moveAtomically(File source, File target) throws IOException {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Rejects lexical escapes and symlinks at or below the snapshot root before
     * performing the canonical containment check. Symlinks above the configured
     * snapshot root remain supported for instance-directory compatibility.
     */
    private static boolean isWithinSnapshotsRoot(File file) {
        return isWithinSnapshotsRoot(snapshotsRoot(), file);
    }

    static boolean isWithinSnapshotsRoot(File rootDirectory, File file) {
        if (rootDirectory == null || file == null) {
            return false;
        }
        try {
            Path lexicalRoot = rootDirectory.getAbsoluteFile()
                .toPath()
                .normalize();
            Path lexicalCandidate = file.getAbsoluteFile()
                .toPath()
                .normalize();
            if (!lexicalCandidate.equals(lexicalRoot) && !lexicalCandidate.startsWith(lexicalRoot)) {
                return false;
            }

            Path current = lexicalRoot;
            if (Files.isSymbolicLink(current)) {
                return false;
            }
            Path relative = lexicalRoot.relativize(lexicalCandidate);
            for (Path part : relative) {
                current = current.resolve(part);
                if (Files.isSymbolicLink(current)) {
                    return false;
                }
            }

            Path root = rootDirectory.getCanonicalFile()
                .toPath();
            Path candidate = file.getCanonicalFile()
                .toPath();
            return candidate.equals(root) || candidate.startsWith(root);
        } catch (IOException | SecurityException e) {
            return false;
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignored) {}
        }
    }
}
