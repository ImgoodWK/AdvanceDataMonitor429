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
import com.imgood.textech.renders.CraftingInfoRenderer;
import com.imgood.textech.renders.LineChartRenderer;
import com.imgood.textech.renders.RenderAdvanceCraftingLink;
import com.imgood.textech.renders.RenderAdvanceDataMonitor;
import com.imgood.textech.renders.RenderAdvanceDataMonitorBlockItem;
import com.imgood.textech.renders.RenderAdvanceNetworkLink;
import com.imgood.textech.renders.RenderAdvanceNetworkLinkBlockItem;
import com.imgood.textech.renders.RenderAdvanceStorageLink;
import com.imgood.textech.renders.RenderController;
import com.imgood.textech.renders.RenderDataImprintItem;
import com.imgood.textech.renders.RenderGrappleAnchor;
import com.imgood.textech.renders.RenderGrappleSlide;
import com.imgood.textech.renders.RenderStarrySwordLineSlash;
import com.imgood.textech.renders.RenderStarrySwordRain;
import com.imgood.textech.renders.RenderStarrySwordSlam;
import com.imgood.textech.renders.RenderStarrySwordThrown;
import com.imgood.textech.renders.RenderSuperOrangeDrone;
import com.imgood.textech.renders.StorageInfoRenderer;
import com.imgood.textech.tileentity.TileEntityAdvanceCraftingLink;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.tileentity.TileEntityAdvanceNetworkLink;
import com.imgood.textech.tileentity.TileEntityAdvanceStorageLink;
import com.imgood.textech.tileentity.TileEntityGrappleAnchor;

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
