package com.imgood.textech.webae.worldmap.engine;

import java.awt.image.BufferedImage;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.WorldServer;

import com.imgood.textech.Config;
import com.imgood.textech.webae.worldmap.WorldMapBlockColorResolver;
import com.imgood.textech.webae.worldmap.WorldMapObliqueDirection;
import com.imgood.textech.webae.worldmap.WorldMapQualityTier;
import com.imgood.textech.webae.worldmap.WorldMapRenderSupport;

/**
 * Oblique terrain renderer using per-pixel orthographic ray tracing with UV/biome/lighting hits.
 * Non-cube blocks use {@link WorldMapBlockPatchRegistry} AABB patches (stairs, slabs, GT JSON).
 */
public final class WorldMapIsoRayRenderer {

    private static final int MAX_TRACE_STEPS = 320;
    private static final int SKY_RGB = 0x1A2030;

    private WorldMapIsoRayRenderer() {}

    public static byte[] renderTerrain(WorldMapQualityTier quality, int dim, int chunkX, int chunkZ,
        WorldMapObliqueDirection direction) {
        if (direction == null) {
            direction = WorldMapObliqueDirection.SE;
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

        WorldMapObliqueProjection projection = WorldMapObliqueProjection.forChunk(chunkX, chunkZ, direction);
        BufferedImage img = new BufferedImage(tilePx, tilePx, BufferedImage.TYPE_INT_RGB);
        WorldMapObliqueProjection.Ray ray = new WorldMapObliqueProjection.Ray();
        int maxDepth = Math.max(1, Math.min(8, Config.webWorldMapMaxRayDepth));
        int painted = 0;
        WorldMapBlockPatchRegistry.ensureLoaded();

        for (int py = 0; py < tilePx; py++) {
            for (int px = 0; px < tilePx; px++) {
                projection.pixelToRay(px, py, tilePx, ray);
                int rgb = tracePixel(ctx, ray, maxDepth);
                if (rgb != 0) {
                    img.setRGB(px, py, 0xFF000000 | (rgb & 0xFFFFFF));
                    painted++;
                } else {
                    img.setRGB(px, py, 0xFF000000 | SKY_RGB);
                }
            }
        }

        if (painted <= 0) {
            return null;
        }
        return WorldMapRenderSupport.toPng(img);
    }

    private static int tracePixel(WorldMapChunkContext ctx, WorldMapObliqueProjection.Ray ray, int maxDepth) {
        int accumA = 0;
        int accumR = 0;
        int accumG = 0;
        int accumB = 0;
        int layers = 0;

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
            Block block = ctx.blockAt(vx, vy, vz);
            if (block != null && block != Blocks.air) {
                int meta = ctx.blockMeta(vx, vy, vz);
                if (WorldMapBlockPatchRegistry.hasPatchModel(block)) {
                    WorldMapBlockPatchRegistry.PatchHit patchHit = WorldMapBlockPatchRegistry
                        .intersectInVoxel(ctx, vx, vy, vz, block, meta, ox, oy, oz, dx, dy, dz, tEnter, tExit);
                    if (patchHit != null) {
                        int rgb = WorldMapFaceRasterizer
                            .sampleFaceRgb(block, meta, patchHit.face, patchHit.texU, patchHit.texV, vx, vy, vz, ctx);
                        if (rgb >= 0) {
                            int alpha = alphaForBlock(block);
                            if (alpha >= 255 || !isRayTransparent(block)) {
                                return rgb;
                            }
                            accumR += ((rgb >> 16) & 0xFF) * alpha;
                            accumG += ((rgb >> 8) & 0xFF) * alpha;
                            accumB += (rgb & 0xFF) * alpha;
                            accumA += alpha;
                            layers++;
                            if (layers >= maxDepth) {
                                break;
                            }
                        }
                    }
                } else {
                    int[] uv = faceUv(entryFace, ox + dx * tExit, oy + dy * tExit, oz + dz * tExit);
                    int rgb = WorldMapFaceRasterizer
                        .sampleFaceRgb(block, meta, entryFace, uv[0], uv[1], vx, vy, vz, ctx);
                    if (rgb >= 0) {
                        int alpha = alphaForBlock(block);
                        if (alpha >= 255 || !isRayTransparent(block)) {
                            return rgb;
                        }
                        accumR += ((rgb >> 16) & 0xFF) * alpha;
                        accumG += ((rgb >> 8) & 0xFF) * alpha;
                        accumB += (rgb & 0xFF) * alpha;
                        accumA += alpha;
                        layers++;
                        if (layers >= maxDepth) {
                            break;
                        }
                    } else if (!isRayTransparent(block)) {
                        break;
                    }
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

        if (accumA <= 0) {
            return 0;
        }
        int r = accumR / accumA;
        int g = accumG / accumA;
        int b = accumB / accumA;
        if (r > 255) {
            r = 255;
        }
        if (g > 255) {
            g = 255;
        }
        if (b > 255) {
            b = 255;
        }
        return (r << 16) | (g << 8) | b;
    }

    private static boolean isRayTransparent(Block block) {
        if (block == null || block == Blocks.air) {
            return true;
        }
        if (WorldMapRenderSupport.isSoftBlock(block)) {
            return true;
        }
        return !block.isOpaqueCube();
    }

    private static int alphaForBlock(Block block) {
        if (block == null) {
            return 0;
        }
        if (WorldMapRenderSupport.isSoftBlock(block)) {
            return 0xB0;
        }
        if (!block.isOpaqueCube()) {
            return 0x90;
        }
        return 255;
    }

    private static WorldMapBlockColorResolver.BlockFace faceOppositeToDir(double dx, double dy, double dz) {
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);
        if (ax >= ay && ax >= az) {
            return dx > 0.0 ? WorldMapBlockColorResolver.BlockFace.WEST : WorldMapBlockColorResolver.BlockFace.EAST;
        }
        if (ay >= ax && ay >= az) {
            return dy > 0.0 ? WorldMapBlockColorResolver.BlockFace.BOTTOM : WorldMapBlockColorResolver.BlockFace.TOP;
        }
        return dz > 0.0 ? WorldMapBlockColorResolver.BlockFace.NORTH : WorldMapBlockColorResolver.BlockFace.SOUTH;
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
