package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.network.handler.PacketHandlers;
import com.imgood.textech.webae.worldmap.WorldMapCaptureCoordinator;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C→S: accept a world map snapshot upload offer. Packet ID 38.
 */
public class PacketWorldMapCaptureAccept implements IMessage {

    public String requestId;

    public PacketWorldMapCaptureAccept() {}

    public PacketWorldMapCaptureAccept(String requestId) {
        this.requestId = requestId;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeUtf8(buf, requestId);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        requestId = readUtf8(buf);
    }

    private static void writeUtf8(ByteBuf buf, String s) {
        if (s == null) {
            buf.writeInt(0);
            return;
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readUtf8(ByteBuf buf) {
        int len = buf.readInt();
        if (len <= 0) {
            return "";
        }
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static class Handler implements IMessageHandler<PacketWorldMapCaptureAccept, IMessage> {

        @Override
        public IMessage onMessage(final PacketWorldMapCaptureAccept message, MessageContext ctx) {
            return PacketHandlers.runOnServer(ctx, new Runnable() {

                @Override
                public void run() {
                    if (message == null || message.requestId == null) {
                        return;
                    }
                    WorldMapCaptureCoordinator.instance()
                        .accept(message.requestId, ctx.getServerHandler().playerEntity);
                }
            });
        }
    }
}
