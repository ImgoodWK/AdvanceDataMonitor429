package com.imgood.advancedatamonitor.blocks;

import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceNetworkLink;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import shedar.mods.ic2.nuclearcontrol.crossmod.appeng.BlockNetworkLink;
import shedar.mods.ic2.nuclearcontrol.crossmod.appeng.TileEntityNetworkLink;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockAdvanceNetworkLink extends BlockContainer{

    @SideOnly(Side.CLIENT)
    private IIcon frontIcon;
    @SideOnly(Side.CLIENT)
    private IIcon sideIcon;
    @SideOnly(Side.CLIENT)
    private IIcon topIcon;
    @SideOnly(Side.CLIENT)
    private IIcon bottomIcon;

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

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister iconRegister) {
        this.frontIcon = iconRegister.registerIcon("nuclearcontrol:network_link_front");
        this.sideIcon = iconRegister.registerIcon("nuclearcontrol:network_link_side");
        this.topIcon = iconRegister.registerIcon("nuclearcontrol:network_link_top");
        this.bottomIcon = iconRegister.registerIcon("nuclearcontrol:network_link_bottom");
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(int side, int meta) {
        // meta 存储了朝向信息: 0-下, 1-上, 2-北, 3-南, 4-西, 5-东
        int facing = meta & 7;

        // 前面图标
        if (side == facing) {
            return frontIcon;
        }

        // 顶部和底部图标
        if (side == 0) return bottomIcon;
        if (side == 1) return topIcon;

        // 其他面使用侧面图标
        return sideIcon;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        // 确定朝向：玩家面对的方向
        int facing = determineFacing(world, x, y, z, placer);
        world.setBlockMetadataWithNotify(x, y, z, facing, 2);

        super.onBlockPlacedBy(world, x, y, z, placer, stack);
    }

    private int determineFacing(World world, int x, int y, int z, EntityLivingBase placer) {
        // 玩家面对的方向就是方块的前面
        int facing = MathHelper.floor_double((placer.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;

        switch (facing) {
            case 0: return 2; // 北
            case 1: return 5; // 东
            case 2: return 3; // 南
            case 3: return 4; // 西
            default: return 2; // 默认北
        }
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
