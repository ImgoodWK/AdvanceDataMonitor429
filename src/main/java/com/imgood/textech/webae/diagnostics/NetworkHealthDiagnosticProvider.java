package com.imgood.textech.webae.diagnostics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.tileentity.TileEntityAdvanceNetworkLink;
import com.imgood.textech.webae.context.NetworkRegistry;
import com.imgood.textech.webae.context.NetworkRegistry.RegisteredNetwork;
import com.imgood.textech.webae.topology.FakeChannelAllocator.ChannelProbeResult;
import com.imgood.textech.webae.topology.TopologySnapshot;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.storage.IStorageGrid;

/**
 * Server-thread sampler and HTTP-safe cache for network health diagnostics.
 *
 * <p>No method used by an HTTP handler resolves a World, TileEntity, or AE grid.
 * The only methods that do so are {@link #tick(long)} and the package-local
 * sampling helpers, which are called from the server tick.</p>
 */
public final class NetworkHealthDiagnosticProvider {

    public static final long SAMPLE_INTERVAL_MS = 5_000L;
    public static final long STALE_AFTER_MS = 15_000L;

    private static final NetworkHealthDiagnosticProvider INSTANCE = new NetworkHealthDiagnosticProvider();

    /** ownerUuid + stable {@code dim:x:y:z}; runtime networkId is rewritten on every read. */
    private final ConcurrentHashMap<String, NetworkHealthDiagnosticDto> cache =
        new ConcurrentHashMap<String, NetworkHealthDiagnosticDto>();
    private volatile long lastSampleAt;

    private NetworkHealthDiagnosticProvider() {}

    public static NetworkHealthDiagnosticProvider instance() {
        return INSTANCE;
    }

    /**
     * Refresh all registered networks at most once per five seconds.  Must run on
     * the Minecraft server thread.
     */
    public void tick(long nowMs) {
        if (nowMs <= 0L) {
            nowMs = System.currentTimeMillis();
        }
        if (nowMs - lastSampleAt < SAMPLE_INTERVAL_MS) {
            return;
        }
        lastSampleAt = nowMs;

        Set<String> activeKeys = new HashSet<String>();
        List<String> owners = NetworkRegistry.getAllOwnerUuids();
        for (String ownerUuid : owners) {
            if (ownerUuid == null || ownerUuid.isEmpty()) {
                continue;
            }
            List<RegisteredNetwork> networks = sortedRawNetworks(ownerUuid);
            for (int fallbackId = 0; fallbackId < networks.size(); fallbackId++) {
                RegisteredNetwork entry = networks.get(fallbackId);
                String networkKey = keyFor(entry);
                if (networkKey == null) {
                    continue;
                }
                Integer currentId = NetworkRegistry.networkIdForKey(ownerUuid, networkKey);
                int networkId = currentId == null ? fallbackId : currentId.intValue();
                String cacheKey = cacheKey(ownerUuid, networkKey);
                activeKeys.add(cacheKey);
                try {
                    cache.put(cacheKey, sample(ownerUuid, networkId, networkKey, entry, nowMs));
                } catch (Throwable error) {
                    AdvanceDataMonitor.LOG.warn(
                        "[WebAE] Network health sample failed: owner={} networkKey={}",
                        ownerUuid,
                        networkKey,
                        error);
                }
            }
        }
        pruneInactive(activeKeys);
    }

    /** Alias used by integrations that call their periodic jobs refreshAll. */
    public void refreshAll(long nowMs) {
        tick(nowMs);
    }

    /** Force one network sample; only call from the server thread. */
    public void refresh(String ownerUuid, int networkId, long nowMs) {
        if (ownerUuid == null || ownerUuid.isEmpty() || networkId < 0) {
            return;
        }
        List<RegisteredNetwork> networks = sortedRawNetworks(ownerUuid);
        String networkKey = NetworkRegistry.keyForNetworkId(ownerUuid, networkId);
        RegisteredNetwork entry = findByKey(networks, networkKey);
        if (entry == null && networkId < networks.size()) {
            entry = networks.get(networkId);
            networkKey = keyFor(entry);
        }
        if (networkKey != null && !networkKey.isEmpty()) {
            cache.put(cacheKey(ownerUuid, networkKey), sample(ownerUuid, networkId, networkKey, entry, nowMs));
        }
    }

