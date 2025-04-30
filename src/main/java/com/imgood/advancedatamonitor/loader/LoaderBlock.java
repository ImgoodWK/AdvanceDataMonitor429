package com.imgood.advancedatamonitor.loader;

import com.imgood.advancedatamonitor.blocks.BlockAdvDataMonitor;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;

/**
 * @program: AdvanceDataMonitor
 * @description:
 * @author: Imgood
 * @create: 2025-04-08 11:12
 **/
public class LoaderBlock {
    public static Block advDataMonitor;
    public static void registerBlocks() {
        advDataMonitor = new BlockAdvDataMonitor();
        GameRegistry.registerBlock(advDataMonitor, "advDataMonitor");
    }
}
