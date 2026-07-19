package com.imgood.textech.command;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

import com.imgood.textech.Config;
import com.imgood.textech.client.screenshot.ClientScreenshotService;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Client command for framebuffer capture, local history preview, and explicit sharing. */
@SideOnly(Side.CLIENT)
public final class CommandScreenshot extends TeXTechCommandBase {

    private static final String[] ACTIONS = { "capture", "list", "preview", "send", "status", "help" };
    private static final String[] SEND_TARGETS = { "web", "qq" };
    private static final int PAGE_SIZE = 8;

    @Override
    public String getCommandName() {
        return "admscreenshot";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return translateKey("adm.command.screenshot.usage");
    }

    @Override
    public List<String> getCommandAliases() {
        return Arrays.asList("admscreen", "texshot");
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sendHelp(sender);
            return;
        }
        String action = args[0].toLowerCase();
        if ("capture".equals(action)) {
            ClientScreenshotService.instance().capture();
        } else if ("list".equals(action)) {
            list(sender, parsePositive(args, 1, 1));
        } else if ("preview".equals(action)) {
            ClientScreenshotService.instance().openGallery(parseIndex(args, 1));
        } else if ("send".equals(action)) {
            send(sender, args);
        } else if ("status".equals(action)) {
            status(sender);
        } else {
            sendHelp(sender);
        }
    }

    private void list(ICommandSender sender, int page) {
        List<File> history = ClientScreenshotService.instance().listHistory();
        if (history.isEmpty()) {
            sendLocalized(sender, EnumChatFormatting.YELLOW, "adm.screenshot.history.empty");
            return;
        }
        int pages = Math.max(1, (history.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int boundedPage = Math.max(1, Math.min(pages, page));
        sendLocalized(
            sender,
            EnumChatFormatting.AQUA,
            "adm.command.screenshot.list.title",
            Integer.valueOf(boundedPage),
            Integer.valueOf(pages),
            Integer.valueOf(history.size()));
        int start = (boundedPage - 1) * PAGE_SIZE;
        int end = Math.min(history.size(), start + PAGE_SIZE);
        for (int i = start; i < end; i++) {
            File file = history.get(i);
            ChatComponentTranslation line = new ChatComponentTranslation(
                "adm.command.screenshot.list.entry",
                Integer.valueOf(i + 1),
                file.getName(),
                Long.valueOf(file.length() / 1024L));
            line.getChatStyle()
                .setColor(EnumChatFormatting.YELLOW)
                .setUnderlined(Boolean.TRUE)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/admscreenshot preview " + (i + 1)))
                .setChatHoverEvent(new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    new ChatComponentText(file.getAbsolutePath())));
            sender.addChatMessage(line);
        }
    }

    private void send(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sendLocalized(sender, EnumChatFormatting.RED, "adm.command.screenshot.send.usage");
            return;
        }
        String destination = args[1].toLowerCase();
        if ("web".equals(destination)) {
            int index = parseIndex(args, 2);
            int captionStart = hasIndexAt(args, 2) ? 3 : 2;
            ClientScreenshotService.instance().queueUpload("web", "", "", index, joinArgs(args, captionStart));
            return;
        }
        if ("qq".equals(destination)) {
            if (args.length < 3) {
                sendLocalized(sender, EnumChatFormatting.RED, "adm.command.screenshot.send.qq_usage");
                return;
            }
            String groupId = args[2];
            int index = parseIndex(args, 3);
            int captionStart = hasIndexAt(args, 3) ? 4 : 3;
            ClientScreenshotService.instance()
                .queueUpload("qq", "group", groupId, index, joinArgs(args, captionStart));
            return;
        }
        sendLocalized(sender, EnumChatFormatting.RED, "adm.command.screenshot.send.usage");
    }

    private void status(ICommandSender sender) {
        ClientScreenshotService service = ClientScreenshotService.instance();
        sendLocalized(sender, EnumChatFormatting.AQUA, "adm.command.screenshot.status.title");
        sendLocalized(
            sender,
            EnumChatFormatting.WHITE,
            "adm.command.screenshot.status.path",
            service.historyDirectory().getAbsolutePath());
        sendLocalized(
            sender,
            EnumChatFormatting.WHITE,
            "adm.command.screenshot.status.capture",
            Integer.valueOf(Config.webScreenshotMaxWidth),
            Integer.valueOf(Config.webScreenshotMaxHeight),
            Integer.valueOf(Config.webScreenshotJpegQualityPercent));
        sendLocalized(
            sender,
            EnumChatFormatting.WHITE,
            "adm.command.screenshot.status.upload",
            Integer.valueOf(Config.webScreenshotMaxUploadKB),
            Integer.valueOf(Config.webScreenshotUploadChunksPerTick),
            Boolean.valueOf(service.isUploadBusy()));
    }

    private void sendHelp(ICommandSender sender) {
        sendHelpHeader(sender, "adm.command.screenshot.title");
        sendUsageSummary(sender, "adm.command.screenshot.usage");
        sendHelpLines(sender, "adm.command.screenshot.help", 6);
    }

    private static int parseIndex(String[] args, int offset) {
        return hasIndexAt(args, offset) ? parsePositive(args, offset, 1) : 1;
    }

    private static boolean hasIndexAt(String[] args, int offset) {
        if (args.length <= offset) return false;
        if ("latest".equalsIgnoreCase(args[offset]) || "newest".equalsIgnoreCase(args[offset])) return true;
        try {
            return Integer.parseInt(args[offset]) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static int parsePositive(String[] args, int offset, int fallback) {
        if (args.length <= offset || "latest".equalsIgnoreCase(args[offset])
            || "newest".equalsIgnoreCase(args[offset])) return fallback;
        try {
            return Math.max(1, Integer.parseInt(args[offset]));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, ACTIONS);
        if (args.length == 2 && "send".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, SEND_TARGETS);
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
