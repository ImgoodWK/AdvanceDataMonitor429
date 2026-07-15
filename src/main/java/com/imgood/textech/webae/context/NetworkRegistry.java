package com.imgood.textech.webae.context;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.ForgeDirection;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.handler.HandlerWebPlayerTracker;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.tileentity.TileEntityAdvanceNetworkLink;
import com.imgood.textech.webae.context.WebAeOwnerContext.NetworkGroup;
import com.imgood.textech.webae.player.PlayerInfo;
import com.imgood.textech.webae.player.PlayerInfoStore;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;

/**
 * Event-driven player-to-AE-network registry for WebAE. Replaces full-dimension
 * {@code scanMonitors()} on the hot path with O(1) lookup after incremental updates.
 */
public final class NetworkRegistry {

    private static final long HEALTH_INTERVAL_MS = 5_000L;

    /** ownerUuid → registered monitor/network entries. */
    private static final ConcurrentHashMap<String, CopyOnWriteArrayList<RegisteredNetwork>> playerNetworks =
        new ConcurrentHashMap<String, CopyOnWriteArrayList<RegisteredNetwork>>();

    /**
     * ownerUuid → stable monitor keys in the same order as API {@code networkId}.
     * Updated when {@link #getNetworks} / group seeding runs on the server thread — never rebuilt via World
     * from HTTP workers.
     */
    private static final ConcurrentHashMap<String, CopyOnWriteArrayList<String>> networkIdKeys =
        new ConcurrentHashMap<String, CopyOnWriteArrayList<String>>();

    private static volatile long lastGlobalHealthCheckMs;

    private NetworkRegistry() {}

    /** One logical AE network anchored at a data monitor. */
    public static final class RegisteredNetwork {

        public int monitorDim;
        public int monitorX;
        public int monitorY;
        public int monitorZ;
        public String ownerName;
        public long registeredAt;
        public volatile boolean healthy;

        private volatile WeakReference<IGrid> cachedGridRef;

        RegisteredNetwork(int monitorDim, int monitorX, int monitorY, int monitorZ, String ownerName) {
            this.monitorDim = monitorDim;
            this.monitorX = monitorX;
            this.monitorY = monitorY;
            this.monitorZ = monitorZ;
            this.ownerName = ownerName == null ? "" : ownerName;
            this.registeredAt = System.currentTimeMillis();
            this.healthy = false;
        }

        String monitorKey() {
            return monitorDim + ":" + monitorX + ":" + monitorY + ":" + monitorZ;
        }
    }

    // ---- public query API ----

    public static List<NetworkGroup> getNetworks(String ownerUuid) {
        if (ownerUuid == null || ownerUuid.isEmpty()) {
            return Collections.emptyList();
        }
        CopyOnWriteArrayList<RegisteredNetwork> entries = playerNetworks.get(ownerUuid);
        if (entries == null || entries.isEmpty()) {
            publishNetworkIdKeys(ownerUuid, Collections.<String>emptyList());
            return Collections.emptyList();
        }
        List<NetworkGroup> groups = new ArrayList<NetworkGroup>();
        for (RegisteredNetwork entry : entries) {
            NetworkGroup group = buildGroupFromEntry(entry);
            if (group != null && hasAnyLink(group)) {
                groups.add(group);
            }
        }
        sortGroups(groups, ownerUuid);
        publishNetworkIdKeysFromGroups(ownerUuid, groups);
        return groups;
    }

    /**
     * Resolve stable {@code dim:x:y:z} for a runtime networkId without touching {@link World}.
     * Safe to call from WebAE HTTP threads.
     */
    public static String keyForNetworkId(String ownerUuid, int networkId) {
        if (ownerUuid == null || ownerUuid.isEmpty() || networkId < 0) {
            return null;
        }
        CopyOnWriteArrayList<String> keys = networkIdKeys.get(ownerUuid);
        if (keys != null && networkId < keys.size()) {
            return keys.get(networkId);
        }
        List<RegisteredNetwork> sorted = listSortedRegistered(ownerUuid);
        if (networkId >= sorted.size()) {
            return null;
        }
        return sorted.get(networkId).monitorKey();
    }

