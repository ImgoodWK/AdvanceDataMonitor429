package com.imgood.advancedatamonitor.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.Config;
import com.imgood.advancedatamonitor.handler.GrappleAnchorPositions;
import com.imgood.advancedatamonitor.handler.GrappleNodeIndex;
import com.imgood.advancedatamonitor.handler.GrapplePlayerState;
import com.imgood.advancedatamonitor.gui.handler.GuiHandler;
import com.imgood.advancedatamonitor.items.ItemGrappleHook;
import com.imgood.advancedatamonitor.tileentity.TileEntityGrappleAnchor;

/**
 * Display names / 显示名称:
 * - EN: Grapple Anchor
 * - ZH: 挂索节点
 * Lang keys: tile.grappleAnchor.name, adm.title.grappleAnchor, adm.title.grappleAnchorConfig
 */
public class BlockGrappleAnchor extends BlockContainer {

    private static final float PLATE = 0.28125F;

    public BlockGrappleAnchor() {
        super(Material.iron);
        this.setBlockName("grappleAnchor");
        this.setBlockTextureName(AdvanceDataMonitor.MODID + ":grapple_anchor");
        this.setHardness(2.0F);
        this.setResistance(5.0F);
        this.setCreativeTab(CreativeTabs.tabRedstone);
        this.lightOpacity = 0;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityGrappleAnchor();
    }

    @Override
    public int getRenderType() {
        return -1;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public boolean canPlaceBlockOnSide(World world, int x, int y, int z, int side) {
        ForgeDirection clicked = ForgeDirection.getOrientation(side);
        if (clicked == ForgeDirection.UNKNOWN) {
            return false;
        }
        ForgeDirection supportDir = clicked.getOpposite();
        int sx = x + supportDir.offsetX;
        int sy = y + supportDir.offsetY;
        int sz = z + supportDir.offsetZ;
        Block support = world.getBlock(sx, sy, sz);
        return support != null && support.isSideSolid(world, sx, sy, sz, clicked);
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        ForgeDirection attach = ForgeDirection.getOrientation(world.getBlockMetadata(x, y, z));
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityGrappleAnchor) {
            ((TileEntityGrappleAnchor) te).setAttachFace(attach);
        }
    }

    @Override
    public int onBlockPlaced(World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ, int meta) {
        ForgeDirection clicked = ForgeDirection.getOrientation(side);
        if (clicked == ForgeDirection.UNKNOWN) {
            return meta;
        }
        return clicked.getOpposite()
            .ordinal();
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        ForgeDirection face = GrappleAnchorPositions.getAttachFace(world, x, y, z);
        setBoundsForFace(face);
    }

    /** Thin plate on the face that touches the support block ({@code attachFace}). */
    private void setBoundsForFace(ForgeDirection face) {
        switch (face) {
            case DOWN:
                this.setBlockBounds(0.2F, 0.0F, 0.2F, 0.8F, PLATE, 0.8F);
                break;
            case UP:
                this.setBlockBounds(0.2F, 1.0F - PLATE, 0.2F, 0.8F, 1.0F, 0.8F);
                break;
            case NORTH:
                this.setBlockBounds(0.2F, 0.2F, 0.0F, 0.8F, 0.8F, PLATE);
                break;
            case SOUTH:
                this.setBlockBounds(0.2F, 0.2F, 1.0F - PLATE, 0.8F, 0.8F, 1.0F);
                break;
            case WEST:
                this.setBlockBounds(0.0F, 0.2F, 0.2F, PLATE, 0.8F, 0.8F);
                break;
            case EAST:
                this.setBlockBounds(1.0F - PLATE, 0.2F, 0.2F, 1.0F, 0.8F, 0.8F);
                break;
            default:
                this.setBlockBounds(0.2F, 0.2F, 0.0F, 0.8F, 0.8F, PLATE);
                break;
        }
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        this.setBlockBoundsBasedOnState(world, x, y, z);
        return super.getCollisionBoundingBoxFromPool(world, x, y, z);
    }

    @Override
    public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int x, int y, int z) {
        this.setBlockBoundsBasedOnState(world, x, y, z);
        return super.getSelectedBoundingBoxFromPool(world, x, y, z);
    }

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        if (!world.isRemote) {
            GrappleNodeIndex.INSTANCE.addNode(world.provider.dimensionId, x, y, z);
        }
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (!world.isRemote) {
            GrappleNodeIndex.INSTANCE.removeNode(world.provider.dimensionId, x, y, z);
            GrapplePlayerState.onAnchorBroken(world.provider.dimensionId, x, y, z);
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (player.isSneaking()) {
            if (!world.isRemote) {
                player.openGui(AdvanceDataMonitor.instance, GuiHandler.GRAPPLE_ANCHOR_GUI_ID, world, x, y, z);
            }
            return true;
        }
        if (world.isRemote) {
            return true;
        }
        if (!ItemGrappleHook.isHoldingHook(player)) {
            return false;
        }
        double distSq = player.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D);
        double maxDist = Config.grappleInteractRange;
        if (distSq > maxDist * maxDist) {
            return false;
        }
        if (GrapplePlayerState.isAttached(player)) {
            GrapplePlayerState.travelTo(player, x, y, z);
        } else {
            GrapplePlayerState.attach(player, world.provider.dimensionId, x, y, z);
        }
        return true;
    }
}
