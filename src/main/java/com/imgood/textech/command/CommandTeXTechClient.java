package com.imgood.textech.command;

import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.EnumChatFormatting;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client hub: {@code /textech <ai|assistant|help> …}. {@code web} redirects to server-only hint.
 */
@SideOnly(Side.CLIENT)
public class CommandTeXTechClient extends CommandTeXTech {

    private static final String[] CLIENT_DOMAINS = { "help", "ai", "assistant", "web" };
    private static final int CLIENT_HUB_LINES = 5;

    private final CommandAIConfig aiCmd = new CommandAIConfig();

    @Override
    protected boolean dispatchDomain(ICommandSender sender, String domain, String[] rest) {
        if ("ai".equals(domain)) {
            aiCmd.processCommand(sender, rest);
            return true;
        }
        if ("web".equals(domain)) {
            sendLocalized(sender, EnumChatFormatting.YELLOW, "adm.command.hub.web_server_only");
            return true;
        }
        return super.dispatchDomain(sender, domain, rest);
    }

    @Override
    protected void sendHubIndex(ICommandSender sender) {
        sendHelpHeader(sender, "adm.command.hub.title");
        sendHelpLines(sender, "adm.command.hub.client", CLIENT_HUB_LINES);
        sendLocalized(sender, "adm.command.hub.footer");
    }

    @Override
    protected String[] domainTabOptions() {
        return CLIENT_DOMAINS;
    }

    @Override
    protected List<String> tabCompleteDomain(ICommandSender sender, String domain, String[] rest) {
        if ("ai".equals(domain)) {
            return aiCmd.addTabCompletionOptions(sender, rest);
        }
        if ("web".equals(domain)) {
            return null;
        }
        return super.tabCompleteDomain(sender, domain, rest);
    }
}
