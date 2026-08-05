package com.imgood.textech.webae.network;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.network.handler.PacketHandlers;
import com.imgood.textech.utils.NetworkPacketCodec;
import com.imgood.textech.webae.worldmap.WorldMapCaptureCoordinator;
import com.imgood.textech.webae.worldmap.WorldMapPacketAuthorization;
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
    public int chunkIndex;
    public int totalChunks = 1;
    public boolean finalizeSnapshot;
    /** Set when finalizeSnapshot=true: dynmap, journeymap, client_gl, or mixed. */
    public String source = "client_gl";
    /** JSON object of per-source chunk counts on finalize. */
    public String sourceStatsJson = "";
    public int tilePx;
    private boolean valid = true;

    private static final int MAX_OWNER_UUID_BYTES = 64;
    private static final int MAX_LAYER_BYTES = 16;
    private static final int MAX_SOURCE_BYTES = 32;
    private static final int MAX_SOURCE_STATS_BYTES = 16 * 1024;
    public static final int MAX_PNG_BYTES = 512 * 1024;
    public static final int MAX_CHUNK_BYTES = WebAeBinaryTransfer.MAX_PACKET_CHUNK_BYTES;
    private static final int MAX_TOTAL_CHUNKS = (MAX_PNG_BYTES + MAX_CHUNK_BYTES - 1) / MAX_CHUNK_BYTES;
    private static final int MAX_ACTIVE_UPLOADS = 32;

    public PacketWorldMapSnapshotTileUpload() {}

    @Override
    public void toBytes(ByteBuf buf) {
        writeUtf8(buf, ownerUuid, MAX_OWNER_UUID_BYTES);
        buf.writeInt(networkId);
        buf.writeInt(snapshotVersion);
        writeUtf8(buf, layer, MAX_LAYER_BYTES);
        buf.writeInt(dim);
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
        if (totalChunks < 1 || totalChunks > MAX_TOTAL_CHUNKS || chunkIndex < 0 || chunkIndex >= totalChunks
            || (finalizeSnapshot && (chunkIndex != 0 || totalChunks != 1))) {
            throw new IllegalArgumentException("Invalid snapshot tile upload chunk");
        }
        buf.writeInt(chunkIndex);
        buf.writeInt(totalChunks);
        buf.writeBoolean(finalizeSnapshot);
        writeUtf8(buf, source, MAX_SOURCE_BYTES);
        buf.writeInt(tilePx);
        writeUtf8(buf, finalizeSnapshot ? sourceStatsJson : null, MAX_SOURCE_STATS_BYTES);
        if (png != null) {
            if (png.length == 0 || png.length > MAX_CHUNK_BYTES || finalizeSnapshot) {
                throw new IllegalArgumentException("Snapshot tile exceeds packet limit");
            }
            buf.writeInt(png.length);
            buf.writeBytes(png);
        } else {
            buf.writeInt(0);
        }
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
            chunkIndex = buf.readInt();
            totalChunks = buf.readInt();
            finalizeSnapshot = buf.readBoolean();
            source = NetworkPacketCodec.readUtf8(buf, MAX_SOURCE_BYTES);
            if (source == null || source.isEmpty()) {
                source = "client_gl";
            }
            tilePx = buf.readInt();
            sourceStatsJson = NetworkPacketCodec.readUtf8(buf, MAX_SOURCE_STATS_BYTES);
            png = NetworkPacketCodec.readBytes(buf, MAX_CHUNK_BYTES);
            if (totalChunks < 1 || totalChunks > MAX_TOTAL_CHUNKS || chunkIndex < 0
                || chunkIndex >= totalChunks || (finalizeSnapshot && (chunkIndex != 0 || totalChunks != 1))) {
                throw new IllegalArgumentException("Invalid snapshot tile upload chunk");
            }
            if (!finalizeSnapshot && sourceStatsJson != null && !sourceStatsJson.isEmpty()) {
                throw new IllegalArgumentException("Unexpected snapshot source stats on tile chunk");
            }
            if (finalizeSnapshot && png.length != 0) {
                throw new IllegalArgumentException("Snapshot finalize packet must not contain tile data");
            }
            if (!finalizeSnapshot && png.length == 0) {
                throw new IllegalArgumentException("Snapshot tile packet must contain tile data");
            }
            if (buf.isReadable()) {
                throw new IllegalArgumentException("Snapshot tile upload has trailing bytes");
            }
        } catch (RuntimeException e) {
            valid = false;
            png = new byte[0];
        }
    }

    private static void writeUtf8(ByteBuf buf, String s, int maxBytes) {
        if (s == null) {
            buf.writeInt(0);
            return;
        }
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > maxBytes) {
            throw new IllegalArgumentException("Snapshot packet string exceeds packet limit");
        }
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    /** Client-side sender for a bounded, ordered snapshot tile upload. */
    public static void sendToServer(String ownerUuid, int networkId, int snapshotVersion, String layer, int dim,
        int chunkX, int chunkZ, int tilePx, byte[] fullPng) {
        if (fullPng == null || fullPng.length == 0) {
            return;
        }
        int total = WebAeBinaryTransfer.chunkCount(fullPng.length, MAX_PNG_BYTES);
        if (total < 1 || total > MAX_TOTAL_CHUNKS) {
            return;
        }
        for (int i = 0; i < total; i++) {
            PacketWorldMapSnapshotTileUpload packet = new PacketWorldMapSnapshotTileUpload();
            packet.ownerUuid = ownerUuid;
            packet.networkId = networkId;
            packet.snapshotVersion = snapshotVersion;
            packet.layer = WorldMapTileLayer.normalize(layer);
            packet.dim = dim;
            packet.chunkX = chunkX;
            packet.chunkZ = chunkZ;
            packet.chunkIndex = i;
            packet.totalChunks = total;
            packet.tilePx = tilePx;
            packet.finalizeSnapshot = false;
            packet.source = "client_gl";
            packet.sourceStatsJson = null;
            packet.png = WebAeBinaryTransfer.copyChunk(fullPng, i);
            AdvanceDataMonitor.ADMCHANEL.sendToServer(packet);
        }
    }

    public static class Handler implements IMessageHandler<PacketWorldMapSnapshotTileUpload, IMessage> {

        @Override
        public IMessage onMessage(final PacketWorldMapSnapshotTileUpload message, MessageContext ctx) {
            final EntityPlayerMP player = ctx == null || ctx.getServerHandler() == null ? null
                : ctx.getServerHandler().playerEntity;
            return PacketHandlers.runOnServer(ctx, new Runnable() {

                @Override
                public void run() {
                    handleOnMainThread(message, player);
                }
            });
        }

        private static void handleOnMainThread(PacketWorldMapSnapshotTileUpload message, EntityPlayerMP player) {
            if (message == null || !message.valid || player == null
                || !WorldMapPacketAuthorization.isValidOwnerUuid(message.ownerUuid)
                || !WorldMapPacketAuthorization.isValidNetworkId(message.networkId)
                || !WorldMapPacketAuthorization.isValidSnapshotVersion(message.snapshotVersion)
                || !WorldMapPacketAuthorization.isValidLayer(message.layer)
                || !WorldMapPacketAuthorization.isValidChunk(message.dim, message.chunkX, message.chunkZ)
                || !WorldMapPacketAuthorization.isValidSource(message.source)
                || message.sourceStatsJson == null || message.sourceStatsJson.length() > MAX_SOURCE_STATS_BYTES
                || !WorldMapPacketAuthorization.canWriteSnapshot(
                    player,
                    message.ownerUuid,
                    message.networkId,
                    message.snapshotVersion)) {
                return;
            }
            if (message.finalizeSnapshot) {
                if (!WorldMapPacketAuthorization.isValidTilePx(message.tilePx)) {
                    return;
                }
                WorldMapCaptureCoordinator.instance()
                    .onSnapshotComplete(
                        player,
                        message.ownerUuid,
                        message.networkId,
                        message.snapshotVersion,
                        message.source,
                        message.sourceStatsJson,
                        message.tilePx);
                return;
            }
            if (message.png == null || message.png.length == 0 || message.png.length > MAX_CHUNK_BYTES) {
                return;
            }
            if (!WorldMapCaptureCoordinator.instance()
                .isExpectedTile(
                    message.ownerUuid,
                    message.networkId,
                    message.snapshotVersion,
                    message.layer,
                    message.dim,
                    message.chunkX,
                    message.chunkZ)) {
                return;
            }
            String key = uploadKey(player, message);
            byte[] fullPng = TileUploadSessions.accept(key, message.chunkIndex, message.totalChunks, message.png);
            if (fullPng == null) {
                return;
            }
            if (!WorldMapRenderSupport.isValidTilePng(fullPng)) {
                TileUploadSessions.remove(key);
                return;
            }
            if (!WorldMapSnapshotStore.writeTile(
                message.ownerUuid,
                message.networkId,
                message.snapshotVersion,
                message.layer,
                message.dim,
                message.chunkX,
                message.chunkZ,
                fullPng)) {
                return;
            }
            WorldMapCaptureCoordinator.instance()
                .onTileUploaded(
                    player,
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

        private static String uploadKey(EntityPlayerMP player, PacketWorldMapSnapshotTileUpload message) {
            return player.getUniqueID()
                .toString() + "|" + message.ownerUuid + "|" + message.networkId + "|" + message.snapshotVersion
                + "|" + message.layer + "|" + message.dim + "|" + message.chunkX + "|" + message.chunkZ;
        }
    }

    private static final class TileUploadSessions {

        private static final Map<String, Session> SESSIONS = new HashMap<String, Session>();

        static synchronized byte[] accept(String key, int index, int total, byte[] chunk) {
            prune();
            if (key == null || key.isEmpty() || total < 1 || total > MAX_TOTAL_CHUNKS || index < 0
                || index >= total || chunk == null || chunk.length > MAX_CHUNK_BYTES) {
                remove(key);
                return null;
            }
            Session session;
            if (index == 0) {
                if (SESSIONS.size() >= MAX_ACTIVE_UPLOADS && !SESSIONS.containsKey(key)) {
                    return null;
                }
                SESSIONS.remove(key);
                session = new Session(total);
                SESSIONS.put(key, session);
            } else {
                session = SESSIONS.get(key);
                if (session == null || session.total != total) {
                    remove(key);
                    return null;
                }
            }
            try {
                byte[] full = session.assembler.accept(index, total, chunk);
                session.lastTouchedMs = System.currentTimeMillis();
                if (full != null) {
                    SESSIONS.remove(key);
                }
                return full;
            } catch (RuntimeException e) {
                SESSIONS.remove(key);
                return null;
            }
        }

        static synchronized void remove(String key) {
            if (key != null) {
                SESSIONS.remove(key);
            }
        }

        private static void prune() {
            long cutoff = System.currentTimeMillis() - WebAeBinaryTransfer.SESSION_TTL_MS;
            Iterator<Map.Entry<String, Session>> iterator = SESSIONS.entrySet()
                .iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Session> entry = iterator.next();
                if (entry.getValue() == null || entry.getValue().lastTouchedMs < cutoff) {
                    iterator.remove();
                }
            }
        }
    }

    private static final class Session {

        final int total;
        final WebAeBinaryTransfer.SequentialAssembler assembler;
        long lastTouchedMs;

        Session(int total) {
            this.total = total;
            this.assembler = new WebAeBinaryTransfer.SequentialAssembler(MAX_PNG_BYTES, MAX_TOTAL_CHUNKS);
            this.lastTouchedMs = System.currentTimeMillis();
        }
    }
}
