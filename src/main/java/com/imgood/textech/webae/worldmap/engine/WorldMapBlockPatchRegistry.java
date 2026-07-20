package com.imgood.textech.webae.worldmap.engine;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.worldmap.WorldMapBlockColorResolver;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * JSON + built-in block patch models for stairs, slabs, GregTech and other mod blocks.
 * Patches replace full-cube ray hits with local AABB geometry.
 */
public final class WorldMapBlockPatchRegistry {

    public static final class PatchHit {

        public final WorldMapBlockColorResolver.BlockFace face;
        public final int texU;
        public final int texV;
        public final double distance;

        PatchHit(WorldMapBlockColorResolver.BlockFace face, int texU, int texV, double distance) {
            this.face = face;
            this.texU = texU;
            this.texV = texV;
            this.distance = distance;
        }
    }

    private static final String PATCH_RESOURCE_PREFIX = "assets/textech/worldmap/patches/";
    private static final String[] PATCH_RESOURCES = { "vanilla_shapes.json", "gregtech_machines.json",
        "gregtech_casings.json", "gregtech_structural.json", "gregtech_pipes.json" };

    private static volatile boolean loaded;
    private static volatile Map<String, List<WorldMapBlockPatch>> jsonByBlockKey = Collections.emptyMap();
    private static volatile Map<String, List<WorldMapBlockPatch>> jsonByClassContains = Collections.emptyMap();
    private static volatile Map<String, List<WorldMapBlockPatch>> jsonByRegistryPrefix = Collections.emptyMap();

    private WorldMapBlockPatchRegistry() {}

