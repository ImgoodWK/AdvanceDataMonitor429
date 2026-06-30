package com.imgood.textech.command;

import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.imgood.textech.assistant.AssistantLexicon;

public class CommandAssistant extends CommandBase {

    private static final String[] ACTIONS = { "reloadLexicon", "reload", "lexicon", "help" };

    @Override
    public String getCommandName() {
        return "admassistant";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/admassistant <reloadLexicon|lexicon|help>";
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
        if ("reloadlexicon".equals(action) || "reload".equals(action)) {
            if (!canReload(sender)) {
                send(
                    sender,
                    EnumChatFormatting.RED + "You need operator permission to reload assistant lexicon config.");
                return;
            }
            String message = AssistantLexicon.reload();
            send(sender, EnumChatFormatting.GREEN + message);
            return;
        }
        if ("lexicon".equals(action)) {
            send(
                sender,
                EnumChatFormatting.AQUA + "Assistant lexicon file: "
                    + AssistantLexicon.file()
                        .getPath());
            send(sender, EnumChatFormatting.AQUA + "Use /admassistant reloadLexicon after editing it.");
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

    private boolean canReload(ICommandSender sender) {
        return sender == null || sender.canCommandSenderUseCommand(2, getCommandName());
    }

    private void sendUsage(ICommandSender sender) {
        send(sender, EnumChatFormatting.YELLOW + getCommandUsage(sender));
        send(sender, EnumChatFormatting.YELLOW + "/admassistant lexicon  Show the assistant lexicon config file path");
        send(
            sender,
            EnumChatFormatting.YELLOW + "/admassistant reloadLexicon  Reload assistant lexicon config after editing");
    }

    private void send(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText(message));
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
