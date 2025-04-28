package com.imgood.advancedatamonitor.loader;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.renders.RenderAdvanceDataMonotor;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceDataMonotor;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.registry.GameRegistry;

public class RenderLoader {
    public static void registerRenderers() {
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityAdvanceDataMonotor.class, new RenderAdvanceDataMonotor());
    }
}
