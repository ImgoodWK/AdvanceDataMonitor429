package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;

import com.imgood.textech.client.worldmap.WorldMapTileRenderWorker;
import com.imgood.textech.webae.worldmap.WorldMapTileLayer;

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
    public int dim;
    public int chunkX;
    public int chunkZ;
    public int networkId;

    public PacketWebMapTileJob() {}

    public PacketWebMapTileJob(String view, int dim, int chunkX, int chunkZ, int networkId) {
        this(view, WorldMapTileLayer.TERRAIN, dim, chunkX, chunkZ, networkId);
    }

    public PacketWebMapTileJob(String view, String layer, int dim, int chunkX, int chunkZ, int networkId) {
        this.view = view;
        this.layer = WorldMapTileLayer.normalize(layer);
        this.dim = dim;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.networkId = networkId;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeUtf8(buf, view);
        writeUtf8(buf, layer);
        buf.writeInt(dim);
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
        buf.writeInt(networkId);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        view = readUtf8(buf);
        layer = WorldMapTileLayer.normalize(readUtf8(buf));
        dim = buf.readInt();
        chunkX = buf.readInt();
        chunkZ = buf.readInt();
        networkId = buf.readInt();
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
    public static class Handler implements IMessageHandler<PacketWebMapTileJob, IMessage> {

        @Override
        public IMessage onMessage(final PacketWebMapTileJob message, MessageContext ctx) {
            WorldMapTileRenderWorker.instance()
                .enqueue(message);
            return null;
        }
    }
}
