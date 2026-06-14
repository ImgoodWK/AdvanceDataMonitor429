package com.imgood.advancedatamonitor.loader;

import com.imgood.advancedatamonitor.handler.HandlerLoot;
import com.imgood.advancedatamonitor.handler.HandlerTick;

import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraftforge.common.MinecraftForge;

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
    }
}
