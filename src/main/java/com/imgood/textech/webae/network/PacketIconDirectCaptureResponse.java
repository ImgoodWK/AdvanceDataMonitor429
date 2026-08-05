package com.imgood.textech.webae.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.network.handler.PacketHandlers;
import com.imgood.textech.utils.NetworkPacketCodec;
import com.imgood.textech.webae.icon.IconDirectCaptureBridge;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C→S: client icon GL render response for HTTP direct capture. Packet ID 48.
 */
public class PacketIconDirectCaptureResponse implements IMessage {

    public String requestId;
    public boolean success;
    public int chunkIndex;
    public int totalChunks = 1;
    public byte[] png;
    private boolean valid = true;

    private static final int MAX_REQUEST_ID_BYTES = 64;
    public static final int MAX_PNG_BYTES = 256 * 1024;
    public static final int MAX_CHUNK_BYTES = WebAeBinaryTransfer.MAX_PACKET_CHUNK_BYTES;
    private static final int MAX_TOTAL_CHUNKS = (MAX_PNG_BYTES + MAX_CHUNK_BYTES - 1) / MAX_CHUNK_BYTES;

    public PacketIconDirectCaptureResponse() {}

    @Override
    public void toBytes(ByteBuf buf) {
        writeUtf8(buf, requestId, MAX_REQUEST_ID_BYTES);
        buf.writeBoolean(success);
        if (totalChunks < 1 || totalChunks > MAX_TOTAL_CHUNKS || chunkIndex < 0 || chunkIndex >= totalChunks
            || (!success && (chunkIndex != 0 || totalChunks != 1))) {
            throw new IllegalArgumentException("Invalid icon capture response chunk");
        }
        buf.writeInt(chunkIndex);
        buf.writeInt(totalChunks);
        if (png != null && png.length > 0) {
            if (png.length > MAX_CHUNK_BYTES || !success) {
                throw new IllegalArgumentException("Icon capture response exceeds packet limit");
            }
            buf.writeInt(png.length);
            buf.writeBytes(png);
        } else {
            buf.writeInt(0);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        valid = true;
        try {
            requestId = NetworkPacketCodec.readUtf8(buf, MAX_REQUEST_ID_BYTES);
            success = buf.readBoolean();
            chunkIndex = buf.readInt();
            totalChunks = buf.readInt();
            png = NetworkPacketCodec.readBytes(buf, MAX_CHUNK_BYTES);
            if (totalChunks < 1 || totalChunks > MAX_TOTAL_CHUNKS || chunkIndex < 0
                || chunkIndex >= totalChunks || (!success && (chunkIndex != 0 || totalChunks != 1))) {
                throw new IllegalArgumentException("Invalid icon capture response chunk");
            }
            if (success && png.length == 0) {
                throw new IllegalArgumentException("Empty successful icon capture response");
            }
            if (!success && png.length != 0) {
                throw new IllegalArgumentException("Failed icon capture response contains data");
            }
            if (buf.isReadable()) {
                throw new IllegalArgumentException("Icon capture response has trailing bytes");
            }
        } catch (RuntimeException e) {
            valid = false;
            requestId = "";
            png = new byte[0];
        }
    }

    private static void writeUtf8(ByteBuf buf, String s, int maxBytes) {
        if (s == null) {
            buf.writeInt(0);
            return;
        }
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > maxBytes) {
            throw new IllegalArgumentException("Capture request id exceeds packet limit");
        }
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    /** Client-side sender for a bounded, ordered icon capture response. */
    public static void sendToServer(String requestId, byte[] fullPng) {
        if (fullPng == null || fullPng.length == 0) {
            sendFailure(requestId);
            return;
        }
        int total = WebAeBinaryTransfer.chunkCount(fullPng.length, MAX_PNG_BYTES);
        if (total < 1 || total > MAX_TOTAL_CHUNKS) {
            sendFailure(requestId);
            return;
        }
        for (int i = 0; i < total; i++) {
            PacketIconDirectCaptureResponse response = new PacketIconDirectCaptureResponse();
            response.requestId = requestId;
            response.success = true;
            response.chunkIndex = i;
            response.totalChunks = total;
            response.png = WebAeBinaryTransfer.copyChunk(fullPng, i);
            AdvanceDataMonitor.ADMCHANEL.sendToServer(response);
        }
    }

    private static void sendFailure(String requestId) {
        PacketIconDirectCaptureResponse response = new PacketIconDirectCaptureResponse();
        response.requestId = requestId;
        response.success = false;
        response.chunkIndex = 0;
        response.totalChunks = 1;
        response.png = new byte[0];
        AdvanceDataMonitor.ADMCHANEL.sendToServer(response);
    }

    public static class Handler implements IMessageHandler<PacketIconDirectCaptureResponse, IMessage> {

        @Override
        public IMessage onMessage(final PacketIconDirectCaptureResponse message, MessageContext ctx) {
            final EntityPlayerMP player = ctx == null || ctx.getServerHandler() == null ? null
                : ctx.getServerHandler().playerEntity;
            if (message == null || !message.valid || player == null || message.requestId == null
                || message.requestId.isEmpty()) {
                return null;
            }
            return PacketHandlers.runOnServer(ctx, new Runnable() {

                @Override
                public void run() {
                    IconDirectCaptureBridge.instance()
                        .complete(
                            message.requestId,
                            player.getUniqueID()
                                .toString(),
                            message.success,
                            message.chunkIndex,
                            message.totalChunks,
                            message.png);
                }
            });
        }
    }
}
