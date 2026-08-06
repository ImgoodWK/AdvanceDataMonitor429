package com.imgood.textech.webae.network;

import com.imgood.textech.network.handler.PacketHandlers;
import com.imgood.textech.utils.NetworkPacketCodec;
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
    private boolean valid = true;

    private static final int MAX_REQUEST_ID_BYTES = 64;
    private static final int MAX_PACKET_BYTES = 30_000;

    public PacketWorldMapCaptureAccept() {}

    public PacketWorldMapCaptureAccept(String requestId) {
        this.requestId = requestId;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int start = buf.writerIndex();
        try {
            writeUtf8(buf, requestId);
            requirePacketBudget(buf, start);
        } catch (RuntimeException e) {
            buf.writerIndex(start);
            throw e;
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        valid = true;
        try {
            if (buf == null || buf.readableBytes() > MAX_PACKET_BYTES) {
                throw new IllegalArgumentException("Capture acceptance exceeds packet budget");
            }
            requestId = NetworkPacketCodec.readUtf8(buf, MAX_REQUEST_ID_BYTES);
            if (buf.isReadable()) {
                throw new IllegalArgumentException("Capture acceptance has trailing bytes");
            }
        } catch (RuntimeException e) {
            valid = false;
            requestId = "";
        }
    }

    private static void writeUtf8(ByteBuf buf, String s) {
        if (s == null) {
            buf.writeInt(0);
            return;
        }
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > MAX_REQUEST_ID_BYTES) {
            throw new IllegalArgumentException("Capture request id exceeds packet limit");
        }
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static void requirePacketBudget(ByteBuf buf, int start) {
        if (buf.writerIndex() - start > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Capture acceptance exceeds packet budget");
        }
    }

    public static class Handler implements IMessageHandler<PacketWorldMapCaptureAccept, IMessage> {

        @Override
        public IMessage onMessage(final PacketWorldMapCaptureAccept message, MessageContext ctx) {
            final net.minecraft.entity.player.EntityPlayerMP player = ctx == null || ctx.getServerHandler() == null
                ? null
                : ctx.getServerHandler().playerEntity;
            if (message == null || !message.valid || ctx == null || player == null) return null;
            return PacketHandlers.runOnServer(ctx, new Runnable() {

                @Override
                public void run() {
                    if (message == null || !message.valid || message.requestId == null) {
                        return;
                    }
                    WorldMapCaptureCoordinator.instance()
                        .accept(message.requestId, player);
                }
            });
        }
    }
}
