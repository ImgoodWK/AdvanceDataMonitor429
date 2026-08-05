package com.imgood.textech.loader;

import net.minecraft.item.Item;
import net.minecraftforge.client.MinecraftForgeClient;

import com.imgood.textech.entity.EntityGrappleSlide;
import com.imgood.textech.entity.EntityStarrySwordLineSlash;
import com.imgood.textech.entity.EntityStarrySwordLineStab;
import com.imgood.textech.entity.EntityStarrySwordRain;
import com.imgood.textech.entity.EntityStarrySwordSlam;
import com.imgood.textech.entity.EntityStarrySwordThrown;
import com.imgood.textech.entity.EntitySuperOrangeDrone;
import com.imgood.textech.renders.CosmicStarrySwordRenderer;
import com.imgood.textech.renders.BarChartRenderer;
import com.imgood.textech.renders.CraftingInfoRenderer;
import com.imgood.textech.renders.DataTableRenderer;
import com.imgood.textech.renders.GaugeRenderer;
import com.imgood.textech.renders.LineChartRenderer;
import com.imgood.textech.renders.PieChartRenderer;
import com.imgood.textech.renders.ProgressBarRenderer;
import com.imgood.textech.renders.RenderAdvanceDataMonitor;
import com.imgood.textech.renders.RenderAdvanceDataMonitorBlockItem;
import com.imgood.textech.renders.RenderAdvanceNetworkLink;
import com.imgood.textech.renders.RenderAdvanceNetworkLinkBlockItem;
import com.imgood.textech.renders.RenderController;
import com.imgood.textech.renders.RenderDataImprintItem;
import com.imgood.textech.renders.RenderGrappleAnchor;
import com.imgood.textech.renders.RenderGrappleSlide;
import com.imgood.textech.renders.RenderMatterBallDecompressor;
import com.imgood.textech.renders.RenderStarrySwordLineSlash;
import com.imgood.textech.renders.RenderStarrySwordRain;
import com.imgood.textech.renders.RenderStarrySwordSlam;
import com.imgood.textech.renders.RenderStarrySwordThrown;
import com.imgood.textech.renders.RenderSuperOrangeDrone;
import com.imgood.textech.renders.StorageInfoRenderer;
import com.imgood.textech.renders.StatCardRenderer;
import com.imgood.textech.renders.WebSurfaceRenderer;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.tileentity.TileEntityAdvanceNetworkLink;
import com.imgood.textech.tileentity.TileEntityGrappleAnchor;
import com.imgood.textech.tileentity.TileEntityMatterBallDecompressor;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class LoaderRender {

    private static Item advanceDataMonitorBlockItem = Item.getItemFromBlock(LoaderBlock.advanceDataMonitor);
    private static Item advanceNetworkLink = Item.getItemFromBlock(LoaderBlock.advanceNetworkLinkBlock);

    public static void registerRenderers() {
        ClientRegistry
            .bindTileEntitySpecialRenderer(TileEntityAdvanceDataMonitor.class, new RenderAdvanceDataMonitor());
        ClientRegistry
            .bindTileEntitySpecialRenderer(TileEntityAdvanceNetworkLink.class, new RenderAdvanceNetworkLink());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityGrappleAnchor.class, new RenderGrappleAnchor());
        ClientRegistry
            .bindTileEntitySpecialRenderer(TileEntityMatterBallDecompressor.class, new RenderMatterBallDecompressor());
        LineChartRenderer lineChart = new LineChartRenderer();
        BarChartRenderer barChart = new BarChartRenderer();
        RenderController.registerRenderer("line", lineChart);
        RenderController.registerRenderer("lineChart", lineChart);
        RenderController.registerRenderer("diffrence", lineChart);
        RenderController.registerRenderer("difference", lineChart);
        RenderController.registerRenderer("statCard", new StatCardRenderer());
        RenderController.registerRenderer("progressBar", new ProgressBarRenderer());
        RenderController.registerRenderer("gauge", new GaugeRenderer());
        RenderController.registerRenderer("barChart", barChart);
        RenderController.registerRenderer("bar", barChart);
        RenderController.registerRenderer("bar3d", barChart);
        RenderController.registerRenderer("waterfall", barChart);
        RenderController.registerRenderer("pieChart", new PieChartRenderer());
        RenderController.registerRenderer("dataTable", new DataTableRenderer());
        RenderController.registerRenderer("crafting", new CraftingInfoRenderer());
        RenderController.registerRenderer("storage", new StorageInfoRenderer());
        RenderController.registerRenderer("web_surface", new WebSurfaceRenderer());

        MinecraftForgeClient.registerItemRenderer(advanceDataMonitorBlockItem, new RenderAdvanceDataMonitorBlockItem());
        MinecraftForgeClient.registerItemRenderer(LoaderItem.dataImprint, new RenderDataImprintItem());
        MinecraftForgeClient.registerItemRenderer(LoaderItem.starryCosmosSword, CosmicStarrySwordRenderer.INSTANCE);
        MinecraftForgeClient.registerItemRenderer(LoaderItem.holyJudgment, CosmicStarrySwordRenderer.INSTANCE);
        MinecraftForgeClient.registerItemRenderer(advanceNetworkLink, new RenderAdvanceNetworkLinkBlockItem());

        RenderingRegistry.registerEntityRenderingHandler(EntitySuperOrangeDrone.class, new RenderSuperOrangeDrone());
        RenderingRegistry.registerEntityRenderingHandler(EntityGrappleSlide.class, new RenderGrappleSlide());
        RenderingRegistry
            .registerEntityRenderingHandler(EntityStarrySwordLineSlash.class, new RenderStarrySwordLineSlash());
        RenderingRegistry.registerEntityRenderingHandler(EntityStarrySwordLineStab.class, new RenderStarrySwordSlam());
        RenderingRegistry.registerEntityRenderingHandler(EntityStarrySwordThrown.class, new RenderStarrySwordThrown());
        RenderingRegistry.registerEntityRenderingHandler(EntityStarrySwordRain.class, new RenderStarrySwordRain());
        RenderingRegistry.registerEntityRenderingHandler(EntityStarrySwordSlam.class, new RenderStarrySwordSlam());
    }
}
