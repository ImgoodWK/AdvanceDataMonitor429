package com.imgood.textech.client;

import net.minecraft.client.Minecraft;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class WebConsoleClientChat {

    private WebConsoleClientChat() {}

    public static void showIssue(String token, int port, String bindAddress) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }

        copyToken(token);

        IChatComponent header = new ChatComponentText(I18n("adm.webconsole.token.issued_header"));
        header.getChatStyle()
            .setColor(EnumChatFormatting.GREEN);
        mc.thePlayer.addChatMessage(header);

        mc.thePlayer.addChatMessage(buildTokenLine(token));
        mc.thePlayer.addChatMessage(buildUrlLine(resolveAccessUrl(port, bindAddress)));

        IChatComponent hint = new ChatComponentText(I18n("adm.webconsole.token.usage_hint"));
        hint.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);
        mc.thePlayer.addChatMessage(hint);
    }

    public static void copyToken(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        if (!WebConsoleClipboard.copy(token)) {
            notify(EnumChatFormatting.RED, "adm.webconsole.token.copy_failed");
            return;
        }
        notify(EnumChatFormatting.GRAY, "adm.webconsole.token.copied");
    }

    private static IChatComponent buildTokenLine(String token) {
        IChatComponent label = new ChatComponentText("Token: ");
        label.getChatStyle()
            .setColor(EnumChatFormatting.AQUA);

        IChatComponent value = new ChatComponentText(token);
        value.getChatStyle()
            .setColor(EnumChatFormatting.WHITE);
        value.getChatStyle()
            .setUnderlined(true);
        value.getChatStyle()
            .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/admweb copy"));
        value.getChatStyle()
            .setChatHoverEvent(
                new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    new ChatComponentText(I18n("adm.webconsole.token.click_copy"))));

        label.appendSibling(value);
        return label;
    }

    private static IChatComponent buildUrlLine(String url) {
        IChatComponent label = new ChatComponentText("Access URL: ");
        label.getChatStyle()
            .setColor(EnumChatFormatting.AQUA);

        IChatComponent link = new ChatComponentText(url);
        link.getChatStyle()
            .setColor(EnumChatFormatting.WHITE);
        link.getChatStyle()
            .setUnderlined(true);
        link.getChatStyle()
            .setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        link.getChatStyle()
            .setChatHoverEvent(
                new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    new ChatComponentText(I18n("adm.webconsole.token.click_open"))));

        label.appendSibling(link);
        return label;
    }

    static String resolveAccessUrl(int port, String bindAddress) {
        String bind = bindAddress != null ? bindAddress.trim() : "";
        if (bind.isEmpty() || "127.0.0.1".equals(bind) || "localhost".equalsIgnoreCase(bind)) {
            return "http://127.0.0.1:" + port;
        }
        if ("0.0.0.0".equals(bind)) {
            String remoteHost = resolveRemoteHost();
            if (remoteHost != null && !remoteHost.isEmpty()) {
                return "http://" + remoteHost + ":" + port;
            }
            return "http://127.0.0.1:" + port;
        }
        return "http://" + bind + ":" + port;
    }

    private static String resolveRemoteHost() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.thePlayer.sendQueue == null) {
            return null;
        }
        try {
            java.net.SocketAddress address = mc.thePlayer.sendQueue.getNetworkManager()
                .getSocketAddress();
            if (address instanceof java.net.InetSocketAddress) {
                java.net.InetSocketAddress inet = (java.net.InetSocketAddress) address;
                String host = inet.getAddress() != null ? inet.getAddress()
                    .getHostAddress() : inet.getHostName();
                if (host != null && !host.isEmpty()) {
                    return host;
                }
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return null;
    }

    private static void notify(EnumChatFormatting color, String key) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }
        IChatComponent line = new ChatComponentText(I18n(key));
        line.getChatStyle()
            .setColor(color);
        mc.thePlayer.addChatMessage(line);
    }

    private static String I18n(String key) {
        return StatCollector.translateToLocal(key);
    }
}
