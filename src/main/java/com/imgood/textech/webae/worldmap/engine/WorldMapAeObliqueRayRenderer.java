package com.imgood.textech.webae.worldmap.engine;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.WorldServer;

import com.imgood.textech.webae.worldmap.WorldMapAeCategory;
import com.imgood.textech.webae.worldmap.WorldMapAePlacementRecord;
import com.imgood.textech.webae.worldmap.WorldMapBlockColorResolver;
import com.imgood.textech.webae.worldmap.WorldMapObliqueDirection;
import com.imgood.textech.webae.worldmap.WorldMapQualityTier;
import com.imgood.textech.webae.worldmap.WorldMapRenderSupport;

/**
 * Oblique AE overlay using the same orthographic ray projection as terrain, outputting category ID pixels.
 */
public final class WorldMapAeObliqueRayRenderer {

    private static final int MAX_TRACE_STEPS = 320;

    private WorldMapAeObliqueRayRenderer() {}

    public static byte[] render(WorldMapQualityTier quality, int dim, int chunkX, int chunkZ,
        WorldMapObliqueDirection direction, List<WorldMapAePlacementRecord> placements) {
        if (direction == null) {
            direction = WorldMapObliqueDirection.SE;
        }
        if (placements == null || placements.isEmpty()) {
            return null;
        }
        int tilePx = WorldMapRenderSupport.tilePx(quality);
        WorldServer world = WorldMapRenderSupport.worldForDim(dim);
        if (world == null) {
            return null;
        }
        WorldMapChunkContext ctx = WorldMapChunkContext.create(world, chunkX, chunkZ);
        if (ctx == null) {
            return null;
        }

        Map<Long, Integer> categoryByPos = buildCategoryMap(placements);
        if (categoryByPos.isEmpty()) {
            return null;
        }

        WorldMapObliqueProjection projection = WorldMapObliqueProjection.forChunk(chunkX, chunkZ, direction);
        BufferedImage img = new BufferedImage(tilePx, tilePx, BufferedImage.TYPE_INT_ARGB);
        WorldMapObliqueProjection.Ray ray = new WorldMapObliqueProjection.Ray();
        int painted = 0;
        WorldMapBlockPatchRegistry.ensureLoaded();

        for (int py = 0; py < tilePx; py++) {
            for (int px = 0; px < tilePx; px++) {
                projection.pixelToRay(px, py, tilePx, ray);
                int categoryId = traceAeCategory(ctx, ray, categoryByPos);
                if (categoryId > 0) {
                    img.setRGB(px, py, WorldMapAeCategory.argbForCategory(categoryId, 0xFF));
                    painted++;
                }
            }
        }

        if (painted <= 0) {
            return null;
        }
        return WorldMapRenderSupport.toPng(img);
    }

    private static Map<Long, Integer> buildCategoryMap(List<WorldMapAePlacementRecord> placements) {
        Map<Long, Integer> out = new HashMap<Long, Integer>();
        for (WorldMapAePlacementRecord placement : placements) {
            if (placement == null || "part".equals(placement.kind)) {
                continue;
            }
            WorldMapAeCategory cat = WorldMapAeCategory.resolve(placement);
            out.put(posKey(placement.x, placement.y, placement.z), cat.id);
        }
        return out;
    }

