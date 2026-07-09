package com.imgood.textech.webae.worldmap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

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
    private static final String CURRENT = "current.json";

    private WorldMapSnapshotStore() {}

    public static File snapshotsRoot() {
        return TeXTechDataDir.webAeDir("map-snapshots");
    }

    public static File networkDir(String ownerUuid, int networkId) {
        return new File(new File(snapshotsRoot(), sanitize(ownerUuid)), String.valueOf(networkId));
    }

    public static File versionDir(String ownerUuid, int networkId, int version) {
        return new File(networkDir(ownerUuid, networkId), "v" + version);
    }

    public static File currentPointerFile(String ownerUuid, int networkId) {
        return new File(networkDir(ownerUuid, networkId), CURRENT);
    }

    public static File manifestFile(String ownerUuid, int networkId, int version) {
        return new File(versionDir(ownerUuid, networkId, version), MANIFEST);
    }

    public static File tileFile(String ownerUuid, int networkId, int version, String layer, int dim, int chunkX,
        int chunkZ) {
        String layerId = WorldMapTileLayer.normalize(layer);
        return new File(
            new File(
                new File(new File(versionDir(ownerUuid, networkId, version), layerId), String.valueOf(dim)),
                String.valueOf(chunkX)),
            chunkZ + ".png");
    }

    public static WorldMapSnapshotCurrentPointer loadCurrent(String ownerUuid, int networkId) {
        File file = currentPointerFile(ownerUuid, networkId);
        if (!file.isFile()) {
            return null;
        }
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            return GSON.fromJson(reader, WorldMapSnapshotCurrentPointer.class);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to read snapshot current pointer {}", file, e);
            return null;
        } finally {
            closeQuietly(reader);
        }
    }

    public static int currentVersion(String ownerUuid, int networkId) {
        WorldMapSnapshotCurrentPointer ptr = loadCurrent(ownerUuid, networkId);
        return ptr != null ? ptr.version : 0;
    }

    public static void saveCurrent(String ownerUuid, int networkId, WorldMapSnapshotCurrentPointer pointer) {
        if (pointer == null) {
            return;
        }
        File dir = networkDir(ownerUuid, networkId);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(currentPointerFile(ownerUuid, networkId)), "UTF-8");
            GSON.toJson(pointer, writer);
        } catch (IOException e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to write snapshot current pointer owner={} network={}",
                ownerUuid, networkId, e);
        } finally {
            closeQuietly(writer);
        }
    }

    public static WorldMapSnapshotManifest loadManifest(String ownerUuid, int networkId, int version) {
        File file = manifestFile(ownerUuid, networkId, version);
        if (!file.isFile()) {
            return null;
        }
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            return GSON.fromJson(reader, WorldMapSnapshotManifest.class);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to read snapshot manifest {}", file, e);
            return null;
        } finally {
            closeQuietly(reader);
        }
    }

    public static WorldMapSnapshotManifest loadCurrentManifest(String ownerUuid, int networkId) {
        int version = currentVersion(ownerUuid, networkId);
        if (version <= 0) {
            return null;
        }
        return loadManifest(ownerUuid, networkId, version);
    }

    public static void saveManifest(WorldMapSnapshotManifest manifest) {
        if (manifest == null || manifest.ownerUuid == null || manifest.ownerUuid.isEmpty()) {
            return;
        }
        File dir = versionDir(manifest.ownerUuid, manifest.networkId, manifest.version);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(
                new FileOutputStream(manifestFile(manifest.ownerUuid, manifest.networkId, manifest.version)),
                "UTF-8");
            GSON.toJson(manifest, writer);
        } catch (IOException e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to write snapshot manifest v{}", manifest.version, e);
        } finally {
            closeQuietly(writer);
        }
    }

    public static int allocateNextVersion(String ownerUuid, int networkId) {
        return Math.max(1, currentVersion(ownerUuid, networkId) + 1);
    }

    public static boolean writeTile(String ownerUuid, int networkId, int version, String layer, int dim, int chunkX,
        int chunkZ, byte[] png) {
        if (png == null || png.length == 0) {
            return false;
        }
        File out = tileFile(ownerUuid, networkId, version, layer, dim, chunkX, chunkZ);
        File parent = out.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(out);
            fos.write(png);
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
        }
    }

    public static File getExistingTile(String ownerUuid, int networkId, int version, String layer, int dim,
        int chunkX, int chunkZ) {
        File file = tileFile(ownerUuid, networkId, version, layer, dim, chunkX, chunkZ);
        return file.isFile() && file.length() > 0 ? file : null;
    }

    public static File getCurrentTile(String ownerUuid, int networkId, String layer, int dim, int chunkX,
        int chunkZ) {
        int version = currentVersion(ownerUuid, networkId);
        if (version <= 0) {
            return null;
        }
        return getExistingTile(ownerUuid, networkId, version, layer, dim, chunkX, chunkZ);
    }

    public static void finalizeSnapshot(WorldMapSnapshotManifest manifest) {
        if (manifest == null) {
            return;
        }
        saveManifest(manifest);
        WorldMapSnapshotCurrentPointer ptr = new WorldMapSnapshotCurrentPointer();
        ptr.version = manifest.version;
        ptr.timestamp = manifest.timestamp;
        ptr.source = manifest.source;
        ptr.tilePx = manifest.tilePx;
        saveCurrent(manifest.ownerUuid, manifest.networkId, ptr);
    }

    public static void registerTileInManifest(WorldMapSnapshotManifest manifest, String layer, int dim, int chunkX,
        int chunkZ, byte[] png) {
        if (manifest == null || png == null) {
            return;
        }
        if (manifest.tiles == null) {
            manifest.tiles = new java.util.HashMap<String, WorldMapSnapshotManifest.TileEntry>();
        }
        WorldMapSnapshotManifest.TileEntry entry = new WorldMapSnapshotManifest.TileEntry();
        entry.size = png.length;
        entry.sha256 = sha256Hex(png);
        manifest.tiles.put(WorldMapSnapshotManifest.tileKey(layer, dim, chunkX, chunkZ), entry);
    }

    public static void markMissingChunk(WorldMapSnapshotManifest manifest, int dim, int chunkX, int chunkZ) {
        if (manifest == null) {
            return;
        }
        if (manifest.missingChunks == null) {
            manifest.missingChunks = new ArrayList<String>();
        }
        String key = WorldMapSnapshotManifest.chunkKey(dim, chunkX, chunkZ);
        if (!manifest.missingChunks.contains(key)) {
            manifest.missingChunks.add(key);
        }
    }

    public static List<String> buildChunkList(WorldMapMetaDto meta) {
        List<String> chunks = new ArrayList<String>();
        if (meta == null || meta.dimensions == null) {
            return chunks;
        }
        for (WorldMapMetaDto.DimensionInfo dimInfo : meta.dimensions) {
            if (dimInfo == null) {
                continue;
            }
            if (dimInfo.allowedChunks != null && !dimInfo.allowedChunks.isEmpty()) {
                for (String pair : dimInfo.allowedChunks) {
                    if (pair != null && !pair.isEmpty()) {
                        chunks.add(dimInfo.dim + ":" + pair);
                    }
                }
                continue;
            }
            if (dimInfo.minChunkX <= dimInfo.maxChunkX && dimInfo.minChunkZ <= dimInfo.maxChunkZ) {
                for (int cx = dimInfo.minChunkX; cx <= dimInfo.maxChunkX; cx++) {
                    for (int cz = dimInfo.minChunkZ; cz <= dimInfo.maxChunkZ; cz++) {
                        chunks.add(dimInfo.dim + ":" + cx + "," + cz);
                    }
                }
            }
        }
        return chunks;
    }

    private static String sanitize(String ownerUuid) {
        if (ownerUuid == null) {
            return "unknown";
        }
        return ownerUuid.replaceAll("[^a-zA-Z0-9\\-]", "_");
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
