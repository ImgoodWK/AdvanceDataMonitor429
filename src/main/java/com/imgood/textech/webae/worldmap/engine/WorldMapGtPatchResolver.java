package com.imgood.textech.webae.worldmap.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;

import com.imgood.textech.webae.worldmap.WorldMapBlockColorResolver;

/**
 * GregTech MetaTileEntity-aware patch geometry with GWM-style multi-thickness pipe/cable models.
 * Generates appropriate {@link WorldMapBlockPatch} shapes for oblique ray hits on GT pipes, cables,
 * frames, and machines.
 */
public final class WorldMapGtPatchResolver {

    private static final double CENTER_THICK_HALF = 0.25;

    private WorldMapGtPatchResolver() {}

    /**
     * Resolves patch geometry for a GregTech block.
     * Returns a list of AABB patches or {@code null} if the block should be rendered as a full cube.
     */
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

        // Frame blocks
        if (lower.contains("frame")) {
            return framePatches();
        }

        // Machine blocks (not pipe/cable/frame) - render as full cube with center highlight
        if (!lower.contains("pipe") && !lower.contains("conduit") && !lower.contains("cable")
            && !lower.contains("wire")) {
            return machinePatches();
        }

        byte connections = readConnections(mte);
        int thickness = readThickness(mte);

        double half = halfFromThickness(thickness, mteClass);
        return pipePatches(connections, half);
    }

    /**
     * Maps GWM-style thickness values to half-radii.
     */
    private static double halfFromThickness(int thickness, String mteClass) {
        boolean longDist = mteClass.toLowerCase()
            .contains("longdistance");
        switch (thickness) {
            case 125:
                return 0.0625;
            case 250:
                return 0.125;
            case 375:
                return 0.1875;
            case 500:
                return 0.25;
            case 600:
                return 0.30;
            case 625:
                return 0.3125;
            case 750:
                return 0.375;
            case 875:
                return 0.4375;
            case 1000:
                return 0.5;
            default:
                return longDist ? 0.125 : 0.1875;
        }
    }

    /**
     * Reads the GregTech MetaTileEntity thickness via reflection.
     */
    private static int readThickness(Object mte) {
        try {
            // Try getMetaInfo which returns int thickness for some GT MTEs
            Object value = mte.getClass()
                .getMethod("getMetaInfo")
                .invoke(mte);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        } catch (Throwable ignored) {}
        // Fallback: try reading from Material
        try {
            Object material = mte.getClass()
                .getMethod("getMaterial")
                .invoke(mte);
            if (material != null) {
                Object thickVal = material.getClass()
                    .getMethod("mPipeSize")
                    .invoke(material);
                if (thickVal instanceof Number) {
                    return ((Number) thickVal).intValue();
                }
            }
        } catch (Throwable ignored) {}
        return 375; // default medium
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
     * GWM-style pipe model: center box + 6 directional arms based on connection bitmask.
     */
    static List<WorldMapBlockPatch> pipePatches(byte connections, double halfThick) {
        double t = 0.5 - halfThick;
        double b = 0.5 + halfThick;
        List<WorldMapBlockPatch> out = new ArrayList<WorldMapBlockPatch>(7);
        WorldMapBlockColorResolver.BlockFace face = WorldMapBlockColorResolver.BlockFace.SOUTH;

        // Center box (always present for non-trivial sizes)
        if (halfThick >= 0.0625) {
            out.add(WorldMapBlockPatch.box(t, t, t, b, b, b, face));
        }

        // Directional arms based on connection bits (Forge order)
        if ((connections & 1) != 0) {
            // DOWN (Forge bit 0 = bottom face, Y-)
            out.add(WorldMapBlockPatch.box(t, 0.0, t, b, b, b, face));
        }
        if ((connections & 2) != 0) {
            // UP (Forge bit 1 = top face, Y+)
            out.add(WorldMapBlockPatch.box(t, t, t, b, 1.0, b, face));
        }
        if ((connections & 4) != 0) {
            // NORTH (Forge bit 2 = north face, Z-)
            out.add(WorldMapBlockPatch.box(t, t, 0.0, b, b, b, face));
        }
        if ((connections & 8) != 0) {
            // SOUTH (Forge bit 3 = south face, Z+)
            out.add(WorldMapBlockPatch.box(t, t, t, b, b, 1.0, face));
        }
        if ((connections & 16) != 0) {
            // WEST (Forge bit 4 = west face, X-)
            out.add(WorldMapBlockPatch.box(0.0, t, t, b, b, b, face));
        }
        if ((connections & 32) != 0) {
            // EAST (Forge bit 5 = east face, X+)
            out.add(WorldMapBlockPatch.box(t, t, t, 1.0, b, b, face));
        }
        return out;
    }

    /**
     * Frame blocks: 4 vertical struts at corners.
     */
    private static List<WorldMapBlockPatch> framePatches() {
        double f = 0.125;
        List<WorldMapBlockPatch> out = new ArrayList<WorldMapBlockPatch>(12);
        WorldMapBlockColorResolver.BlockFace face = WorldMapBlockColorResolver.BlockFace.SOUTH;
        out.add(WorldMapBlockPatch.box(0, 0, 0, f, 1, f, face));
        out.add(WorldMapBlockPatch.box(1 - f, 0, 0, 1, 1, f, face));
        out.add(WorldMapBlockPatch.box(0, 0, 1 - f, f, 1, 1, face));
        out.add(WorldMapBlockPatch.box(1 - f, 0, 1 - f, 1, 1, 1, face));
        return out;
    }

    /**
     * Machine blocks: full cube shape (no special patch - default to cube rendering).
     */
    private static List<WorldMapBlockPatch> machinePatches() {
        return Collections.singletonList(
            WorldMapBlockPatch.box(0, 0, 0, 1, 1, 1, WorldMapBlockColorResolver.BlockFace.TOP));
    }

    public static List<WorldMapBlockPatch> centerThinBox(double halfThick) {
        double t = 0.5 - halfThick;
        double b = 0.5 + halfThick;
        return Collections.singletonList(
            WorldMapBlockPatch.box(t, t, t, b, b, b, WorldMapBlockColorResolver.BlockFace.SOUTH));
    }
}