    private static int traceAeCategory(WorldMapChunkContext ctx, WorldMapObliqueProjection.Ray ray,
        Map<Long, Integer> categoryByPos) {
        double ox = ray.originX;
        double oy = ray.originY;
        double oz = ray.originZ;
        double dx = ray.dirX;
        double dy = ray.dirY;
        double dz = ray.dirZ;

        int vx = floor(ox);
        int vy = floor(oy);
        int vz = floor(oz);

        int stepX = dx >= 0.0 ? 1 : -1;
        int stepY = dy >= 0.0 ? 1 : -1;
        int stepZ = dz >= 0.0 ? 1 : -1;

        double tDeltaX = safeInv(dx);
        double tDeltaY = safeInv(dy);
        double tDeltaZ = safeInv(dz);

        double tMaxX = nextBoundaryT(ox, dx, stepX);
        double tMaxY = nextBoundaryT(oy, dy, stepY);
        double tMaxZ = nextBoundaryT(oz, dz, stepZ);

        WorldMapBlockColorResolver.BlockFace entryFace = faceOppositeToDir(dx, dy, dz);
        double tEnter = 0.0;

        for (int step = 0; step < MAX_TRACE_STEPS; step++) {
            double tExit = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
            Integer categoryId = categoryByPos.get(posKey(vx, vy, vz));
            if (categoryId != null && categoryId > 0) {
                Block block = ctx.blockAt(vx, vy, vz);
                if (block != null && block != Blocks.air) {
                    int meta = ctx.blockMeta(vx, vy, vz);
                    if (WorldMapBlockPatchRegistry.hasPatchModel(block)) {
                        WorldMapBlockPatchRegistry.PatchHit patchHit = WorldMapBlockPatchRegistry.intersectInVoxel(
                            ctx,
                            vx,
                            vy,
                            vz,
                            block,
                            meta,
                            ox,
                            oy,
                            oz,
                            dx,
                            dy,
                            dz,
                            tEnter,
                            tExit);
                        if (patchHit != null) {
                            return categoryId;
                        }
                    } else {
                        int[] uv = faceUv(entryFace, ox + dx * tExit, oy + dy * tExit, oz + dz * tExit);
                        int rgb = WorldMapFaceRasterizer.sampleFaceRgb(
                            block,
                            meta,
                            entryFace,
                            uv[0],
                            uv[1],
                            vx,
                            vy,
                            vz,
                            ctx);
                        if (rgb >= 0) {
                            return categoryId;
                        }
                    }
                } else {
                    return categoryId;
                }
            }

            tEnter = tExit;
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    vx += stepX;
                    entryFace = faceForStep(stepX, 0, 0);
                    tMaxX += tDeltaX;
                } else {
                    vz += stepZ;
                    entryFace = faceForStep(0, 0, stepZ);
                    tMaxZ += tDeltaZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    vy += stepY;
                    entryFace = faceForStep(0, stepY, 0);
                    tMaxY += tDeltaY;
                } else {
                    vz += stepZ;
                    entryFace = faceForStep(0, 0, stepZ);
                    tMaxZ += tDeltaZ;
                }
            }

            if (vy < 0 || vy > 255) {
                break;
            }
        }
        return 0;
    }

    private static long posKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) | (((long) y & 0xFFL) << 26) | (((long) z & 0x3FFFFFFL) << 34);
    }

    private static WorldMapBlockColorResolver.BlockFace faceOppositeToDir(double dx, double dy, double dz) {
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);
        if (ax >= ay && ax >= az) {
            return dx > 0.0 ? WorldMapBlockColorResolver.BlockFace.WEST
                : WorldMapBlockColorResolver.BlockFace.EAST;
        }
        if (ay >= ax && ay >= az) {
            return dy > 0.0 ? WorldMapBlockColorResolver.BlockFace.BOTTOM
                : WorldMapBlockColorResolver.BlockFace.TOP;
        }
        return dz > 0.0 ? WorldMapBlockColorResolver.BlockFace.NORTH
            : WorldMapBlockColorResolver.BlockFace.SOUTH;
    }

    private static WorldMapBlockColorResolver.BlockFace faceForStep(int stepX, int stepY, int stepZ) {
        if (stepX > 0) {
            return WorldMapBlockColorResolver.BlockFace.WEST;
        }
        if (stepX < 0) {
            return WorldMapBlockColorResolver.BlockFace.EAST;
        }
        if (stepY > 0) {
            return WorldMapBlockColorResolver.BlockFace.BOTTOM;
        }
        if (stepY < 0) {
            return WorldMapBlockColorResolver.BlockFace.TOP;
        }
        if (stepZ > 0) {
            return WorldMapBlockColorResolver.BlockFace.NORTH;
        }
        if (stepZ < 0) {
            return WorldMapBlockColorResolver.BlockFace.SOUTH;
        }
        return WorldMapBlockColorResolver.BlockFace.TOP;
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

    private static int floor(double v) {
        int i = (int) v;
        if (v < 0.0 && v != i) {
            return i - 1;
        }
        return i;
    }

    private static double frac(double v) {
        return v - floor(v);
    }

    private static double safeInv(double d) {
        if (Math.abs(d) < 1.0e-9) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.abs(1.0 / d);
    }

    private static double nextBoundaryT(double origin, double dir, int step) {
        if (Math.abs(dir) < 1.0e-9) {
            return Double.POSITIVE_INFINITY;
        }
        double v = floor(origin);
        if (step > 0) {
            return (v + 1.0 - origin) / dir;
        }
        return (v - origin) / dir;
    }
}
