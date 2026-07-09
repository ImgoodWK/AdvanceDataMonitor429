package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;

import com.imgood.textech.client.worldmap.WorldMapSnapshotCaptureWorker;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * S→C: offer to upload a world map snapshot (consent flow). Packet ID 37.
 */
public class PacketWorldMapCaptureOffer implements IMessage {

    public String requestId;
    public String ownerUuid;
    public int networkId;
    public String requesterName;
    public int estimatedChunks;
    public long expiresAtMs;

    public PacketWorldMapCaptureOffer() {}

    public PacketWorldMapCaptureOffer(String requestId, String ownerUuid, int networkId, String requesterName,
        int estimatedChunks, long expiresAtMs) {
        this.requestId = requestId;
        this.ownerUuid = ownerUuid;
        this.networkId = networkId;
        this.requesterName = requesterName;
        this.estimatedChunks = estimatedChunks;
        this.expiresAtMs = expiresAtMs;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeUtf8(buf, requestId);
        writeUtf8(buf, ownerUuid);
        buf.writeInt(networkId);
        writeUtf8(buf, requesterName);
        buf.writeInt(estimatedChunks);
        buf.writeLong(expiresAtMs);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        requestId = readUtf8(buf);
        ownerUuid = readUtf8(buf);
        networkId = buf.readInt();
        requesterName = readUtf8(buf);
        estimatedChunks = buf.readInt();
        expiresAtMs = buf.readLong();
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

    public static class Handler implements IMessageHandler<PacketWorldMapCaptureOffer, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketWorldMapCaptureOffer message, MessageContext ctx) {
            WorldMapSnapshotCaptureWorker.instance()
                .onCaptureOffer(message);
            return null;
        }
    }
}
