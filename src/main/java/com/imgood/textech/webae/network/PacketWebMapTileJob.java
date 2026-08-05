package com.imgood.textech.webae.network;

import com.imgood.textech.client.worldmap.WorldMapTileRenderWorker;
import com.imgood.textech.utils.NetworkPacketCodec;
import com.imgood.textech.webae.worldmap.WorldMapQualityTier;
import com.imgood.textech.webae.worldmap.WorldMapPacketAuthorization;
import com.imgood.textech.webae.worldmap.WorldMapTileLayer;
import com.imgood.textech.webae.worldmap.WorldMapView;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * S→C: ask an online client to render and upload an HD world map chunk tile.
 * Packet ID 34.
 */
public class PacketWebMapTileJob implements IMessage {

    public String view;
    public String layer = WorldMapTileLayer.TERRAIN;
    public String quality = WorldMapQualityTier.ULTRA.id;
    public int dim;
    public int chunkX;
    public int chunkZ;
    public int networkId;
    private boolean valid = true;

    private static final int MAX_VIEW_BYTES = 64;
    private static final int MAX_LAYER_BYTES = 16;
    private static final int MAX_QUALITY_BYTES = 16;
    private static final int MAX_PACKET_BYTES = 30_000;

    public PacketWebMapTileJob() {}

    public PacketWebMapTileJob(String view, int dim, int chunkX, int chunkZ, int networkId) {
        this(view, WorldMapTileLayer.TERRAIN, dim, chunkX, chunkZ, networkId);
    }

    public PacketWebMapTileJob(String view, String layer, int dim, int chunkX, int chunkZ, int networkId) {
        this(view, layer, WorldMapQualityTier.ULTRA.id, dim, chunkX, chunkZ, networkId);
    }

    public PacketWebMapTileJob(String view, String layer, String quality, int dim, int chunkX, int chunkZ,
        int networkId) {
        this.view = view;
        this.layer = WorldMapTileLayer.normalize(layer);
        this.quality = quality != null && !quality.isEmpty() ? quality : WorldMapQualityTier.ULTRA.id;
        this.dim = dim;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.networkId = networkId;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int start = buf.writerIndex();
        try {
            requireEnums(view, layer, quality);
            writeUtf8(buf, view, MAX_VIEW_BYTES);
            writeUtf8(buf, layer, MAX_LAYER_BYTES);
            writeUtf8(buf, quality, MAX_QUALITY_BYTES);
            buf.writeInt(dim);
            buf.writeInt(chunkX);
            buf.writeInt(chunkZ);
            buf.writeInt(networkId);
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
                throw new IllegalArgumentException("World map job exceeds packet budget");
            }
            view = NetworkPacketCodec.readUtf8(buf, MAX_VIEW_BYTES);
            String rawLayer = NetworkPacketCodec.readUtf8(buf, MAX_LAYER_BYTES);
            quality = NetworkPacketCodec.readUtf8(buf, MAX_QUALITY_BYTES);
            requireEnums(view, rawLayer, quality);
            layer = WorldMapTileLayer.normalize(rawLayer);
            dim = buf.readInt();
            chunkX = buf.readInt();
            chunkZ = buf.readInt();
            networkId = buf.readInt();
            if (buf.isReadable()) {
                throw new IllegalArgumentException("World map job has trailing bytes");
            }
        } catch (RuntimeException e) {
            valid = false;
            view = "";
        }
    }

    private static void writeUtf8(ByteBuf buf, String s, int maxBytes) {
        if (s == null) {
            buf.writeInt(0);
            return;
        }
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > maxBytes) {
            throw new IllegalArgumentException("World map job field exceeds packet limit");
        }
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static void requireEnums(String view, String layer, String quality) {
        if (view == null || view.isEmpty() || WorldMapView.fromId(view) == null) {
            throw new IllegalArgumentException("Invalid world map view");
        }
        if (!WorldMapPacketAuthorization.isValidLayer(layer)) {
            throw new IllegalArgumentException("Invalid world map layer");
        }
        if (!PacketWebMapTileUpload.isValidQuality(quality)) {
            throw new IllegalArgumentException("Invalid world map quality");
        }
    }

    private static void requirePacketBudget(ByteBuf buf, int start) {
        if (buf.writerIndex() - start > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("World map job exceeds packet budget");
        }
    }

    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<PacketWebMapTileJob, IMessage> {

        @Override
        public IMessage onMessage(final PacketWebMapTileJob message, MessageContext ctx) {
            if (message == null || !message.valid) return null;
            WorldMapTileRenderWorker.instance()
                .enqueue(message);
            return null;
        }
    }
}
