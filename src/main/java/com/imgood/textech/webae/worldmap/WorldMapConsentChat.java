package com.imgood.textech.webae.worldmap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

/**
 * Server-side clickable chat for world map snapshot consent offers.
 */
public final class WorldMapConsentChat {

    private WorldMapConsentChat() {}

    public static void sendOffer(EntityPlayerMP player, String requestId, int networkId, String requesterName,
        int chunkCount, long expiresAtMs) {
        if (player == null || requestId == null || requestId.isEmpty()) {
            return;
        }
        String requester = requesterName != null && !requesterName.isEmpty() ? requesterName : "?";
        IChatComponent header = new ChatComponentTranslation(
            "adm.worldmap.consent.header",
            requester,
            networkId,
            chunkCount);
        header.getChatStyle()
            .setColor(EnumChatFormatting.AQUA);
        player.addChatMessage(header);

        IChatComponent actions = new ChatComponentText("");
        actions.appendSibling(
            clickableAction(
                StatCollector.translateToLocal("adm.worldmap.consent.accept"),
                EnumChatFormatting.GREEN,
                "/admweb wm y " + requestId,
                StatCollector.translateToLocalFormatted("adm.worldmap.consent.accept_hover", requestId)));
        actions.appendSibling(new ChatComponentText(" "));
        actions.appendSibling(
            clickableAction(
                StatCollector.translateToLocal("adm.worldmap.consent.reject"),
                EnumChatFormatting.RED,
                "/admweb wm n " + requestId,
                StatCollector.translateToLocal("adm.worldmap.consent.reject_hover")));
        player.addChatMessage(actions);

        IChatComponent hint = new ChatComponentTranslation("adm.worldmap.consent.hint", requestId);
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
