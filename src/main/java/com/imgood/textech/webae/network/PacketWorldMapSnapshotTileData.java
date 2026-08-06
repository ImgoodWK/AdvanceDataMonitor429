package com.imgood.textech.webae.network;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.client.worldmap.WorldMapSnapshotDownloadHandler;
import com.imgood.textech.utils.NetworkPacketCodec;
import com.imgood.textech.webae.worldmap.WorldMapPacketAuthorization;
import com.imgood.textech.webae.worldmap.WorldMapRenderSupport;
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
    public int chunkIndex;
    public int totalChunks = 1;
    public byte[] png;
    public boolean malformed;

    private static final int MAX_OWNER_UUID_BYTES = 64;
    private static final int MAX_LAYER_BYTES = 16;
    public static final int MAX_PNG_BYTES = 512 * 1024;
    public static final int MAX_CHUNK_BYTES = WebAeBinaryTransfer.MAX_PACKET_CHUNK_BYTES;
    private static final int MAX_TOTAL_CHUNKS = (MAX_PNG_BYTES + MAX_CHUNK_BYTES - 1) / MAX_CHUNK_BYTES;
    private static final int MAX_ACTIVE_DOWNLOADS = 16;

    public PacketWorldMapSnapshotTileData() {}

    @Override
    public void toBytes(ByteBuf buf) {
        writeUtf8(buf, ownerUuid, MAX_OWNER_UUID_BYTES);
        buf.writeInt(networkId);
        buf.writeInt(snapshotVersion);
        writeUtf8(buf, layer, MAX_LAYER_BYTES);
        buf.writeInt(dim);
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
        if (totalChunks < 1 || totalChunks > MAX_TOTAL_CHUNKS || chunkIndex < 0 || chunkIndex >= totalChunks) {
            throw new IllegalArgumentException("Invalid snapshot tile data chunk");
        }
        buf.writeInt(chunkIndex);
        buf.writeInt(totalChunks);
        if (png != null) {
            if (png.length == 0 || png.length > MAX_CHUNK_BYTES) {
                throw new IllegalArgumentException("Snapshot tile data exceeds packet limit");
            }
            buf.writeInt(png.length);
            buf.writeBytes(png);
        } else {
            buf.writeInt(0);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        malformed = false;
        try {
            ownerUuid = NetworkPacketCodec.readUtf8(buf, MAX_OWNER_UUID_BYTES);
            networkId = buf.readInt();
            snapshotVersion = buf.readInt();
            String rawLayer = NetworkPacketCodec.readUtf8(buf, MAX_LAYER_BYTES);
            layer = WorldMapTileLayer.normalize(rawLayer);
            if (!WorldMapTileLayer.TERRAIN.equals(rawLayer) && !WorldMapTileLayer.AE.equals(rawLayer)) {
                throw new IllegalArgumentException("Invalid snapshot tile layer");
            }
            dim = buf.readInt();
            chunkX = buf.readInt();
            chunkZ = buf.readInt();
            chunkIndex = buf.readInt();
            totalChunks = buf.readInt();
            png = NetworkPacketCodec.readBytes(buf, MAX_CHUNK_BYTES);
            if (totalChunks < 1 || totalChunks > MAX_TOTAL_CHUNKS
                || chunkIndex < 0
                || chunkIndex >= totalChunks
                || png.length == 0) {
                throw new IllegalArgumentException("Invalid snapshot tile data chunk");
            }
            if (buf.isReadable()) {
                throw new IllegalArgumentException("Snapshot tile data has trailing bytes");
            }
        } catch (RuntimeException e) {
            malformed = true;
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
            throw new IllegalArgumentException("Snapshot tile string exceeds packet limit");
        }
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    /** Server-side sender for a bounded, ordered snapshot tile download. */
    public static void sendToPlayer(String ownerUuid, int networkId, int snapshotVersion, String layer, int dim,
        int chunkX, int chunkZ, byte[] fullPng, EntityPlayerMP player) {
        if (player == null || fullPng == null || fullPng.length == 0) {
            return;
        }
        int total = WebAeBinaryTransfer.chunkCount(fullPng.length, MAX_PNG_BYTES);
        if (total < 1 || total > MAX_TOTAL_CHUNKS) {
            return;
        }
        for (int i = 0; i < total; i++) {
            PacketWorldMapSnapshotTileData data = new PacketWorldMapSnapshotTileData();
            data.ownerUuid = ownerUuid;
            data.networkId = networkId;
            data.snapshotVersion = snapshotVersion;
            data.layer = WorldMapTileLayer.normalize(layer);
            data.dim = dim;
            data.chunkX = chunkX;
            data.chunkZ = chunkZ;
            data.chunkIndex = i;
            data.totalChunks = total;
            data.png = WebAeBinaryTransfer.copyChunk(fullPng, i);
            AdvanceDataMonitor.ADMCHANEL.sendTo(data, player);
        }
    }

    public static class Handler implements IMessageHandler<PacketWorldMapSnapshotTileData, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketWorldMapSnapshotTileData message, MessageContext ctx) {
            if (message == null || message.malformed
                || !WorldMapPacketAuthorization.isValidOwnerUuid(message.ownerUuid)
                || !WorldMapPacketAuthorization.isValidNetworkId(message.networkId)
                || !WorldMapPacketAuthorization.isValidSnapshotVersion(message.snapshotVersion)
                || !WorldMapPacketAuthorization.isValidChunk(message.dim, message.chunkX, message.chunkZ)
                || message.png == null
                || message.png.length == 0
                || message.png.length > MAX_CHUNK_BYTES) {
                return null;
            }
            String key = transferKey(message);
            byte[] fullPng = DownloadSessions.accept(key, message.chunkIndex, message.totalChunks, message.png);
            if (fullPng == null) {
                return null;
            }
            if (!WorldMapRenderSupport.isValidTilePng(fullPng)) {
                DownloadSessions.remove(key);
                return null;
            }
            message.png = fullPng;
            message.chunkIndex = 0;
            message.totalChunks = 1;
            WorldMapSnapshotDownloadHandler.instance()
                .onTileData(message);
            return null;
        }

        private static String transferKey(PacketWorldMapSnapshotTileData message) {
            return message.ownerUuid + "|"
                + message.networkId
                + "|"
                + message.snapshotVersion
                + "|"
                + message.layer
                + "|"
                + message.dim
                + "|"
                + message.chunkX
                + "|"
                + message.chunkZ;
        }
    }

    private static final class DownloadSessions {

        private static final Map<String, Session> SESSIONS = new HashMap<String, Session>();

        static synchronized byte[] accept(String key, int index, int total, byte[] chunk) {
            prune();
            if (key == null || key.isEmpty()
                || total < 1
                || total > MAX_TOTAL_CHUNKS
                || index < 0
                || index >= total
                || chunk == null
                || chunk.length > MAX_CHUNK_BYTES) {
                remove(key);
                return null;
            }
            Session session;
            if (index == 0) {
                if (SESSIONS.size() >= MAX_ACTIVE_DOWNLOADS && !SESSIONS.containsKey(key)) {
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
