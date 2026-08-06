package com.imgood.textech.webae.network;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import net.minecraft.entity.player.EntityPlayerMP;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.network.handler.PacketHandlers;
import com.imgood.textech.utils.NetworkPacketCodec;
import com.imgood.textech.webae.icon.IconMissingQueue;
import com.imgood.textech.webae.icon.IconRenderMode;
import com.imgood.textech.webae.icon.IconStore;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C→S client uploads a rendered icon bundle (itemId → base64 PNG) in chunks.
 *
 * Packet ID 28. Reuses the chunked-upload pattern from {@link PacketWebRecipeUpload}
 * but the reassembled payload is a JSON map of itemId → base64PNG strings, which the
 * server decodes and writes to {@code TeXTech/WebAE/icons/<packName>/}.
 */
public class PacketWebIconUpload implements IMessage {

    private static final int MAX_PACK_NAME_BYTES = 128;
    private static final int MAX_RENDER_MODE_BYTES = 32;
    private static final int MAX_PLAYER_UUID_BYTES = 64;
    private static final int MAX_PACKET_BODY_BYTES = 30000;
    private static final int MAX_CHUNK_BYTES = WebAeBinaryTransfer.MAX_PACKET_CHUNK_BYTES;
    private static final int MAX_BUNDLE_JSON_BYTES = 8 * 1024 * 1024;
    private static final int MAX_TOTAL_CHUNKS = (MAX_BUNDLE_JSON_BYTES + MAX_CHUNK_BYTES - 1) / MAX_CHUNK_BYTES;
    private static final int MAX_ICONS_PER_BUNDLE = 4096;
    private static final int MAX_BASE64_ICON_BYTES = ((IconStore.MAX_PNG_BYTES + 2) / 3) * 4;
    private static final int MAX_ACTIVE_SINKS = 16;
    private static final int MAX_ACTIVE_SINKS_PER_PLAYER = 2;
    private static final long MAX_RESERVED_SINK_BYTES = 32L * 1024L * 1024L;
    private static final long SINK_TTL_MS = 120000L;
    private static final Pattern BASE64_PATTERN = Pattern.compile("[A-Za-z0-9+/]*={0,2}\\z");
    private boolean valid = true;

    public boolean isStart;
    public boolean isEnd;
    public int chunkIndex;
    public int totalChunks;
    public String packName;
    /** Render mode subdirectory, e.g. hybrid / atlas. Empty → hybrid (legacy clients). */
    public String renderMode;
    public String playerUuid;
    public byte[] chunkData;

    public PacketWebIconUpload() {}

    public PacketWebIconUpload(boolean isStart, boolean isEnd, int chunkIndex, int totalChunks, String packName,
        String playerUuid, byte[] chunkData) {
        this(isStart, isEnd, chunkIndex, totalChunks, packName, IconRenderMode.NEI.getId(), playerUuid, chunkData);
    }

