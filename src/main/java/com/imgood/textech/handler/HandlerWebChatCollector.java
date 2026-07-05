package com.imgood.textech.handler;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.ServerChatEvent;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.webae.chat.ChatMessage;
import com.imgood.textech.webae.chat.ChatMessageStore;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Server-side event handler that captures in-game chat messages into
 * {@link ChatMessageStore} for the WebAE {@code /api/chat/*} endpoints.
 *
 * <p>
 * The event is <em>not</em> cancelled — the original chat packet is still
 * delivered to all players, so existing chat behavior is unchanged. We only
 * mirror the message into the WebAE ring buffer.
 * </p>
 */
public class HandlerWebChatCollector {

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        if (event.player == null) return;
        if (event.player.worldObj != null && event.player.worldObj.isRemote) return;
        try {
            EntityPlayerMP player = event.player;
            String uuid = player.getUniqueID()
                .toString();
            String name = player.getDisplayName();
            if (name == null) name = player.getCommandSenderName();
            String content = event.message != null ? event.message : "";
            ChatMessageStore.instance()
                .append(uuid, name, content, System.currentTimeMillis(), ChatMessage.SOURCE_GAME);
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to capture chat message", t);
        }
    }
}
