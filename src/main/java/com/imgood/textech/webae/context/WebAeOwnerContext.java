package com.imgood.textech.webae.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;

import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.tileentity.TileEntityAdvanceNetworkLink;
import com.imgood.textech.webae.player.PlayerInfoStore;
import com.imgood.textech.webae.snapshot.AeSnapshotCollector.NetworkInfo;
import com.mojang.authlib.GameProfile;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;

/**
 * Owner-scoped AE network discovery for WebAE. Scans all loaded dimensions for
 * {@link TileEntityAdvanceDataMonitor} blocks owned by the resolved player name.
 */
public final class WebAeOwnerContext {

    private static final long CONNECTOR_CACHE_TTL_MS = 30_000L;

    private static final ConcurrentHashMap<String, CachedConnectors> connectorCache = new ConcurrentHashMap<String, CachedConnectors>();
    private static final ConcurrentHashMap<String, EntityPlayerMP> fakePlayerCache = new ConcurrentHashMap<String, EntityPlayerMP>();

    private WebAeOwnerContext() {}

    public static class NetworkGroup {

        public int monitorX;
        public int monitorY;
        public int monitorZ;
        public int monitorDim;
        public TileEntityAdvanceNetworkLink storageLink;
        public TileEntityAdvanceNetworkLink craftingLink;
        public TileEntityAdvanceNetworkLink networkLink;
    }

    public static void cacheOwnerName(String ownerUuid, String ownerName) {
        if (ownerUuid == null || ownerName == null || ownerName.isEmpty()) {
            return;
        }
        try {
            PlayerInfoStore.instance()
                .touchLogin(UUID.fromString(ownerUuid), ownerName, System.currentTimeMillis());
        } catch (IllegalArgumentException ignored) {}
    }

    public static String resolveOwnerName(String ownerUuid) {
        if (ownerUuid == null || ownerUuid.isEmpty()) {
            return "";
        }
        try {
            UUID uuid = UUID.fromString(ownerUuid);
            com.imgood.textech.webae.player.PlayerInfo info = PlayerInfoStore.instance()
                .getPlayer(uuid);
            if (info != null && info.name != null && !info.name.isEmpty()) {
                return info.name;
            }
        } catch (IllegalArgumentException ignored) {}
        EntityPlayerMP online = findOnlinePlayer(ownerUuid);
        if (online != null) {
            return online.getCommandSenderName();
        }
        return "";
    }

    public static int countMonitors(String ownerUuid) {
        String ownerName = resolveOwnerName(ownerUuid);
        if (ownerName.isEmpty()) {
            return 0;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return 0;
        }
        int count = 0;
        for (int d = 0; d < server.worldServers.length; d++) {
            WorldServer world = server.worldServers[d];
            if (world == null) {
                continue;
            }
            for (Object obj : world.loadedTileEntityList) {
                if (!(obj instanceof TileEntityAdvanceDataMonitor)) {
                    continue;
                }
                TileEntityAdvanceDataMonitor monitor = (TileEntityAdvanceDataMonitor) obj;
                if (ownerName.equals(monitor.getOwnerName())) {
                    count++;
                }
            }
        }
        return count;
    }

    public static List<NetworkGroup> findNetworkGroups(String ownerUuid) {
        return findNetworkGroups(ownerUuid, false);
    }

    public static List<NetworkGroup> findNetworkGroups(String ownerUuid, boolean forceRefresh) {
        if (ownerUuid == null || ownerUuid.isEmpty()) {
            return Collections.emptyList();
        }
        CachedConnectors cached = connectorCache.get(ownerUuid);
        long now = System.currentTimeMillis();
        if (!forceRefresh && cached != null && now - cached.cachedAt < CONNECTOR_CACHE_TTL_MS) {
            return cached.groups;
        }
        // HTTP / off-thread: never rebuild via World. Prefer stale cache, else coord-only registry index.
        if (!HandlerTick.isServerThread()) {
            if (cached != null) {
                return cached.groups;
            }
            return NetworkRegistry.getIndexGroupsNoWorld(ownerUuid);
        }
        List<NetworkGroup> groups = NetworkRegistry.getNetworks(ownerUuid);
        if (groups.isEmpty()) {
            groups = bootstrapScanMonitors(ownerUuid);
        }
        connectorCache.put(ownerUuid, new CachedConnectors(groups, now));
        NetworkRegistry.publishNetworkIdKeysFromGroups(ownerUuid, groups);
        return groups;
    }

