package com.imgood.advancedatamonitor.loader;

import com.imgood.advancedatamonitor.blocks.BlockAdvanceNetworkLink;
import net.minecraft.block.Block;

import com.imgood.advancedatamonitor.blocks.BlockAdvDataMonitor;

import cpw.mods.fml.common.registry.GameRegistry;
import shedar.mods.ic2.nuclearcontrol.crossmod.appeng.BlockNetworkLink;

/**
 * @program: AdvanceDataMonitor
 * @description:
 * @author: Imgood
 * @create: 2025-04-08 11:12
 **/
public class LoaderBlock {

    public static Block advDataMonitor;
    public static BlockAdvanceNetworkLink networkLinkBlock;
    public static void registerBlocks() {
        advDataMonitor = new BlockAdvDataMonitor();
        networkLinkBlock = new BlockAdvanceNetworkLink();
        GameRegistry.registerBlock(advDataMonitor, "advDataMonitor");
        GameRegistry.registerBlock(networkLinkBlock, "advNetworkLinkBlock");
    }
}
