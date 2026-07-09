package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;

import com.imgood.textech.client.worldmap.WorldMapDirectCaptureClientWorker;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * S→C: request client GL capture for SP direct tile serve. Packet ID 45.
 */
public class PacketWorldMapDirectCaptureRequest implements IMessage {

    public String requestId;
    public String layer;
    public String ownerUuid;
    public int networkId;
    public int dim;
    public int chunkX;
    public int chunkZ;
    public int tilePx;

    public PacketWorldMapDirectCaptureRequest() {}

    @Override
    public void toBytes(ByteBuf buf) {
        writeUtf8(buf, requestId);
        writeUtf8(buf, layer);
        writeUtf8(buf, ownerUuid);
        buf.writeInt(networkId);
        buf.writeInt(dim);
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
        buf.writeInt(tilePx);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        requestId = readUtf8(buf);
        layer = readUtf8(buf);
        ownerUuid = readUtf8(buf);
        networkId = buf.readInt();
        dim = buf.readInt();
        chunkX = buf.readInt();
        chunkZ = buf.readInt();
        tilePx = buf.readInt();
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

    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<PacketWorldMapDirectCaptureRequest, IMessage> {

        @Override
        public IMessage onMessage(PacketWorldMapDirectCaptureRequest message, MessageContext ctx) {
            WorldMapDirectCaptureClientWorker.instance().enqueue(message);
            return null;
        }
    }
}
