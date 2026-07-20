package com.imgood.textech.loader;

import net.minecraftforge.common.MinecraftForge;

import com.imgood.textech.handler.HandlerDataLoomCell;
import com.imgood.textech.handler.HandlerGrapple;
import com.imgood.textech.handler.HandlerLoot;
import com.imgood.textech.handler.HandlerPlayerJoin;
import com.imgood.textech.handler.HandlerPocketWorldLoad;
import com.imgood.textech.handler.HandlerStarryCosmosSword;
import com.imgood.textech.handler.HandlerSuperOrange;
import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.handler.HandlerWebChatCollector;
import com.imgood.textech.handler.HandlerWebPlayerTracker;
import com.imgood.textech.handler.HandlerWebTileEntityIndex;

import cpw.mods.fml.common.FMLCommonHandler;

/**
 * @program: AdvanceDataMonitor
 * @description:
 * @author: Imgood
 * @create: 2025-04-23 15:06
 **/
public class LoaderHandler {

    public static void registerHandlers() {
        FMLCommonHandler.instance()
            .bus()
            .register(new HandlerTick());
        MinecraftForge.EVENT_BUS.register(new HandlerLoot());
        HandlerLoot.registerChestLoot();
        MinecraftForge.EVENT_BUS.register(new HandlerPlayerJoin());
        MinecraftForge.EVENT_BUS.register(new HandlerSuperOrange());
        MinecraftForge.EVENT_BUS.register(new HandlerStarryCosmosSword());
        MinecraftForge.EVENT_BUS.register(new HandlerGrapple());
        MinecraftForge.EVENT_BUS.register(new HandlerDataLoomCell());
        MinecraftForge.EVENT_BUS.register(new HandlerWebTileEntityIndex());
        MinecraftForge.EVENT_BUS.register(new HandlerPocketWorldLoad());
        // HandlerWebPlayerTracker listens to FML PlayerLoggedIn/LoggedOut events → register on FML bus.
        FMLCommonHandler.instance()
            .bus()
            .register(new HandlerWebPlayerTracker());
        // HandlerWebChatCollector listens to Forge ServerChatEvent → register on MinecraftForge.EVENT_BUS.
        MinecraftForge.EVENT_BUS.register(new HandlerWebChatCollector());
        // WebAeServerHandler lifecycle managed via @Mod.EventHandler in AdvanceDataMonitor
    }
}
