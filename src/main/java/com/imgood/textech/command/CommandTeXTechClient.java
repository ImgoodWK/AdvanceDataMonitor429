package com.imgood.textech.command;

import net.minecraft.command.ICommandSender;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class CommandTeXTechClient extends CommandTeXTech {

    @Override
    protected void sendHubIndex(ICommandSender sender) {
        sendHelpHeader(sender, "adm.command.hub.title");
        sendHelpLines(sender, "adm.command.hub.client", 5);
        sendLocalized(sender, "adm.command.hub.footer");
    }
}
