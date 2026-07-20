package com.imgood.textech.client.worldmap;

import java.util.ArrayDeque;
import java.util.Deque;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.network.PacketWorldMapSnapshotSyncRequest;
import com.imgood.textech.webae.network.PacketWorldMapSnapshotSyncResponse;
import com.imgood.textech.webae.network.PacketWorldMapSnapshotTileData;
import com.imgood.textech.webae.network.PacketWorldMapSnapshotTilePull;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Downloads snapshot tiles from server to MC client local cache when local version is stale.
 */
@SideOnly(Side.CLIENT)
public final class WorldMapSnapshotDownloadHandler {

    private static final WorldMapSnapshotDownloadHandler INSTANCE = new WorldMapSnapshotDownloadHandler();
    private static final int SYNC_INTERVAL_TICKS = 200;

    private final Deque<TilePull> pullQueue = new ArrayDeque<TilePull>();
    private String ownerUuid;
    private int networkId;
    private int targetVersion;
    private int targetPreviousVersion;
    private int tickCounter;
    private boolean syncRequested;

    private WorldMapSnapshotDownloadHandler() {}

    public static WorldMapSnapshotDownloadHandler instance() {
        return INSTANCE;
    }

    public void scheduleSyncForOwner(String ownerUuid, int networkId) {
        if (ownerUuid == null || ownerUuid.isEmpty() || networkId < 0) {
            return;
        }
        this.ownerUuid = ownerUuid;
        this.networkId = networkId;
        this.syncRequested = true;
        this.tickCounter = SYNC_INTERVAL_TICKS;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (ownerUuid == null || networkId < 0) {
            return;
        }
        tickCounter++;
        if (syncRequested && tickCounter >= SYNC_INTERVAL_TICKS) {
            syncRequested = false;
            int localVersion = WorldMapSnapshotLocalCache.readLocalVersion(ownerUuid, networkId);
            PacketWorldMapSnapshotSyncRequest req = new PacketWorldMapSnapshotSyncRequest(
                ownerUuid,
                networkId,
                localVersion);
            AdvanceDataMonitor.ADMCHANEL.sendToServer(req);
        }
        int budget = Math.max(1, Config.worldMapClientDownloadBudgetPerTick);
        for (int i = 0; i < budget && !pullQueue.isEmpty(); i++) {
            TilePull pull = pullQueue.pollFirst();
            if (pull == null) {
                break;
            }
            PacketWorldMapSnapshotTilePull packet = new PacketWorldMapSnapshotTilePull();
            packet.ownerUuid = pull.ownerUuid;
            packet.networkId = pull.networkId;
            packet.snapshotVersion = pull.version;
            packet.layer = pull.layer;
            packet.dim = pull.dim;
            packet.chunkX = pull.chunkX;
            packet.chunkZ = pull.chunkZ;
            AdvanceDataMonitor.ADMCHANEL.sendToServer(packet);
        }
    }

    public void onSyncResponse(PacketWorldMapSnapshotSyncResponse message) {
        if (message == null || message.serverVersion <= 0) {
            return;
        }
        targetVersion = message.serverVersion;
        targetPreviousVersion = message.previousServerVersion;
        pullQueue.clear();
        if (message.tileKeys == null || message.tileKeys.isEmpty()) {
            WorldMapSnapshotLocalCache.writeLocalVersion(message.ownerUuid, message.networkId, message.serverVersion);
            WorldMapSnapshotLocalCache.pruneOldVersions(
                message.ownerUuid,
                message.networkId,
                message.serverVersion,
                message.previousServerVersion);
            return;
        }
        for (String key : message.tileKeys) {
            TilePull pull = parseKey(message.ownerUuid, message.networkId, message.serverVersion, key);
            if (pull == null) {
                continue;
            }
            if (WorldMapSnapshotLocalCache.getExistingTile(
                pull.ownerUuid,
                pull.networkId,
                pull.version,
                pull.layer,
                pull.dim,
                pull.chunkX,
                pull.chunkZ) != null) {
                continue;
            }
            pullQueue.offerLast(pull);
        }
    }

    public void onTileData(PacketWorldMapSnapshotTileData message) {
        if (message == null || message.png == null || message.png.length == 0) {
            return;
        }
        WorldMapSnapshotLocalCache.writeTile(
            message.ownerUuid,
            message.networkId,
            message.snapshotVersion,
            message.layer,
            message.dim,
            message.chunkX,
            message.chunkZ,
            message.png);
        if (pullQueue.isEmpty()) {
            WorldMapSnapshotLocalCache.writeLocalVersion(message.ownerUuid, message.networkId, targetVersion);
            WorldMapSnapshotLocalCache
                .pruneOldVersions(message.ownerUuid, message.networkId, targetVersion, targetPreviousVersion);
        }
    }

    private static TilePull parseKey(String ownerUuid, int networkId, int version, String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        String[] parts = key.split(":");
        if (parts.length != 4) {
            return null;
        }
        try {
            TilePull pull = new TilePull();
            pull.ownerUuid = ownerUuid;
            pull.networkId = networkId;
            pull.version = version;
            pull.layer = parts[0];
            pull.dim = Integer.parseInt(parts[1]);
            pull.chunkX = Integer.parseInt(parts[2]);
            pull.chunkZ = Integer.parseInt(parts[3]);
            return pull;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final class TilePull {

        String ownerUuid;
        int networkId;
        int version;
        String layer;
        int dim;
        int chunkX;
        int chunkZ;
    }
}
