package com.imgood.textech.webae.network;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.network.handler.PacketHandlers;
import com.imgood.textech.utils.NetworkPacketCodec;
import com.imgood.textech.webae.worldmap.WorldMapChunkSetBuilder;
import com.imgood.textech.webae.worldmap.WorldMapClientCaptureMode;
import com.imgood.textech.webae.worldmap.WorldMapHdSupport;
import com.imgood.textech.webae.worldmap.WorldMapMetaDto;
import com.imgood.textech.webae.worldmap.WorldMapQualityTier;
import com.imgood.textech.webae.worldmap.WorldMapPacketAuthorization;
import com.imgood.textech.webae.worldmap.WorldMapRenderSupport;
import com.imgood.textech.webae.worldmap.WorldMapTileCache;
import com.imgood.textech.webae.worldmap.WorldMapTileLayer;
import com.imgood.textech.webae.worldmap.WorldMapTileProgressTracker;
import com.imgood.textech.webae.worldmap.WorldMapView;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C→S: client uploads a rendered HD world map chunk PNG.
 * Packet ID 35.
 */
public class PacketWebMapTileUpload implements IMessage {

    public String view;
    public String layer = WorldMapTileLayer.TERRAIN;
    public int dim;
    public int chunkX;
    public int chunkZ;
    public String quality = WorldMapQualityTier.ULTRA.id;
    public int networkId;
    public String ownerUuid;
    public int chunkIndex;
    public int totalChunks = 1;
    public byte[] png;
    private boolean valid = true;

    private static final int MAX_VIEW_BYTES = 64;
    private static final int MAX_LAYER_BYTES = 16;
    private static final int MAX_QUALITY_BYTES = 16;
    private static final int MAX_OWNER_UUID_BYTES = 64;
    public static final int MAX_PNG_BYTES = 512 * 1024;
    public static final int MAX_CHUNK_BYTES = WebAeBinaryTransfer.MAX_PACKET_CHUNK_BYTES;
    private static final int MAX_TOTAL_CHUNKS = (MAX_PNG_BYTES + MAX_CHUNK_BYTES - 1) / MAX_CHUNK_BYTES;
    private static final int MAX_ACTIVE_UPLOADS = 32;

    public PacketWebMapTileUpload() {}

    public PacketWebMapTileUpload(String view, int dim, int chunkX, int chunkZ, int networkId, String ownerUuid,
        byte[] png) {
        this(view, WorldMapTileLayer.TERRAIN, dim, chunkX, chunkZ, networkId, ownerUuid, png);
    }

    public PacketWebMapTileUpload(String view, String layer, int dim, int chunkX, int chunkZ, int networkId,
        String ownerUuid, byte[] png) {
        this(view, layer, WorldMapQualityTier.ULTRA.id, dim, chunkX, chunkZ, networkId, ownerUuid, png);
    }

