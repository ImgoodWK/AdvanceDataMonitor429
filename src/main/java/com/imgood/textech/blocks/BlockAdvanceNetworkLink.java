package com.imgood.textech.blocks;

import java.util.Random;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.tileentity.TileEntityAdvanceNetworkLink;

/**
 * Display names / 显示名称:
 * - EN: Network Linker
 * - ZH: 网络链接�?
 * Lang keys: tile.NetworkLinkBlock.name, adm.title.data_config_ae_network
 */
public class BlockAdvanceNetworkLink extends BlockContainer {

    // 更新间隔（tick），1 = 每tick�?0 = 每秒。建议根据网络大小调整，避免性能问题�?
    private static final int UPDATE_INTERVAL = 20; // 可改�?20 或更�?

    public BlockAdvanceNetworkLink() {
        super(Material.iron);
        this.setHardness(3.0F);
        this.setResistance(10.0F);
        this.setStepSound(soundTypeMetal);
        this.setCreativeTab(CreativeTabs.tabRedstone);
        this.setBlockName("NetworkLinkBlock");
        this.setBlockTextureName(AdvanceDataMonitor.MODID + ":adv_network_link");
        this.setTickRandomly(true); // 允许接收计划�?
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityAdvanceNetworkLink();
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        int direction = MathHelper.floor_double((double) ((placer.rotationYaw + 180) * 4.0F / 360.0F) + 0.5D) & 3;
        TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (tileEntity instanceof TileEntityAdvanceNetworkLink) {
            TileEntityAdvanceNetworkLink link = (TileEntityAdvanceNetworkLink) tileEntity;
            link.facing = direction;
            link.setOwnerFromPlacer(placer);
        }
    }

    private int determineFacing(World world, int x, int y, int z, EntityLivingBase placer) {
        // 玩家面对的方向就是方块的前面
        int facing = MathHelper.floor_double((placer.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
        switch (facing) {
            case 0:
                return 2; // �?
            case 1:
                return 5; // �?
            case 2:
                return 3; // �?
            case 3:
                return 4; // �?
            default:
                return 2;
        }
    }

    // ---------- 定时刷新（计划刻�?----------
    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        if (!world.isRemote) {
            world.scheduleBlockUpdate(x, y, z, this, UPDATE_INTERVAL);
        }
    }

    @Override
    public void updateTick(World world, int x, int y, int z, Random random) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileEntityAdvanceNetworkLink) {
                ((TileEntityAdvanceNetworkLink) te).updateNetworkCache();
            }
            // 重新调度，形成循�?
            world.scheduleBlockUpdate(x, y, z, this, UPDATE_INTERVAL);
        }
    }

    // ---------- 右键交互（保留原有逐条显示逻辑�?----------
    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileEntityAdvanceNetworkLink) {
                TileEntityAdvanceNetworkLink link = (TileEntityAdvanceNetworkLink) te;

                // 手动强制刷新一�?
                link.updateNetworkCache();

                // 显示网络信息（保持原有格式）
                player.addChatMessage(new ChatComponentText("AE2 Network Status"));
                player.addChatMessage(
                    new ChatComponentText(
                        "Items: " + link.getItemUsedBytes() + "/" + link.getItemTotalBytes() + " bytes"));
                player.addChatMessage(
                    new ChatComponentText(
                        "Fluids: " + link.getFluidUsedBytes() + "/" + link.getFluidTotalBytes() + " bytes"));
                player.addChatMessage(
                    new ChatComponentText("Item Types: " + link.getItemUsedTypes() + "/" + link.getItemTotalTypes()));
                player.addChatMessage(
                    new ChatComponentText(
                        "Fluid Types: " + link.getFluidUsedTypes() + "/" + link.getFluidTotalTypes()));

                return true;
            }
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
