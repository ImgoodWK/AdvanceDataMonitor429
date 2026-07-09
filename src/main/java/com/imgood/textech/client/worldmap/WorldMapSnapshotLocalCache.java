package com.imgood.textech.client.worldmap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.imgood.textech.TeXTechDataDir;

/**
 * Local on-disk cache for downloaded/uploaded world map snapshots on the MC client.
 */
public final class WorldMapSnapshotLocalCache {

    private WorldMapSnapshotLocalCache() {}

    public static File cacheRoot() {
        return TeXTechDataDir.webAeDir("map-cache");
    }

    public static File versionDir(String ownerUuid, int networkId, int version) {
        String safeOwner = ownerUuid != null ? ownerUuid.replaceAll("[^a-zA-Z0-9\\-]", "_") : "unknown";
        return new File(new File(new File(cacheRoot(), safeOwner), String.valueOf(networkId)), "v" + version);
    }

    public static File tileFile(String ownerUuid, int networkId, int version, String layer, int dim, int chunkX,
        int chunkZ) {
        return new File(
            new File(
                new File(new File(versionDir(ownerUuid, networkId, version), layer), String.valueOf(dim)),
                String.valueOf(chunkX)),
            chunkZ + ".png");
    }

    public static File currentPointerFile(String ownerUuid, int networkId) {
        String safeOwner = ownerUuid != null ? ownerUuid.replaceAll("[^a-zA-Z0-9\\-]", "_") : "unknown";
        return new File(new File(new File(cacheRoot(), safeOwner), String.valueOf(networkId)), "current.json");
    }

    public static void writeTile(String ownerUuid, int networkId, int version, String layer, int dim, int chunkX,
        int chunkZ, byte[] png) {
        if (png == null || png.length == 0) {
            return;
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
        } catch (IOException ignored) {
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

    public static int readLocalVersion(String ownerUuid, int networkId) {
        File file = currentPointerFile(ownerUuid, networkId);
        if (!file.isFile()) {
            return 0;
        }
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();
            String json = new String(data, "UTF-8");
            int idx = json.indexOf("\"version\"");
            if (idx < 0) {
                return 0;
            }
            int colon = json.indexOf(':', idx);
            int end = json.indexOf(',', colon);
            if (end < 0) {
                end = json.indexOf('}', colon);
            }
            return Integer.parseInt(json.substring(colon + 1, end).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    public static void writeLocalVersion(String ownerUuid, int networkId, int version) {
        File file = currentPointerFile(ownerUuid, networkId);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            fos.write(("{\"version\":" + version + "}").getBytes("UTF-8"));
        } catch (IOException ignored) {
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ignored) {}
            }
        }
    }
}
