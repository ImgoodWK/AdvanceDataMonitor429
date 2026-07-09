package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;

import com.imgood.textech.client.worldmap.WorldMapSnapshotDownloadHandler;
import com.imgood.textech.webae.worldmap.WorldMapTileLayer;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * S→C: snapshot tile PNG data for client local cache. Packet ID 44.
 */
public class PacketWorldMapSnapshotTileData implements IMessage {

    public String ownerUuid;
    public int networkId;
    public int snapshotVersion;
    public String layer = WorldMapTileLayer.TERRAIN;
    public int dim;
    public int chunkX;
    public int chunkZ;
    public byte[] png;

    public PacketWorldMapSnapshotTileData() {}

    @Override
    public void toBytes(ByteBuf buf) {
        writeUtf8(buf, ownerUuid);
        buf.writeInt(networkId);
        buf.writeInt(snapshotVersion);
        writeUtf8(buf, layer);
        buf.writeInt(dim);
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
        if (png != null) {
            buf.writeInt(png.length);
            buf.writeBytes(png);
        } else {
            buf.writeInt(0);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        ownerUuid = readUtf8(buf);
        networkId = buf.readInt();
        snapshotVersion = buf.readInt();
        layer = WorldMapTileLayer.normalize(readUtf8(buf));
        dim = buf.readInt();
        chunkX = buf.readInt();
        chunkZ = buf.readInt();
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
        if (len <= 0) {
            return "";
        }
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static class Handler implements IMessageHandler<PacketWorldMapSnapshotTileData, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketWorldMapSnapshotTileData message, MessageContext ctx) {
            WorldMapSnapshotDownloadHandler.instance()
                .onTileData(message);
            return null;
        }
    }
}
