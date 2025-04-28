package com.imgood.advancedatamonitor.loader;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.gui.handler.GuiHandler;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
/**
 * @program: AdvanceDataMonitor
 * @description:
 * @author: Imgood
 * @create: 2025-04-27 10:27
 **/
public class GuiLoaer {
    public static void registerGui() {
        GuiHandler guiHandler = new GuiHandler();
        FMLCommonHandler.instance().bus().register(guiHandler);
        NetworkRegistry.INSTANCE.registerGuiHandler(AdvanceDataMonitor.instance, guiHandler);
    }
}
