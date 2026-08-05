package com.imgood.textech.webae.network;

import java.util.ArrayList;
import java.util.List;

import com.imgood.textech.client.worldmap.WorldMapSnapshotCaptureWorker;
import com.imgood.textech.utils.NetworkPacketCodec;
import com.imgood.textech.webae.worldmap.WorldMapPacketAuthorization;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * S→C: start world map snapshot capture job. Packet ID 39.
 */
public class PacketWorldMapCaptureJob implements IMessage {

    public String ownerUuid;
    public int networkId;
    public int snapshotVersion;
    public int tilePx;
    /** Comma-separated capture priority from server config (informational). */
    public String sourcePriority = "";
    public int pageIndex;
    public int pageCount = 1;
    public int chunkOffset;
    public int totalChunks;
    public List<String> chunks = new ArrayList<String>();
    private boolean valid = true;

    public static final int MAX_PACKET_BYTES = 30_000;
    public static final int MAX_TOTAL_CHUNKS = 100_000;
    private static final int MAX_OWNER_UUID_BYTES = 64;
    private static final int MAX_SOURCE_PRIORITY_BYTES = 128;
    private static final int MAX_CHUNK_ENTRY_BYTES = 64;
    private static final int FIXED_INT_BYTES = 8 * 4;

    public PacketWorldMapCaptureJob() {}

    public PacketWorldMapCaptureJob(String ownerUuid, int networkId, int snapshotVersion, List<String> chunks,
        int tilePx) {
        this.ownerUuid = ownerUuid;
        this.networkId = networkId;
        this.snapshotVersion = snapshotVersion;
        this.tilePx = tilePx;
        if (chunks != null) {
            this.chunks = new ArrayList<String>(chunks);
        }
        this.totalChunks = this.chunks.size();
    }

