package com.imgood.textech.webae.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.network.handler.PacketHandlers;
import com.imgood.textech.utils.NetworkPacketCodec;
import com.imgood.textech.webae.worldmap.WorldMapPacketAuthorization;
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
    private boolean valid = true;

    private static final int MAX_OWNER_UUID_BYTES = 64;
    private static final int MAX_LAYER_BYTES = 16;

    public PacketWorldMapSnapshotTilePull() {}

    @Override
    public void toBytes(ByteBuf buf) {
        writeUtf8(buf, ownerUuid, MAX_OWNER_UUID_BYTES);
        buf.writeInt(networkId);
        buf.writeInt(snapshotVersion);
        writeUtf8(buf, layer, MAX_LAYER_BYTES);
        buf.writeInt(dim);
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        valid = true;
        try {
            ownerUuid = NetworkPacketCodec.readUtf8(buf, MAX_OWNER_UUID_BYTES);
            networkId = buf.readInt();
            snapshotVersion = buf.readInt();
            String rawLayer = NetworkPacketCodec.readUtf8(buf, MAX_LAYER_BYTES);
            layer = WorldMapTileLayer.normalize(rawLayer);
            if (!WorldMapTileLayer.TERRAIN.equals(rawLayer) && !WorldMapTileLayer.AE.equals(rawLayer)) {
                valid = false;
            }
            dim = buf.readInt();
            chunkX = buf.readInt();
            chunkZ = buf.readInt();
            if (buf.isReadable()) {
                throw new IllegalArgumentException("Snapshot tile pull has trailing bytes");
            }
        } catch (RuntimeException e) {
            valid = false;
            ownerUuid = "";
        }
    }

    private static void writeUtf8(ByteBuf buf, String s, int maxBytes) {
        if (s == null) {
            buf.writeInt(0);
            return;
        }
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > maxBytes) {
            throw new IllegalArgumentException("Snapshot pull string exceeds packet limit");
        }
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    public static class Handler implements IMessageHandler<PacketWorldMapSnapshotTilePull, IMessage> {

        @Override
        public IMessage onMessage(final PacketWorldMapSnapshotTilePull message, MessageContext ctx) {
            final EntityPlayerMP player = ctx == null || ctx.getServerHandler() == null ? null
                : ctx.getServerHandler().playerEntity;
            return PacketHandlers.runOnServer(ctx, new Runnable() {

                @Override
                public void run() {
                    if (message == null || !message.valid
                        || player == null
                        || !WorldMapPacketAuthorization.isValidOwnerUuid(message.ownerUuid)
                        || !WorldMapPacketAuthorization.isValidNetworkId(message.networkId)
                        || !WorldMapPacketAuthorization.isValidSnapshotVersion(message.snapshotVersion)
                        || !WorldMapPacketAuthorization.isValidLayer(message.layer)
                        || !WorldMapPacketAuthorization.isValidChunk(message.dim, message.chunkX, message.chunkZ)
                        || !WorldMapPacketAuthorization
                            .canReadSnapshot(player, message.ownerUuid, message.networkId, message.snapshotVersion)) {
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
                    byte[] png = null;
                    if (file != null) {
                        try {
                            long length = file.length();
                            if (length <= 0 || length > PacketWorldMapSnapshotTileData.MAX_PNG_BYTES) {
                                png = null;
                            } else {
                                java.io.FileInputStream fis = new java.io.FileInputStream(file);
                                png = new byte[(int) length];
                                int offset = 0;
                                while (offset < png.length) {
                                    int read = fis.read(png, offset, png.length - offset);
                                    if (read < 0) break;
                                    offset += read;
                                }
                                fis.close();
                                if (offset != png.length) png = null;
                            }
                        } catch (Exception ignored) {
                            png = null;
                        }
                    }
                    PacketWorldMapSnapshotTileData.sendToPlayer(
                        message.ownerUuid,
                        message.networkId,
                        message.snapshotVersion,
                        message.layer,
                        message.dim,
                        message.chunkX,
                        message.chunkZ,
                        png,
                        player);
                }
            });
        }
    }
}
