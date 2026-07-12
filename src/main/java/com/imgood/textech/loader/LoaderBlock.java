package com.imgood.textech.loader;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;

import com.imgood.textech.Config;
import com.imgood.textech.blocks.BlockAdvanceDataMonitor;
import com.imgood.textech.blocks.BlockAdvanceNetworkLink;
import com.imgood.textech.blocks.BlockGrappleAnchor;
import com.imgood.textech.blocks.BlockMatterBallDecompressor;
import com.imgood.textech.blocks.BlockUiFrameworkDebug;
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
    /**
     * Legacy registry aliases kept so existing worlds never hit missing-mapping
     * corruption after the three linkers were merged. Same TE/behavior as
     * {@link #advanceNetworkLinkBlock}; hidden from creative tabs.
     */
    public static BlockAdvanceNetworkLink advanceStorageLinkAlias;
    public static BlockAdvanceNetworkLink advanceCraftingLinkAlias;
    public static BlockGrappleAnchor grappleAnchor;
    public static BlockMatterBallDecompressor matterBallDecompressor;
    public static BlockUiFrameworkDebug uiFrameworkDebug;

    public static void registerBlocks() {
        advanceDataMonitor = new BlockAdvanceDataMonitor();
        advanceNetworkLinkBlock = new BlockAdvanceNetworkLink();
        advanceStorageLinkAlias = createLegacyLinkAlias();
        advanceCraftingLinkAlias = createLegacyLinkAlias();
        grappleAnchor = new BlockGrappleAnchor();
        matterBallDecompressor = new BlockMatterBallDecompressor();

        GameRegistry.registerBlock(advanceDataMonitor, "advDataMonitor");
        GameRegistry.registerBlock(advanceNetworkLinkBlock, "advNetworkLinkBlock");
        // Keep old registry names registered — critical for 1.7.10 world ID maps.
        GameRegistry.registerBlock(advanceStorageLinkAlias, "advStorageLink");
        GameRegistry.registerBlock(advanceCraftingLinkAlias, "advCraftingLink");
        GameRegistry.registerBlock(grappleAnchor, ItemBlockGrappleAnchor.class, "grappleAnchor");
        GameRegistry.registerBlock(matterBallDecompressor, "matterBallDecompressor");

        if (Config.debugUiFrameworkBlock) {
            uiFrameworkDebug = new BlockUiFrameworkDebug();
            GameRegistry.registerBlock(uiFrameworkDebug, "uiFrameworkDebug");
        }
    }

    private static BlockAdvanceNetworkLink createLegacyLinkAlias() {
        BlockAdvanceNetworkLink alias = new BlockAdvanceNetworkLink();
        alias.setCreativeTab((CreativeTabs) null);
        return alias;
    }
}
