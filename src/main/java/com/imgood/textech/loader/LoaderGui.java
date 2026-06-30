package com.imgood.textech.loader;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.gui.handler.GuiHandler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.NetworkRegistry;

/**
 * @program: AdvanceDataMonitor
 * @description:
 * @author: Imgood
 * @create: 2025-04-27 10:27
 **/
public class LoaderGui {

    public static void registerGui() {
        GuiHandler guiHandler = new GuiHandler();
        FMLCommonHandler.instance()
            .bus()
            .register(guiHandler);
        NetworkRegistry.INSTANCE.registerGuiHandler(AdvanceDataMonitor.instance, guiHandler);
    }
}
