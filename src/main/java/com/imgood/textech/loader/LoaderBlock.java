package com.imgood.textech.loader;

import net.minecraft.block.Block;

import com.imgood.textech.blocks.BlockAdvanceCraftingLink;
import com.imgood.textech.blocks.BlockAdvanceDataMonitor;
import com.imgood.textech.blocks.BlockAdvanceNetworkLink;
import com.imgood.textech.blocks.BlockAdvanceStorageLink;
import com.imgood.textech.blocks.BlockGrappleAnchor;
import com.imgood.textech.items.ItemBlockGrappleAnchor;

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
    public static BlockGrappleAnchor grappleAnchor;

    public static void registerBlocks() {
        advanceDataMonitor = new BlockAdvanceDataMonitor();
        advanceNetworkLinkBlock = new BlockAdvanceNetworkLink();
        advanceStorageLinkBlock = new BlockAdvanceStorageLink();
        advanceCraftingLink = new BlockAdvanceCraftingLink();
        grappleAnchor = new BlockGrappleAnchor();

        GameRegistry.registerBlock(advanceDataMonitor, "advDataMonitor");
        GameRegistry.registerBlock(advanceNetworkLinkBlock, "advNetworkLinkBlock");
        GameRegistry.registerBlock(advanceStorageLinkBlock, "advStorageLink");
        GameRegistry.registerBlock(advanceCraftingLink, "advCraftingLink");
        GameRegistry.registerBlock(grappleAnchor, ItemBlockGrappleAnchor.class, "grappleAnchor");
    }
}
