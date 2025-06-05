package com.imgood.advancedatamonitor.loader;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;

import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceNetworkLink;
import cpw.mods.fml.common.registry.GameRegistry;
import shedar.mods.ic2.nuclearcontrol.crossmod.appeng.TileEntityNetworkLink;

public class LoaderTileEntity {

    public static void registerTileEntities() {
        GameRegistry.registerTileEntity(TileEntityAdvanceDataMonitor.class, AdvanceDataMonitor.MODID + ":advancedatamonitor");
        GameRegistry.registerTileEntity(TileEntityAdvanceNetworkLink.class, AdvanceDataMonitor.MODID + "TileEntityNetworkLink");
    }
}
