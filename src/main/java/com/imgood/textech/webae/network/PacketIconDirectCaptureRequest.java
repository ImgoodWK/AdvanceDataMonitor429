package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;

import com.imgood.textech.client.icon.IconDirectCaptureClientWorker;

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

    public PacketIconDirectCaptureRequest() {}

    @Override
    public void toBytes(ByteBuf buf) {
        writeUtf8(buf, requestId);
        writeUtf8(buf, packName);
        writeUtf8(buf, renderMode);
        writeUtf8(buf, itemId);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        requestId = readUtf8(buf);
        packName = readUtf8(buf);
        renderMode = readUtf8(buf);
        itemId = readUtf8(buf);
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

    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<PacketIconDirectCaptureRequest, IMessage> {

        @Override
        public IMessage onMessage(PacketIconDirectCaptureRequest message, MessageContext ctx) {
            IconDirectCaptureClientWorker.instance()
                .enqueue(message);
            return null;
        }
    }
}
