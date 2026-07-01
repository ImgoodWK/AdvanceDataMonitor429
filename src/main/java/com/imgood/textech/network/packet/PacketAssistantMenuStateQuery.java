package com.imgood.textech.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.assistant.AssistantServerServices;
import com.imgood.textech.handler.HandlerTick;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client-to-server packet: request the current AI assistant menu state so the
 * client can grey/red color-code features in the feature menu based on nearby
 * connector availability and AE security terminal permissions.
 *
 * Lang keys: none (no direct user-visible text).
 */
public class PacketAssistantMenuStateQuery implements IMessage {

    public PacketAssistantMenuStateQuery() {}

    @Override
    public void toBytes(ByteBuf buf) {
        // No payload — the player identity is taken from the server handler.
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        // No payload.
    }

    public static class Handler implements IMessageHandler<PacketAssistantMenuStateQuery, IMessage> {

        @Override
        public IMessage onMessage(final PacketAssistantMenuStateQuery message, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            HandlerTick.enqueueServerTask(new Runnable() {

                @Override
                public void run() {
                    if (player == null) {
                        return;
                    }
                    AssistantServerServices.respondMenuState(player);
                }
            });
            return null;
        }
    }
}