    /**
     * Map stable key back to runtime networkId without touching {@link World}.
     * Safe to call from WebAE HTTP threads.
     */
    public static Integer networkIdForKey(String ownerUuid, String networkKey) {
        if (ownerUuid == null || networkKey == null || networkKey.isEmpty()) {
            return null;
        }
        CopyOnWriteArrayList<String> keys = networkIdKeys.get(ownerUuid);
        if (keys != null) {
            for (int i = 0; i < keys.size(); i++) {
                if (networkKey.equals(keys.get(i))) {
                    return Integer.valueOf(i);
                }
            }
        }
        List<RegisteredNetwork> sorted = listSortedRegistered(ownerUuid);
        for (int i = 0; i < sorted.size(); i++) {
            if (networkKey.equals(sorted.get(i).monitorKey())) {
                return Integer.valueOf(i);
            }
        }
        return null;
    }

    /**
     * Coord-only NetworkGroups (no TileEntity links) for HTTP/index fallbacks. Does not touch World.
     */
    public static List<NetworkGroup> getIndexGroupsNoWorld(String ownerUuid) {
        List<RegisteredNetwork> sorted = listSortedRegistered(ownerUuid);
        List<NetworkGroup> groups = new ArrayList<NetworkGroup>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            RegisteredNetwork entry = sorted.get(i);
            NetworkGroup group = new NetworkGroup();
            group.monitorDim = entry.monitorDim;
            group.monitorX = entry.monitorX;
            group.monitorY = entry.monitorY;
            group.monitorZ = entry.monitorZ;
            groups.add(group);
        }
        if (!sorted.isEmpty() && networkIdKeys.get(ownerUuid) == null) {
            publishNetworkIdKeysFromGroups(ownerUuid, groups);
        }
        return groups;
    }

    /** Publish networkId→key order from an already-built group list (e.g. connector cache refresh). */
    public static void publishNetworkIdKeysFromGroups(String ownerUuid, List<NetworkGroup> groups) {
        if (ownerUuid == null || ownerUuid.isEmpty()) {
            return;
        }
        List<String> keys = new ArrayList<String>();
        if (groups != null) {
            for (int i = 0; i < groups.size(); i++) {
                NetworkGroup g = groups.get(i);
                if (g != null) {
                    keys.add(g.monitorDim + ":" + g.monitorX + ":" + g.monitorY + ":" + g.monitorZ);
                }
            }
        }
        publishNetworkIdKeys(ownerUuid, keys);
    }

    private static void publishNetworkIdKeys(String ownerUuid, List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            networkIdKeys.remove(ownerUuid);
        } else {
            networkIdKeys.put(ownerUuid, new CopyOnWriteArrayList<String>(keys));
        }
    }

    public static boolean isHealthy(String ownerUuid, int networkId) {
        RegisteredNetwork entry = getEntryByNetworkId(ownerUuid, networkId);
        return entry != null && entry.healthy;
    }

    public static IGrid getCachedGrid(String ownerUuid, int networkId) {
        RegisteredNetwork entry = getEntryByNetworkId(ownerUuid, networkId);
        if (entry == null) {
            return null;
        }
        WeakReference<IGrid> ref = entry.cachedGridRef;
        if (ref == null) {
            return null;
        }
        IGrid grid = ref.get();
        if (grid == null) {
            entry.cachedGridRef = null;
        }
        return grid;
    }

    public static void updateCachedGrid(String ownerUuid, int networkId, IGrid grid) {
        RegisteredNetwork entry = getEntryByNetworkId(ownerUuid, networkId);
        if (entry == null) {
            return;
        }
        if (grid != null) {
            entry.cachedGridRef = new WeakReference<IGrid>(grid);
            entry.healthy = true;
        } else {
            entry.cachedGridRef = null;
            entry.healthy = false;
        }
    }

    // ---- event hooks ----

    public static void onLinkPlaced(TileEntityAdvanceNetworkLink link, int dim) {
        if (link == null) {
            return;
        }
        List<TileEntityAdvanceDataMonitor> monitors = TileEntityIndex.getByType(
            dim,
            TileEntityAdvanceDataMonitor.class);
        for (TileEntityAdvanceDataMonitor monitor : monitors) {
            if (monitorBindsTo(monitor, link)) {
                refreshBindings(monitor, dim);
            }
        }
    }

    public static void onLinkRemoved(TileEntityAdvanceNetworkLink link, int dim) {
        if (link == null) {
            return;
        }
        List<TileEntityAdvanceDataMonitor> monitors = TileEntityIndex.getByType(
            dim,
            TileEntityAdvanceDataMonitor.class);
        for (TileEntityAdvanceDataMonitor monitor : monitors) {
            if (monitorBindsTo(monitor, link)) {
                refreshBindings(monitor, dim);
            }
        }
    }

    public static void refreshBindings(TileEntityAdvanceDataMonitor monitor, int dim) {
        if (monitor == null) {
            return;
        }
        String ownerName = monitor.getOwnerName();
        if (ownerName == null || ownerName.isEmpty()) {
            return;
        }
        String ownerUuid = resolveOwnerUuid(ownerName);
        if (ownerUuid == null) {
            return;
        }

        int dimId = dim;
        String key = dimId + ":" + monitor.xCoord + ":" + monitor.yCoord + ":" + monitor.zCoord;

        CopyOnWriteArrayList<RegisteredNetwork> list = playerNetworks.get(ownerUuid);
        if (list == null) {
            list = new CopyOnWriteArrayList<RegisteredNetwork>();
            CopyOnWriteArrayList<RegisteredNetwork> prev = playerNetworks.putIfAbsent(ownerUuid, list);
            if (prev != null) {
                list = prev;
            }
        }

        RegisteredNetwork existing = findByMonitorKey(list, key);
        NetworkGroup group = buildGroupFromMonitor(monitor, dimId);
        if (group == null || !hasAnyLink(group)) {
            if (existing != null) {
                list.remove(existing);
            }
            WebAeOwnerContext.invalidateConnectorCache(ownerUuid);
            return;
        }

        RegisteredNetwork entry = existing;
        if (entry == null) {
            entry = new RegisteredNetwork(dimId, monitor.xCoord, monitor.yCoord, monitor.zCoord, ownerName);
            list.add(entry);
        } else {
            entry.ownerName = ownerName;
        }
        entry.healthy = checkGroupHealthy(group);
        IGrid grid = resolveGridFromGroup(group);
        if (grid != null) {
            entry.cachedGridRef = new WeakReference<IGrid>(grid);
        } else {
            entry.cachedGridRef = null;
        }
        WebAeOwnerContext.invalidateConnectorCache(ownerUuid);
    }

    public static void onPlayerLogin(String ownerUuid) {
        if (ownerUuid == null || ownerUuid.isEmpty()) {
            return;
        }
        List<NetworkGroup> scanned = WebAeOwnerContext.bootstrapScanMonitors(ownerUuid);
        seedFromGroups(ownerUuid, scanned);
        healthCheck(ownerUuid);
    }

    public static void onChunkLoad(Chunk chunk, int dim) {
        if (chunk == null) {
            return;
        }
        for (Object obj : chunk.chunkTileEntityMap.values()) {
            if (obj instanceof TileEntityAdvanceNetworkLink) {
                onLinkPlaced((TileEntityAdvanceNetworkLink) obj, dim);
            } else if (obj instanceof TileEntityAdvanceDataMonitor) {
                refreshBindings((TileEntityAdvanceDataMonitor) obj, dim);
            }
        }
    }

    public static void onChunkUnload(Chunk chunk, int dim) {
        if (chunk == null) {
            return;
        }
        for (Object obj : chunk.chunkTileEntityMap.values()) {
            if (obj instanceof TileEntityAdvanceNetworkLink) {
                markMonitorEntriesUnhealthyNear((TileEntityAdvanceNetworkLink) obj, dim);
            } else if (obj instanceof TileEntityAdvanceDataMonitor) {
                markEntryUnhealthy((TileEntityAdvanceDataMonitor) obj, dim);
            }
        }
    }

    public static void seedFromGroups(String ownerUuid, List<NetworkGroup> groups) {
        if (ownerUuid == null || ownerUuid.isEmpty() || groups == null) {
            return;
        }
        CopyOnWriteArrayList<RegisteredNetwork> list = new CopyOnWriteArrayList<RegisteredNetwork>();
        for (NetworkGroup group : groups) {
            RegisteredNetwork entry = new RegisteredNetwork(
                group.monitorDim,
                group.monitorX,
                group.monitorY,
                group.monitorZ,
                WebAeOwnerContext.resolveOwnerName(ownerUuid));
            entry.healthy = checkGroupHealthy(group);
            IGrid grid = resolveGridFromGroup(group);
            if (grid != null) {
                entry.cachedGridRef = new WeakReference<IGrid>(grid);
            }
            list.add(entry);
        }
        if (list.isEmpty()) {
            playerNetworks.remove(ownerUuid);
            networkIdKeys.remove(ownerUuid);
        } else {
            playerNetworks.put(ownerUuid, list);
            publishNetworkIdKeysFromGroups(ownerUuid, groups);
        }
    }

    /** Periodic health check; call from server tick (main thread). */
    public static void tickHealthCheck(long nowMs) {
        if (nowMs - lastGlobalHealthCheckMs < HEALTH_INTERVAL_MS) {
            return;
        }
        lastGlobalHealthCheckMs = nowMs;
        for (String ownerUuid : playerNetworks.keySet()) {
            healthCheck(ownerUuid);
        }
    }

    public static void refreshHealth(String ownerUuid) {
        healthCheck(ownerUuid);
    }

    /**
     * Return a snapshot of all known owner UUIDs that have registered networks.
     */
    public static List<String> getAllOwnerUuids() {
        return new ArrayList<String>(playerNetworks.keySet());
    }

    /**
     * Return the raw registered network list for one owner (may include unhealthy
     * entries that {@link #getNetworks(String)} filters out).
     */
    public static List<RegisteredNetwork> getRawNetworks(String ownerUuid) {
        if (ownerUuid == null || ownerUuid.isEmpty()) {
            return Collections.emptyList();
        }
        CopyOnWriteArrayList<RegisteredNetwork> list = playerNetworks.get(ownerUuid);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<RegisteredNetwork>(list);
    }

    // ---- internals ----

    private static void healthCheck(String ownerUuid) {
        CopyOnWriteArrayList<RegisteredNetwork> list = playerNetworks.get(ownerUuid);
        if (list == null) {
            return;
        }
        for (RegisteredNetwork entry : list) {
            boolean wasHealthy = entry.healthy;
            NetworkGroup group = buildGroupFromEntry(entry);
            boolean nowHealthy = group != null && checkGroupHealthy(group);
            entry.healthy = nowHealthy;
            if (nowHealthy) {
                IGrid grid = resolveGridFromGroup(group);
                if (grid != null) {
                    entry.cachedGridRef = new WeakReference<IGrid>(grid);
                }
            } else {
                entry.cachedGridRef = null;
            }
            if (wasHealthy != nowHealthy) {
                if (nowHealthy) {
                    AdvanceDataMonitor.LOG.info(
                        "[WebAE] Network reconnected: player={} monitor={}:{}:{}@{}",
                        ownerUuid,
                        Integer.valueOf(entry.monitorX),
                        Integer.valueOf(entry.monitorY),
                        Integer.valueOf(entry.monitorZ),
                        Integer.valueOf(entry.monitorDim));
                } else {
                    AdvanceDataMonitor.LOG.warn(
                        "[WebAE] Network lost (chunk unloaded?): player={} monitor={}:{}:{}@{}",
                        ownerUuid,
                        Integer.valueOf(entry.monitorX),
                        Integer.valueOf(entry.monitorY),
                        Integer.valueOf(entry.monitorZ),
                        Integer.valueOf(entry.monitorDim));
                }
                WebAeOwnerContext.invalidateConnectors(ownerUuid);
            }
        }
    }

    private static RegisteredNetwork getEntryByNetworkId(String ownerUuid, int networkId) {
        String key = keyForNetworkId(ownerUuid, networkId);
        if (key == null) {
            return null;
        }
        CopyOnWriteArrayList<RegisteredNetwork> list = playerNetworks.get(ownerUuid);
        if (list == null) {
            return null;
        }
        return findByMonitorKey(list, key);
    }

    /**
     * Registered networks sorted like {@link #sortGroups} — coordinate order only, no World access.
     */
    private static List<RegisteredNetwork> listSortedRegistered(String ownerUuid) {
        CopyOnWriteArrayList<RegisteredNetwork> list = playerNetworks.get(ownerUuid);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<RegisteredNetwork> sorted = new ArrayList<RegisteredNetwork>(list);
        sortRegistered(sorted, ownerUuid);
        return sorted;
    }

    private static void sortRegistered(List<RegisteredNetwork> entries, final String ownerUuid) {
        final EntityPlayerMP online = HandlerWebPlayerTracker.findOnlinePlayer(ownerUuid);
        final double sortX;
        final double sortY;
        final double sortZ;
        if (online != null) {
            sortX = online.posX;
            sortY = online.posY;
            sortZ = online.posZ;
        } else {
            sortX = 0;
            sortY = 0;
            sortZ = 0;
        }
        Collections.sort(entries, new Comparator<RegisteredNetwork>() {

            @Override
            public int compare(RegisteredNetwork a, RegisteredNetwork b) {
                if (online != null) {
                    double dA = distSq(sortX, sortY, sortZ, a.monitorX, a.monitorY, a.monitorZ);
                    double dB = distSq(sortX, sortY, sortZ, b.monitorX, b.monitorY, b.monitorZ);
                    return Double.compare(dA, dB);
                }
                int dimCmp = Integer.compare(a.monitorDim, b.monitorDim);
                if (dimCmp != 0) {
                    return dimCmp;
                }
                if (a.monitorX != b.monitorX) {
                    return Integer.compare(a.monitorX, b.monitorX);
                }
                if (a.monitorY != b.monitorY) {
                    return Integer.compare(a.monitorY, b.monitorY);
                }
                return Integer.compare(a.monitorZ, b.monitorZ);
            }
        });
    }

    private static RegisteredNetwork findByMonitorKey(CopyOnWriteArrayList<RegisteredNetwork> list, String key) {
        for (RegisteredNetwork entry : list) {
            if (key.equals(entry.monitorKey())) {
                return entry;
            }
        }
        return null;
    }

    private static void markEntryUnhealthy(TileEntityAdvanceDataMonitor monitor, int dim) {
        String ownerUuid = resolveOwnerUuid(monitor.getOwnerName());
        if (ownerUuid == null) {
            return;
        }
        String key = dim + ":" + monitor.xCoord + ":" + monitor.yCoord + ":" + monitor.zCoord;
        CopyOnWriteArrayList<RegisteredNetwork> list = playerNetworks.get(ownerUuid);
        if (list == null) {
            return;
        }
        RegisteredNetwork entry = findByMonitorKey(list, key);
        if (entry != null && entry.healthy) {
            entry.healthy = false;
            entry.cachedGridRef = null;
            AdvanceDataMonitor.LOG.warn(
                "[WebAE] Network lost (chunk unloaded?): player={} monitor={}:{}:{}@{}",
                ownerUuid,
                Integer.valueOf(entry.monitorX),
                Integer.valueOf(entry.monitorY),
                Integer.valueOf(entry.monitorZ),
                Integer.valueOf(entry.monitorDim));
            WebAeOwnerContext.invalidateConnectorCache(ownerUuid);
        }
    }

    private static void markMonitorEntriesUnhealthyNear(TileEntityAdvanceNetworkLink link, int dim) {
        List<TileEntityAdvanceDataMonitor> monitors = TileEntityIndex.getByType(
            dim,
            TileEntityAdvanceDataMonitor.class);
        for (TileEntityAdvanceDataMonitor monitor : monitors) {
            if (monitorBindsTo(monitor, link)) {
                markEntryUnhealthy(monitor, dim);
            }
        }
    }

    private static boolean monitorBindsTo(TileEntityAdvanceDataMonitor monitor, TileEntityAdvanceNetworkLink link) {
        int count = monitor.getDataBoundCount();
        for (int i = 0; i < count; i++) {
            int[] pos = monitor.parseBoundXYZ(i);
            if (pos != null && pos[0] == link.xCoord && pos[1] == link.yCoord && pos[2] == link.zCoord) {
                return true;
            }
        }
        return false;
    }

    private static NetworkGroup buildGroupFromEntry(RegisteredNetwork entry) {
        World world = resolveWorld(entry.monitorDim);
        if (world == null || !world.blockExists(entry.monitorX, entry.monitorY, entry.monitorZ)) {
            return null;
        }
        TileEntity te = world.getTileEntity(entry.monitorX, entry.monitorY, entry.monitorZ);
        if (!(te instanceof TileEntityAdvanceDataMonitor)) {
            return null;
        }
        return buildGroupFromMonitor((TileEntityAdvanceDataMonitor) te, entry.monitorDim);
    }

    private static NetworkGroup buildGroupFromMonitor(TileEntityAdvanceDataMonitor monitor, int dim) {
        World world = resolveWorld(dim);
        if (world == null) {
            return null;
        }
        NetworkGroup group = new NetworkGroup();
        group.monitorX = monitor.xCoord;
        group.monitorY = monitor.yCoord;
        group.monitorZ = monitor.zCoord;
        group.monitorDim = dim;

        int count = monitor.getDataBoundCount();
        for (int i = 0; i < count; i++) {
            int[] pos = monitor.parseBoundXYZ(i);
            if (pos == null) {
                continue;
            }
            if (!world.blockExists(pos[0], pos[1], pos[2])) {
                continue;
            }
            TileEntity boundTe = world.getTileEntity(pos[0], pos[1], pos[2]);
            if (boundTe instanceof TileEntityAdvanceNetworkLink) {
                if (hasAeAccess(boundTe)) {
                    TileEntityAdvanceNetworkLink link = (TileEntityAdvanceNetworkLink) boundTe;
                    if (group.networkLink == null) {
                        group.networkLink = link;
                    }
                    if (group.storageLink == null) {
                        group.storageLink = link;
                    }
                    if (group.craftingLink == null) {
                        group.craftingLink = link;
                    }
                }
            }
        }
        return group;
    }

    private static boolean hasAnyLink(NetworkGroup group) {
        return group.storageLink != null || group.craftingLink != null || group.networkLink != null;
    }

    private static boolean checkGroupHealthy(NetworkGroup group) {
        if (group == null) {
            return false;
        }
        TileEntity link = group.craftingLink != null ? group.craftingLink
            : group.storageLink != null ? group.storageLink : group.networkLink;
        return hasAeAccess(link);
    }

    private static IGrid resolveGridFromGroup(NetworkGroup group) {
        if (group == null) {
            return null;
        }
        TileEntity link = group.craftingLink != null ? group.craftingLink
            : group.storageLink != null ? group.storageLink : group.networkLink;
        if (!(link instanceof IGridHost)) {
            return null;
        }
        try {
            IGridNode node = ((IGridHost) link).getGridNode(ForgeDirection.UNKNOWN);
            return node != null ? node.getGrid() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean hasAeAccess(TileEntity te) {
        if (!(te instanceof IGridHost)) {
            return false;
        }
        try {
            IGridNode node = ((IGridHost) te).getGridNode(ForgeDirection.UNKNOWN);
            return node != null && node.getGrid() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static void sortGroups(List<NetworkGroup> groups, final String ownerUuid) {
        final EntityPlayerMP online = HandlerWebPlayerTracker.findOnlinePlayer(ownerUuid);
        final double sortX;
        final double sortY;
        final double sortZ;
        if (online != null) {
            sortX = online.posX;
            sortY = online.posY;
            sortZ = online.posZ;
        } else {
            sortX = 0;
            sortY = 0;
            sortZ = 0;
        }
        Collections.sort(groups, new Comparator<NetworkGroup>() {

            @Override
            public int compare(NetworkGroup a, NetworkGroup b) {
                if (online != null) {
                    double dA = distSq(sortX, sortY, sortZ, a.monitorX, a.monitorY, a.monitorZ);
                    double dB = distSq(sortX, sortY, sortZ, b.monitorX, b.monitorY, b.monitorZ);
                    return Double.compare(dA, dB);
                }
                int dimCmp = Integer.compare(a.monitorDim, b.monitorDim);
                if (dimCmp != 0) {
                    return dimCmp;
                }
                if (a.monitorX != b.monitorX) {
                    return Integer.compare(a.monitorX, b.monitorX);
                }
                if (a.monitorY != b.monitorY) {
                    return Integer.compare(a.monitorY, b.monitorY);
                }
                return Integer.compare(a.monitorZ, b.monitorZ);
            }
        });
    }

    private static double distSq(double px, double py, double pz, int x, int y, int z) {
        double dx = px - (x + 0.5);
        double dy = py - (y + 0.5);
        double dz = pz - (z + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    private static World resolveWorld(int dim) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null && dim >= 0 && dim < server.worldServers.length) {
            return server.worldServers[dim];
        }
        return null;
    }

    static String resolveOwnerUuid(String ownerName) {
        if (ownerName == null || ownerName.isEmpty()) {
            return null;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null && server.getConfigurationManager() != null) {
            for (Object obj : server.getConfigurationManager().playerEntityList) {
                if (obj instanceof EntityPlayerMP) {
                    EntityPlayerMP player = (EntityPlayerMP) obj;
                    if (ownerName.equals(player.getCommandSenderName())) {
                        return player.getUniqueID()
                            .toString();
                    }
                }
            }
        }
        for (PlayerInfo info : PlayerInfoStore.instance()
            .getAllPlayers()) {
            if (ownerName.equals(info.name) && info.uuid != null) {
                return info.uuid;
            }
        }
        return null;
    }
}
