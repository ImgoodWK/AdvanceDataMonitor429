package com.imgood.textech.network.handler;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.handler.HandlerTick;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

/**
 * Shared helpers for SimpleNetworkWrapper packet handlers.
 */
public final class PacketHandlers {

    private PacketHandlers() {}

    /**
     * Schedule work on the main server thread (never mutate the world from the Netty thread).
     */
    public static IMessage runOnServer(final MessageContext ctx, final Runnable task) {
        final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                if (player == null) {
                    return;
                }
                task.run();
            }
        });
        return null;
    }

    /**
     * Schedule work on the main server thread without a player guard (e.g. tile-only packets).
     */
    public static IMessage runOnServerThread(final Runnable task) {
        HandlerTick.enqueueServerTask(task);
        return null;
    }
}
