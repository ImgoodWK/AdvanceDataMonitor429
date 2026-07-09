package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;

import com.imgood.textech.network.handler.PacketHandlers;
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

    public PacketWorldMapSnapshotSyncRequest() {}

    public PacketWorldMapSnapshotSyncRequest(String ownerUuid, int networkId, int localVersion) {
        this.ownerUuid = ownerUuid;
        this.networkId = networkId;
        this.localVersion = localVersion;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeUtf8(buf, ownerUuid);
        buf.writeInt(networkId);
        buf.writeInt(localVersion);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        ownerUuid = readUtf8(buf);
        networkId = buf.readInt();
        localVersion = buf.readInt();
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

    public static class Handler implements IMessageHandler<PacketWorldMapSnapshotSyncRequest, IMessage> {

        @Override
        public IMessage onMessage(final PacketWorldMapSnapshotSyncRequest message, MessageContext ctx) {
            return PacketHandlers.runOnServer(ctx, new Runnable() {

                @Override
                public void run() {
                    if (message == null || message.ownerUuid == null || message.ownerUuid.isEmpty()) {
                        return;
                    }
                    int serverVersion = WorldMapSnapshotStore.currentVersion(message.ownerUuid, message.networkId);
                    WorldMapSnapshotCurrentPointer ptr = WorldMapSnapshotStore.loadCurrent(
                        message.ownerUuid,
                        message.networkId);
                    PacketWorldMapSnapshotSyncResponse resp = new PacketWorldMapSnapshotSyncResponse();
                    resp.ownerUuid = message.ownerUuid;
                    resp.networkId = message.networkId;
                    resp.serverVersion = serverVersion;
                    resp.previousServerVersion = ptr != null ? ptr.previousVersion : 0;
                    if (serverVersion > message.localVersion) {
                        WorldMapSnapshotManifest manifest = WorldMapSnapshotStore.loadManifest(
                            message.ownerUuid,
                            message.networkId,
                            serverVersion);
                        if (manifest != null && manifest.tiles != null) {
                            for (String key : manifest.tiles.keySet()) {
                                resp.tileKeys.add(key);
                            }
                        }
                    }
                    com.imgood.textech.AdvanceDataMonitor.ADMCHANEL.sendTo(
                        resp,
                        ctx.getServerHandler().playerEntity);
                }
            });
        }
    }
}
