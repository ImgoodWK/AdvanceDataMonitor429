package com.imgood.textech.items;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.gui.handler.GuiHandler;

/**
 * Display names / 显示名称:
 * - EN: AdvanceDataMonitor Manual
 * - ZH: 高级数据监视器手�?
 * Lang keys: item.manual.name
 *
 * Right-click to open the in-game manual GUI.
 * First item received when a player joins a world for the first time.
 */
public class ItemManual extends Item {

    public ItemManual() {
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabTools);
        setUnlocalizedName("manual");
        setTextureName(AdvanceDataMonitor.MODID + ":manual");
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) {
            player.openGui(
                AdvanceDataMonitor.instance,
                GuiHandler.MANUAL_GUI_ID,
                world,
                (int) player.posX,
                (int) player.posY,
                (int) player.posZ);
        }
        return stack;
    }
}
