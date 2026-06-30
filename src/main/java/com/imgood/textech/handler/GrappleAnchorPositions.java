package com.imgood.textech.handler;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.imgood.textech.loader.LoaderBlock;
import com.imgood.textech.tileentity.TileEntityGrappleAnchor;

public final class GrappleAnchorPositions {

    private GrappleAnchorPositions() {}

    public static ForgeDirection getAttachFace(IBlockAccess world, int x, int y, int z) {
        if (world == null) {
            return ForgeDirection.NORTH;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityGrappleAnchor) {
            return ((TileEntityGrappleAnchor) te).getAttachFace();
        }
        if (world.getBlock(x, y, z) == LoaderBlock.grappleAnchor) {
            ForgeDirection face = ForgeDirection.getOrientation(world.getBlockMetadata(x, y, z));
            if (face != null && face != ForgeDirection.UNKNOWN) {
                return face;
            }
        }
        return ForgeDirection.NORTH;
    }

    public static ForgeDirection getAttachFace(World world, int x, int y, int z) {
        return getAttachFace((IBlockAccess) world, x, y, z);
    }

    public static boolean isFloorMounted(World world, int x, int y, int z) {
        return getAttachFace(world, x, y, z) == ForgeDirection.DOWN;
    }

    /** Player hang / slide endpoint (physics). Centered under the anchor block column. */
    public static double[] resolveHangPosition(World world, int x, int y, int z) {
        if (isFloorMounted(world, x, y, z)) {
            return new double[] { x + 0.5D, y + 2.0D, z + 0.5D };
        }
        return new double[] { x + 0.5D, y - 1.0D, z + 0.5D };
    }

    /** Center of a block at integer coordinates. */
    public static double[] blockCenter(int bx, int by, int bz) {
        return new double[] { bx + 0.5D, by + 0.5D, bz + 0.5D };
    }

    /** Center of the block directly below the anchor (icon / aim / line anchor). */
    public static double[] resolveNodeIconBlockCenter(int anchorX, int anchorY, int anchorZ) {
        return blockCenter(anchorX, anchorY - 1, anchorZ);
    }

    /**
     * Billboard anchor in world space: top edge flush with the node bottom face so the icon
     * occupies the block below even when scaled up ({@code anchorY - iconHalfSize}).
     */
    public static double[] resolveNodeIconBillboardAnchor(int anchorX, int anchorY, int anchorZ, float iconHalfSize) {
        return new double[] { anchorX + 0.5D, anchorY - iconHalfSize, anchorZ + 0.5D };
    }

    /** Icon and preview line anchor (client HUD). */
    public static double[] resolveNodeRenderPosition(World world, int x, int y, int z) {
        return resolveNodeIconBlockCenter(x, y, z);
    }
}
