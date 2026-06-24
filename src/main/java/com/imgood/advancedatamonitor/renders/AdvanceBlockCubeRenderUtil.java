package com.imgood.advancedatamonitor.renders;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Shared placeholder geometry for ADM blocks until dedicated OBJ models are wired in.
 */
@SideOnly(Side.CLIENT)
public final class AdvanceBlockCubeRenderUtil {

    private static final float PLATE = 0.28125F;
    private static final float INSET = 0.2F;
    private static final float OUTER = 0.8F;

    private AdvanceBlockCubeRenderUtil() {}

    /** Full 1x1x1 cube on the block origin (0,0,0)-(1,1,1). */
    public static void renderUnitCube(IIcon icon) {
        if (icon == null) {
            return;
        }
        Tessellator tess = Tessellator.instance;
        double uMin = icon.getMinU();
        double uMax = icon.getMaxU();
        double vMin = icon.getMinV();
        double vMax = icon.getMaxV();

        tess.startDrawingQuads();
        tess.setNormal(0.0F, -1.0F, 0.0F);
        tess.addVertexWithUV(0.0D, 0.0D, 1.0D, uMin, vMax);
        tess.addVertexWithUV(1.0D, 0.0D, 1.0D, uMax, vMax);
        tess.addVertexWithUV(1.0D, 0.0D, 0.0D, uMax, vMin);
        tess.addVertexWithUV(0.0D, 0.0D, 0.0D, uMin, vMin);

        tess.setNormal(0.0F, 1.0F, 0.0F);
        tess.addVertexWithUV(0.0D, 1.0D, 0.0D, uMin, vMin);
        tess.addVertexWithUV(1.0D, 1.0D, 0.0D, uMax, vMin);
        tess.addVertexWithUV(1.0D, 1.0D, 1.0D, uMax, vMax);
        tess.addVertexWithUV(0.0D, 1.0D, 1.0D, uMin, vMax);

        tess.setNormal(0.0F, 0.0F, -1.0F);
        tess.addVertexWithUV(1.0D, 1.0D, 0.0D, uMin, vMin);
        tess.addVertexWithUV(0.0D, 1.0D, 0.0D, uMax, vMin);
        tess.addVertexWithUV(0.0D, 0.0D, 0.0D, uMax, vMax);
        tess.addVertexWithUV(1.0D, 0.0D, 0.0D, uMin, vMax);

        tess.setNormal(0.0F, 0.0F, 1.0F);
        tess.addVertexWithUV(0.0D, 1.0D, 1.0D, uMin, vMin);
        tess.addVertexWithUV(1.0D, 1.0D, 1.0D, uMax, vMin);
        tess.addVertexWithUV(1.0D, 0.0D, 1.0D, uMax, vMax);
        tess.addVertexWithUV(0.0D, 0.0D, 1.0D, uMin, vMax);

        tess.setNormal(-1.0F, 0.0F, 0.0F);
        tess.addVertexWithUV(0.0D, 1.0D, 0.0D, uMin, vMin);
        tess.addVertexWithUV(0.0D, 1.0D, 1.0D, uMax, vMin);
        tess.addVertexWithUV(0.0D, 0.0D, 1.0D, uMax, vMax);
        tess.addVertexWithUV(0.0D, 0.0D, 0.0D, uMin, vMax);

        tess.setNormal(1.0F, 0.0F, 0.0F);
        tess.addVertexWithUV(1.0D, 1.0D, 1.0D, uMin, vMin);
        tess.addVertexWithUV(1.0D, 1.0D, 0.0D, uMax, vMin);
        tess.addVertexWithUV(1.0D, 0.0D, 0.0D, uMax, vMax);
        tess.addVertexWithUV(1.0D, 0.0D, 1.0D, uMin, vMax);
        tess.draw();
    }

    /** Thin plate on {@code face}, matching {@link com.imgood.advancedatamonitor.blocks.BlockGrappleAnchor}. */
    public static void renderGrappleAnchorPlate(IIcon icon, net.minecraftforge.common.util.ForgeDirection face) {
        if (icon == null || face == null) {
            return;
        }
        double uMin = icon.getMinU();
        double uMax = icon.getMaxU();
        double vMin = icon.getMinV();
        double vMax = icon.getMaxV();
        Tessellator tess = Tessellator.instance;

        tess.startDrawingQuads();
        switch (face) {
            case DOWN:
                tess.setNormal(0.0F, -1.0F, 0.0F);
                tess.addVertexWithUV(INSET, 0.0D, INSET, uMin, vMax);
                tess.addVertexWithUV(OUTER, 0.0D, INSET, uMax, vMax);
                tess.addVertexWithUV(OUTER, 0.0D, OUTER, uMax, vMin);
                tess.addVertexWithUV(INSET, 0.0D, OUTER, uMin, vMin);
                break;
            case UP:
                tess.setNormal(0.0F, 1.0F, 0.0F);
                tess.addVertexWithUV(INSET, 1.0D - PLATE, INSET, uMin, vMin);
                tess.addVertexWithUV(INSET, 1.0D - PLATE, OUTER, uMin, vMax);
                tess.addVertexWithUV(OUTER, 1.0D - PLATE, OUTER, uMax, vMax);
                tess.addVertexWithUV(OUTER, 1.0D - PLATE, INSET, uMax, vMin);
                break;
            case NORTH:
                tess.setNormal(0.0F, 0.0F, -1.0F);
                tess.addVertexWithUV(OUTER, INSET, 0.0D, uMax, vMin);
                tess.addVertexWithUV(INSET, INSET, 0.0D, uMin, vMin);
                tess.addVertexWithUV(INSET, OUTER, 0.0D, uMin, vMax);
                tess.addVertexWithUV(OUTER, OUTER, 0.0D, uMax, vMax);
                break;
            case SOUTH:
                tess.setNormal(0.0F, 0.0F, 1.0F);
                tess.addVertexWithUV(INSET, INSET, 1.0D - PLATE, uMin, vMin);
                tess.addVertexWithUV(OUTER, INSET, 1.0D - PLATE, uMax, vMin);
                tess.addVertexWithUV(OUTER, OUTER, 1.0D - PLATE, uMax, vMax);
                tess.addVertexWithUV(INSET, OUTER, 1.0D - PLATE, uMin, vMax);
                break;
            case WEST:
                tess.setNormal(-1.0F, 0.0F, 0.0F);
                tess.addVertexWithUV(0.0D, INSET, OUTER, uMax, vMin);
                tess.addVertexWithUV(0.0D, INSET, INSET, uMin, vMin);
                tess.addVertexWithUV(0.0D, OUTER, INSET, uMin, vMax);
                tess.addVertexWithUV(0.0D, OUTER, OUTER, uMax, vMax);
                break;
            case EAST:
                tess.setNormal(1.0F, 0.0F, 0.0F);
                tess.addVertexWithUV(1.0D - PLATE, INSET, INSET, uMin, vMin);
                tess.addVertexWithUV(1.0D - PLATE, INSET, OUTER, uMax, vMin);
                tess.addVertexWithUV(1.0D - PLATE, OUTER, OUTER, uMax, vMax);
                tess.addVertexWithUV(1.0D - PLATE, OUTER, INSET, uMin, vMax);
                break;
            default:
                break;
        }
        tess.draw();
    }
}
