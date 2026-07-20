package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;

import com.imgood.textech.network.handler.PacketHandlers;
import com.imgood.textech.webae.worldmap.WorldMapSnapshotStore;
import com.imgood.textech.webae.worldmap.WorldMapTileLayer;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C→S: request a single snapshot tile for local cache download. Packet ID 43.
 */
public class PacketWorldMapSnapshotTilePull implements IMessage {

    public String ownerUuid;
    public int networkId;
    public int snapshotVersion;
    public String layer = WorldMapTileLayer.TERRAIN;
    public int dim;
    public int chunkX;
    public int chunkZ;

    public PacketWorldMapSnapshotTilePull() {}

    @Override
    public void toBytes(ByteBuf buf) {
        writeUtf8(buf, ownerUuid);
        buf.writeInt(networkId);
        buf.writeInt(snapshotVersion);
        writeUtf8(buf, layer);
        buf.writeInt(dim);
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
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

    public static class Handler implements IMessageHandler<PacketWorldMapSnapshotTilePull, IMessage> {

        @Override
        public IMessage onMessage(final PacketWorldMapSnapshotTilePull message, MessageContext ctx) {
            return PacketHandlers.runOnServer(ctx, new Runnable() {

                @Override
                public void run() {
                    if (message == null || message.ownerUuid == null) {
                        return;
                    }
                    java.io.File file = WorldMapSnapshotStore.getExistingTile(
                        message.ownerUuid,
                        message.networkId,
                        message.snapshotVersion,
                        message.layer,
                        message.dim,
                        message.chunkX,
                        message.chunkZ);
                    PacketWorldMapSnapshotTileData data = new PacketWorldMapSnapshotTileData();
                    data.ownerUuid = message.ownerUuid;
                    data.networkId = message.networkId;
                    data.snapshotVersion = message.snapshotVersion;
                    data.layer = message.layer;
                    data.dim = message.dim;
                    data.chunkX = message.chunkX;
                    data.chunkZ = message.chunkZ;
                    if (file != null) {
                        try {
                            java.io.FileInputStream fis = new java.io.FileInputStream(file);
                            data.png = new byte[(int) file.length()];
                            fis.read(data.png);
                            fis.close();
                        } catch (Exception ignored) {
                            data.png = new byte[0];
                        }
                    } else {
                        data.png = new byte[0];
                    }
                    com.imgood.textech.AdvanceDataMonitor.ADMCHANEL.sendTo(data, ctx.getServerHandler().playerEntity);
                }
            });
        }
    }
}
