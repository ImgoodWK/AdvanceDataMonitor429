package com.imgood.textech.webae.alerts;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.imgood.textech.config.ConfigWebAlertsLoader;
import com.imgood.textech.webae.auth.WebAuthToken;
import com.imgood.textech.webae.api.handler.OrderHandler;
import com.imgood.textech.webae.cache.SnapshotCache;
import com.imgood.textech.webae.cache.SnapshotScheduler;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.context.WebAeOwnerContext.NetworkGroup;
import com.imgood.textech.webae.dto.GtMachineDto;
import com.imgood.textech.webae.dto.GtMachineListDto;
import com.imgood.textech.webae.dto.StorageDto;
import com.imgood.textech.webae.dto.StorageDto.CpuEntry;
import com.imgood.textech.webae.dto.StorageDto.FluidEntry;
import com.imgood.textech.webae.dto.StorageDto.ItemEntry;
import com.imgood.textech.webae.dto.OrderStatus;
import com.imgood.textech.webae.snapshot.AeSnapshotCollector.NetworkInfo;
import com.imgood.textech.webae.topology.TopologyCache;
import com.imgood.textech.webae.topology.TopologyEdge;
import com.imgood.textech.webae.topology.TopologySnapshot;

/**
 * Server tick alert engine for WebAE automation (A1–A5).
 */
public final class WebAlertEngine {

    private static final ConcurrentHashMap<String, Long> lastPollByOwner = new ConcurrentHashMap<String, Long>();
    private static final ConcurrentHashMap<String, Map<String, CpuTrack>> cpuHistory = new ConcurrentHashMap<String, Map<String, CpuTrack>>();
    private static final ConcurrentHashMap<String, Set<String>> seenOrderIds = new ConcurrentHashMap<String, Set<String>>();

    private WebAlertEngine() {}

    public static void onServerTick(long now) {
        WebAlertsConfig cfg = ConfigWebAlertsLoader.get();
        if (cfg == null || !cfg.enabled) {
            return;
        }
        long intervalMs = Math.max(1000L, (long) cfg.pollIntervalSeconds * 1000L);
        for (String ownerUuid : WebAuthToken.listActiveOwnerUuids()) {
            Long last = lastPollByOwner.get(ownerUuid);
            if (last != null && now - last < intervalMs) {
                continue;
            }
            lastPollByOwner.put(ownerUuid, now);
            evaluateOwner(ownerUuid, cfg, now);
        }
    }

    private static void evaluateOwner(String ownerUuid, WebAlertsConfig cfg, long now) {
        List<NetworkInfo> networks = WebAeOwnerContext.findNetworksForOwner(ownerUuid);
        Set<String> activeSources = new HashSet<String>();

        for (NetworkInfo net : networks) {
            int networkId = net.networkId;
            checkInventoryThresholds(ownerUuid, networkId, cfg, activeSources);
            checkCpuStuck(ownerUuid, networkId, cfg, now, activeSources);
            checkGtErrors(ownerUuid, networkId, cfg, activeSources);
            checkChannelOverload(ownerUuid, networkId, cfg, activeSources);
        }

        if (cfg.orderCompleteEnabled) {
            checkOrderCompletions(ownerUuid, activeSources, now);
        }

        pruneInactive(ownerUuid, activeSources);
    }

    private static void checkInventoryThresholds(String ownerUuid, int networkId, WebAlertsConfig cfg,
        Set<String> activeSources) {
        if (cfg.inventoryThresholds == null || cfg.inventoryThresholds.isEmpty()) {
            return;
        }
        StorageDto storage = SnapshotCache.instance()
            .getStale(ownerUuid, networkId, SnapshotScheduler.TYPE_STORAGE);
        if (storage == null) {
            return;
        }
        for (WebAlertsConfig.InventoryThresholdRule rule : cfg.inventoryThresholds) {
            if (rule == null) {
                continue;
            }
            if (rule.networkId >= 0 && rule.networkId != networkId) {
                continue;
            }
            String sourceKey = "inv:" + networkId + ":" + (rule.itemId != null ? rule.itemId : rule.fluidName);
            long amount = 0L;
            if (rule.itemId != null && !rule.itemId.isEmpty()) {
                amount = findItemAmount(storage, rule.itemId);
            } else if (rule.fluidName != null && !rule.fluidName.isEmpty()) {
                amount = findFluidAmount(storage, rule.fluidName);
            } else {
                continue;
            }
            if (amount < rule.minAmount) {
                WebAlertDto alert = new WebAlertDto();
                alert.type = "inventory_threshold";
                alert.severity = "warning";
                alert.networkId = networkId;
                alert.timestamp = System.currentTimeMillis();
                alert.sourceKey = sourceKey;
                alert.title = rule.label != null && !rule.label.isEmpty() ? rule.label : "Low inventory";
                alert.message = "Amount "
                    + amount
                    + " below threshold "
                    + rule.minAmount
                    + " on network "
                    + networkId;
                WebAlertStore.instance()
                    .upsert(ownerUuid, alert);
                activeSources.add(sourceKey);
            } else {
                WebAlertStore.instance()
                    .clearSource(ownerUuid, sourceKey);
            }
        }
    }