    /**
     * Read the most recent snapshot.  This method is safe for HTTP threads and
     * performs no World/TileEntity/AE calls.
     */
    public NetworkHealthDiagnosticDto get(String ownerUuid, int networkId) {
        if (ownerUuid == null || ownerUuid.isEmpty() || networkId < 0) {
            return NetworkHealthDiagnosticDto.unknown(ownerUuid, networkId, null);
        }
        String networkKey = NetworkRegistry.keyForNetworkId(ownerUuid, networkId);
        if (networkKey == null || networkKey.isEmpty()) {
            return missingRegistration(ownerUuid, networkId, System.currentTimeMillis());
        }
        return getCached(ownerUuid, networkId, networkKey, System.currentTimeMillis());
    }

    /** HTTP-safe owner-scoped list for /api/server/diagnostics. */
    public List<NetworkHealthDiagnosticDto> snapshotForOwner(String ownerUuid) {
        if (ownerUuid == null || ownerUuid.isEmpty()) {
            return Collections.emptyList();
        }
        List<NetworkHealthDiagnosticDto> out = new ArrayList<NetworkHealthDiagnosticDto>();
        List<RegisteredNetwork> networks = sortedRawNetworks(ownerUuid);
        long nowMs = System.currentTimeMillis();
        for (int fallbackId = 0; fallbackId < networks.size(); fallbackId++) {
            String networkKey = keyFor(networks.get(fallbackId));
            if (networkKey == null) {
                continue;
            }
            Integer currentId = NetworkRegistry.networkIdForKey(ownerUuid, networkKey);
            int networkId = currentId == null ? fallbackId : currentId.intValue();
            out.add(getCached(ownerUuid, networkId, networkKey, nowMs));
        }
        Collections.sort(out, new Comparator<NetworkHealthDiagnosticDto>() {

            @Override
            public int compare(NetworkHealthDiagnosticDto a, NetworkHealthDiagnosticDto b) {
                return Integer.compare(a.networkId, b.networkId);
            }
        });
        return out;
    }

    /** Package/test hook for deterministic evaluator and stale-cache tests. */
    public void putForTests(NetworkHealthDiagnosticDto dto) {
        if (dto == null || dto.ownerUuid == null || dto.ownerUuid.isEmpty() || dto.networkKey == null
            || dto.networkKey.isEmpty()) {
            return;
        }
        cache.put(cacheKey(dto.ownerUuid, dto.networkKey), dto.copy());
    }

    /** Clear cache between world lifecycles; does not mutate NetworkRegistry. */
    public void clear() {
        cache.clear();
        lastSampleAt = 0L;
    }

    private static String cacheKey(String ownerUuid, String networkKey) {
        return ownerUuid + "|" + networkKey;
    }

    private static String keyFor(RegisteredNetwork entry) {
        if (entry == null) {
            return null;
        }
        return entry.monitorDim + ":" + entry.monitorX + ":" + entry.monitorY + ":" + entry.monitorZ;
    }

    private static RegisteredNetwork findByKey(List<RegisteredNetwork> entries, String networkKey) {
        if (entries == null || networkKey == null || networkKey.isEmpty()) {
            return null;
        }
        for (RegisteredNetwork entry : entries) {
            if (networkKey.equals(keyFor(entry))) {
                return entry;
            }
        }
        return null;
    }

