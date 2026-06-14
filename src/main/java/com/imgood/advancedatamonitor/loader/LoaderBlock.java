package com.imgood.advancedatamonitor.loader;

import net.minecraft.block.Block;

import com.imgood.advancedatamonitor.blocks.BlockAdvanceCraftingLink;
import com.imgood.advancedatamonitor.blocks.BlockAdvanceDataMonitor;
import com.imgood.advancedatamonitor.blocks.BlockAdvanceNetworkLink;
import com.imgood.advancedatamonitor.blocks.BlockAdvanceStorageLink;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * @program: AdvanceDataMonitor
 * @description:
 * @author: Imgood
 * @create: 2025-04-08 11:12
 **/
public class LoaderBlock {

    public static Block advanceDataMonitor;
    public static BlockAdvanceNetworkLink advanceNetworkLinkBlock;
    public static BlockAdvanceStorageLink advanceStorageLinkBlock;
    public static BlockAdvanceCraftingLink advanceCraftingLink;

    public static void registerBlocks() {
        advanceDataMonitor = new BlockAdvanceDataMonitor();
        advanceNetworkLinkBlock = new BlockAdvanceNetworkLink();
        advanceStorageLinkBlock = new BlockAdvanceStorageLink();
        advanceCraftingLink = new BlockAdvanceCraftingLink();

        GameRegistry.registerBlock(advanceDataMonitor, "advDataMonitor");
        GameRegistry.registerBlock(advanceNetworkLinkBlock, "advNetworkLinkBlock");
        GameRegistry.registerBlock(advanceStorageLinkBlock, "advStorageLink");
        GameRegistry.registerBlock(advanceCraftingLink, "advCraftingLink");
    }
}
