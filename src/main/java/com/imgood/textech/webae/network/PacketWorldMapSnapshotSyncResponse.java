package com.imgood.textech.webae.network;

import java.util.ArrayList;
import java.util.List;

import com.imgood.textech.client.worldmap.WorldMapSnapshotDownloadHandler;
import com.imgood.textech.utils.NetworkPacketCodec;
import com.imgood.textech.webae.worldmap.WorldMapPacketAuthorization;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * S→C: server snapshot version and tile key list for sync. Packet ID 42.
 */
public class PacketWorldMapSnapshotSyncResponse implements IMessage {

    public String ownerUuid;
    public int networkId;
    public int serverVersion;
    /** Previous finalized version kept on server for fallback (0 = none). */
    public int previousServerVersion;
    /** Absolute tile index represented by the first key in this page. */
    public int batchOffset;
    /** Absolute tile index the client should request next. */
    public int nextOffset;
    /** True when this page completes the manifest key stream. */
    public boolean complete = true;
    public List<String> tileKeys = new ArrayList<String>();
    public boolean malformed;

    private static final int MAX_OWNER_UUID_BYTES = 64;
    static final int MAX_PACKET_BYTES = 30_000;
    static final int MAX_TILE_KEYS = 2048;
    static final int MAX_TILE_KEY_BYTES = 128;
    static final int MAX_TILE_OFFSET = 200000;

    public PacketWorldMapSnapshotSyncResponse() {}

    @Override
    public void toBytes(ByteBuf buf) {
        int start = buf.writerIndex();
        try {
            int encodedBytes = encodedPayloadBytes();
            if (encodedBytes > MAX_PACKET_BYTES) {
                throw new IllegalArgumentException("Snapshot sync response exceeds FML packet budget");
            }
            writeUtf8(buf, ownerUuid, MAX_OWNER_UUID_BYTES);
            buf.writeInt(networkId);
            buf.writeInt(serverVersion);
            buf.writeInt(previousServerVersion);
            buf.writeInt(batchOffset);
            buf.writeInt(nextOffset);
            buf.writeBoolean(complete);
            int count = tileKeys != null ? tileKeys.size() : 0;
            if (count > MAX_TILE_KEYS) {
                throw new IllegalArgumentException("Snapshot sync response contains too many tiles");
            }
            buf.writeInt(count);
            if (tileKeys != null) {
                for (String key : tileKeys) {
                    writeUtf8(buf, key, MAX_TILE_KEY_BYTES);
                }
            }
            if (buf.writerIndex() - start > MAX_PACKET_BYTES) {
                throw new IllegalArgumentException("Snapshot sync response exceeds FML packet budget");
            }
        } catch (RuntimeException e) {
            buf.writerIndex(start);
            throw e;
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        malformed = false;
        try {
            if (buf == null || buf.readableBytes() > MAX_PACKET_BYTES) {
                throw new IllegalArgumentException("Snapshot sync response exceeds packet budget");
            }
            ownerUuid = NetworkPacketCodec.readUtf8(buf, MAX_OWNER_UUID_BYTES);
            networkId = buf.readInt();
            serverVersion = buf.readInt();
            previousServerVersion = buf.readInt();
            batchOffset = buf.readInt();
            nextOffset = buf.readInt();
            complete = buf.readBoolean();
            int count = buf.readInt();
            if (count < 0 || count > MAX_TILE_KEYS || count > buf.readableBytes() / 4) {
                throw new IllegalArgumentException("Invalid snapshot tile key count");
            }
            long pageLength = (long) nextOffset - batchOffset;
            if (batchOffset < 0 || batchOffset > MAX_TILE_OFFSET
                || nextOffset < batchOffset
                || nextOffset > MAX_TILE_OFFSET
                || pageLength != count) {
                throw new IllegalArgumentException("Invalid snapshot tile page offsets");
            }
            tileKeys = new ArrayList<String>(count);
            for (int i = 0; i < count; i++) {
                tileKeys.add(NetworkPacketCodec.readUtf8(buf, MAX_TILE_KEY_BYTES));
            }
            if (buf.isReadable()) {
                throw new IllegalArgumentException("Snapshot sync response has trailing bytes");
            }
        } catch (RuntimeException e) {
            malformed = true;
            ownerUuid = "";
            tileKeys = new ArrayList<String>();
        }
    }

    /** Adds one manifest key when it still fits the conservative FML payload budget. */
    boolean tryAddTileKey(String key) {
        if (tileKeys == null) {
            tileKeys = new ArrayList<String>();
        }
        if (tileKeys.size() >= MAX_TILE_KEYS) {
            return false;
        }
        int keyBytes = utf8Length(key, MAX_TILE_KEY_BYTES);
        if (encodedPayloadBytes() + 4 + keyBytes > MAX_PACKET_BYTES) {
            return false;
        }
        tileKeys.add(key);
        return true;
    }

    private int encodedPayloadBytes() {
        int total = 4 + utf8Length(ownerUuid, MAX_OWNER_UUID_BYTES);
        // network/version/previous/batch/next, complete flag, and tile count.
        total += 5 * 4 + 1 + 4;
        int count = tileKeys != null ? tileKeys.size() : 0;
        if (count > MAX_TILE_KEYS) {
            throw new IllegalArgumentException("Snapshot sync response contains too many tiles");
        }
        if (tileKeys != null) {
            for (String key : tileKeys) {
                total += 4 + utf8Length(key, MAX_TILE_KEY_BYTES);
            }
        }
        return total;
    }

    private static int utf8Length(String value, int maxBytes) {
        int length = value == null ? 0 : value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (length > maxBytes) {
            throw new IllegalArgumentException("Snapshot sync response string exceeds packet limit");
        }
        return length;
    }

    private static void writeUtf8(ByteBuf buf, String s, int maxBytes) {
        if (s == null) {
            buf.writeInt(0);
            return;
        }
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > maxBytes) {
            throw new IllegalArgumentException("Snapshot sync response string exceeds packet limit");
        }
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    public static class Handler implements IMessageHandler<PacketWorldMapSnapshotSyncResponse, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketWorldMapSnapshotSyncResponse message, MessageContext ctx) {
            if (message == null || message.malformed
                || !WorldMapPacketAuthorization.isValidOwnerUuid(message.ownerUuid)
                || !WorldMapPacketAuthorization.isValidNetworkId(message.networkId)
                || !WorldMapPacketAuthorization.isValidSnapshotVersion(message.serverVersion)
                || message.previousServerVersion < 0
                || message.previousServerVersion > WorldMapPacketAuthorization.MAX_SNAPSHOT_VERSION) {
                return null;
            }
            WorldMapSnapshotDownloadHandler.instance()
                .onSyncResponse(message);
            return null;
        }
    }
}
