package com.imgood.textech.command;

import java.util.Arrays;
import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.EnumChatFormatting;

import com.imgood.textech.assistant.AssistantLexicon;

public class CommandAssistant extends TeXTechCommandBase {

    private static final String[] ACTIONS = { "reload", "reload-lexicon", "reloadLexicon", "lexicon", "help" };
    private static final int HELP_LINES = 4;

    @Override
    public String getCommandName() {
        return "admassistant";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return translate("adm.command.admassistant.usage");
    }

    @Override
    public List<String> getCommandAliases() {
        return Arrays.asList("adm-assistant", "admast");
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sendUsage(sender);
            return;
        }
        String action = args[0].toLowerCase();
        if ("reloadlexicon".equals(action) || "reload-lexicon".equals(action) || "reload".equals(action)) {
            if (!canUseOpCommands(sender)) {
                sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admassistant.op_required");
                return;
            }
            String message = AssistantLexicon.reload();
            sendLocalized(sender, EnumChatFormatting.GREEN, "adm.command.admassistant.reload_ok", message);
            return;
        }
        if ("lexicon".equals(action)) {
            sendLocalized(
                sender,
                EnumChatFormatting.AQUA,
                "adm.command.admassistant.lexicon_path",
                AssistantLexicon.file()
                    .getPath());
            sendLocalized(sender, EnumChatFormatting.AQUA, "adm.command.admassistant.lexicon_hint");
            return;
        }
        sendUsage(sender);
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, ACTIONS);
        }
        return null;
    }

    private void sendUsage(ICommandSender sender) {
        sendHelpHeader(sender, "adm.command.admassistant.title");
        sendUsageSummary(sender, "adm.command.admassistant.usage");
        sendHelpLines(sender, "adm.command.admassistant.help", HELP_LINES);
    }

    private static String translate(String key) {
        return net.minecraft.util.StatCollector.translateToLocal(key);
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
