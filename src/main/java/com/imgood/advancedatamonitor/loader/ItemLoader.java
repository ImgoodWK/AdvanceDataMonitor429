package com.imgood.advancedatamonitor.loader;

import com.imgood.advancedatamonitor.items.DataWeave;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.Item;

/**
 * @program: AdvanceDataMonitor
 * @description:
 * @author: Imgood
 * @create: 2025-04-08 11:12
 **/
public class ItemLoader {
    public static Item dataWeave;

    public static void registerItems() {
        dataWeave = new DataWeave().setUnlocalizedName("dataWeave").setTextureName("advancedatamonitor:data_weave");

        GameRegistry.registerItem(dataWeave, "data_weave");
    }
}
