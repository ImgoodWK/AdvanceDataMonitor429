package com.imgood.textech.webae.icon;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

/**
 * Clickable chat offer for WebAE lazy icon capture consent (resource-pack aware).
 */
public final class IconLazyConsentChat {

    private IconLazyConsentChat() {}

    public static void sendOffer(EntityPlayerMP player, int pendingCount) {
        if (player == null) return;
        IChatComponent header = new ChatComponentTranslation(
            "adm.command.admweb.icons.consent.header",
            Integer.valueOf(pendingCount));
        header.getChatStyle()
            .setColor(EnumChatFormatting.AQUA);
        player.addChatMessage(header);

        IChatComponent textureNote = new ChatComponentTranslation("adm.command.admweb.icons.consent.texture_note");
        textureNote.getChatStyle()
            .setColor(EnumChatFormatting.YELLOW);
        player.addChatMessage(textureNote);

        IChatComponent actions = new ChatComponentText("");
        actions.appendSibling(
            clickableAction(
                StatCollector.translateToLocal("adm.command.admweb.icons.consent.accept"),
                EnumChatFormatting.GREEN,
                "/admweb icons y",
                StatCollector.translateToLocal("adm.command.admweb.icons.consent.accept_hover")));
        actions.appendSibling(new ChatComponentText(" "));
        actions.appendSibling(
            clickableAction(
                StatCollector.translateToLocal("adm.command.admweb.icons.consent.reject"),
                EnumChatFormatting.RED,
                "/admweb icons n",
                StatCollector.translateToLocal("adm.command.admweb.icons.consent.reject_hover")));
        player.addChatMessage(actions);

        IChatComponent hint = new ChatComponentTranslation("adm.command.admweb.icons.consent.hint");
        hint.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);
        player.addChatMessage(hint);
    }

    private static IChatComponent clickableAction(String label, EnumChatFormatting color, String command,
        String hover) {
        IChatComponent part = new ChatComponentText("[" + label + "]");
        part.getChatStyle()
            .setColor(color);
        part.getChatStyle()
            .setUnderlined(true);
        part.getChatStyle()
            .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        part.getChatStyle()
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(hover)));
        return part;
    }
}