    private static List<RegisteredNetwork> sortedRawNetworks(String ownerUuid) {
        List<RegisteredNetwork> out = new ArrayList<RegisteredNetwork>(NetworkRegistry.getRawNetworks(ownerUuid));
        Collections.sort(out, new Comparator<RegisteredNetwork>() {

            @Override
            public int compare(RegisteredNetwork a, RegisteredNetwork b) {
                int c = Integer.compare(a.monitorDim, b.monitorDim);
                if (c != 0) return c;
                c = Integer.compare(a.monitorX, b.monitorX);
                if (c != 0) return c;
                c = Integer.compare(a.monitorY, b.monitorY);
                if (c != 0) return c;
                return Integer.compare(a.monitorZ, b.monitorZ);
            }
        });
        return out;
    }

    /**
     * Cache-only request projection. Package visibility keeps stable-key/reorder behavior directly testable
     * without constructing a Minecraft World or AE grid.
     */
    NetworkHealthDiagnosticDto getCached(String ownerUuid, int networkId, String networkKey, long nowMs) {
        NetworkHealthDiagnosticDto current = cache.get(cacheKey(ownerUuid, networkKey));
        if (current == null) {
            return NetworkHealthDiagnosticDto.unknown(ownerUuid, networkId, networkKey);
        }
        return withFreshness(current, ownerUuid, networkId, networkKey, nowMs);
    }

    int cacheSizeForTests() {
        return cache.size();
    }

    private void pruneInactive(Set<String> activeKeys) {
        for (String cachedKey : new ArrayList<String>(cache.keySet())) {
            if (!activeKeys.contains(cachedKey)) {
                cache.remove(cachedKey);
            }
        }
    }

    private static NetworkHealthDiagnosticDto missingRegistration(String ownerUuid, int networkId, long nowMs) {
        NetworkHealthDiagnosticDto dto = new NetworkHealthDiagnosticDto(ownerUuid, networkId, null);
        dto.checkedAt = nowMs;
        dto.sampleAgeMs = Long.valueOf(0L);
        dto.stale = false;
        dto.links.registered = Boolean.FALSE;
        addIssue(dto, NetworkHealthDiagnosticDto.Issue.error(
            "no_registered_network",
            "webae.networkHealth.issue.noRegisteredNetwork",
            "webae.networkHealth.suggestion.registerNetwork",
            Integer.valueOf(networkId)));
        NetworkHealthStatusEvaluator.evaluateInto(dto);
        return dto;
    }

    private static NetworkHealthDiagnosticDto withFreshness(NetworkHealthDiagnosticDto current, String ownerUuid,
        int networkId, String networkKey, long nowMs) {
        NetworkHealthDiagnosticDto out = current.copy();
        // networkId is a transient list index and may change when the player moves or registrations change.
        // Owner + stable coordinate key are the cache identity; rewrite the current id defensively per request.
        out.ownerUuid = ownerUuid;
        out.networkId = networkId;
        out.networkKey = networkKey;
        if (out.checkedAt <= 0L) {
            out.sampleAgeMs = null;
            out.stale = true;
        } else {
            long age = Math.max(0L, nowMs - out.checkedAt);
            out.sampleAgeMs = Long.valueOf(age);
            out.stale = age > STALE_AFTER_MS;
        }
        if (out.stale) {
            addIssue(out, NetworkHealthDiagnosticDto.Issue.unknown(
                "sample_stale",
                "webae.networkHealth.issue.sampleStale",
                "webae.networkHealth.suggestion.waitForSample",
                Long.valueOf(out.sampleAgeMs == null ? -1L : out.sampleAgeMs.longValue())));
        }
        NetworkHealthStatusEvaluator.evaluateInto(out);
        return out;
    }

