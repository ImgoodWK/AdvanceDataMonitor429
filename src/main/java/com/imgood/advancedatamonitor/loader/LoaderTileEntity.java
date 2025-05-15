package com.imgood.advancedatamonitor.loader;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceDataMonitor;

import cpw.mods.fml.common.registry.GameRegistry;

public class LoaderTileEntity {

    public static void registerTileEntities() {
        GameRegistry
            .registerTileEntity(TileEntityAdvanceDataMonitor.class, AdvanceDataMonitor.MODID + ":advancedatamonitor");
    }
}