    public PacketWebIconUpload(boolean isStart, boolean isEnd, int chunkIndex, int totalChunks, String packName,
        String renderMode, String playerUuid, byte[] chunkData) {
        this.isStart = isStart;
        this.isEnd = isEnd;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.packName = packName;
        this.renderMode = renderMode;
        this.playerUuid = playerUuid;
        this.chunkData = chunkData;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int startIndex = buf.writerIndex();
        buf.writeBoolean(isStart);
        buf.writeBoolean(isEnd);
        buf.writeInt(chunkIndex);
        buf.writeInt(totalChunks);
        writeUtf8(buf, packName, MAX_PACK_NAME_BYTES);
        writeUtf8(buf, renderMode, MAX_RENDER_MODE_BYTES);
        writeUtf8(buf, playerUuid, MAX_PLAYER_UUID_BYTES);
        if (chunkData == null || chunkData.length == 0 || chunkData.length > MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Icon upload chunk is empty or exceeds packet limit");
        }
        buf.writeInt(chunkData.length);
        buf.writeBytes(chunkData);
        if (buf.writerIndex() - startIndex > MAX_PACKET_BODY_BYTES) {
            throw new IllegalArgumentException("Icon upload packet exceeds packet body limit");
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        valid = true;
        try {
            if (buf.readableBytes() > MAX_PACKET_BODY_BYTES) {
                throw new IllegalArgumentException("Icon upload packet exceeds packet body limit");
            }
            isStart = buf.readBoolean();
            isEnd = buf.readBoolean();
            chunkIndex = buf.readInt();
            totalChunks = buf.readInt();
            packName = NetworkPacketCodec.readUtf8(buf, MAX_PACK_NAME_BYTES);
            renderMode = NetworkPacketCodec.readUtf8(buf, MAX_RENDER_MODE_BYTES);
            playerUuid = NetworkPacketCodec.readUtf8(buf, MAX_PLAYER_UUID_BYTES);
            chunkData = NetworkPacketCodec.readBytes(buf, MAX_CHUNK_BYTES);
            if (chunkData.length == 0 || buf.readableBytes() != 0) {
                throw new IllegalArgumentException("Invalid icon upload packet framing");
            }
        } catch (RuntimeException e) {
            valid = false;
            chunkData = new byte[0];
        }
    }

    private static void writeUtf8(ByteBuf buf, String s, int maxBytes) {
        if (s == null) {
            buf.writeInt(0);
            return;
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxBytes) {
            throw new IllegalArgumentException("String field exceeds packet limit");
        }
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    static Map<String, byte[]> decodeBundle(byte[] jsonBytes) {
        if (jsonBytes == null || jsonBytes.length == 0 || jsonBytes.length > MAX_BUNDLE_JSON_BYTES) {
            throw new IllegalArgumentException("Icon bundle JSON is empty or exceeds server limit");
        }
        String json = NetworkPacketCodec.decodeUtf8(jsonBytes);
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setLenient(false);
        Map<String, byte[]> decoded = new LinkedHashMap<String, byte[]>();
        long totalPngBytes = 0L;
        try {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                throw new IllegalArgumentException("Icon bundle root must be an object");
            }
            reader.beginObject();
            while (reader.hasNext()) {
                if (decoded.size() >= MAX_ICONS_PER_BUNDLE) {
                    throw new IllegalArgumentException("Icon bundle contains too many entries");
                }
                String itemId = reader.nextName();
                if (!IconStore.isValidItemId(itemId) || !hasValidSurrogates(itemId) || decoded.containsKey(itemId)) {
                    throw new IllegalArgumentException("Icon bundle contains an invalid or duplicate item id");
                }
                if (reader.peek() != JsonToken.STRING) {
                    throw new IllegalArgumentException("Icon bundle values must be base64 strings");
                }
                String base64 = reader.nextString();
                if (base64 == null || base64.isEmpty()
                    || base64.length() > MAX_BASE64_ICON_BYTES
                    || (base64.length() & 3) != 0
                    || !BASE64_PATTERN.matcher(base64)
                        .matches()) {
                    throw new IllegalArgumentException("Icon bundle contains invalid base64 data");
                }
                byte[] png;
                try {
                    png = Base64.getDecoder()
                        .decode(base64);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Icon bundle contains invalid base64 data", e);
                }
                if (!IconStore.isValidPng(png) || totalPngBytes > IconStore.MAX_ICON_PACK_PNG_BYTES - png.length) {
                    throw new IllegalArgumentException("Icon bundle contains an invalid or oversized PNG resource");
                }
                totalPngBytes += png.length;
                decoded.put(itemId, png);
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("Icon bundle contains trailing JSON data");
            }
            return decoded;
        } catch (IOException e) {
            throw new IllegalArgumentException("Icon bundle JSON is malformed", e);
        } finally {
            try {
                reader.close();
            } catch (IOException ignored) {}
        }
    }

    private static boolean hasValidSurrogates(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++i))) return false;
            } else if (Character.isLowSurrogate(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Server-side handler: accumulates chunks and, when the final chunk arrives,
     * reassembles the JSON bundle, decodes each base64 PNG, and writes it to disk.
     */
    public static class Handler implements IMessageHandler<PacketWebIconUpload, IMessage> {

        @Override
        public IMessage onMessage(final PacketWebIconUpload message, MessageContext ctx) {
            final EntityPlayerMP player = ctx == null || ctx.getServerHandler() == null ? null
                : ctx.getServerHandler().playerEntity;
            if (!isValid(message, player)) {
                return null;
            }
            return PacketHandlers.runOnServer(ctx, new Runnable() {

                @Override
                public void run() {
                    IMessage ack = processOnServerThread(message, player);
                    if (ack != null) {
                        AdvanceDataMonitor.ADMCHANEL.sendTo(ack, player);
                    }
                }
            });
        }

        private static boolean isValid(PacketWebIconUpload message, EntityPlayerMP player) {
            if (message == null || !message.valid || player == null || !matchesPlayerUuid(message.playerUuid, player)) {
                return false;
            }
            if (!Config.webIconUploadEnabled || !player.canCommandSenderUseCommand(2, "admweb")) {
                return false;
            }
            if (!IconStore.isValidPackName(message.packName)) {
                return false;
            }
            String modeId = IconStore.normalizeModeId(message.renderMode);
            if (!IconStore.isValidModeDirName(modeId)) {
                return false;
            }
            return message.totalChunks >= 1 && message.totalChunks <= MAX_TOTAL_CHUNKS
                && message.chunkIndex >= 0
                && message.chunkIndex < message.totalChunks
                && message.isStart == (message.chunkIndex == 0)
                && message.isEnd == (message.chunkIndex == message.totalChunks - 1)
                && message.chunkData != null
                && message.chunkData.length > 0
                && message.chunkData.length <= MAX_CHUNK_BYTES;
        }

        private static IMessage processOnServerThread(PacketWebIconUpload message, EntityPlayerMP player) {
            String actorUuid = player.getUniqueID()
                .toString();
            String modeId = IconStore.normalizeModeId(message.renderMode);
            try {
                ChunkSink sink = ChunkSink
                    .get(actorUuid, message.packName, modeId, message.totalChunks, message.isStart);
                if (sink == null) {
                    return new PacketWebIconUploadAck(
                        false,
                        message.chunkIndex,
                        message.totalChunks,
                        "Icon upload session limit reached or session is not active.");
                }
                if (!sink.put(message.chunkIndex, message.chunkData)) {
                    ChunkSink.remove(actorUuid, message.packName, modeId);
                    return new PacketWebIconUploadAck(
                        false,
                        message.chunkIndex,
                        message.totalChunks,
                        "Invalid or incomplete icon upload chunk.");
                }

                if (message.isEnd) {
                    byte[] full = sink.reassemble();
                    if (full == null) {
                        ChunkSink.remove(actorUuid, message.packName, modeId);
                        return new PacketWebIconUploadAck(
                            false,
                            message.chunkIndex,
                            message.totalChunks,
                            "Icon upload is missing one or more chunks.");
                    }
                    ChunkSink.remove(actorUuid, message.packName, modeId);
                    BundleResult result = processBundle(message.packName, modeId, full);
                    int written = result.itemIds.size();
                    com.imgood.textech.webae.debug.WebAeDebugLog.info(
                        com.imgood.textech.webae.debug.WebAeDebugLog.Feature.ICONS,
                        "icon upload complete on server: pack={} iconsWritten={} totalChunks={}",
                        message.packName,
                        written,
                        message.totalChunks);
                    // Record this as the most recent default pack so the web frontend can
                    // pick it automatically on first load.
                    IconStore.instance()
                        .recordDefaultPack(message.packName);
                    IconStore.instance()
                        .recordModeUpload(message.packName, modeId, written);
                    IconMissingQueue.instance()
                        .setProviderUuid(actorUuid);
                    acknowledgeUploadedIcons(message.packName, modeId, result.itemIds);
                    if (written <= 1 && message.totalChunks <= 1) {
                        com.imgood.textech.webae.debug.WebAeDebugLog.info(
                            com.imgood.textech.webae.debug.WebAeDebugLog.Feature.ICONS,
                            "lazy icon upload: pack={} mode={} iconsWritten={} player={}",
                            message.packName,
                            modeId,
                            written,
                            actorUuid);
                    } else {
                        AdvanceDataMonitor.LOG.info(
                            "[WebAE] Icon upload complete: {}/{} chunks, {} icons written for pack '{}' mode '{}' from player {}",
                            message.totalChunks,
                            message.totalChunks,
                            written,
                            message.packName,
                            modeId,
                            actorUuid);
                    }
                    return new PacketWebIconUploadAck(
                        true,
                        message.totalChunks,
                        message.totalChunks,
                        "Icon upload complete. " + written
                            + " icons stored in pack '"
                            + message.packName
                            + "' mode '"
                            + modeId
                            + "'.");
                }

                return new PacketWebIconUploadAck(
                    true,
                    message.chunkIndex + 1,
                    message.totalChunks,
                    "Chunk " + (message.chunkIndex + 1) + "/" + message.totalChunks + " received.");
            } catch (Exception e) {
                AdvanceDataMonitor.LOG.error("[WebAE] Failed to process icon upload chunk", e);
                return new PacketWebIconUploadAck(
                    false,
                    message.chunkIndex,
                    message.totalChunks,
                    "Error processing icon upload chunk.");
            }
        }

        private static boolean matchesPlayerUuid(String supplied, EntityPlayerMP player) {
            return supplied == null || supplied.isEmpty()
                || supplied.equalsIgnoreCase(
                    player.getUniqueID()
                        .toString());
        }

        private static BundleResult processBundle(String packName, String modeId, byte[] jsonBytes) {
            Map<String, byte[]> bundle = decodeBundle(jsonBytes);
            if (!IconStore.instance()
                .writeIconPngBatch(packName, modeId, bundle)) {
                throw new IllegalStateException("Icon bundle could not be promoted atomically");
            }
            List<String> writtenIds = new ArrayList<String>(bundle.keySet());
            for (String itemId : writtenIds) {
                try {
                    com.imgood.textech.webae.events.EventStreamHub.instance()
                        .publishIconReady(packName, modeId, itemId);
                } catch (RuntimeException e) {
                    AdvanceDataMonitor.LOG.warn(
                        "[WebAE] Failed to publish icon-ready event for {} in pack {}: {}",
                        itemId,
                        packName,
                        e.getMessage());
                }
            }
            return new BundleResult(writtenIds);
        }

        private static void acknowledgeUploadedIcons(String packName, String modeId, List<String> itemIds) {
            if (itemIds == null) return;
            for (String itemId : itemIds) {
                IconMissingQueue.instance()
                    .acknowledge(packName, modeId, itemId);
            }
        }

        private static final class BundleResult {

            final List<String> itemIds;

            BundleResult(List<String> itemIds) {
                this.itemIds = itemIds;
            }
        }
    }

    /** Per-(player, pack, mode) strictly ordered chunk accumulator. */
    static final class ChunkSink {

        private static final Map<String, ChunkSink> SINKS = new ConcurrentHashMap<String, ChunkSink>();
        private static long totalReservedBytes;

        private final String playerUuid;
        private final int totalChunks;
        private final long reservedBytes;
        private final WebAeBinaryTransfer.SequentialAssembler assembler;
        private volatile long lastTouchedMs;
        private byte[] completedPayload;

        private ChunkSink(String playerUuid, int totalChunks, long reservedBytes) {
            this.playerUuid = playerUuid;
            this.totalChunks = totalChunks;
            this.reservedBytes = reservedBytes;
            this.assembler = new WebAeBinaryTransfer.SequentialAssembler(MAX_BUNDLE_JSON_BYTES, MAX_TOTAL_CHUNKS);
            this.lastTouchedMs = System.currentTimeMillis();
        }

        static synchronized ChunkSink get(String playerUuid, String packName, String modeId, int totalChunks,
            boolean start) {
            pruneExpired();
            if (playerUuid == null || playerUuid.isEmpty() || totalChunks < 1 || totalChunks > MAX_TOTAL_CHUNKS) {
                return null;
            }
            String key = key(playerUuid, packName, modeId);
            ChunkSink sink = SINKS.get(key);
            if (!start) {
                if (sink == null || sink.totalChunks != totalChunks) return null;
                sink.lastTouchedMs = System.currentTimeMillis();
                return sink;
            }
            if (sink != null || SINKS.size() >= MAX_ACTIVE_SINKS
                || activeSinkCount(playerUuid) >= MAX_ACTIVE_SINKS_PER_PLAYER) {
                return null;
            }
            long reservation = Math.min(MAX_BUNDLE_JSON_BYTES, (long) totalChunks * (long) MAX_CHUNK_BYTES);
            if (reservation <= 0L || totalReservedBytes > MAX_RESERVED_SINK_BYTES - reservation) return null;
            sink = new ChunkSink(playerUuid, totalChunks, reservation);
            SINKS.put(key, sink);
            totalReservedBytes += reservation;
            return sink;
        }

        static synchronized void remove(String playerUuid, String packName, String modeId) {
            release(SINKS.remove(key(playerUuid, packName, modeId)));
        }

        synchronized boolean put(int index, byte[] data) {
            try {
                completedPayload = assembler.accept(index, totalChunks, data);
                lastTouchedMs = System.currentTimeMillis();
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        synchronized byte[] reassemble() {
            return completedPayload;
        }

        private static synchronized void pruneExpired() {
            long now = System.currentTimeMillis();
            for (Map.Entry<String, ChunkSink> entry : SINKS.entrySet()) {
                ChunkSink sink = entry.getValue();
                if (sink != null && now - sink.lastTouchedMs > SINK_TTL_MS) {
                    if (SINKS.remove(entry.getKey(), sink)) release(sink);
                }
            }
        }

        static synchronized void clearAllForTests() {
            SINKS.clear();
            totalReservedBytes = 0L;
        }

        private static int activeSinkCount(String playerUuid) {
            int count = 0;
            for (ChunkSink sink : SINKS.values()) {
                if (sink != null && playerUuid.equals(sink.playerUuid)) count++;
            }
            return count;
        }

        private static void release(ChunkSink sink) {
            if (sink == null) return;
            totalReservedBytes -= sink.reservedBytes;
            if (totalReservedBytes < 0L) totalReservedBytes = 0L;
        }

        private static String key(String playerUuid, String packName, String modeId) {
            return String.valueOf(playerUuid) + '\0' + String.valueOf(packName) + '\0' + String.valueOf(modeId);
        }
    }
}