    private static long findItemAmount(StorageDto storage, String itemId) {
        if (storage.items == null) {
            return 0L;
        }
        long total = 0L;
        String needle = itemId.toLowerCase();
        for (StorageDto.ItemEntry item : storage.items) {
            if (item == null) {
                continue;
            }
            String id = item.itemId != null ? item.itemId.toLowerCase() : "";
            String reg = item.registryName != null ? item.registryName.toLowerCase() : "";
            if (id.contains(needle) || reg.contains(needle) || needle.contains(id)) {
                total += item.amount;
            }
        }
        return total;
    }

    private static long findFluidAmount(StorageDto storage, String fluidName) {
        if (storage.fluids == null) {
            return 0L;
        }
        long total = 0L;
        String needle = fluidName.toLowerCase();
        for (FluidEntry fluid : storage.fluids) {
            if (fluid == null || fluid.fluidName == null) {
                continue;
            }
            if (fluid.fluidName.toLowerCase()
                .contains(needle)) {
                total += fluid.amount;
            }
        }
        return total;
    }

    private static void checkCpuStuck(String ownerUuid, int networkId, WebAlertsConfig cfg, long now,
        Set<String> activeSources) {
        if (cfg.cpuStuckMinutes <= 0) {
            return;
        }
        StorageDto storage = SnapshotCache.instance()
            .getStale(ownerUuid, networkId, SnapshotScheduler.TYPE_STORAGE);
        if (storage == null || storage.cpus == null) {
            return;
        }
        Map<String, CpuTrack> tracks = cpuHistory.get(ownerUuid);
        if (tracks == null) {
            tracks = new HashMap<String, CpuTrack>();
            cpuHistory.put(ownerUuid, tracks);
        }
        long stuckMs = (long) cfg.cpuStuckMinutes * 60_000L;

        for (CpuEntry cpu : storage.cpus) {
            if (cpu == null || !cpu.isBusy) {
                continue;
            }
            String cpuKey = networkId + ":" + cpu.name + ":" + cpu.x + ":" + cpu.y + ":" + cpu.z;
            CpuTrack track = tracks.get(cpuKey);
            if (track == null) {
                track = new CpuTrack();
                track.elapsedTime = cpu.elapsedTime;
                track.sinceMs = now;
                tracks.put(cpuKey, track);
                continue;
            }
            if (cpu.elapsedTime != track.elapsedTime) {
                track.elapsedTime = cpu.elapsedTime;
                track.sinceMs = now;
                WebAlertStore.instance()
                    .clearSource(ownerUuid, "cpu:" + cpuKey);
                continue;
            }
            if (now - track.sinceMs >= stuckMs) {
                String sourceKey = "cpu:" + cpuKey;
                WebAlertDto alert = new WebAlertDto();
                alert.type = "cpu_stuck";
                alert.severity = "error";
                alert.networkId = networkId;
                alert.timestamp = now;
                alert.sourceKey = sourceKey;
                alert.title = "CPU stuck: " + cpu.name;
                alert.message = "CPU busy for "
                    + cfg.cpuStuckMinutes
                    + "+ minutes without elapsedTime progress on network "
                    + networkId;
                WebAlertStore.instance()
                    .upsert(ownerUuid, alert);
                activeSources.add(sourceKey);
            }
        }
    }