    public PacketWebMapTileUpload(String view, String layer, String quality, int dim, int chunkX, int chunkZ,
        int networkId, String ownerUuid, byte[] png) {
        this.view = view;
        this.layer = WorldMapTileLayer.normalize(layer);
        this.quality = quality;
        this.dim = dim;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.networkId = networkId;
        this.ownerUuid = ownerUuid;
        this.png = png;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        if (!isValidQuality(quality)) {
            throw new IllegalArgumentException("Invalid world map tile quality");
        }
        writeUtf8(buf, view, MAX_VIEW_BYTES);
        writeUtf8(buf, layer, MAX_LAYER_BYTES);
        writeUtf8(buf, quality, MAX_QUALITY_BYTES);
        buf.writeInt(dim);
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
        buf.writeInt(networkId);
        writeUtf8(buf, ownerUuid, MAX_OWNER_UUID_BYTES);
        if (totalChunks < 1 || totalChunks > MAX_TOTAL_CHUNKS || chunkIndex < 0 || chunkIndex >= totalChunks) {
            throw new IllegalArgumentException("Invalid world map tile upload chunk");
        }
        buf.writeInt(chunkIndex);
        buf.writeInt(totalChunks);
        if (png != null) {
            if (png.length == 0 || png.length > MAX_CHUNK_BYTES) {
                throw new IllegalArgumentException("World map tile exceeds packet limit");
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
            view = NetworkPacketCodec.readUtf8(buf, MAX_VIEW_BYTES);
            String rawLayer = NetworkPacketCodec.readUtf8(buf, MAX_LAYER_BYTES);
            layer = WorldMapTileLayer.normalize(rawLayer);
            if (!WorldMapTileLayer.TERRAIN.equals(rawLayer) && !WorldMapTileLayer.AE.equals(rawLayer)) {
                valid = false;
            }
            quality = NetworkPacketCodec.readUtf8(buf, MAX_QUALITY_BYTES);
            if (!isValidQuality(quality)) {
                throw new IllegalArgumentException("Invalid world map tile quality");
            }
            dim = buf.readInt();
            chunkX = buf.readInt();
            chunkZ = buf.readInt();
            networkId = buf.readInt();
            ownerUuid = NetworkPacketCodec.readUtf8(buf, MAX_OWNER_UUID_BYTES);
            chunkIndex = buf.readInt();
            totalChunks = buf.readInt();
            png = NetworkPacketCodec.readBytes(buf, MAX_CHUNK_BYTES);
            if (totalChunks < 1 || totalChunks > MAX_TOTAL_CHUNKS || chunkIndex < 0
                || chunkIndex >= totalChunks || png.length == 0) {
                throw new IllegalArgumentException("Invalid world map tile upload chunk");
            }
            if (buf.isReadable()) {
                throw new IllegalArgumentException("World map tile upload has trailing bytes");
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
            throw new IllegalArgumentException("World map tile string exceeds packet limit");
        }
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    /** Client-side sender for one bounded world-map tile upload. */
    public static boolean sendToServer(String view, String layer, String quality, int dim, int chunkX, int chunkZ,
        int networkId, String ownerUuid, byte[] fullPng) {
        if (!isValidQuality(quality) || fullPng == null || fullPng.length == 0) {
            return false;
        }
        int total = WebAeBinaryTransfer.chunkCount(fullPng.length, MAX_PNG_BYTES);
        if (total < 1 || total > MAX_TOTAL_CHUNKS) {
            return false;
        }
        for (int i = 0; i < total; i++) {
            PacketWebMapTileUpload packet = new PacketWebMapTileUpload();
            packet.view = view;
            packet.layer = WorldMapTileLayer.normalize(layer);
            packet.quality = quality;
            packet.dim = dim;
            packet.chunkX = chunkX;
            packet.chunkZ = chunkZ;
            packet.networkId = networkId;
            packet.ownerUuid = ownerUuid;
            packet.chunkIndex = i;
            packet.totalChunks = total;
            packet.png = WebAeBinaryTransfer.copyChunk(fullPng, i);
            AdvanceDataMonitor.ADMCHANEL.sendToServer(packet);
        }
        return true;
    }

    public static class Handler implements IMessageHandler<PacketWebMapTileUpload, IMessage> {

        @Override
        public IMessage onMessage(final PacketWebMapTileUpload message, MessageContext ctx) {
            final EntityPlayerMP player = ctx == null || ctx.getServerHandler() == null ? null
                : ctx.getServerHandler().playerEntity;
            return PacketHandlers.runOnServer(ctx, new Runnable() {

                @Override
                public void run() {
                    handleOnMainThread(message, player);
                }
            });
        }

        private static void handleOnMainThread(PacketWebMapTileUpload message, EntityPlayerMP player) {
            if (message == null || !message.valid || player == null || !isValidQuality(message.quality)) {
                return;
            }
            if (!WorldMapHdSupport.isHdEnabled()) {
                return;
            }
            if (!WorldMapPacketAuthorization.isValidOwnerUuid(message.ownerUuid)
                || !WorldMapPacketAuthorization.isValidNetworkId(message.networkId)
                || !WorldMapPacketAuthorization.isValidChunk(message.dim, message.chunkX, message.chunkZ)) {
                return;
            }
            if (!WorldMapPacketAuthorization.isValidLayer(message.layer)) {
                return;
            }
            if (!WorldMapHdSupport.canUploadForOwner(player, message.ownerUuid, message.networkId)) {
                AdvanceDataMonitor.LOG
                    .debug("[WebAE] Rejected world map HD upload from non-owner {}", player.getCommandSenderName());
                return;
            }
            WorldMapView parsed = WorldMapView.fromId(message.view);
            if (parsed == null || !WorldMapView.isEnabled(parsed)) {
                return;
            }
            if (message.png == null || message.png.length == 0 || message.png.length > MAX_CHUNK_BYTES) {
                return;
            }
            if (Config.webWorldMapRequireNetworkScope && message.networkId >= 0) {
                if (!isChunkAllowed(
                    message.ownerUuid,
                    message.networkId,
                    message.dim,
                    message.chunkX,
                    message.chunkZ)) {
                    return;
                }
            }
            WorldMapQualityTier tier = WorldMapQualityTier.fromId(message.quality);
            if (!WorldMapClientCaptureMode.shouldUseClientForTier(tier)) {
                return;
            }
            String key = uploadKey(player, message);
            byte[] fullPng = TileUploadSessions.accept(
                key,
                message.chunkIndex,
                message.totalChunks,
                message.png);
            if (fullPng == null) {
                return;
            }
            if (!WorldMapRenderSupport.isValidTilePng(fullPng)) {
                TileUploadSessions.remove(key);
                return;
            }
            WorldMapTileCache
                .writeHd(parsed.id, message.layer, tier, message.dim, message.chunkX, message.chunkZ, fullPng);
            WorldMapTileProgressTracker.instance()
                .markDone(
                    message.networkId,
                    parsed.id,
                    tier,
                    message.dim,
                    message.chunkX,
                    message.chunkZ,
                    message.layer);
            AdvanceDataMonitor.LOG.debug(
                "[WebAE] Stored HD world map tile view={} layer={} dim={} cx={} cz={} bytes={}",
                parsed.id,
                message.layer,
                message.dim,
                message.chunkX,
                message.chunkZ,
                fullPng.length);
        }

        private static String uploadKey(EntityPlayerMP player, PacketWebMapTileUpload message) {
            return player.getUniqueID()
                .toString() + "|" + message.ownerUuid + "|" + message.networkId + "|" + message.view + "|"
                + message.quality + "|" + message.layer + "|" + message.dim + "|" + message.chunkX + "|"
                + message.chunkZ;
        }

        private static boolean isChunkAllowed(String ownerUuid, int networkId, int dim, int chunkX, int chunkZ) {
            WorldMapMetaDto meta = com.imgood.textech.webae.worldmap.WorldMapBoundsBuilder
                .rebuild(ownerUuid, networkId);
            if (meta == null || meta.dimensions == null || meta.dimensions.isEmpty()) {
                return meta != null && meta.hasLogicalSnapshot;
            }
            for (WorldMapMetaDto.DimensionInfo info : meta.dimensions) {
                if (info == null || info.dim != dim) {
                    continue;
                }
                return WorldMapChunkSetBuilder.containsChunk(info, chunkX, chunkZ);
            }
            return false;
        }
    }

    static boolean isValidQuality(String quality) {
        return WorldMapQualityTier.LOW.id.equals(quality) || WorldMapQualityTier.MEDIUM.id.equals(quality)
            || WorldMapQualityTier.HIGH.id.equals(quality) || WorldMapQualityTier.ULTRA.id.equals(quality);
    }

    boolean isValid() {
        return valid;
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