    private static NetworkHealthDiagnosticDto sample(String ownerUuid, int networkId, String networkKey,
        RegisteredNetwork entry, long nowMs) {
        NetworkHealthDiagnosticDto dto = new NetworkHealthDiagnosticDto(ownerUuid, networkId, networkKey);
        dto.checkedAt = nowMs;
        dto.sampleAgeMs = Long.valueOf(0L);
        dto.stale = false;

        if (entry == null) {
            dto.links.registered = Boolean.FALSE;
            addIssue(dto, NetworkHealthDiagnosticDto.Issue.error(
                "no_registered_network",
                "webae.networkHealth.issue.noRegisteredNetwork",
                "webae.networkHealth.suggestion.registerNetwork",
                networkId));
            NetworkHealthStatusEvaluator.evaluateInto(dto);
            return dto;
        }

        dto.links.registered = Boolean.TRUE;
        // The registry entry itself proves that this monitor anchor is registered, even if its chunk is not
        // currently loaded. Do not collapse registration, loading and validity into the same boolean.
        dto.monitors.registered = Boolean.TRUE;
        World world = resolveWorld(entry.monitorDim);
        if (world == null) {
            // The server cannot distinguish a missing chunk from a missing block
            // off-thread; leave evidence null so status is explicitly unknown.
            addIssue(dto, NetworkHealthDiagnosticDto.Issue.warning(
                "monitor_stale",
                "webae.networkHealth.issue.monitorStale",
                "webae.networkHealth.suggestion.loadMonitorChunk",
                networkKey));
            NetworkHealthStatusEvaluator.evaluateInto(dto);
            return dto;
        }

        TileEntity monitorTile = null;
        boolean monitorPositionLoaded = false;
        try {
            if (world.blockExists(entry.monitorX, entry.monitorY, entry.monitorZ)) {
                monitorPositionLoaded = true;
                monitorTile = world.getTileEntity(entry.monitorX, entry.monitorY, entry.monitorZ);
            }
        } catch (Exception ignored) {}

        if (!(monitorTile instanceof TileEntityAdvanceDataMonitor)) {
            dto.links.loaded = null;
            dto.links.reachable = null;
            dto.monitors.bound = null;
            // A loaded position containing another block is a confirmed invalid anchor. If the
            // chunk is unavailable, the monitor's validity is unknown rather than false.
            dto.monitors.valid = monitorPositionLoaded ? Boolean.FALSE : null;
            addIssue(dto, NetworkHealthDiagnosticDto.Issue.warning(
                "monitor_stale",
                "webae.networkHealth.issue.monitorStale",
                "webae.networkHealth.suggestion.loadMonitorChunk",
                networkKey));
            NetworkHealthStatusEvaluator.evaluateInto(dto);
            return dto;
        }

        TileEntityAdvanceDataMonitor monitor = (TileEntityAdvanceDataMonitor) monitorTile;
        dto.monitors.valid = Boolean.TRUE;

        BindingEvidence bindings = inspectBindings(world, monitor);
        dto.monitors.bound = Boolean.valueOf(bindings.bound);
        dto.links.loaded = bindings.linkLoaded;
        TileEntityAdvanceNetworkLink link = bindings.link;
        if (!bindings.bound) {
            addIssue(dto, NetworkHealthDiagnosticDto.Issue.warning(
                "monitor_unbound",
                "webae.networkHealth.issue.monitorUnbound",
                "webae.networkHealth.suggestion.bindNetworkLink",
                networkKey));
        }
        if (link == null) {
            dto.links.reachable = bindings.targetUnavailable || bindings.linkLoaded == null ? null : Boolean.FALSE;
            if (bindings.targetUnavailable) {
                addIssue(dto, NetworkHealthDiagnosticDto.Issue.warning(
                    "monitor_stale",
                    "webae.networkHealth.issue.monitorStale",
                    "webae.networkHealth.suggestion.loadMonitorChunk",
                    networkKey));
            } else {
                addIssue(dto, NetworkHealthDiagnosticDto.Issue.error(
                    "no_link",
                    "webae.networkHealth.issue.noLink",
                    "webae.networkHealth.suggestion.bindNetworkLink",
                    networkKey));
            }
            NetworkHealthStatusEvaluator.evaluateInto(dto);
            return dto;
        }

        IGridNode node = null;
        try {
            node = link.getGridNode(ForgeDirection.UNKNOWN);
        } catch (Exception e) {
            // An exception is missing evidence, not proof that the connector or Grid is absent.
            AdvanceDataMonitor.LOG.debug("[WebAE] Network health connector probe failed: {}", e.getMessage());
            NetworkHealthStatusEvaluator.evaluateInto(dto);
            return dto;
        }
        dto.grid.connectorAvailable = Boolean.valueOf(node != null);
        if (node == null) {
            dto.links.reachable = Boolean.FALSE;
            dto.grid.present = Boolean.FALSE;
            addIssue(dto, NetworkHealthDiagnosticDto.Issue.warning(
                "network_connector_unavailable",
                "webae.networkHealth.issue.networkConnectorUnavailable",
                "webae.networkHealth.suggestion.checkAeCable",
                networkKey));
            addIssue(dto, NetworkHealthDiagnosticDto.Issue.error(
                "grid_missing",
                "webae.networkHealth.issue.gridMissing",
                "webae.networkHealth.suggestion.checkAeCable",
                networkKey));
            NetworkHealthStatusEvaluator.evaluateInto(dto);
            return dto;
        }

        IGrid grid;
        try {
            grid = node.getGrid();
        } catch (Exception e) {
            // The connector is known to exist, but Grid reachability could not be verified.
            dto.links.reachable = null;
            dto.grid.present = null;
            AdvanceDataMonitor.LOG.debug("[WebAE] Network health Grid probe failed: {}", e.getMessage());
            NetworkHealthStatusEvaluator.evaluateInto(dto);
            return dto;
        }
        dto.links.reachable = Boolean.valueOf(grid != null);
        dto.grid.present = Boolean.valueOf(grid != null);
        if (grid == null) {
            addIssue(dto, NetworkHealthDiagnosticDto.Issue.error(
                "grid_missing",
                "webae.networkHealth.issue.gridMissing",
                "webae.networkHealth.suggestion.checkAeCable",
                networkKey));
            NetworkHealthStatusEvaluator.evaluateInto(dto);
            return dto;
        }

        try {
            dto.grid.storageAvailable = Boolean.valueOf(grid.getCache(IStorageGrid.class) != null);
        } catch (Exception e) {
            dto.grid.storageAvailable = null;
        }
        try {
            dto.grid.craftingAvailable = Boolean.valueOf(grid.getCache(ICraftingGrid.class) != null);
        } catch (Exception e) {
            dto.grid.craftingAvailable = null;
        }
        if (Boolean.FALSE.equals(dto.grid.connectorAvailable)) {
            addIssue(dto, NetworkHealthDiagnosticDto.Issue.warning(
                "network_connector_unavailable",
                "webae.networkHealth.issue.networkConnectorUnavailable",
                "webae.networkHealth.suggestion.checkAeCable",
                networkKey));
        }
        if (Boolean.FALSE.equals(dto.grid.storageAvailable)) {
            addIssue(dto, NetworkHealthDiagnosticDto.Issue.warning(
                "storage_unavailable",
                "webae.networkHealth.issue.storageUnavailable",
                "webae.networkHealth.suggestion.checkStorageGrid",
                networkKey));
        }
        if (Boolean.FALSE.equals(dto.grid.craftingAvailable)) {
            addIssue(dto, NetworkHealthDiagnosticDto.Issue.warning(
                "crafting_unavailable",
                "webae.networkHealth.issue.craftingUnavailable",
                "webae.networkHealth.suggestion.checkCraftingGrid",
                networkKey));
        }

        ChannelProbeResult channelProbe = TopologySnapshot.probeRealChannels(grid);
        applyChannelProbe(dto, channelProbe != null && channelProbe.available,
            channelProbe == null ? -1 : channelProbe.used,
            channelProbe == null ? -1 : channelProbe.max,
            networkKey);
        NetworkHealthStatusEvaluator.evaluateInto(dto);
        return dto;
    }

