package com.imgood.advancedatamonitor.loader;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.imgood.advancedatamonitor.items.ItemAdvancePlanner;
import com.imgood.advancedatamonitor.items.ItemAdvanceStorageLinkCell;
import com.imgood.advancedatamonitor.items.ItemDataWeave;
import com.imgood.advancedatamonitor.items.ItemOrange;

import appeng.api.config.Upgrades;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * @program: AdvanceDataMonitor
 * @description:
 * @author: Imgood
 * @create: 2025-04-08 11:12
 **/
public class LoaderItem {

    public static Item dataWeave;
    public static Item advanceStorageLinkCell;
    public static Item advancePlanner;
    public static Item orange;

    public static void registerItems() {
        dataWeave = new ItemDataWeave().setUnlocalizedName("dataWeave")
            .setTextureName("advancedatamonitor:data_weave");
        advanceStorageLinkCell = new ItemAdvanceStorageLinkCell().setUnlocalizedName("advanceStorageLinkCell")
            .setTextureName("advancedatamonitor:advance_storage_link_cell");
        advancePlanner = new ItemAdvancePlanner().setUnlocalizedName("advancePlanner")
            .setTextureName("advancedatamonitor:advance_planner");
        orange = new ItemOrange().setUnlocalizedName("orange")
            .setTextureName("advancedatamonitor:orange");

        GameRegistry.registerItem(dataWeave, "data_weave");
        GameRegistry.registerItem(advanceStorageLinkCell, "advance_storage_link_cell");
        GameRegistry.registerItem(advancePlanner, "advance_planner");
        GameRegistry.registerItem(orange, "orange");

        Upgrades.FUZZY.registerItem(new ItemStack(advanceStorageLinkCell), 1);
        Upgrades.INVERTER.registerItem(new ItemStack(advanceStorageLinkCell), 1);
    }
}
