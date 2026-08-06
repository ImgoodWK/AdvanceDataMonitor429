package com.imgood.textech.webae.network;

import com.imgood.textech.client.icon.IconDirectCaptureClientWorker;
import com.imgood.textech.utils.NetworkPacketCodec;
import com.imgood.textech.webae.icon.IconRenderMode;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * S→C: request client icon GL render for HTTP direct capture. Packet ID 47.
 */
public class PacketIconDirectCaptureRequest implements IMessage {

    public String requestId;
    public String packName;
    public String renderMode;
    public String itemId;
    private boolean valid = true;

    private static final int MAX_REQUEST_ID_BYTES = 64;
    private static final int MAX_PACK_NAME_BYTES = 128;
    private static final int MAX_RENDER_MODE_BYTES = 32;
    private static final int MAX_ITEM_ID_BYTES = 256;
    private static final int MAX_PACKET_BYTES = 30_000;

    public PacketIconDirectCaptureRequest() {}

    @Override
    public void toBytes(ByteBuf buf) {
        int start = buf.writerIndex();
        try {
            if (!IconRenderMode.isValidModeId(renderMode)) {
                throw new IllegalArgumentException("Invalid direct icon render mode");
            }
            writeUtf8(buf, requestId, MAX_REQUEST_ID_BYTES);
            writeUtf8(buf, packName, MAX_PACK_NAME_BYTES);
            writeUtf8(buf, renderMode, MAX_RENDER_MODE_BYTES);
            writeUtf8(buf, itemId, MAX_ITEM_ID_BYTES);
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
                throw new IllegalArgumentException("Direct icon request exceeds packet budget");
            }
            requestId = NetworkPacketCodec.readUtf8(buf, MAX_REQUEST_ID_BYTES);
            packName = NetworkPacketCodec.readUtf8(buf, MAX_PACK_NAME_BYTES);
            renderMode = NetworkPacketCodec.readUtf8(buf, MAX_RENDER_MODE_BYTES);
            if (!IconRenderMode.isValidModeId(renderMode)) {
                throw new IllegalArgumentException("Invalid direct icon render mode");
            }
            itemId = NetworkPacketCodec.readUtf8(buf, MAX_ITEM_ID_BYTES);
            if (buf.isReadable()) {
                throw new IllegalArgumentException("Direct icon request has trailing bytes");
            }
        } catch (RuntimeException e) {
            valid = false;
            requestId = "";
        }
    }

    private static void writeUtf8(ByteBuf buf, String s, int maxBytes) {
        if (s == null) {
            buf.writeInt(0);
            return;
        }
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > maxBytes) {
            throw new IllegalArgumentException("Direct icon request field exceeds packet limit");
        }
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static void requirePacketBudget(ByteBuf buf, int start) {
        if (buf.writerIndex() - start > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Direct icon request exceeds packet budget");
        }
    }

    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<PacketIconDirectCaptureRequest, IMessage> {

        @Override
        public IMessage onMessage(PacketIconDirectCaptureRequest message, MessageContext ctx) {
            if (message == null || !message.valid) return null;
            IconDirectCaptureClientWorker.instance()
                .enqueue(message);
            return null;
        }
    }
}
