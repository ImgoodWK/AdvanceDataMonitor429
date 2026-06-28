package com.imgood.advancedatamonitor.loader;

import net.minecraftforge.common.MinecraftForge;

import com.imgood.advancedatamonitor.handler.HandlerDataLoomCell;
import com.imgood.advancedatamonitor.handler.HandlerGrapple;
import com.imgood.advancedatamonitor.handler.HandlerLoot;
import com.imgood.advancedatamonitor.handler.HandlerPlayerJoin;
import com.imgood.advancedatamonitor.handler.HandlerStarryCosmosSword;
import com.imgood.advancedatamonitor.handler.HandlerSuperOrange;
import com.imgood.advancedatamonitor.handler.HandlerTick;
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
    }
}
