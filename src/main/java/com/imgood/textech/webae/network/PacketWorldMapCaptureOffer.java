package com.imgood.textech.webae.network;

import com.imgood.textech.client.worldmap.WorldMapSnapshotCaptureWorker;
import com.imgood.textech.utils.NetworkPacketCodec;

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
    private boolean valid = true;

    private static final int MAX_REQUEST_ID_BYTES = 64;
    private static final int MAX_OWNER_UUID_BYTES = 64;
    private static final int MAX_REQUESTER_NAME_BYTES = 256;
    private static final int MAX_ESTIMATED_CHUNKS = 8192;
    private static final int MAX_PACKET_BYTES = 30_000;

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
        int start = buf.writerIndex();
        try {
            if (estimatedChunks < 0 || estimatedChunks > MAX_ESTIMATED_CHUNKS) {
                throw new IllegalArgumentException("Invalid estimated world map chunk count");
            }
            writeUtf8(buf, requestId, MAX_REQUEST_ID_BYTES);
            writeUtf8(buf, ownerUuid, MAX_OWNER_UUID_BYTES);
            buf.writeInt(networkId);
            writeUtf8(buf, requesterName, MAX_REQUESTER_NAME_BYTES);
            buf.writeInt(estimatedChunks);
            buf.writeLong(expiresAtMs);
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
                throw new IllegalArgumentException("World map capture offer exceeds packet budget");
            }
            requestId = NetworkPacketCodec.readUtf8(buf, MAX_REQUEST_ID_BYTES);
            ownerUuid = NetworkPacketCodec.readUtf8(buf, MAX_OWNER_UUID_BYTES);
            networkId = buf.readInt();
            requesterName = NetworkPacketCodec.readUtf8(buf, MAX_REQUESTER_NAME_BYTES);
            estimatedChunks = buf.readInt();
            expiresAtMs = buf.readLong();
            if (estimatedChunks < 0 || estimatedChunks > MAX_ESTIMATED_CHUNKS) {
                throw new IllegalArgumentException("Invalid estimated world map chunk count");
            }
            if (buf.isReadable()) {
                throw new IllegalArgumentException("World map capture offer has trailing bytes");
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
            throw new IllegalArgumentException("World map capture offer field exceeds packet limit");
        }
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static void requirePacketBudget(ByteBuf buf, int start) {
        if (buf.writerIndex() - start > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("World map capture offer exceeds packet budget");
        }
    }

    public static class Handler implements IMessageHandler<PacketWorldMapCaptureOffer, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketWorldMapCaptureOffer message, MessageContext ctx) {
            if (message == null || !message.valid) return null;
            WorldMapSnapshotCaptureWorker.instance()
                .onCaptureOffer(message);
            return null;
        }
    }
}
