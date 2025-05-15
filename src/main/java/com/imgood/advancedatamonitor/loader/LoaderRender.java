package com.imgood.advancedatamonitor.loader;

import com.imgood.advancedatamonitor.renders.LineChartRenderer;
import com.imgood.advancedatamonitor.renders.RenderAdvanceDataMonotor;
import com.imgood.advancedatamonitor.renders.RenderController;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceDataMonotor;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class LoaderRender {

    public static void registerRenderers() {
        ClientRegistry
            .bindTileEntitySpecialRenderer(TileEntityAdvanceDataMonotor.class, new RenderAdvanceDataMonotor());
        RenderController.registerRenderer("line", new LineChartRenderer());
    }
}
