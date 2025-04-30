package com.imgood.advancedatamonitor.loader;

import com.imgood.advancedatamonitor.renders.RenderAdvanceDataMonotor;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceDataMonotor;
import cpw.mods.fml.client.registry.ClientRegistry;

public class LoaderRender {
    public static void registerRenderers() {
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityAdvanceDataMonotor.class, new RenderAdvanceDataMonotor());
    }
}
