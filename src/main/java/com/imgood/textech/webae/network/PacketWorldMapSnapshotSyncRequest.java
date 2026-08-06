package com.imgood.textech.webae.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.network.handler.PacketHandlers;
import com.imgood.textech.utils.NetworkPacketCodec;
import com.imgood.textech.webae.worldmap.WorldMapPacketAuthorization;
import com.imgood.textech.webae.worldmap.WorldMapSnapshotCurrentPointer;
import com.imgood.textech.webae.worldmap.WorldMapSnapshotManifest;
import com.imgood.textech.webae.worldmap.WorldMapSnapshotStore;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C→S: query server snapshot version for client local cache sync. Packet ID 41.
 */
public class PacketWorldMapSnapshotSyncRequest implements IMessage {

    public String ownerUuid;
    public int networkId;
    public int localVersion;
    public int tileOffset;
    private boolean valid = true;

    private static final int MAX_OWNER_UUID_BYTES = 64;
    private static final int MAX_TILE_OFFSET = 200000;
    private static final int MAX_PACKET_BYTES = 30_000;

    public PacketWorldMapSnapshotSyncRequest() {}

    public PacketWorldMapSnapshotSyncRequest(String ownerUuid, int networkId, int localVersion) {
        this(ownerUuid, networkId, localVersion, 0);
    }

    public PacketWorldMapSnapshotSyncRequest(String ownerUuid, int networkId, int localVersion, int tileOffset) {
        this.ownerUuid = ownerUuid;
        this.networkId = networkId;
        this.localVersion = localVersion;
        this.tileOffset = tileOffset;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int start = buf.writerIndex();
        try {
            writeUtf8(buf, ownerUuid);
            buf.writeInt(networkId);
            buf.writeInt(localVersion);
            buf.writeInt(tileOffset);
            requirePacketBudget(buf, start);
        } catch (RuntimeException e) {
            buf.writerIndex(start);
            throw e;
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        valid = true;
        try {
            if (buf == null || buf.readableBytes() > MAX_PACKET_BYTES) {
                throw new IllegalArgumentException("Snapshot sync request exceeds packet budget");
            }
            ownerUuid = NetworkPacketCodec.readUtf8(buf, MAX_OWNER_UUID_BYTES);
            networkId = buf.readInt();
            localVersion = buf.readInt();
            tileOffset = buf.readInt();
            if (buf.isReadable()) {
                throw new IllegalArgumentException("Snapshot sync request has trailing bytes");
            }
        } catch (RuntimeException e) {
            valid = false;
            ownerUuid = "";
        }
    }

    private static void writeUtf8(ByteBuf buf, String s) {
        if (s == null) {
            buf.writeInt(0);
            return;
        }
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > MAX_OWNER_UUID_BYTES) {
            throw new IllegalArgumentException("Snapshot owner UUID exceeds packet limit");
        }
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static void requirePacketBudget(ByteBuf buf, int start) {
        if (buf.writerIndex() - start > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Snapshot sync request exceeds packet budget");
        }
    }

    public static class Handler implements IMessageHandler<PacketWorldMapSnapshotSyncRequest, IMessage> {

        @Override
        public IMessage onMessage(final PacketWorldMapSnapshotSyncRequest message, MessageContext ctx) {
            final EntityPlayerMP player = ctx == null || ctx.getServerHandler() == null ? null
                : ctx.getServerHandler().playerEntity;
            if (message == null || !message.valid || player == null) {
                return null;
            }
            return PacketHandlers.runOnServer(ctx, new Runnable() {

                @Override
                public void run() {
                    if (message == null || !message.valid
                        || player == null
                        || !WorldMapPacketAuthorization.isValidOwnerUuid(message.ownerUuid)
                        || !WorldMapPacketAuthorization.isValidNetworkId(message.networkId)
                        || message.localVersion < 0
                        || message.localVersion > WorldMapPacketAuthorization.MAX_SNAPSHOT_VERSION
                        || message.tileOffset < 0
                        || message.tileOffset > MAX_TILE_OFFSET
                        || !WorldMapPacketAuthorization
                            .canReadSnapshotScope(player, message.ownerUuid, message.networkId)) {
                        return;
                    }
                    WorldMapSnapshotCurrentPointer ptr = WorldMapSnapshotStore
                        .loadCurrent(message.ownerUuid, message.networkId);
                    int serverVersion = ptr != null ? ptr.version : 0;
                    PacketWorldMapSnapshotSyncResponse resp = new PacketWorldMapSnapshotSyncResponse();
                    resp.ownerUuid = message.ownerUuid;
                    resp.networkId = message.networkId;
                    resp.serverVersion = serverVersion;
                    resp.previousServerVersion = ptr != null ? ptr.previousVersion : 0;
                    resp.batchOffset = message.tileOffset;
                    resp.nextOffset = message.tileOffset;
                    resp.complete = true;
                    if (serverVersion > message.localVersion) {
                        WorldMapSnapshotManifest manifest = WorldMapSnapshotStore
                            .loadManifest(message.ownerUuid, message.networkId, serverVersion);
                        if (manifest != null && manifest.tiles != null) {
                            List<String> keys = new ArrayList<String>(manifest.tiles.keySet());
                            if (message.tileOffset > keys.size()) {
                                return;
                            }
                            int index = message.tileOffset;
                            while (index < keys.size() && resp.tryAddTileKey(keys.get(index))) {
                                index++;
                            }
                            resp.nextOffset = index;
                            resp.complete = index >= keys.size();
                            if (index == message.tileOffset && !resp.complete) {
                                return;
                            }
                        } else if (message.tileOffset != 0) {
                            return;
                        }
                    } else if (message.tileOffset != 0) {
                        return;
                    }
                    if (serverVersion > 0 && !WorldMapPacketAuthorization
                        .canReadSnapshot(player, message.ownerUuid, message.networkId, serverVersion)) {
                        return;
                    }
                    com.imgood.textech.AdvanceDataMonitor.ADMCHANEL.sendTo(resp, player);
                }
            });
        }
    }
}
