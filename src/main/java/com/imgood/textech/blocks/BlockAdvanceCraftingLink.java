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
import net.minecraft.world.World;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.tileentity.TileEntityAdvanceCraftingLink;

/**
 * Display names / 显示名称:
 * - EN: Crafting Linker
 * - ZH: 合成链接�?
 * Lang keys: tile.CraftingMonitorBlock.name
 */
public class BlockAdvanceCraftingLink extends BlockContainer {

    private static final int UPDATE_INTERVAL = 20;

    public BlockAdvanceCraftingLink() {
        super(Material.iron);
        this.setHardness(3.0F);
        this.setResistance(10.0F);
        this.setStepSound(soundTypeMetal);
        this.setCreativeTab(CreativeTabs.tabRedstone);
        this.setBlockName("CraftingMonitorBlock");
        this.setBlockTextureName(AdvanceDataMonitor.MODID + ":adv_crafting_link");
        this.setTickRandomly(true); // 允许接收计划�?
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityAdvanceCraftingLink();
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityAdvanceCraftingLink) {
            ((TileEntityAdvanceCraftingLink) te).setOwnerFromPlacer(placer);
        }
    }

    // ---------- 方块放置时启动计划刻 ----------
    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        if (!world.isRemote) {
            world.scheduleBlockUpdate(x, y, z, this, UPDATE_INTERVAL);
        }
    }

    // ---------- 计划刻回调：更新数据并重新调�?----------
    @Override
    public void updateTick(World world, int x, int y, int z, Random random) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileEntityAdvanceCraftingLink) {
                ((TileEntityAdvanceCraftingLink) te).updateCraftingStats();
            }
            // 重新调度下一次更新（实现循环定时�?
            world.scheduleBlockUpdate(x, y, z, this, UPDATE_INTERVAL);
        }
    }

    // ---------- 右键交互：强制刷新并显示 ----------
    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileEntityAdvanceCraftingLink) {
                TileEntityAdvanceCraftingLink monitor = (TileEntityAdvanceCraftingLink) te;
                // 手动强制刷新
                monitor.updateCraftingStats();
                // 显示信息（已包含所有指标）
                player.addChatMessage(new ChatComponentText(monitor.getStatsInfo()));
                return true;
            }
        }
        return false;
    }

    // ========== 渲染相关（保留原有设置） ==========
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
