package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.webae.events.EventStreamHub;
import com.imgood.textech.webae.icon.IconMissingQueue;
import com.imgood.textech.webae.icon.IconStore;
import com.imgood.textech.webae.icon.IconRenderMode;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C→S client uploads a rendered icon bundle (itemId → base64 PNG) in chunks.
 *
 * Packet ID 28. Reuses the chunked-upload pattern from {@link PacketWebRecipeUpload}
 * but the reassembled payload is a JSON map of itemId → base64PNG strings, which the
 * server decodes and writes to {@code config/textech/web-icons/<packName>/}.
 */
public class PacketWebIconUpload implements IMessage {

    private static final Gson GSON = new GsonBuilder().create();

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
        buf.writeBoolean(isStart);
        buf.writeBoolean(isEnd);
        buf.writeInt(chunkIndex);
        buf.writeInt(totalChunks);
        writeUtf8(buf, packName);
        writeUtf8(buf, renderMode);
        writeUtf8(buf, playerUuid);
        if (chunkData != null) {
            buf.writeInt(chunkData.length);
            buf.writeBytes(chunkData);
        } else {
            buf.writeInt(0);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        isStart = buf.readBoolean();
        isEnd = buf.readBoolean();
        chunkIndex = buf.readInt();
        totalChunks = buf.readInt();
        packName = readUtf8(buf);
        renderMode = readUtf8(buf);
        playerUuid = readUtf8(buf);
        int dataLen = buf.readInt();
        if (dataLen > 0) {
            chunkData = new byte[dataLen];
            buf.readBytes(chunkData);
        } else {
            chunkData = new byte[0];
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
        if (len <= 0) return "";
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Server-side handler: accumulates chunks and, when the final chunk arrives,
     * reassembles the JSON bundle, decodes each base64 PNG, and writes it to disk.
     */
    public static class Handler implements IMessageHandler<PacketWebIconUpload, IMessage> {

        @Override
        public IMessage onMessage(PacketWebIconUpload message, MessageContext ctx) {
            try {
                if (!IconStore.isValidPackName(message.packName)) {
                    return new PacketWebIconUploadAck(
                        false,
                        message.chunkIndex + 1,
                        message.totalChunks,
                        "Invalid pack name: " + message.packName);
                }
                String modeId = IconStore.normalizeModeId(message.renderMode);
                if (!IconStore.isValidModeDirName(modeId)) {
                    return new PacketWebIconUploadAck(
                        false,
                        message.chunkIndex + 1,
                        message.totalChunks,
                        "Invalid render mode: " + message.renderMode);
                }
                ChunkSink sink = ChunkSink.get(message.playerUuid, message.packName, modeId, message.totalChunks);
                sink.put(message.chunkIndex, message.chunkData);

                if (message.isEnd) {
                    byte[] full = sink.reassemble();
                    ChunkSink.remove(message.playerUuid, message.packName, modeId);
                    int written = processBundle(message.packName, modeId, full);
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
                        .setProviderUuid(message.playerUuid);
                    acknowledgeUploadedIcons(message.packName, modeId, full);
                    AdvanceDataMonitor.LOG.info(
                        "[WebAE] Icon upload complete: {}/{} chunks, {} icons written for pack '{}' mode '{}' from player {}",
                        message.totalChunks,
                        message.totalChunks,
                        written,
                        message.packName,
                        modeId,
                        message.playerUuid);
                    return new PacketWebIconUploadAck(
                        true,
                        message.totalChunks,
                        message.totalChunks,
                        "Icon upload complete. " + written + " icons stored in pack '" + message.packName + "' mode '"
                            + modeId + "'.");
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
                    "Error processing chunk: " + e.getMessage());
            }
        }

        @SuppressWarnings("unchecked")
        private int processBundle(String packName, String modeId, byte[] jsonBytes) {
            if (jsonBytes == null || jsonBytes.length == 0) return 0;
            String json = new String(jsonBytes, StandardCharsets.UTF_8);
            Map<String, String> bundle = GSON.fromJson(json, new TypeToken<Map<String, String>>() {}.getType());
            if (bundle == null || bundle.isEmpty()) return 0;
            int written = 0;
            for (Map.Entry<String, String> e : bundle.entrySet()) {
                String itemId = e.getKey();
                String base64 = e.getValue();
                if (!IconStore.isValidItemId(itemId) || base64 == null || base64.isEmpty()) continue;
                try {
                    byte[] png = javax.xml.bind.DatatypeConverter.parseBase64Binary(base64);
                    java.io.File target = IconStore.instance()
                        .resolveWriteTarget(packName, modeId, itemId);
                    if (target == null) continue;
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(target);
                    try {
                        fos.write(png);
                    } finally {
                        fos.close();
                    }
                    written++;
                    com.imgood.textech.webae.events.EventStreamHub.instance()
                        .publishIconReady(packName, modeId, itemId);
                } catch (Exception ex) {
                    AdvanceDataMonitor.LOG.warn("[WebAE] Failed to write icon {} to pack {}", itemId, packName, ex);
                }
            }
            IconStore.instance()
                .refreshPack(packName);
            return written;
        }

        private static void acknowledgeUploadedIcons(String packName, String modeId, byte[] jsonBytes) {
            if (jsonBytes == null || jsonBytes.length == 0) return;
            try {
                String json = new String(jsonBytes, StandardCharsets.UTF_8);
                Map<String, String> bundle = GSON.fromJson(json, new TypeToken<Map<String, String>>() {}.getType());
                if (bundle == null) return;
                for (String itemId : bundle.keySet()) {
                    IconMissingQueue.instance()
                        .acknowledge(packName, modeId, itemId);
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Per-(player,pack) chunk accumulator. Chunks arrive in arbitrary order and are
     * reassembled in index order when the final chunk is received.
     */
    static final class ChunkSink {

        private static final Map<String, ChunkSink> SINKS = new ConcurrentHashMap<String, ChunkSink>();

        private final int totalChunks;
        private final Map<Integer, byte[]> chunks = new ConcurrentHashMap<Integer, byte[]>();

        private ChunkSink(int totalChunks) {
            this.totalChunks = totalChunks;
        }

        static ChunkSink get(String playerUuid, String packName, String modeId, int totalChunks) {
            String key = playerUuid + "|" + packName + "|" + modeId;
            ChunkSink sink = SINKS.get(key);
            if (sink == null) {
                sink = new ChunkSink(totalChunks);
                ChunkSink existing = SINKS.putIfAbsent(key, sink);
                if (existing != null) sink = existing;
            }
            return sink;
        }

        static void remove(String playerUuid, String packName, String modeId) {
            SINKS.remove(playerUuid + "|" + packName + "|" + modeId);
        }

        void put(int index, byte[] data) {
            chunks.put(index, data);
        }

        byte[] reassemble() {
            List<Integer> indices = new ArrayList<Integer>(chunks.keySet());
            Collections.sort(indices);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            for (int idx : indices) {
                byte[] d = chunks.get(idx);
                if (d != null) out.write(d, 0, d.length);
            }
            return out.toByteArray();
        }
    }
}
