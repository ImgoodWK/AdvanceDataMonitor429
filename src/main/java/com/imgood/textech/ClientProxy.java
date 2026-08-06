package com.imgood.textech;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.imgood.textech.client.AiClientPreferences;
import com.imgood.textech.client.DataImprintScrollHandler;
import com.imgood.textech.client.GrappleRoutePickerHud;
import com.imgood.textech.client.HandlerGrappleClient;
import com.imgood.textech.client.KeyBindings;
import com.imgood.textech.client.PocketOverlayHandler;
import com.imgood.textech.client.StarryCosmosClientWarmup;
import com.imgood.textech.client.VoiceAssistantKeyHandler;
import com.imgood.textech.loader.LoaderCommand;
import com.imgood.textech.renders.GrappleHudRenderer;
import com.imgood.textech.renders.GrappleTravelLineRenderer;
import com.imgood.textech.renders.OrangeNameplateRenderer;
import com.imgood.textech.renders.PlannerHudRenderer;
import com.imgood.textech.renders.SuperOrangeHaloRenderer;
import com.imgood.textech.renders.VoiceHudRenderer;
import com.imgood.textech.renders.WebAlertHudRenderer;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    private final VoiceAssistantKeyHandler voiceAssistantKeyHandler = new VoiceAssistantKeyHandler();
    private final KeyBindings keyBindings = new KeyBindings();
    private final HandlerGrappleClient grappleClientHandler = new HandlerGrappleClient();
    private final StarryCosmosClientWarmup starryCosmosClientWarmup = new StarryCosmosClientWarmup();

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        File configDir = new File(event.getModConfigurationDirectory(), AdvanceDataMonitor.MODID);
        File configFile = new File(configDir, "textech.cfg");
        Configuration shared = new Configuration(configFile);
        shared.load();
        AiClientPreferences.initialize(configDir, shared);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        LoaderCommand.registerClient();
        this.voiceAssistantKeyHandler.register();
        this.keyBindings.register();
        FMLCommonHandler.instance()
            .bus()
            .register(this.voiceAssistantKeyHandler);
        FMLCommonHandler.instance()
            .bus()
            .register(this.keyBindings);
        FMLCommonHandler.instance()
            .bus()
            .register(com.imgood.textech.client.screenshot.ClientScreenshotService.instance());
        FMLCommonHandler.instance()
            .bus()
            .register(this.grappleClientHandler);
        // WebAE icon renderer listens on the render tick (FML bus). Icon upload
        // itself is now triggered only via /admweb icons upload (Phase 2 removed
        // the in-game keybind handler).
        FMLCommonHandler.instance()
            .bus()
            .register(com.imgood.textech.webae.icon.IconRenderer.instance());
        FMLCommonHandler.instance()
            .bus()
            .register(com.imgood.textech.webae.icon.IconLazyRenderQueue.instance());
        FMLCommonHandler.instance()
            .bus()
            .register(com.imgood.textech.client.worldmap.WorldMapTileRenderWorker.instance());
        FMLCommonHandler.instance()
            .bus()
            .register(com.imgood.textech.client.worldmap.WorldMapChunkCaptureHandler.instance());
        FMLCommonHandler.instance()
            .bus()
            .register(com.imgood.textech.client.worldmap.WorldMapSnapshotCaptureWorker.instance());
        FMLCommonHandler.instance()
            .bus()
            .register(com.imgood.textech.client.worldmap.WorldMapSnapshotDownloadHandler.instance());
        FMLCommonHandler.instance()
            .bus()
            .register(com.imgood.textech.client.worldmap.WorldMapDirectCaptureClientWorker.instance());
        FMLCommonHandler.instance()
            .bus()
            .register(com.imgood.textech.client.icon.IconDirectCaptureClientWorker.instance());
        FMLCommonHandler.instance()
            .bus()
            .register(com.imgood.textech.webae.network.RecipeUploadThrottler.instance());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this.grappleClientHandler);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new PlannerHudRenderer());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new GrappleHudRenderer());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new GrappleTravelLineRenderer());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new GrappleRoutePickerHud());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new OrangeNameplateRenderer());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new SuperOrangeHaloRenderer());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(VoiceHudRenderer.instance());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(WebAlertHudRenderer.instance());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(PocketOverlayHandler.instance());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new DataImprintScrollHandler());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS
            .register(new com.imgood.textech.client.websurface.McefWebSurfaceSource.UnloadHandler());
        // TickEvent.ClientTickEvent is an FML bus event, so register there too — otherwise
        // the overlay never updates/renders even when enabled.
        FMLCommonHandler.instance()
            .bus()
            .register(PocketOverlayHandler.instance());
        FMLCommonHandler.instance()
            .bus()
            .register(this.starryCosmosClientWarmup);
    }
}
