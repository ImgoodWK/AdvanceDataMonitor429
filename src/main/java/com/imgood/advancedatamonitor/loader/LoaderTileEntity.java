package com.imgood.advancedatamonitor.loader;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceCraftingLink;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceNetworkLink;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceStorageLink;
import com.imgood.advancedatamonitor.tileentity.TileEntityGrappleAnchor;

import cpw.mods.fml.common.registry.GameRegistry;

public class LoaderTileEntity {

    public static void registerTileEntities() {
        GameRegistry.registerTileEntity(
            TileEntityAdvanceDataMonitor.class,
            AdvanceDataMonitor.MODID + "TileEntityAdvanceDataMonitor");
        GameRegistry.registerTileEntity(
            TileEntityAdvanceNetworkLink.class,
            AdvanceDataMonitor.MODID + "TileEntityAdvanceNetworkLink");
        GameRegistry.registerTileEntity(
            TileEntityAdvanceStorageLink.class,
            AdvanceDataMonitor.MODID + "TileEntityAdvanceStorageLink");
        GameRegistry.registerTileEntity(
            TileEntityAdvanceCraftingLink.class,
            AdvanceDataMonitor.MODID + "TileEntityAdvanceCraftingLink");
        GameRegistry
            .registerTileEntity(TileEntityGrappleAnchor.class, AdvanceDataMonitor.MODID + "TileEntityGrappleAnchor");

    }
}
