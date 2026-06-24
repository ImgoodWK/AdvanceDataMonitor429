package com.imgood.advancedatamonitor.blocks;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.gui.handler.GuiHandler;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceStorageLink;

/**
 * Display names / 显示名称:
 * - EN: Advanced Storage Linker
 * - ZH: 高级存储链接器
 * Lang keys: tile.StorageLinkBlock.name, adm.title.storage
 */
public class BlockAdvanceStorageLink extends BlockContainer {

    public BlockAdvanceStorageLink() {
        super(Material.iron);
        this.setHardness(3.0F);
        this.setResistance(10.0F);
        this.setStepSound(soundTypeMetal);
        this.setCreativeTab(CreativeTabs.tabRedstone);
        this.setBlockName("StorageLinkBlock");
        this.setBlockTextureName(AdvanceDataMonitor.MODID + ":adv_storage_link");
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityAdvanceStorageLink();
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityAdvanceStorageLink) {
            ((TileEntityAdvanceStorageLink) te).setOwnerFromPlacer(placer);
        }
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        AdvanceDataMonitor.LOG.info("StorageLink activated");
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityAdvanceStorageLink) {
            ((TileEntityAdvanceStorageLink) te).handleRightClick(player);
            player.openGui(AdvanceDataMonitor.instance, GuiHandler.ADM_STORAGELINK_ID, world, x, y, z);
            return true;
        }
        return false;
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
