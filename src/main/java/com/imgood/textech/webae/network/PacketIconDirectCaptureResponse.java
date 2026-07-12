package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;

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
    public byte[] png;

    public PacketIconDirectCaptureResponse() {}

    @Override
    public void toBytes(ByteBuf buf) {
        writeUtf8(buf, requestId);
        buf.writeBoolean(success);
        if (png != null && png.length > 0) {
            buf.writeInt(png.length);
            buf.writeBytes(png);
        } else {
            buf.writeInt(0);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        requestId = readUtf8(buf);
        success = buf.readBoolean();
        int len = buf.readInt();
        if (len > 0) {
            png = new byte[len];
            buf.readBytes(png);
        } else {
            png = new byte[0];
        }
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
        if (len <= 0) return "";
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static class Handler implements IMessageHandler<PacketIconDirectCaptureResponse, IMessage> {

        private static final int MAX_PNG_BYTES = 256 * 1024;

        @Override
        public IMessage onMessage(final PacketIconDirectCaptureResponse message, MessageContext ctx) {
            if (message == null || message.requestId == null || message.requestId.isEmpty()) {
                return null;
            }
            if (message.png != null && message.png.length > MAX_PNG_BYTES) {
                IconDirectCaptureBridge.instance().complete(message.requestId, null);
                return null;
            }
            IconDirectCaptureBridge.instance()
                .complete(message.requestId, message.success ? message.png : null);
            return null;
        }
    }
}
