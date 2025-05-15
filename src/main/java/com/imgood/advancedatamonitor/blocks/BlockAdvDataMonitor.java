package com.imgood.advancedatamonitor.blocks;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceDataMonotor;

public class BlockAdvDataMonitor extends BlockContainer {

    public BlockAdvDataMonitor() {
        super(Material.iron);
        this.setBlockName("advDataMonitor");
        this.setBlockTextureName(AdvanceDataMonitor.MODID + ":adv_data_monitor");
        this.setHardness(2.0F);
        this.setResistance(5.0F);
        this.setCreativeTab(CreativeTabs.tabRedstone);
        this.lightOpacity = 15;
        this.lightValue = 15;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityAdvanceDataMonotor();
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
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack itemIn) {
        int direction = MathHelper.floor_double((double) ((placer.rotationYaw + 180) * 4.0F / 360.0F) + 0.5D) & 3;
        TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (tileEntity instanceof TileEntityAdvanceDataMonotor) {
            ((TileEntityAdvanceDataMonotor) tileEntity).facing = direction;
        }
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {

        TileEntityAdvanceDataMonotor te = (TileEntityAdvanceDataMonotor) world.getTileEntity(x, y, z);
        player.openGui(AdvanceDataMonitor.instance, 1, world, x, y, z);

        return true;
    }
}
