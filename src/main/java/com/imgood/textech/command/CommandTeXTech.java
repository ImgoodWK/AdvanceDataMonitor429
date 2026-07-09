package com.imgood.textech.command;

import java.util.Arrays;
import java.util.List;

import net.minecraft.command.ICommandSender;

public class CommandTeXTech extends TeXTechCommandBase {

    private static final String[] ACTIONS = { "help" };

    @Override
    public String getCommandName() {
        return "textech";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/textech help";
    }

    @Override
    public List<String> getCommandAliases() {
        return Arrays.asList("adm", "txt");
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sendHubIndex(sender);
            return;
        }
        sendUsageSummary(sender, "adm.command.hub.usage");
        sendHubIndex(sender);
    }

    protected void sendHubIndex(ICommandSender sender) {
        sendHelpHeader(sender, "adm.command.hub.title");
        sendHelpLines(sender, "adm.command.hub", 4);
        sendLocalized(sender, "adm.command.hub.footer");
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return filterTabCompletion(args, ACTIONS);
        }
        return null;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }
}
