package com.imgood.textech.loader;

import net.minecraft.tileentity.TileEntity;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.tileentity.TileEntityAdvanceCraftingLink;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.tileentity.TileEntityAdvanceNetworkLink;
import com.imgood.textech.tileentity.TileEntityAdvanceStorageLink;
import com.imgood.textech.tileentity.TileEntityGrappleAnchor;
import com.imgood.textech.tileentity.TileEntityMatterBallDecompressor;

import cpw.mods.fml.common.registry.GameRegistry;

public class LoaderTileEntity {

    public static void registerTileEntities() {
        // Register with fixed short IDs (not prefixed by MODID) to survive future ID changes
        GameRegistry.registerTileEntity(TileEntityAdvanceDataMonitor.class, "texDataMonitor");
        GameRegistry.registerTileEntity(TileEntityAdvanceNetworkLink.class, "texNetworkLink");
        GameRegistry.registerTileEntity(TileEntityAdvanceStorageLink.class, "texStorageLink");
        GameRegistry.registerTileEntity(TileEntityAdvanceCraftingLink.class, "texCraftingLink");
        GameRegistry.registerTileEntity(TileEntityGrappleAnchor.class, "texGrappleAnchor");
        GameRegistry.registerTileEntity(TileEntityMatterBallDecompressor.class, "texMatterBallDecompressor");

        // Legacy ID mappings
        addLegacyMapping(TileEntityAdvanceDataMonitor.class, "advancedatamonitorTileEntityAdvanceDataMonitor");
        addLegacyMapping(TileEntityAdvanceNetworkLink.class, "advancedatamonitorTileEntityAdvanceNetworkLink");
        addLegacyMapping(TileEntityAdvanceStorageLink.class, "advancedatamonitorTileEntityAdvanceStorageLink");
        addLegacyMapping(TileEntityAdvanceCraftingLink.class, "advancedatamonitorTileEntityAdvanceCraftingLink");
        addLegacyMapping(TileEntityGrappleAnchor.class, "advancedatamonitorTileEntityGrappleAnchor");
    }

    private static void addLegacyMapping(Class<? extends TileEntity> clazz, String legacyId) {
        TileEntity.addMapping(clazz, legacyId);
        AdvanceDataMonitor.LOG.info("[TeXTech] Registered legacy TE mapping: {}", legacyId);
    }
}
