package com.imgood.advancedatamonitor.loader;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceDataMonotor;
import cpw.mods.fml.common.registry.GameRegistry;

public class TileEntityLoader {
    public static void registerTileEntities()
    {
        GameRegistry.registerTileEntity(TileEntityAdvanceDataMonotor.class, AdvanceDataMonitor.MODID+ ":advancedatamonitor");
    }
}
