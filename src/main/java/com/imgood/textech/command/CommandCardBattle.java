package com.imgood.textech.command;

import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.imgood.textech.Config;
import com.imgood.textech.handler.CardBattleProcessHandler;

/**
 * {@code /textech card status|start|stop|restart}
 */
public class CommandCardBattle extends TeXTechCommandBase {

    @Override
    public String getCommandName() {
        return "card";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/textech card <status|start|stop|restart>";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + getCommandUsage(sender)));
            return;
        }
        String sub = args[0].toLowerCase();
        if ("status".equals(sub)) {
            boolean on = CardBattleProcessHandler.isRunning();
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GREEN + "[CardBattle] running="
                        + on
                        + " enabled="
                        + Config.cardBattleEnabled
                        + " port="
                        + Config.cardBattlePort));
            String url = CardBattleProcessHandler.getLastUrl();
            if (url != null && url.length() > 0) {
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "URL: " + url));
            }
            String err = CardBattleProcessHandler.getLastError();
            if (err != null && err.length() > 0) {
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "lastError: " + err));
            }
            return;
        }
        if ("start".equals(sub) || "stop".equals(sub) || "restart".equals(sub)) {
            if (!requireOp(sender)) {
                return;
            }
            if ("start".equals(sub)) {
                CardBattleProcessHandler.startIfEnabled();
            } else if ("stop".equals(sub)) {
                CardBattleProcessHandler.stopServer();
            } else {
                CardBattleProcessHandler.restartServer();
            }
            processCommand(sender, new String[] { "status" });
            return;
        }
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + getCommandUsage(sender)));
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return filterTabCompletion(args, new String[] { "status", "start", "stop", "restart", "help" });
        }
        return null;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }
}
