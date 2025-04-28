package com.imgood.advancedatamonitor.blocks;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceDataMonotor;
import java.util.Random;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class BlockAdvDataMonitor extends BlockContainer {

    public BlockAdvDataMonitor() {
        super(Material.iron);
        this.setBlockName("advDataMonitor");
        this.setBlockTextureName(AdvanceDataMonitor.MODID + ":adv_data_monitor"); // 需要准备对应材质
        this.setHardness(2.0F);
        this.setResistance(5.0F);
        this.setCreativeTab(CreativeTabs.tabRedstone); // 放在创造模式物品栏
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityAdvanceDataMonotor();
    }

    @Override
    public int getRenderType() {
        return -1; // 需要自定义渲染时需要修改这个值
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
    public boolean onBlockActivated(World world, int x, int y, int z,
                                    EntityPlayer player,
                                    int side,
                                    float hitX,
                                    float hitY,
                                    float hitZ) {
        if (world.isRemote) {
            TileEntityAdvanceDataMonotor te = (TileEntityAdvanceDataMonotor) world.getTileEntity(x, y, z);
            if (te != null) {
                // 清空旧数据
                //te.getDataValues().clear();
                // 添加测试数据
                te.setScale(5f);
                te.setRotationY(45f);
                te.setHeightOffset(2f);
                double randomValue = te.getYMin() + Math.random() * (te.getYMax() - te.getYMin());
                te.addData(randomValue);
                player.addChatMessage(new ChatComponentText("已生成测试数据！"+randomValue));
            }
        }
        return true;
    }
}