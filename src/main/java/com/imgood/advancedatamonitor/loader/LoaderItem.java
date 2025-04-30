package com.imgood.advancedatamonitor.loader;

import com.imgood.advancedatamonitor.items.ItemDataWeave;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.Item;

/**
 * @program: AdvanceDataMonitor
 * @description:
 * @author: Imgood
 * @create: 2025-04-08 11:12
 **/
public class LoaderItem {
    public static Item dataWeave;

    public static void registerItems() {
        dataWeave = new ItemDataWeave().setUnlocalizedName("dataWeave").setTextureName("advancedatamonitor:data_weave");

        GameRegistry.registerItem(dataWeave, "data_weave");
    }
}
