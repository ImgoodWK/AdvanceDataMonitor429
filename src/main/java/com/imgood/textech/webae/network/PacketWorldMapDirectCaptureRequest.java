package com.imgood.textech.webae.network;

import com.imgood.textech.client.worldmap.WorldMapDirectCaptureClientWorker;
import com.imgood.textech.utils.NetworkPacketCodec;
import com.imgood.textech.webae.worldmap.WorldMapPacketAuthorization;

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
    private boolean valid = true;

    private static final int MAX_REQUEST_ID_BYTES = 64;
    private static final int MAX_LAYER_BYTES = 16;
    private static final int MAX_OWNER_UUID_BYTES = 64;
    private static final int MAX_PACKET_BYTES = 30_000;

    public PacketWorldMapDirectCaptureRequest() {}

    @Override
    public void toBytes(ByteBuf buf) {
        int start = buf.writerIndex();
        try {
            if (!WorldMapPacketAuthorization.isValidLayer(layer)) {
                throw new IllegalArgumentException("Invalid direct world-map layer");
            }
            writeUtf8(buf, requestId, MAX_REQUEST_ID_BYTES);
            writeUtf8(buf, layer, MAX_LAYER_BYTES);
            writeUtf8(buf, ownerUuid, MAX_OWNER_UUID_BYTES);
            buf.writeInt(networkId);
            buf.writeInt(dim);
            buf.writeInt(chunkX);
            buf.writeInt(chunkZ);
            buf.writeInt(tilePx);
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
                throw new IllegalArgumentException("Direct world-map request exceeds packet budget");
            }
            requestId = NetworkPacketCodec.readUtf8(buf, MAX_REQUEST_ID_BYTES);
            layer = NetworkPacketCodec.readUtf8(buf, MAX_LAYER_BYTES);
            if (!WorldMapPacketAuthorization.isValidLayer(layer)) {
                throw new IllegalArgumentException("Invalid direct world-map layer");
            }
            ownerUuid = NetworkPacketCodec.readUtf8(buf, MAX_OWNER_UUID_BYTES);
            networkId = buf.readInt();
            dim = buf.readInt();
            chunkX = buf.readInt();
            chunkZ = buf.readInt();
            tilePx = buf.readInt();
            if (buf.isReadable()) {
                throw new IllegalArgumentException("Direct world-map request has trailing bytes");
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
            throw new IllegalArgumentException("Direct world-map request field exceeds packet limit");
        }
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static void requirePacketBudget(ByteBuf buf, int start) {
        if (buf.writerIndex() - start > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Direct world-map request exceeds packet budget");
        }
    }

    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<PacketWorldMapDirectCaptureRequest, IMessage> {

        @Override
        public IMessage onMessage(PacketWorldMapDirectCaptureRequest message, MessageContext ctx) {
            if (message == null || !message.valid) return null;
            WorldMapDirectCaptureClientWorker.instance()
                .enqueue(message);
            return null;
        }
    }
}
