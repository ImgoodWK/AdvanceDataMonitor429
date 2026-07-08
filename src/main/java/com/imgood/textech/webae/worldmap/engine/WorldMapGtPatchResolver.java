package com.imgood.textech.webae.worldmap.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;

import com.imgood.textech.webae.worldmap.WorldMapBlockColorResolver;

/**
 * GregTech MetaTileEntity-aware patch geometry (pipes, cables, frames) for oblique ray hits.
 */
public final class WorldMapGtPatchResolver {

    private static final double PIPE_HALF = 0.0625;
    private static final double CABLE_HALF = 0.03125;
    private static final double FRAME_THICK = 0.125;

    private WorldMapGtPatchResolver() {}

    public static List<WorldMapBlockPatch> resolve(WorldMapChunkContext ctx, int wx, int wy, int wz, Block block) {
        if (ctx == null || block == null) {
            return null;
        }
        TileEntity te = ctx.tileEntityAt(wx, wy, wz);
        if (te == null) {
            return null;
        }
        Object gtTe = asGregTechTile(te);
        if (gtTe == null) {
            return null;
        }
        Object mte = getMetaTileEntity(gtTe);
        if (mte == null) {
            return null;
        }
        String mteClass = mte.getClass()
            .getName();
        String lower = mteClass.toLowerCase();
        if (lower.contains("frame")) {
            return framePatches();
        }
        if (lower.contains("pipe") || lower.contains("conduit")) {
            byte connections = readConnections(mte);
            double half = lower.contains("longdistance") ? 0.125 : PIPE_HALF;
            return pipePatches(connections, half);
        }
        if (lower.contains("cable") || lower.contains("wire")) {
            byte connections = readConnections(mte);
            return pipePatches(connections, CABLE_HALF);
        }
        return null;
    }

    private static Object asGregTechTile(TileEntity te) {
        try {
            Class<?> iface = Class.forName("gregtech.api.interfaces.tileentity.IGregTechTileEntity");
            if (iface.isInstance(te)) {
                return te;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object getMetaTileEntity(Object gtTe) {
        try {
            return gtTe.getClass()
                .getMethod("getMetaTileEntity")
                .invoke(gtTe);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static byte readConnections(Object mte) {
        try {
            Object value = mte.getClass()
                .getMethod("getConnections")
                .invoke(mte);
            if (value instanceof Number) {
                return ((Number) value).byteValue();
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    /**
     * Forge side order: 0=DOWN, 1=UP, 2=NORTH, 3=SOUTH, 4=WEST, 5=EAST.
     */
    static List<WorldMapBlockPatch> pipePatches(byte connections, double halfThick) {
        double t = 0.5 - halfThick;
        double b = 0.5 + halfThick;
        List<WorldMapBlockPatch> out = new ArrayList<WorldMapBlockPatch>(7);
        WorldMapBlockColorResolver.BlockFace face = WorldMapBlockColorResolver.BlockFace.SOUTH;
        out.add(WorldMapBlockPatch.box(t, t, t, b, b, b, face));
        if ((connections & 1) != 0) {
            out.add(WorldMapBlockPatch.box(t, 0.0, t, b, t, b, face));
        }
        if ((connections & 2) != 0) {
            out.add(WorldMapBlockPatch.box(t, b, t, b, 1.0, b, face));
        }
        if ((connections & 4) != 0) {
            out.add(WorldMapBlockPatch.box(t, t, 0.0, b, b, t, face));
        }
        if ((connections & 8) != 0) {
            out.add(WorldMapBlockPatch.box(t, t, b, b, b, 1.0, face));
        }
        if ((connections & 16) != 0) {
            out.add(WorldMapBlockPatch.box(0.0, t, t, t, b, b, face));
        }
        if ((connections & 32) != 0) {
            out.add(WorldMapBlockPatch.box(b, t, t, 1.0, b, b, face));
        }
        return out;
    }

    private static List<WorldMapBlockPatch> framePatches() {
        double f = FRAME_THICK;
        List<WorldMapBlockPatch> out = new ArrayList<WorldMapBlockPatch>(12);
        WorldMapBlockColorResolver.BlockFace face = WorldMapBlockColorResolver.BlockFace.SOUTH;
        // Vertical struts
        out.add(WorldMapBlockPatch.box(0, 0, 0, f, 1, f, face));
        out.add(WorldMapBlockPatch.box(1 - f, 0, 0, 1, 1, f, face));
        out.add(WorldMapBlockPatch.box(0, 0, 1 - f, f, 1, 1, face));
        out.add(WorldMapBlockPatch.box(1 - f, 0, 1 - f, 1, 1, 1, face));
        return out;
    }

    public static List<WorldMapBlockPatch> centerThinBox(double halfThick) {
        double t = 0.5 - halfThick;
        double b = 0.5 + halfThick;
        return Collections.singletonList(
            WorldMapBlockPatch.box(t, t, t, b, b, b, WorldMapBlockColorResolver.BlockFace.SOUTH));
    }
}