    private static void checkGtErrors(String ownerUuid, int networkId, WebAlertsConfig cfg, Set<String> activeSources) {
        if (!cfg.gtErrorEnabled) {
            return;
        }
        GtMachineListDto gt = SnapshotCache.instance()
            .getStale(ownerUuid, networkId, SnapshotScheduler.TYPE_GT_MACHINES);
        if (gt == null || gt.machines == null) {
            return;
        }
        for (GtMachineDto machine : gt.machines) {
            if (machine == null || machine.errorId == 0) {
                continue;
            }
            String sourceKey = "gt:" + networkId + ":" + machine.x + ":" + machine.y + ":" + machine.z;
            WebAlertDto alert = new WebAlertDto();
            alert.type = "gt_error";
            alert.severity = "error";
            alert.networkId = networkId;
            alert.timestamp = System.currentTimeMillis();
            alert.sourceKey = sourceKey;
            alert.title = "GT machine error";
            alert.message = "Machine at "
                + machine.x
                + ","
                + machine.y
                + ","
                + machine.z
                + " errorId="
                + machine.errorId
                + " ("
                + (machine.statusText != null ? machine.statusText : "")
                + ")";
            WebAlertStore.instance()
                .upsert(ownerUuid, alert);
            activeSources.add(sourceKey);
        }
    }

    private static void checkChannelOverload(String ownerUuid, int networkId, WebAlertsConfig cfg,
        Set<String> activeSources) {
        TopologyCache.CachedResult cached = TopologyCache.instance()
            .getCached(ownerUuid, networkId, "logical");
        TopologySnapshot snapshot = cached != null ? cached.snapshot : null;
        if (snapshot == null || snapshot.meta == null) {
            return;
        }
        // Topology "simulated" channels are visualization-only (star fake cables); never use them for alerts.
        String sourceKey = "channel:" + networkId;
        if (snapshot.meta.channelsReal == null || !snapshot.meta.channelsReal.available) {
            WebAlertStore.instance()
                .clearSource(ownerUuid, sourceKey);
            return;
        }
        int used = snapshot.meta.channelsReal.used;
        int max = snapshot.meta.channelsReal.max;
        if (used < 0 || max <= 0) {
            WebAlertStore.instance()
                .clearSource(ownerUuid, sourceKey);
            return;
        }
        boolean overload = used >= cfg.channelThresholdAbsolute;
        if (!overload && cfg.channelThresholdPercent > 0) {
            overload = (double) used / (double) max * 100.0 >= cfg.channelThresholdPercent;
        }
        if (overload) {
            WebAlertDto alert = new WebAlertDto();
            alert.type = "channel_overload";
            alert.severity = "warning";
            alert.networkId = networkId;
            alert.timestamp = System.currentTimeMillis();
            alert.sourceKey = sourceKey;
            alert.title = "Channel overload";
            alert.message = "Channels "
                + used
                + "/"
                + max
                + " exceed threshold on network "
                + networkId;
            WebAlertStore.instance()
                .upsert(ownerUuid, alert);
            activeSources.add(sourceKey);
        } else {
            WebAlertStore.instance()
                .clearSource(ownerUuid, sourceKey);
        }
    }

    private static void checkOrderCompletions(String ownerUuid, Set<String> activeSources, long now) {
        List<OrderStatus> recent = OrderHandler.getRecentCompletedOrders(ownerUuid, 20);
        Set<String> seen = seenOrderIds.get(ownerUuid);
        if (seen == null) {
            seen = new HashSet<String>();
            seenOrderIds.put(ownerUuid, seen);
        }
        for (OrderStatus status : recent) {
            if (status == null || status.craftJobId == null) {
                continue;
            }
            if (seen.contains(status.craftJobId)) {
                continue;
            }
            seen.add(status.craftJobId);
            String sourceKey = "order:" + status.craftJobId;
            WebAlertDto alert = new WebAlertDto();
            alert.type = "order_complete";
            alert.severity = "info";
            alert.networkId = -1;
            alert.timestamp = status.completedAt > 0 ? status.completedAt : now;
            alert.sourceKey = sourceKey;
            alert.title = "Order completed";
            alert.message = status.message != null ? status.message : ("Job " + status.craftJobId + " completed");
            WebAlertStore.instance()
                .upsert(ownerUuid, alert);
            activeSources.add(sourceKey);
        }
        while (seen.size() > 500) {
            Iterator<String> it = seen.iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            } else {
                break;
            }
        }
    }

    private static void pruneInactive(String ownerUuid, Set<String> activeSources) {
        List<WebAlertDto> current = WebAlertStore.instance()
            .getAlerts(ownerUuid);
        for (WebAlertDto alert : current) {
            if (alert == null || alert.sourceKey == null) {
                continue;
            }
            if ("order_complete".equals(alert.type)) {
                continue;
            }
            if (!activeSources.contains(alert.sourceKey)) {
                WebAlertStore.instance()
                    .clearSource(ownerUuid, alert.sourceKey);
            }
        }
    }

    private static final class CpuTrack {

        long elapsedTime;
        long sinceMs;
    }
}
