package com.imgood.textech.command;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

public abstract class TeXTechCommandBase extends CommandBase {

    protected void sendLocalized(ICommandSender sender, String key, Object... args) {
        sender.addChatMessage(new ChatComponentTranslation(key, args));
    }

    protected void sendLocalized(ICommandSender sender, EnumChatFormatting color, String key, Object... args) {
        ChatComponentTranslation component = new ChatComponentTranslation(key, args);
        component.getChatStyle()
            .setColor(color);
        sender.addChatMessage(component);
    }

    protected void sendPlain(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText(message));
    }

    protected void sendPlain(ICommandSender sender, EnumChatFormatting color, String message) {
        ChatComponentText component = new ChatComponentText(message);
        component.getChatStyle()
            .setColor(color);
        sender.addChatMessage(component);
    }

    protected void sendHelpHeader(ICommandSender sender, String titleKey) {
        sendLocalized(sender, EnumChatFormatting.AQUA, titleKey);
    }

    protected void sendHelpLines(ICommandSender sender, String baseKey, int lineCount) {
        for (int i = 1; i <= lineCount; i++) {
            sendLocalized(sender, EnumChatFormatting.YELLOW, baseKey + "." + i);
        }
    }

    protected void sendUsageSummary(ICommandSender sender, String usageKey) {
        sendLocalized(sender, EnumChatFormatting.YELLOW, usageKey);
    }

    protected boolean requireOp(ICommandSender sender) {
        if (canUseOpCommands(sender)) {
            return true;
        }
        sendLocalized(sender, EnumChatFormatting.RED, "adm.command.common.op_required");
        return false;
    }

    protected boolean requirePlayer(ICommandSender sender) {
        if (sender instanceof EntityPlayerMP) {
            return true;
        }
        sendLocalized(sender, EnumChatFormatting.RED, "adm.command.common.player_required");
        return false;
    }

    protected boolean canUseOpCommands(ICommandSender sender) {
        return sender == null || sender.canCommandSenderUseCommand(2, getCommandName());
    }

    protected List<String> filterTabCompletion(String[] args, String[] candidates) {
        if (args.length == 0 || candidates == null) {
            return null;
        }
        String prefix = args[args.length - 1].toLowerCase();
        List<String> filtered = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate != null && candidate.toLowerCase()
                .startsWith(prefix)) {
                filtered.add(candidate);
            }
        }
        return filtered.isEmpty() ? null : filtered;
    }

    protected List<String> filterTabCompletion(String[] args, List<String> candidates) {
        if (candidates == null) {
            return null;
        }
        return filterTabCompletion(args, candidates.toArray(new String[candidates.size()]));
    }

    protected String joinArgs(String[] args, int start) {
        if (args.length <= start) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString()
            .trim();
    }

    /** Slice args from {@code start} (exclusive end = length). Empty if out of range. */
    protected static String[] sliceArgs(String[] args, int start) {
        if (args == null || args.length <= start) {
            return new String[0];
        }
        String[] rest = new String[args.length - start];
        System.arraycopy(args, start, rest, 0, rest.length);
        return rest;
    }

    protected static String translateKey(String key) {
        return net.minecraft.util.StatCollector.translateToLocal(key);
    }

    protected String maskKey(String key) {
        if (key == null || key.length() <= 8) {
            return "********";
        }
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }

    protected boolean parseOnOffToggle(String value, ToggleTarget target) {
        if ("toggle".equals(value)) {
            target.toggle();
            return true;
        }
        if ("on".equals(value) || "true".equals(value) || "enable".equals(value) || "enabled".equals(value)) {
            target.set(true);
            return true;
        }
        if ("off".equals(value) || "false".equals(value) || "disable".equals(value) || "disabled".equals(value)) {
            target.set(false);
            return true;
        }
        return false;
    }

    protected interface ToggleTarget {

        void set(boolean enabled);

        void toggle();
    }
}
