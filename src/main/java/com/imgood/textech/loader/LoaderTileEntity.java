package com.imgood.textech.loader;

import net.minecraft.tileentity.TileEntity;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.tileentity.TileEntityAdvanceNetworkLink;
import com.imgood.textech.tileentity.TileEntityGrappleAnchor;
import com.imgood.textech.tileentity.TileEntityMatterBallDecompressor;

import cpw.mods.fml.common.registry.GameRegistry;

public class LoaderTileEntity {

    public static void registerTileEntities() {
        GameRegistry.registerTileEntity(TileEntityAdvanceDataMonitor.class, "texDataMonitor");
        GameRegistry.registerTileEntity(TileEntityGrappleAnchor.class, "texGrappleAnchor");
        GameRegistry.registerTileEntity(TileEntityMatterBallDecompressor.class, "texMatterBallDecompressor");

        // Legacy / merged link TE names must be registered BEFORE the canonical id so
        // classToNameMap keeps "texNetworkLink" for newly saved tiles.
        addLegacyMapping(TileEntityAdvanceNetworkLink.class, "texStorageLink");
        addLegacyMapping(TileEntityAdvanceNetworkLink.class, "texCraftingLink");
        addLegacyMapping(TileEntityAdvanceNetworkLink.class, "advancedatamonitorTileEntityAdvanceNetworkLink");
        addLegacyMapping(TileEntityAdvanceNetworkLink.class, "advancedatamonitorTileEntityAdvanceStorageLink");
        addLegacyMapping(TileEntityAdvanceNetworkLink.class, "advancedatamonitorTileEntityAdvanceCraftingLink");
        GameRegistry.registerTileEntity(TileEntityAdvanceNetworkLink.class, "texNetworkLink");

        addLegacyMapping(TileEntityAdvanceDataMonitor.class, "advancedatamonitorTileEntityAdvanceDataMonitor");
        addLegacyMapping(TileEntityGrappleAnchor.class, "advancedatamonitorTileEntityGrappleAnchor");
    }

    private static void addLegacyMapping(Class<? extends TileEntity> clazz, String legacyId) {
        TileEntity.addMapping(clazz, legacyId);
        AdvanceDataMonitor.LOG.info("[TeXTech] Registered legacy TE mapping: {}", legacyId);
    }
}