    /** Pure projection kept package-visible for real/unknown channel contract tests. */
    static void applyChannelProbe(NetworkHealthDiagnosticDto dto, boolean available, int used, int max,
        String networkKey) {
        if (dto == null) {
            return;
        }
        if (available && used >= 0 && max > 0) {
            dto.channels.available = Boolean.TRUE;
            dto.channels.used = Integer.valueOf(used);
            dto.channels.max = Integer.valueOf(max);
            if (used > max) {
                Map<String, Object> evidence = new LinkedHashMap<String, Object>();
                evidence.put("used", Integer.valueOf(used));
                evidence.put("max", Integer.valueOf(max));
                addIssue(dto, NetworkHealthDiagnosticDto.Issue.error(
                    "channel_over_limit",
                    "webae.networkHealth.issue.channelOverLimit",
                    "webae.networkHealth.suggestion.reduceChannels",
                    evidence));
            }
        } else {
            dto.channels.available = Boolean.FALSE;
            // Do not manufacture a max/used value from simulated topology data.
            dto.channels.used = null;
            dto.channels.max = null;
        }
    }

    private static BindingEvidence inspectBindings(World world, TileEntityAdvanceDataMonitor monitor) {
        BindingEvidence result = new BindingEvidence();
        Map<Integer, net.minecraft.nbt.NBTTagCompound> entries = monitor.getDataBoundList();
        if (entries == null) {
            return result;
        }
        for (Map.Entry<Integer, net.minecraft.nbt.NBTTagCompound> entry : entries.entrySet()) {
            net.minecraft.nbt.NBTTagCompound nbt = entry.getValue();
            if (nbt == null || !nbt.hasKey("XYZ")) {
                continue;
            }
            int[] xyz = parseXYZ(nbt.getString("XYZ"));
            if (xyz == null || (xyz[0] == monitor.xCoord && xyz[1] == monitor.yCoord && xyz[2] == monitor.zCoord)) {
                continue;
            }
            result.bound = true;
            TileEntity target = null;
            boolean loaded = false;
            try {
                if (world.blockExists(xyz[0], xyz[1], xyz[2])) {
                    loaded = true;
                    target = world.getTileEntity(xyz[0], xyz[1], xyz[2]);
                }
            } catch (Exception ignored) {}
            if (!loaded) {
                result.targetUnavailable = true;
                continue;
            }
            if (target instanceof TileEntityAdvanceNetworkLink && result.link == null) {
                result.link = (TileEntityAdvanceNetworkLink) target;
                result.linkLoaded = Boolean.TRUE;
            }
        }
        if (result.link == null) {
            if (result.targetUnavailable) {
                // One or more binding targets are outside loaded chunks. Their type cannot be proven.
                result.linkLoaded = null;
            } else if (result.bound) {
                result.linkLoaded = Boolean.FALSE;
            } else {
                result.linkLoaded = null;
            }
        }
        return result;
    }

    private static int[] parseXYZ(String value) {
        if (value == null) return null;
        String[] parts = value.split(",");
        if (parts.length != 3) return null;
        try {
            return new int[] { Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim()) };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static World resolveWorld(int dimension) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null && server.worldServers != null) {
            // Dimension ids are not array indexes (Nether and mod dimensions
            // are commonly negative or sparse), so inspect the provider's id.
            for (World world : server.worldServers) {
                if (world != null && world.provider != null && world.provider.dimensionId == dimension) {
                    return world;
                }
            }
        }
        try {
            return DimensionManager.getWorld(dimension);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void addIssue(NetworkHealthDiagnosticDto dto, NetworkHealthDiagnosticDto.Issue issue) {
        if (dto == null || issue == null) return;
        if (dto.issues == null) dto.issues = new ArrayList<NetworkHealthDiagnosticDto.Issue>();
        for (NetworkHealthDiagnosticDto.Issue old : dto.issues) {
            if (old != null && issue.code != null && issue.code.equals(old.code)) return;
        }
        dto.issues.add(issue);
    }

    private static final class BindingEvidence {

        boolean bound;
        Boolean linkLoaded;
        boolean targetUnavailable;
        TileEntityAdvanceNetworkLink link;
    }
}