    /**
     * One-time bootstrap scan used when the event-driven registry has no entries yet
     * (server startup or first access before chunk events fire).
     */
    public static List<NetworkGroup> bootstrapScanMonitors(String ownerUuid) {
        List<NetworkGroup> groups = scanMonitors(ownerUuid);
        if (!groups.isEmpty()) {
            NetworkRegistry.seedFromGroups(ownerUuid, groups);
        }
        return groups;
    }

    public static List<NetworkInfo> findNetworksForOwner(String ownerUuid) {
        return findNetworksForOwner(ownerUuid, false);
    }

    public static List<NetworkInfo> findNetworksForOwner(String ownerUuid, boolean forceRefresh) {
        List<NetworkGroup> groups = findNetworkGroups(ownerUuid, forceRefresh);
        List<NetworkInfo> result = new ArrayList<NetworkInfo>();
        for (int i = 0; i < groups.size(); i++) {
            NetworkGroup group = groups.get(i);
            NetworkInfo info = new NetworkInfo();
            info.networkId = i;
            info.monitorDim = group.monitorDim;
            info.monitorX = group.monitorX;
            info.monitorY = group.monitorY;
            info.monitorZ = group.monitorZ;
            info.networkKey = group.monitorDim + ":" + group.monitorX + ":" + group.monitorY + ":" + group.monitorZ;
            info.hasStorage = group.storageLink != null;
            info.hasCrafting = group.craftingLink != null;
            info.hasNetworkLink = group.networkLink != null;
            info.healthy = NetworkRegistry.isHealthy(ownerUuid, i);
            result.add(info);
        }
        return result;
    }

    public static NetworkGroup getNetworkGroup(String ownerUuid, int networkId) {
        List<NetworkGroup> groups = findNetworkGroups(ownerUuid);
        if (networkId < 0 || networkId >= groups.size()) {
            return null;
        }
        return groups.get(networkId);
    }

    public static World getWorldForNetwork(String ownerUuid, int networkId) {
        NetworkGroup group = getNetworkGroup(ownerUuid, networkId);
        if (group == null) {
            return null;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return null;
        }
        if (group.monitorDim >= 0 && group.monitorDim < server.worldServers.length) {
            return server.worldServers[group.monitorDim];
        }
        return null;
    }

    public static TileEntityAdvanceDataMonitor getMonitor(String ownerUuid, int networkId) {
        NetworkGroup group = getNetworkGroup(ownerUuid, networkId);
        if (group == null) {
            return null;
        }
        World world = getWorldForNetwork(ownerUuid, networkId);
        if (world == null) {
            return null;
        }
        if (!world.blockExists(group.monitorX, group.monitorY, group.monitorZ)) {
            return null;
        }
        TileEntity te = world.getTileEntity(group.monitorX, group.monitorY, group.monitorZ);
        if (te instanceof TileEntityAdvanceDataMonitor) {
            return (TileEntityAdvanceDataMonitor) te;
        }
        return null;
    }

