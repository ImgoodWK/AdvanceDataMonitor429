package com.imgood.textech.command;

import java.util.Arrays;
import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.EnumChatFormatting;

/**
 * Server hub: {@code /textech <web|assistant|help> …}. Legacy roots ({@code /admweb},
 * {@code /admassistant}) remain registered and forward to the same handlers.
 */
public class CommandTeXTech extends TeXTechCommandBase {

    protected static final String[] SERVER_DOMAINS = { "help", "web", "assistant", "ai" };
    protected static final int HUB_LINES = 5;

    protected final CommandAssistant assistantCmd = new CommandAssistant();
    protected final CommandWebConsole webCmd = new CommandWebConsole();

    @Override
    public String getCommandName() {
        return "textech";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return translateKey("adm.command.hub.usage");
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
        String domain = args[0].toLowerCase();
        String[] rest = sliceArgs(args, 1);
        if (!dispatchDomain(sender, domain, rest)) {
            sendUsageSummary(sender, "adm.command.hub.usage");
            sendHubIndex(sender);
        }
    }

    /**
     * @return true if the domain was recognized (including side-only redirects)
     */
    protected boolean dispatchDomain(ICommandSender sender, String domain, String[] rest) {
        if ("assistant".equals(domain)) {
            assistantCmd.processCommand(sender, rest);
            return true;
        }
        if ("web".equals(domain)) {
            webCmd.processCommand(sender, rest);
            return true;
        }
        if ("ai".equals(domain)) {
            sendLocalized(sender, EnumChatFormatting.YELLOW, "adm.command.hub.ai_client_only");
            return true;
        }
        return false;
    }

    protected void sendHubIndex(ICommandSender sender) {
        sendHelpHeader(sender, "adm.command.hub.title");
        sendHelpLines(sender, "adm.command.hub", HUB_LINES);
        sendLocalized(sender, "adm.command.hub.footer");
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return filterTabCompletion(args, domainTabOptions());
        }
        if (args.length >= 2) {
            String domain = args[0].toLowerCase();
            String[] rest = sliceArgs(args, 1);
            return tabCompleteDomain(sender, domain, rest);
        }
        return null;
    }

    protected String[] domainTabOptions() {
        return SERVER_DOMAINS;
    }

    protected List<String> tabCompleteDomain(ICommandSender sender, String domain, String[] rest) {
        if ("assistant".equals(domain)) {
            return assistantCmd.addTabCompletionOptions(sender, rest);
        }
        if ("web".equals(domain)) {
            return webCmd.addTabCompletionOptions(sender, rest);
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
