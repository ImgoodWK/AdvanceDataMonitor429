package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.network.handler.PacketHandlers;
import com.imgood.textech.webae.worldmap.WorldMapCaptureCoordinator;
import com.imgood.textech.webae.worldmap.WorldMapRenderSupport;
import com.imgood.textech.webae.worldmap.WorldMapSnapshotStore;
import com.imgood.textech.webae.worldmap.WorldMapTileLayer;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C→S: upload a snapshot tile PNG. Packet ID 40.
 */
public class PacketWorldMapSnapshotTileUpload implements IMessage {

    public String ownerUuid;
    public int networkId;
    public int snapshotVersion;
    public String layer = WorldMapTileLayer.TERRAIN;
    public int dim;
    public int chunkX;
    public int chunkZ;
    public byte[] png;
    public boolean finalizeSnapshot;
    /** Set when finalizeSnapshot=true: journeymap or client_gl. */
    public String source = "client_gl";
    public int tilePx;

    public PacketWorldMapSnapshotTileUpload() {}

    @Override
    public void toBytes(ByteBuf buf) {
        writeUtf8(buf, ownerUuid);
        buf.writeInt(networkId);
        buf.writeInt(snapshotVersion);
        writeUtf8(buf, layer);
        buf.writeInt(dim);
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
        buf.writeBoolean(finalizeSnapshot);
        writeUtf8(buf, source);
        buf.writeInt(tilePx);
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
        finalizeSnapshot = buf.readBoolean();
        source = readUtf8(buf);
        if (source == null || source.isEmpty()) {
            source = "client_gl";
        }
        tilePx = buf.readInt();
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

    public static class Handler implements IMessageHandler<PacketWorldMapSnapshotTileUpload, IMessage> {

        private static final int MAX_PNG_BYTES = 512 * 1024;

        @Override
        public IMessage onMessage(final PacketWorldMapSnapshotTileUpload message, MessageContext ctx) {
            return PacketHandlers.runOnServer(ctx, new Runnable() {

                @Override
                public void run() {
                    handleOnMainThread(message, ctx.getServerHandler().playerEntity);
                }
            });
        }

        private static void handleOnMainThread(PacketWorldMapSnapshotTileUpload message, EntityPlayerMP player) {
            if (message == null || player == null || message.ownerUuid == null || message.ownerUuid.isEmpty()) {
                return;
            }
            if (message.finalizeSnapshot) {
                WorldMapCaptureCoordinator.instance()
                    .onSnapshotComplete(message.ownerUuid, message.networkId, message.snapshotVersion, message.source,
                        message.tilePx);
                return;
            }
            if (message.png == null || message.png.length == 0 || message.png.length > MAX_PNG_BYTES) {
                return;
            }
            if (!WorldMapRenderSupport.isValidTilePng(message.png)) {
                return;
            }
            WorldMapSnapshotStore.writeTile(
                message.ownerUuid,
                message.networkId,
                message.snapshotVersion,
                message.layer,
                message.dim,
                message.chunkX,
                message.chunkZ,
                message.png);
            WorldMapCaptureCoordinator.instance()
                .onTileUploaded(
                    message.ownerUuid,
                    message.networkId,
                    message.snapshotVersion,
                    message.layer,
                    message.dim,
                    message.chunkX,
                    message.chunkZ);
            AdvanceDataMonitor.LOG.debug(
                "[WebAE] Snapshot tile owner={} net={} v={} layer={} dim={} cx={} cz={}",
                message.ownerUuid,
                message.networkId,
                message.snapshotVersion,
                message.layer,
                message.dim,
                message.chunkX,
                message.chunkZ);
        }
    }
}