    public static IGrid getGrid(String ownerUuid, int networkId) {
        IGrid cached = NetworkRegistry.getCachedGrid(ownerUuid, networkId);
        if (cached != null) {
            return cached;
        }
        NetworkGroup group = getNetworkGroup(ownerUuid, networkId);
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
            IGrid grid = node != null ? node.getGrid() : null;
            NetworkRegistry.updateCachedGrid(ownerUuid, networkId, grid);
            return grid;
        } catch (Exception e) {
            NetworkRegistry.updateCachedGrid(ownerUuid, networkId, null);
            return null;
        }
    }

    public static EntityPlayerMP getOwnerPlayerOrFake(String ownerUuid) {
        EntityPlayerMP online = findOnlinePlayer(ownerUuid);
        if (online != null) {
            return online;
        }
        EntityPlayerMP cached = fakePlayerCache.get(ownerUuid);
        if (cached != null) {
            return cached;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return null;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(ownerUuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
        String name = resolveOwnerName(ownerUuid);
        if (name.isEmpty()) {
            name = "WebOwner";
        }
        WorldServer world = server.worldServers.length > 0 ? server.worldServers[0] : null;
        if (world == null) {
            return null;
        }
        GameProfile profile = new GameProfile(uuid, name);
        FakePlayer fake = FakePlayerFactory.get(world, profile);
        fakePlayerCache.put(ownerUuid, fake);
        return fake;
    }

    /**
     * Move an offline {@link FakePlayer} to the monitor for chunk-loading fallback scans.
     * Real online players must never be repositioned.
     */
    public static void positionPlayerAtMonitor(EntityPlayerMP player, NetworkGroup group) {
        if (player == null || group == null) {
            return;
        }
        if (!(player instanceof FakePlayer)) {
            return;
        }
        WorldServer targetWorld = resolveWorldServer(group.monitorDim);
        if (targetWorld == null) {
            return;
        }
        if (player.dimension != group.monitorDim || player.worldObj != targetWorld) {
            player.dimension = group.monitorDim;
            player.worldObj = targetWorld;
        }
        player.setPositionAndUpdate(group.monitorX + 0.5, group.monitorY + 0.5, group.monitorZ + 0.5);
    }

    private static WorldServer resolveWorldServer(int dim) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null && dim >= 0 && dim < server.worldServers.length) {
            return server.worldServers[dim];
        }
        World world = DimensionManager.getWorld(dim);
        return world instanceof WorldServer ? (WorldServer) world : null;
    }

    public static void invalidateConnectors(String ownerUuid) {
        if (ownerUuid == null) {
            return;
        }
        connectorCache.remove(ownerUuid);
        if (HandlerTick.isServerThread()) {
            NetworkRegistry.refreshHealth(ownerUuid);
        } else {
            final String uuid = ownerUuid;
            HandlerTick.enqueueServerTask(new Runnable() {

                @Override
                public void run() {
                    NetworkRegistry.refreshHealth(uuid);
                }
            });
        }
    }

    static void invalidateConnectorCache(String ownerUuid) {
        if (ownerUuid != null) {
            connectorCache.remove(ownerUuid);
        }
    }

    public static void invalidateAllFakePlayers() {
        fakePlayerCache.clear();
    }

    private static List<NetworkGroup> scanMonitors(String ownerUuid) {
        String ownerName = resolveOwnerName(ownerUuid);
        if (ownerName.isEmpty()) {
            return Collections.emptyList();
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return Collections.emptyList();
        }

        EntityPlayerMP online = findOnlinePlayer(ownerUuid);
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

        List<NetworkGroup> groups = new ArrayList<NetworkGroup>();
        for (int d = 0; d < server.worldServers.length; d++) {
            WorldServer world = server.worldServers[d];
            if (world == null) {
                continue;
            }
            int dim = world.provider.dimensionId;
            // Use the TE index to scan only this dimension's monitors, avoiding a
            // full per-dimension loadedTileEntityList traversal for other TE types.
            List<TileEntityAdvanceDataMonitor> monitors = TileEntityIndex.getByType(dim, TileEntityAdvanceDataMonitor.class);
            for (TileEntityAdvanceDataMonitor monitor : monitors) {
                if (!ownerName.equals(monitor.getOwnerName())) {
                    continue;
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
                    if (boundTe == null) {
                        continue;
                    }
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
                if (group.storageLink != null || group.craftingLink != null || group.networkLink != null) {
                    groups.add(group);
                }
            }
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
        return groups;
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

    private static double distSq(double px, double py, double pz, int x, int y, int z) {
        double dx = px - (x + 0.5);
        double dy = py - (y + 0.5);
        double dz = pz - (z + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    /** O(1) player lookup via the shared online-player index. */
    public static EntityPlayerMP findOnlinePlayer(String ownerUuid) {
        return com.imgood.textech.handler.HandlerWebPlayerTracker.findOnlinePlayer(ownerUuid);
    }

    private static final class CachedConnectors {

        final List<NetworkGroup> groups;
        final long cachedAt;

        CachedConnectors(List<NetworkGroup> groups, long cachedAt) {
            this.groups = groups;
            this.cachedAt = cachedAt;
        }
    }
}
