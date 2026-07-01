package com.imgood.textech.blocks;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.gui.handler.GuiHandler;
import com.imgood.textech.tileentity.TileEntityMatterBallDecompressor;
import com.imgood.textech.utils.AeSecurityCheck;

/**
 * Matter ball decompressor — extracts Avaritia matter clusters into AE network or local buffer.
 */
public class BlockMatterBallDecompressor extends BlockContainer {

    public BlockMatterBallDecompressor() {
        super(Material.iron);
        setHardness(3.0F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
        setCreativeTab(CreativeTabs.tabRedstone);
        setBlockName("matterBallDecompressor");
        setBlockTextureName(AdvanceDataMonitor.MODID + ":adv_matter_ball_decompressor");
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityMatterBallDecompressor();
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        // AE2 security check: reject placement if the player lacks BUILD
        // permission on an adjacent AE network with a security terminal.
        String denial = StatCollector.translateToLocal("adm.ae.no_build_permission");
        AeSecurityCheck.rejectIfUnauthorized(world, x, y, z, this, placer, denial);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (!world.isRemote) {
            player.openGui(AdvanceDataMonitor.instance, GuiHandler.MATTER_BALL_DECOMPRESSOR_GUI_ID, world, x, y, z);
        }
        return true;
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
}
