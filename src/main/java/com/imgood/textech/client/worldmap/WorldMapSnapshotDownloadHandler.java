package com.imgood.textech.client.worldmap;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

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
    private final Set<String> pendingTileKeys = new HashSet<String>();
    private String ownerUuid;
    private int networkId;
    private int targetVersion;
    private int targetPreviousVersion;
    private int syncLocalVersion;
    private int expectedPageOffset;
    private int pendingPageOffset = -1;
    private int tickCounter;
    private boolean syncRequested;
    private boolean manifestPagesComplete;

    private WorldMapSnapshotDownloadHandler() {}

    public static WorldMapSnapshotDownloadHandler instance() {
        return INSTANCE;
    }

    public synchronized void scheduleSyncForOwner(String ownerUuid, int networkId) {
        if (ownerUuid == null || ownerUuid.isEmpty() || networkId < 0) {
            return;
        }
        boolean scopeChanged = !ownerUuid.equals(this.ownerUuid) || networkId != this.networkId;
        this.ownerUuid = ownerUuid;
        this.networkId = networkId;
        this.syncRequested = true;
        this.tickCounter = SYNC_INTERVAL_TICKS;
        if (scopeChanged) {
            resetTransferState();
        }
    }

    @SubscribeEvent
    public synchronized void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (ownerUuid == null || networkId < 0) {
            return;
        }
        tickCounter++;
        if (syncRequested && tickCounter >= SYNC_INTERVAL_TICKS) {
            syncRequested = false;
            syncLocalVersion = WorldMapSnapshotLocalCache.readLocalVersion(ownerUuid, networkId);
            pendingPageOffset = -1;
            expectedPageOffset = 0;
            manifestPagesComplete = false;
            PacketWorldMapSnapshotSyncRequest req = new PacketWorldMapSnapshotSyncRequest(
                ownerUuid,
                networkId,
                syncLocalVersion,
                0);
            AdvanceDataMonitor.ADMCHANEL.sendToServer(req);
        }
        if (pendingPageOffset >= 0) {
            int offset = pendingPageOffset;
            pendingPageOffset = -1;
            AdvanceDataMonitor.ADMCHANEL.sendToServer(
                new PacketWorldMapSnapshotSyncRequest(ownerUuid, networkId, syncLocalVersion, offset));
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

    public synchronized void onSyncResponse(PacketWorldMapSnapshotSyncResponse message) {
        if (message == null || message.serverVersion <= 0 || ownerUuid == null
            || !ownerUuid.equals(message.ownerUuid) || networkId != message.networkId) {
            return;
        }
        if (message.batchOffset == 0) {
            resetTransferState();
            targetVersion = message.serverVersion;
            targetPreviousVersion = message.previousServerVersion;
            expectedPageOffset = 0;
        } else if (message.serverVersion != targetVersion || message.batchOffset != expectedPageOffset) {
            pendingPageOffset = 0;
            return;
        }
        if (message.batchOffset != expectedPageOffset || message.nextOffset < message.batchOffset
            || message.tileKeys == null || message.nextOffset - message.batchOffset != message.tileKeys.size()) {
            pendingPageOffset = 0;
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
            if (pendingTileKeys.add(pull.key)) {
                pullQueue.offerLast(pull);
            }
        }
        expectedPageOffset = message.nextOffset;
        manifestPagesComplete = message.complete;
        if (!message.complete) {
            pendingPageOffset = message.nextOffset;
        } else {
            finishIfComplete(message.ownerUuid, message.networkId);
        }
    }

    public synchronized void onTileData(PacketWorldMapSnapshotTileData message) {
        if (message == null || message.png == null || message.png.length == 0 || ownerUuid == null
            || !ownerUuid.equals(message.ownerUuid) || networkId != message.networkId
            || targetVersion != message.snapshotVersion) {
            return;
        }
        String key = message.layer + ":" + message.dim + ":" + message.chunkX + ":" + message.chunkZ;
        if (!pendingTileKeys.remove(key)) {
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
        finishIfComplete(message.ownerUuid, message.networkId);
    }

    private void finishIfComplete(String transferOwnerUuid, int transferNetworkId) {
        if (!manifestPagesComplete || !pullQueue.isEmpty() || !pendingTileKeys.isEmpty()) {
            return;
        }
        WorldMapSnapshotLocalCache.writeLocalVersion(transferOwnerUuid, transferNetworkId, targetVersion);
        WorldMapSnapshotLocalCache
            .pruneOldVersions(transferOwnerUuid, transferNetworkId, targetVersion, targetPreviousVersion);
    }

    private void resetTransferState() {
        pullQueue.clear();
        pendingTileKeys.clear();
        targetVersion = 0;
        targetPreviousVersion = 0;
        expectedPageOffset = 0;
        pendingPageOffset = -1;
        manifestPagesComplete = false;
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
            pull.key = key;
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
        String key;
    }
}
