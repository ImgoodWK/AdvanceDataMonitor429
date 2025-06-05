package com.imgood.advancedatamonitor.loader;

import com.imgood.advancedatamonitor.renders.LineChartRenderer;
import com.imgood.advancedatamonitor.renders.RenderAdvanceDataMonotor;
import com.imgood.advancedatamonitor.renders.RenderAdvanceNetworkLink;
import com.imgood.advancedatamonitor.renders.RenderAdvanceNetworkLinkBlockItem;
import com.imgood.advancedatamonitor.renders.RenderController;
import com.imgood.advancedatamonitor.renders.RenderDataWeaveItem;
import com.imgood.advancedatamonitor.renders.RenderadvanceDataMonitorBlockItem;

import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceNetworkLink;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.item.Item;
import net.minecraftforge.client.MinecraftForgeClient;

@SideOnly(Side.CLIENT)
public class LoaderRender {
    private static Item advanceDataMonitorBlockItem = Item.getItemFromBlock(LoaderBlock.advDataMonitor);
    private static Item advanceNetworkLink = Item.getItemFromBlock(LoaderBlock.networkLinkBlock);
    public static void registerRenderers() {
        ClientRegistry
            .bindTileEntitySpecialRenderer(TileEntityAdvanceDataMonitor.class, new RenderAdvanceDataMonotor());
        ClientRegistry
                .bindTileEntitySpecialRenderer(TileEntityAdvanceNetworkLink.class, new RenderAdvanceNetworkLink());
        RenderController.registerRenderer("line", new LineChartRenderer());
        MinecraftForgeClient.registerItemRenderer(advanceDataMonitorBlockItem, new RenderadvanceDataMonitorBlockItem());
        MinecraftForgeClient.registerItemRenderer(LoaderItem.dataWeave, new RenderDataWeaveItem());
        MinecraftForgeClient.registerItemRenderer(advanceNetworkLink, new RenderAdvanceNetworkLinkBlockItem());
    }
}