    public static List<PacketWorldMapCaptureJob> createPages(String ownerUuid, int networkId, int snapshotVersion,
        List<String> chunks, int tilePx, String sourcePriority) {
        String priority = sourcePriority == null ? "" : sourcePriority;
        byte[] ownerBytes = utf8(ownerUuid, MAX_OWNER_UUID_BYTES);
        byte[] priorityBytes = utf8(priority, MAX_SOURCE_PRIORITY_BYTES);
        if (!WorldMapPacketAuthorization.isValidOwnerUuid(ownerUuid)
            || !WorldMapPacketAuthorization.isValidNetworkId(networkId)
            || !WorldMapPacketAuthorization.isValidSnapshotVersion(snapshotVersion)
            || !WorldMapPacketAuthorization.isValidTilePx(tilePx)
            || chunks == null
            || chunks.isEmpty()
            || chunks.size() > MAX_TOTAL_CHUNKS) {
            throw new IllegalArgumentException("Invalid world map capture job metadata");
        }

        int baseBytes = FIXED_INT_BYTES + 4 + ownerBytes.length + 4 + priorityBytes.length;
        List<List<String>> chunkPages = new ArrayList<List<String>>();
        List<String> current = new ArrayList<String>();
        int currentBytes = baseBytes;
        for (String chunk : chunks) {
            byte[] chunkBytes = utf8(chunk, MAX_CHUNK_ENTRY_BYTES);
            if (parseChunkEntry(chunk) == null) {
                throw new IllegalArgumentException("Invalid world map capture chunk entry");
            }
            int entryBytes = 4 + chunkBytes.length;
            if (!current.isEmpty() && currentBytes + entryBytes > MAX_PACKET_BYTES) {
                chunkPages.add(current);
                current = new ArrayList<String>();
                currentBytes = baseBytes;
            }
            if (currentBytes + entryBytes > MAX_PACKET_BYTES) {
                throw new IllegalArgumentException("World map capture chunk cannot fit packet budget");
            }
            current.add(chunk);
            currentBytes += entryBytes;
        }
        if (!current.isEmpty()) {
            chunkPages.add(current);
        }

        List<PacketWorldMapCaptureJob> pages = new ArrayList<PacketWorldMapCaptureJob>(chunkPages.size());
        int offset = 0;
        for (int index = 0; index < chunkPages.size(); index++) {
            PacketWorldMapCaptureJob page = new PacketWorldMapCaptureJob(
                ownerUuid,
                networkId,
                snapshotVersion,
                chunkPages.get(index),
                tilePx);
            page.sourcePriority = priority;
            page.pageIndex = index;
            page.pageCount = chunkPages.size();
            page.chunkOffset = offset;
            page.totalChunks = chunks.size();
            if (!page.isStructurallyValid() || page.encodedBodySize() > MAX_PACKET_BYTES) {
                throw new IllegalArgumentException("World map capture page exceeds packet budget");
            }
            pages.add(page);
            offset += page.chunks.size();
        }
        if (offset != chunks.size()) {
            throw new IllegalArgumentException("World map capture pagination lost chunks");
        }
        return pages;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        if (!isStructurallyValid() || encodedBodySize() > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Invalid or oversized world map capture page");
        }
        writeUtf8(buf, ownerUuid, MAX_OWNER_UUID_BYTES);
        buf.writeInt(networkId);
        buf.writeInt(snapshotVersion);
        buf.writeInt(tilePx);
        writeUtf8(buf, sourcePriority, MAX_SOURCE_PRIORITY_BYTES);
        buf.writeInt(pageIndex);
        buf.writeInt(pageCount);
        buf.writeInt(chunkOffset);
        buf.writeInt(totalChunks);
        int count = chunks != null ? chunks.size() : 0;
        buf.writeInt(count);
        if (chunks != null) {
            for (String chunk : chunks) {
                writeUtf8(buf, chunk, MAX_CHUNK_ENTRY_BYTES);
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        valid = true;
        try {
            int bodyBytes = buf.readableBytes();
            if (bodyBytes <= 0 || bodyBytes > MAX_PACKET_BYTES) {
                throw new IllegalArgumentException("World map capture page exceeds packet budget");
            }
            ownerUuid = NetworkPacketCodec.readUtf8(buf, MAX_OWNER_UUID_BYTES);
            networkId = buf.readInt();
            snapshotVersion = buf.readInt();
            tilePx = buf.readInt();
            sourcePriority = NetworkPacketCodec.readUtf8(buf, MAX_SOURCE_PRIORITY_BYTES);
            pageIndex = buf.readInt();
            pageCount = buf.readInt();
            chunkOffset = buf.readInt();
            totalChunks = buf.readInt();
            int count = buf.readInt();
            if (count < 1 || count > MAX_TOTAL_CHUNKS || count > buf.readableBytes() / 4) {
                throw new IllegalArgumentException("Invalid world map capture chunk count");
            }
            chunks = new ArrayList<String>(count);
            for (int i = 0; i < count; i++) {
                String chunk = NetworkPacketCodec.readUtf8(buf, MAX_CHUNK_ENTRY_BYTES);
                if (parseChunkEntry(chunk) == null) {
                    throw new IllegalArgumentException("Invalid world map capture chunk entry");
                }
                chunks.add(chunk);
            }
            if (buf.isReadable() || !isStructurallyValid() || encodedBodySize() != bodyBytes) {
                throw new IllegalArgumentException("Malformed world map capture page");
            }
        } catch (RuntimeException e) {
            valid = false;
            ownerUuid = "";
            sourcePriority = "";
            pageIndex = 0;
            pageCount = 0;
            chunkOffset = 0;
            totalChunks = 0;
            chunks = new ArrayList<String>();
        }
    }

    public boolean isValid() {
        return valid && isStructurallyValid();
    }

    public int encodedBodySize() {
        int size = FIXED_INT_BYTES;
        size += 4 + utf8(ownerUuid, MAX_OWNER_UUID_BYTES).length;
        size += 4 + utf8(sourcePriority, MAX_SOURCE_PRIORITY_BYTES).length;
        if (chunks != null) {
            for (String chunk : chunks) {
                size += 4 + utf8(chunk, MAX_CHUNK_ENTRY_BYTES).length;
            }
        }
        return size;
    }

    public static int[] parseChunkEntry(String entry) {
        if (entry == null || entry.isEmpty()) {
            return null;
        }
        int colon = entry.indexOf(':');
        int comma = entry.indexOf(',', colon + 1);
        if (colon <= 0 || comma <= colon + 1
            || comma >= entry.length() - 1
            || entry.indexOf(':', colon + 1) >= 0
            || entry.indexOf(',', comma + 1) >= 0) {
            return null;
        }
        try {
            int dim = parseCanonicalInt(entry.substring(0, colon));
            int chunkX = parseCanonicalInt(entry.substring(colon + 1, comma));
            int chunkZ = parseCanonicalInt(entry.substring(comma + 1));
            return WorldMapPacketAuthorization.isValidChunk(dim, chunkX, chunkZ) ? new int[] { dim, chunkX, chunkZ }
                : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isStructurallyValid() {
        if (!WorldMapPacketAuthorization.isValidOwnerUuid(ownerUuid)
            || !WorldMapPacketAuthorization.isValidNetworkId(networkId)
            || !WorldMapPacketAuthorization.isValidSnapshotVersion(snapshotVersion)
            || !WorldMapPacketAuthorization.isValidTilePx(tilePx)
            || pageCount < 1
            || pageCount > MAX_TOTAL_CHUNKS
            || pageIndex < 0
            || pageIndex >= pageCount
            || totalChunks < 1
            || totalChunks > MAX_TOTAL_CHUNKS
            || pageCount > totalChunks
            || chunks == null
            || chunks.isEmpty()
            || chunks.size() > totalChunks
            || chunkOffset < 0
            || chunkOffset > totalChunks - chunks.size()
            || sourcePriority == null) {
            return false;
        }
        if ((pageIndex == 0 && chunkOffset != 0)
            || (pageIndex == pageCount - 1 && chunkOffset + chunks.size() != totalChunks)) {
            return false;
        }
        try {
            utf8(sourcePriority, MAX_SOURCE_PRIORITY_BYTES);
            for (String chunk : chunks) {
                if (parseChunkEntry(chunk) == null) {
                    return false;
                }
                utf8(chunk, MAX_CHUNK_ENTRY_BYTES);
            }
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static int parseCanonicalInt(String value) {
        int parsed = Integer.parseInt(value);
        if (!String.valueOf(parsed)
            .equals(value)) {
            throw new IllegalArgumentException("Non-canonical integer");
        }
        return parsed;
    }

    private static byte[] utf8(String value, int maxBytes) {
        String normalized = value == null ? "" : value;
        byte[] bytes = normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > maxBytes) {
            throw new IllegalArgumentException("World map capture job field exceeds packet limit");
        }
        return bytes;
    }

    private static void writeUtf8(ByteBuf buf, String s, int maxBytes) {
        byte[] bytes = utf8(s, maxBytes);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    public static class Handler implements IMessageHandler<PacketWorldMapCaptureJob, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketWorldMapCaptureJob message, MessageContext ctx) {
            if (message == null || !message.isValid()) return null;
            WorldMapSnapshotCaptureWorker.instance()
                .acceptJobPage(message);
            return null;
        }
    }
}
