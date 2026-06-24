package com.imgood.advancedatamonitor.loader;

import net.minecraft.item.Item;
import net.minecraftforge.client.MinecraftForgeClient;

import com.imgood.advancedatamonitor.entity.EntityGrappleSlide;
import com.imgood.advancedatamonitor.entity.EntityStarrySwordLineSlash;
import com.imgood.advancedatamonitor.entity.EntityStarrySwordLineStab;
import com.imgood.advancedatamonitor.entity.EntityStarrySwordRain;
import com.imgood.advancedatamonitor.entity.EntityStarrySwordSlam;
import com.imgood.advancedatamonitor.entity.EntityStarrySwordThrown;
import com.imgood.advancedatamonitor.entity.EntitySuperOrangeDrone;
import com.imgood.advancedatamonitor.renders.CosmicStarrySwordRenderer;
import com.imgood.advancedatamonitor.renders.CraftingInfoRenderer;
import com.imgood.advancedatamonitor.renders.LineChartRenderer;
import com.imgood.advancedatamonitor.renders.RenderAdvanceCraftingLink;
import com.imgood.advancedatamonitor.renders.RenderAdvanceDataMonitor;
import com.imgood.advancedatamonitor.renders.RenderAdvanceNetworkLink;
import com.imgood.advancedatamonitor.renders.RenderAdvanceNetworkLinkBlockItem;
import com.imgood.advancedatamonitor.renders.RenderAdvanceStorageLink;
import com.imgood.advancedatamonitor.renders.RenderController;
import com.imgood.advancedatamonitor.renders.RenderDataImprintItem;
import com.imgood.advancedatamonitor.renders.RenderGrappleAnchor;
import com.imgood.advancedatamonitor.renders.RenderGrappleSlide;
import com.imgood.advancedatamonitor.renders.RenderStarrySwordLineSlash;
import com.imgood.advancedatamonitor.renders.RenderStarrySwordRain;
import com.imgood.advancedatamonitor.renders.RenderStarrySwordSlam;
import com.imgood.advancedatamonitor.renders.RenderStarrySwordThrown;
import com.imgood.advancedatamonitor.renders.RenderSuperOrangeDrone;
import com.imgood.advancedatamonitor.renders.RenderAdvanceDataMonitorBlockItem;
import com.imgood.advancedatamonitor.renders.StorageInfoRenderer;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceCraftingLink;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceNetworkLink;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceStorageLink;
import com.imgood.advancedatamonitor.tileentity.TileEntityGrappleAnchor;

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
        ClientRegistry
            .bindTileEntitySpecialRenderer(TileEntityAdvanceStorageLink.class, new RenderAdvanceStorageLink());
        ClientRegistry
            .bindTileEntitySpecialRenderer(TileEntityAdvanceCraftingLink.class, new RenderAdvanceCraftingLink());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityGrappleAnchor.class, new RenderGrappleAnchor());
        RenderController.registerRenderer("line", new LineChartRenderer());
        RenderController.registerRenderer("crafting", new CraftingInfoRenderer());
        RenderController.registerRenderer("storage", new StorageInfoRenderer());

        MinecraftForgeClient.registerItemRenderer(advanceDataMonitorBlockItem, new RenderAdvanceDataMonitorBlockItem());
        MinecraftForgeClient.registerItemRenderer(LoaderItem.dataImprint, new RenderDataImprintItem());
        MinecraftForgeClient.registerItemRenderer(LoaderItem.starryCosmosSword, CosmicStarrySwordRenderer.INSTANCE);
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