    public static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (WorldMapBlockPatchRegistry.class) {
            if (loaded) {
                return;
            }
            loadJsonPatches();
            loaded = true;
        }
    }

    public static void reloadForDebug() {
        synchronized (WorldMapBlockPatchRegistry.class) {
            loaded = false;
            loadJsonPatches();
            loaded = true;
        }
    }

    public static boolean hasPatchModel(Block block) {
        if (!Config.webWorldMapBlockPatchesEnabled || block == null) {
            return false;
        }
        ensureLoaded();
        return resolvePatches(null, 0, 0, 0, block, 0) != null;
    }

    public static int loadedJsonEntryCount() {
        ensureLoaded();
        return jsonByBlockKey.size() + jsonByClassContains.size() + jsonByRegistryPrefix.size();
    }

    public static PatchHit intersectInVoxel(WorldMapChunkContext ctx, int vx, int vy, int vz, Block block, int meta,
        double ox, double oy, double oz, double dx, double dy, double dz, double tEnter, double tExit) {
        if (!Config.webWorldMapBlockPatchesEnabled || block == null) {
            return null;
        }
        List<WorldMapBlockPatch> patches = resolvePatches(ctx, vx, vy, vz, block, meta);
        if (patches == null || patches.isEmpty()) {
            return null;
        }
        if (tExit <= tEnter) {
            return null;
        }

        double bestT = Double.POSITIVE_INFINITY;
        WorldMapBlockPatch bestPatch = null;
        WorldMapBlockColorResolver.BlockFace bestFace = null;
        double bestHx = 0;
        double bestHy = 0;
        double bestHz = 0;

        for (WorldMapBlockPatch patch : patches) {
            double wMinX = vx + patch.minX;
            double wMinY = vy + patch.minY;
            double wMinZ = vz + patch.minZ;
            double wMaxX = vx + patch.maxX;
            double wMaxY = vy + patch.maxY;
            double wMaxZ = vz + patch.maxZ;
            double[] tNearFar = new double[2];
            if (!rayAabb(ox, oy, oz, dx, dy, dz, wMinX, wMinY, wMinZ, wMaxX, wMaxY, wMaxZ, tNearFar)) {
                continue;
            }
            double tHit = tNearFar[0];
            if (tHit < tEnter || tHit > tExit || tHit >= bestT) {
                continue;
            }
            double hx = ox + dx * tHit;
            double hy = oy + dy * tHit;
            double hz = oz + dz * tHit;
            WorldMapBlockColorResolver.BlockFace face = hitFace(patch, hx - vx, hy - vy, hz - vz, dx, dy, dz);
            bestT = tHit;
            bestPatch = patch;
            bestFace = face;
            bestHx = hx;
            bestHy = hy;
            bestHz = hz;
        }

        if (bestPatch == null || bestFace == null) {
            return null;
        }
        int[] uv = faceUv(bestFace, bestHx, bestHy, bestHz);
        return new PatchHit(bestFace, uv[0], uv[1], bestT);
    }

    private static List<WorldMapBlockPatch> resolvePatches(WorldMapChunkContext ctx, int wx, int wy, int wz,
        Block block, int meta) {
        ensureLoaded();
        // AE2 cable blocks: use cable-shaped patches
        if (ctx != null) {
            List<WorldMapBlockPatch> ae2 = WorldMapAe2PatchResolver.resolve(ctx, wx, wy, wz, block, meta);
            if (ae2 != null && !ae2.isEmpty()) {
                return ae2;
            }
        }
        if (ctx != null && isGregTechBlock(block)) {
            List<WorldMapBlockPatch> gt = WorldMapGtPatchResolver.resolve(ctx, wx, wy, wz, block);
            if (gt != null && !gt.isEmpty()) {
                return gt;
            }
        }
        String blockKey = blockRegistryKey(block);
        if (blockKey != null) {
            List<WorldMapBlockPatch> exact = jsonByBlockKey.get(blockKey);
            if (exact != null) {
                return exact;
            }
            for (Map.Entry<String, List<WorldMapBlockPatch>> entry : jsonByRegistryPrefix.entrySet()) {
                if (blockKey.startsWith(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        String className = block.getClass()
            .getName();
        for (Map.Entry<String, List<WorldMapBlockPatch>> entry : jsonByClassContains.entrySet()) {
            if (className.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        if (className.contains("BlockStairs")) {
            return stairPatches(meta);
        }
        if (className.contains("BlockSlab") && !className.contains("Double")) {
            return slabPatches(meta);
        }
        return null;
    }

    private static boolean isGregTechBlock(Block block) {
        String key = blockRegistryKey(block);
        if (key != null && key.startsWith("gregtech:")) {
            return true;
        }
        return block.getClass()
            .getName()
            .startsWith("gregtech.");
    }

    private static List<WorldMapBlockPatch> stairPatches(int meta) {
        int facing = meta & 3;
        boolean upsideDown = (meta & 4) != 0;
        List<WorldMapBlockPatch> out = new ArrayList<WorldMapBlockPatch>(2);
        if (facing == 2) {
            out.add(box(0, 0, 0, 1, 0.5, 1, WorldMapBlockColorResolver.BlockFace.TOP));
            out.add(box(0, 0.5, 0.5, 1, 1, 1, WorldMapBlockColorResolver.BlockFace.TOP));
        } else if (facing == 3) {
            out.add(box(0, 0, 0, 1, 0.5, 1, WorldMapBlockColorResolver.BlockFace.TOP));
            out.add(box(0, 0.5, 0, 1, 1, 0.5, WorldMapBlockColorResolver.BlockFace.TOP));
        } else if (facing == 1) {
            out.add(box(0, 0, 0, 1, 0.5, 1, WorldMapBlockColorResolver.BlockFace.TOP));
            out.add(box(0, 0.5, 0, 0.5, 1, 1, WorldMapBlockColorResolver.BlockFace.TOP));
        } else {
            out.add(box(0, 0, 0, 1, 0.5, 1, WorldMapBlockColorResolver.BlockFace.TOP));
            out.add(box(0.5, 0.5, 0, 1, 1, 1, WorldMapBlockColorResolver.BlockFace.TOP));
        }
        if (upsideDown) {
            return flipPatchesY(out);
        }
        return out;
    }

    private static List<WorldMapBlockPatch> slabPatches(int meta) {
        boolean top = (meta & 8) != 0;
        if (top) {
            return Collections.singletonList(box(0, 0.5, 0, 1, 1, 1, WorldMapBlockColorResolver.BlockFace.TOP));
        }
        return Collections.singletonList(box(0, 0, 0, 1, 0.5, 1, WorldMapBlockColorResolver.BlockFace.TOP));
    }

    private static List<WorldMapBlockPatch> flipPatchesY(List<WorldMapBlockPatch> src) {
        List<WorldMapBlockPatch> out = new ArrayList<WorldMapBlockPatch>(src.size());
        for (WorldMapBlockPatch p : src) {
            out.add(WorldMapBlockPatch.box(p.minX, 1.0 - p.maxY, p.minZ, p.maxX, 1.0 - p.minY, p.maxZ, p.textureFace));
        }
        return out;
    }

    private static WorldMapBlockPatch box(double x0, double y0, double z0, double x1, double y1, double z1,
        WorldMapBlockColorResolver.BlockFace face) {
        return WorldMapBlockPatch.box(x0, y0, z0, x1, y1, z1, face);
    }

    private static WorldMapBlockColorResolver.BlockFace hitFace(WorldMapBlockPatch patch, double lx, double ly,
        double lz, double dx, double dy, double dz) {
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);
        double eps = 1.0e-4;
        if (ax >= ay && ax >= az) {
            if (Math.abs(lx - patch.minX) < eps) {
                return WorldMapBlockColorResolver.BlockFace.EAST;
            }
            if (Math.abs(lx - patch.maxX) < eps) {
                return WorldMapBlockColorResolver.BlockFace.WEST;
            }
        }
        if (ay >= ax && ay >= az) {
            if (Math.abs(ly - patch.minY) < eps) {
                return WorldMapBlockColorResolver.BlockFace.BOTTOM;
            }
            if (Math.abs(ly - patch.maxY) < eps) {
                return WorldMapBlockColorResolver.BlockFace.TOP;
            }
        }
        if (Math.abs(lz - patch.minZ) < eps) {
            return WorldMapBlockColorResolver.BlockFace.NORTH;
        }
        if (Math.abs(lz - patch.maxZ) < eps) {
            return WorldMapBlockColorResolver.BlockFace.SOUTH;
        }
        return patch.textureFace;
    }

    private static int[] faceUv(WorldMapBlockColorResolver.BlockFace face, double hitX, double hitY, double hitZ) {
        double fu = frac(hitX);
        double fv = frac(hitY);
        double fw = frac(hitZ);
        int u = 8;
        int v = 8;
        if (face == null) {
            face = WorldMapBlockColorResolver.BlockFace.TOP;
        }
        switch (face) {
            case TOP:
                u = (int) (fu * 16.0);
                v = (int) (fw * 16.0);
                break;
            case BOTTOM:
                u = (int) (fu * 16.0);
                v = (int) ((1.0 - fw) * 16.0);
                break;
            case SOUTH:
                u = (int) (fu * 16.0);
                v = (int) ((1.0 - fv) * 16.0);
                break;
            case NORTH:
                u = (int) ((1.0 - fu) * 16.0);
                v = (int) ((1.0 - fv) * 16.0);
                break;
            case EAST:
                u = (int) ((1.0 - fw) * 16.0);
                v = (int) ((1.0 - fv) * 16.0);
                break;
            case WEST:
                u = (int) (fw * 16.0);
                v = (int) ((1.0 - fv) * 16.0);
                break;
            default:
                break;
        }
        if (u < 0) {
            u = 0;
        }
        if (u > 15) {
            u = 15;
        }
        if (v < 0) {
            v = 0;
        }
        if (v > 15) {
            v = 15;
        }
        return new int[] { u, v };
    }

    private static double frac(double v) {
        int i = (int) v;
        if (v < 0.0 && v != i) {
            i--;
        }
        return v - i;
    }

    private static boolean rayAabb(double ox, double oy, double oz, double dx, double dy, double dz, double minX,
        double minY, double minZ, double maxX, double maxY, double maxZ, double[] outNearFar) {
        double tMin = Double.NEGATIVE_INFINITY;
        double tMax = Double.POSITIVE_INFINITY;
        if (!slabAxis(ox, dx, minX, maxX, outNearFar)) {
            return false;
        }
        tMin = outNearFar[0];
        tMax = outNearFar[1];
        if (!slabAxis(oy, dy, minY, maxY, outNearFar)) {
            return false;
        }
        if (outNearFar[0] > tMin) {
            tMin = outNearFar[0];
        }
        if (outNearFar[1] < tMax) {
            tMax = outNearFar[1];
        }
        if (!slabAxis(oz, dz, minZ, maxZ, outNearFar)) {
            return false;
        }
        if (outNearFar[0] > tMin) {
            tMin = outNearFar[0];
        }
        if (outNearFar[1] < tMax) {
            tMax = outNearFar[1];
        }
        if (tMax < Math.max(0.0, tMin)) {
            return false;
        }
        outNearFar[0] = Math.max(0.0, tMin);
        outNearFar[1] = tMax;
        return true;
    }

    private static boolean slabAxis(double origin, double dir, double min, double max, double[] out) {
        if (Math.abs(dir) < 1.0e-9) {
            if (origin < min || origin > max) {
                return false;
            }
            out[0] = Double.NEGATIVE_INFINITY;
            out[1] = Double.POSITIVE_INFINITY;
            return true;
        }
        double inv = 1.0 / dir;
        double t0 = (min - origin) * inv;
        double t1 = (max - origin) * inv;
        if (t0 > t1) {
            double tmp = t0;
            t0 = t1;
            t1 = tmp;
        }
        out[0] = t0;
        out[1] = t1;
        return true;
    }

    private static void loadJsonPatches() {
        Map<String, List<WorldMapBlockPatch>> byBlock = new HashMap<String, List<WorldMapBlockPatch>>();
        Map<String, List<WorldMapBlockPatch>> byClass = new HashMap<String, List<WorldMapBlockPatch>>();
        Map<String, List<WorldMapBlockPatch>> byPrefix = new HashMap<String, List<WorldMapBlockPatch>>();
        ClassLoader loader = WorldMapBlockPatchRegistry.class.getClassLoader();
        for (String resource : PATCH_RESOURCES) {
            parseJsonResource(loader, PATCH_RESOURCE_PREFIX + resource, byBlock, byClass, byPrefix);
        }
        jsonByBlockKey = Collections.unmodifiableMap(byBlock);
        jsonByClassContains = Collections.unmodifiableMap(byClass);
        jsonByRegistryPrefix = Collections.unmodifiableMap(byPrefix);
        AdvanceDataMonitor.LOG.info(
            "[WebAE] World map block patches loaded: {} blocks, {} classes, {} prefixes",
            byBlock.size(),
            byClass.size(),
            byPrefix.size());
    }

    private static void parseJsonResource(ClassLoader loader, String path,
        Map<String, List<WorldMapBlockPatch>> byBlock, Map<String, List<WorldMapBlockPatch>> byClass,
        Map<String, List<WorldMapBlockPatch>> byPrefix) {
        InputStream in = null;
        try {
            in = loader.getResourceAsStream(path);
            if (in == null) {
                return;
            }
            JsonObject root = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8))
                .getAsJsonObject();
            JsonArray entries = root.getAsJsonArray("entries");
            if (entries == null) {
                return;
            }
            for (JsonElement el : entries) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject entry = el.getAsJsonObject();
                List<WorldMapBlockPatch> patches = parsePatchList(entry.getAsJsonArray("boxes"));
                if (patches.isEmpty()) {
                    continue;
                }
                JsonArray blocks = entry.getAsJsonArray("blocks");
                if (blocks != null) {
                    for (JsonElement blockEl : blocks) {
                        String key = blockEl.getAsString();
                        if (key != null && !key.isEmpty()) {
                            byBlock.put(
                                key.trim()
                                    .toLowerCase(),
                                patches);
                        }
                    }
                }
                if (entry.has("registryPrefix")) {
                    String prefix = entry.get("registryPrefix")
                        .getAsString();
                    if (prefix != null && !prefix.isEmpty()) {
                        byPrefix.put(
                            prefix.trim()
                                .toLowerCase(),
                            patches);
                    }
                }
                if (entry.has("classContains")) {
                    String cls = entry.get("classContains")
                        .getAsString();
                    if (cls != null && !cls.isEmpty()) {
                        byClass.put(cls.trim(), patches);
                    }
                }
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] World map patch JSON load failed {}: {}", path, e.getMessage());
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private static List<WorldMapBlockPatch> parsePatchList(JsonArray boxes) {
        if (boxes == null || boxes.size() == 0) {
            return Collections.emptyList();
        }
        List<WorldMapBlockPatch> out = new ArrayList<WorldMapBlockPatch>(boxes.size());
        for (JsonElement el : boxes) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject box = el.getAsJsonObject();
            WorldMapBlockColorResolver.BlockFace face = parseFace(
                box.has("face") ? box.get("face")
                    .getAsString() : "TOP");
            out.add(
                WorldMapBlockPatch.box(
                    box.get("x0")
                        .getAsDouble(),
                    box.get("y0")
                        .getAsDouble(),
                    box.get("z0")
                        .getAsDouble(),
                    box.get("x1")
                        .getAsDouble(),
                    box.get("y1")
                        .getAsDouble(),
                    box.get("z1")
                        .getAsDouble(),
                    face));
        }
        return out;
    }

    private static WorldMapBlockColorResolver.BlockFace parseFace(String raw) {
        if (raw == null) {
            return WorldMapBlockColorResolver.BlockFace.TOP;
        }
        String upper = raw.trim()
            .toUpperCase();
        if ("SIDE".equals(upper)) {
            return WorldMapBlockColorResolver.BlockFace.SOUTH;
        }
        try {
            return WorldMapBlockColorResolver.BlockFace.valueOf(upper);
        } catch (Exception ignored) {
            return WorldMapBlockColorResolver.BlockFace.TOP;
        }
    }

    private static String blockRegistryKey(Block block) {
        GameRegistry.UniqueIdentifier uid = GameRegistry.findUniqueIdentifierFor(block);
        if (uid == null || uid.modId == null || uid.name == null) {
            return null;
        }
        return (uid.modId + ":" + uid.name).toLowerCase();
    }
}
