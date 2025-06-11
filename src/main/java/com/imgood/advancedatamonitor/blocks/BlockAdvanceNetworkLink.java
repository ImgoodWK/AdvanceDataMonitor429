package com.imgood.advancedatamonitor.blocks;

import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceNetworkLink;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockAdvanceNetworkLink extends BlockContainer {

    public BlockAdvanceNetworkLink() {
        super(Material.iron);
        this.setHardness(3.0F);
        this.setResistance(10.0F);
        this.setStepSound(soundTypeMetal);
        this.setCreativeTab(CreativeTabs.tabRedstone);
        this.setBlockName("NetworkLinkBlock");
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityAdvanceNetworkLink();
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
                                    int side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileEntityAdvanceNetworkLink) {
                TileEntityAdvanceNetworkLink link = (TileEntityAdvanceNetworkLink) te;

                // 显示网络信息
                player.addChatMessage(new ChatComponentText("AE2 Network Status"));
                player.addChatMessage(new ChatComponentText("Items: " +
                        link.getItemUsedBytes() + "/" + link.getItemTotalBytes() + " bytes"));
                player.addChatMessage(new ChatComponentText("Fluids: " +
                        link.getFluidUsedBytes() + "/" + link.getFluidTotalBytes() + " bytes"));
                player.addChatMessage(new ChatComponentText("Item Types: " +
                        link.getItemUsedTypes() + "/" + link.getItemTotalTypes()));
                player.addChatMessage(new ChatComponentText("Fluid Types: " +
                        link.getFluidUsedTypes() + "/" + link.getFluidTotalTypes()));
                return true;
            }
        }
        return false;
    }

}