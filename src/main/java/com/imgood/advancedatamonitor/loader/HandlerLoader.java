package com.imgood.advancedatamonitor.loader;

import com.imgood.advancedatamonitor.handler.TickHandler;
import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraftforge.common.MinecraftForge;

/**
 * @program: AdvanceDataMonitor
 * @description:
 * @author: Imgood
 * @create: 2025-04-23 15:06
 **/
public class HandlerLoader {
    public static void registerHandlers() {
        FMLCommonHandler.instance().bus().register(new TickHandler());
    }
}
