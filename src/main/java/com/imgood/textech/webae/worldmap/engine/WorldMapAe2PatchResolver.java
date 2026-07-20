package com.imgood.textech.webae.worldmap.engine;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;

import com.imgood.textech.Config;
import com.imgood.textech.webae.worldmap.WorldMapBlockColorResolver;

/**
 * Detects AE2 cable/bus blocks and generates cable-shaped patches for oblique ray hits.
 * Ported from GWM {@code MECableRenderer} pipe generation patterns.
 */
public final class WorldMapAe2PatchResolver {

    /** AE2 cable half-radius values matching GWM's small/medium/large pipe sizes. */
    private static final double SMALL_PIPE_HALF = 2.0 / 16;
    private static final double MEDIUM_PIPE_HALF = 3.0 / 16;
    private static final double LARGE_PIPE_HALF = 5.0 / 16;

    private WorldMapAe2PatchResolver() {}

    /**
     * Returns cable patches if the block is an AE2 cable/bus with connections.
     * Returns {@code null} if not applicable.
     */
    public static List<WorldMapBlockPatch> resolve(WorldMapChunkContext ctx, int wx, int wy, int wz, Block block,
        int meta) {
        if (!Config.webWorldMapAeOverlayIncludeCables || ctx == null || block == null) {
            return null;
        }
        if (!isAe2CableBlock(block)) {
            return null;
        }

        byte connections = readAe2Connections(ctx, wx, wy, wz, block);
        double half = pipeHalf(block);
        WorldMapBlockColorResolver.BlockFace face = WorldMapBlockColorResolver.BlockFace.SOUTH;

        List<WorldMapBlockPatch> patches = generateCablePatches(connections, half, face);
        if (patches.isEmpty()) {
            patches.add(
                WorldMapBlockPatch.box(0.5 - half, 0.5 - half, 0.5 - half, 0.5 + half, 0.5 + half, 0.5 + half, face));
        }
        return patches;
    }

    private static boolean isAe2CableBlock(Block block) {
        String reg = net.minecraft.block.Block.blockRegistry.getNameForObject(block);
        if (reg == null) {
            return false;
        }
        String lower = reg.toLowerCase();
        return lower.contains("appliedenergistics") && lower.contains("cable");
    }

    /**
     * Reads connection directions from the AE2 cable tile entity.
     * For AE2 cables the connections are 6 booleans (DOWN, UP, NORTH, SOUTH, WEST, EAST).
     */
    private static byte readAe2Connections(WorldMapChunkContext ctx, int wx, int wy, int wz, Block block) {
        TileEntity te = ctx.tileEntityAt(wx, wy, wz);
        if (te == null) {
            return 0;
        }
        byte bits = 0;
        try {
            Class<?> teClass = te.getClass();
            // AE2 cables use method isConnected(ForgeDirection)
            for (int i = 0; i < 6; i++) {
                Object dir = getForgeDirection(i);
                if (dir != null) {
                    Object result = teClass.getMethod("isConnected", dir.getClass())
                        .invoke(te, dir);
                    if (result instanceof Boolean && ((Boolean) result).booleanValue()) {
                        bits |= (1 << i);
                    }
                }
            }
        } catch (Throwable ignored) {
            // Fallback: try getConnections style
            return readConnectionsByte(te);
        }
        return bits;
    }

    private static byte readConnectionsByte(TileEntity te) {
        try {
            Object value = te.getClass()
                .getMethod("getConnections")
                .invoke(te);
            if (value instanceof Number) {
                return ((Number) value).byteValue();
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private static Object getForgeDirection(int ordinal) {
        try {
            Class<?> fdClass = Class.forName("net.minecraftforge.common.util.ForgeDirection");
            for (Object dir : fdClass.getEnumConstants()) {
                if (((Enum<?>) dir).ordinal() == ordinal) {
                    return dir;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static double pipeHalf(Block block) {
        String reg = net.minecraft.block.Block.blockRegistry.getNameForObject(block);
        if (reg == null) {
            return SMALL_PIPE_HALF;
        }
        String lower = reg.toLowerCase();
        if (lower.contains("dense") || lower.contains("smart")) {
            return MEDIUM_PIPE_HALF;
        }
        if (lower.contains("glass") || lower.contains("covered")) {
            return SMALL_PIPE_HALF;
        }
        return SMALL_PIPE_HALF;
    }

    /**
     * Generate cable patches using GWM-style pipe model: center box + directional arms.
     */
    private static List<WorldMapBlockPatch> generateCablePatches(byte connections, double halfThick,
        WorldMapBlockColorResolver.BlockFace face) {
        double t = 0.5 - halfThick;
        double b = 0.5 + halfThick;
        List<WorldMapBlockPatch> out = new ArrayList<WorldMapBlockPatch>(7);

        // Center connection box
        out.add(WorldMapBlockPatch.box(t, t, t, b, b, b, face));

        if ((connections & 1) != 0) {
            out.add(WorldMapBlockPatch.box(t, 0.0, t, b, b, b, face));
        }
        if ((connections & 2) != 0) {
            out.add(WorldMapBlockPatch.box(t, t, t, b, 1.0, b, face));
        }
        if ((connections & 4) != 0) {
            out.add(WorldMapBlockPatch.box(t, t, 0.0, b, b, b, face));
        }
        if ((connections & 8) != 0) {
            out.add(WorldMapBlockPatch.box(t, t, t, b, b, 1.0, face));
        }
        if ((connections & 16) != 0) {
            out.add(WorldMapBlockPatch.box(0.0, t, t, b, b, b, face));
        }
        if ((connections & 32) != 0) {
            out.add(WorldMapBlockPatch.box(t, t, t, 1.0, b, b, face));
        }
        return out;
    }
}
