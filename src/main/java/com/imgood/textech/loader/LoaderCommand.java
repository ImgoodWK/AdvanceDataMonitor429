package com.imgood.textech.loader;

import net.minecraftforge.client.ClientCommandHandler;

import com.imgood.textech.command.CommandAIConfig;
import com.imgood.textech.command.CommandAssistant;
import com.imgood.textech.command.CommandTeXTech;
import com.imgood.textech.command.CommandTeXTechClient;
import com.imgood.textech.command.CommandWebConsole;

import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Registers TeXTech chat commands. Server: {@code /textech}, {@code /admassistant}, {@code /admweb}.
 * Client: {@code /textech}, {@code /admai}, {@code /admassistant}.
 */
public final class LoaderCommand {

    private LoaderCommand() {}

    public static void registerServer(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandTeXTech());
        event.registerServerCommand(new CommandAssistant());
        event.registerServerCommand(new CommandWebConsole());
    }

    @SideOnly(Side.CLIENT)
    public static void registerClient() {
        ClientCommandHandler.instance.registerCommand(new CommandAIConfig());
        ClientCommandHandler.instance.registerCommand(new CommandAssistant());
        ClientCommandHandler.instance.registerCommand(new CommandTeXTechClient());
    }
}
