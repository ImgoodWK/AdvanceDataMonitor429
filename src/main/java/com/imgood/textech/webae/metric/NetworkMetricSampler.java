package com.imgood.textech.webae.metric;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.cache.SnapshotCache;
import com.imgood.textech.webae.cache.SnapshotScheduler;
import com.imgood.textech.webae.dto.GtMachineDto;
import com.imgood.textech.webae.dto.GtMachineListDto;
import com.imgood.textech.webae.dto.NetworkMetricEntityHistoryDto;
import com.imgood.textech.webae.dto.NetworkMetricFluidHistoryDto;
import com.imgood.textech.webae.dto.NetworkMetricHistoryDto;
import com.imgood.textech.webae.dto.NetworkMetricItemHistoryDto;
import com.imgood.textech.webae.dto.StorageDto;
import com.imgood.textech.webae.dto.StorageDto.CpuEntry;
import com.imgood.textech.webae.dto.StorageDto.FluidEntry;
import com.imgood.textech.webae.dto.StorageDto.ItemEntry;

/**
 * Network-wide scalar metric sampler for the WebAE dashboard trend charts,
 * plus optional per-item / per-fluid / per-entity (CPU/GT) pinned tracks.
 */
public class NetworkMetricSampler {

    private static final NetworkMetricSampler INSTANCE = new NetworkMetricSampler();
    private static final long IDLE_EVICT_MS = 120_000L;

    private final ConcurrentHashMap<String, NetworkMetricRecord> records = new ConcurrentHashMap<String, NetworkMetricRecord>();
    private SnapshotCache snapshotCache;
    private long lastSampleTime;

    private NetworkMetricSampler() {}

    public static NetworkMetricSampler getInstance() {
        return INSTANCE;
    }

    public void setSnapshotCache(SnapshotCache cache) {
        this.snapshotCache = cache;
    }

    public void onServerTick() {
        long now = System.currentTimeMillis();
        long interval = Config.webMetricSampleIntervalMs > 0 ? Config.webMetricSampleIntervalMs : 10_000L;
        if (now - lastSampleTime < interval) {
            return;
        }
        lastSampleTime = now;

        if (snapshotCache == null) return;

        for (java.util.Map.Entry<String, NetworkMetricRecord> entry : records.entrySet()) {
            String recordKey = entry.getKey();
            NetworkMetricRecord record = entry.getValue();
            if (now - record.lastAccessTime > IDLE_EVICT_MS) {
                records.remove(recordKey);
                continue;
            }

            int colonIdx = recordKey.lastIndexOf(':');
            if (colonIdx < 0) continue;
            String playerUuid = recordKey.substring(0, colonIdx);
            int networkId;
            try {
                networkId = Integer.parseInt(recordKey.substring(colonIdx + 1));
            } catch (NumberFormatException e) {
                continue;
            }

            try {
                sampleFromCache(playerUuid, networkId, record, now);
            } catch (Throwable t) {
                AdvanceDataMonitor.LOG
                    .error("[WebAE] Network metric sample failed for player={} network={}", playerUuid, networkId, t);
            }
        }
    }

    private void sampleFromCache(String playerUuid, int networkId, NetworkMetricRecord record, long now) {
        StorageDto storage = snapshotCache.getStale(playerUuid, networkId, SnapshotScheduler.TYPE_STORAGE);
        GtMachineListDto machines = snapshotCache.getStale(playerUuid, networkId, SnapshotScheduler.TYPE_GT_MACHINES);

        if (storage == null && machines == null) {
            return;
        }

        int itemCount = 0;
        int fluidCount = 0;
        int essentiaCount = 0;
        long bytesUsed = 0L;
        long bytesMax = 0L;
        double bytesPercent = 0.0;
        long itemTotal = 0L;
        long fluidTotal = 0L;
        int activeCpu = 0;
        int busyCpu = 0;

        if (storage != null) {
            if (storage.items != null) {
                itemCount = storage.items.size();
                for (StorageDto.ItemEntry item : storage.items) {
                    itemTotal += item.amount;
                }
            }
            if (storage.fluids != null) {
                fluidCount = storage.fluids.size();
                for (StorageDto.FluidEntry fluid : storage.fluids) {
                    fluidTotal += fluid.amount;
                }
            }
            if (storage.essentia != null) {
                essentiaCount = storage.essentia.size();
            }
            bytesUsed = storage.bytesUsed;
            bytesMax = storage.bytesMax;
            if (bytesMax > 0) {
                bytesPercent = (double) bytesUsed / (double) bytesMax;
                if (bytesPercent > 1.0) bytesPercent = 1.0;
            }
            if (storage.cpus != null) {
                activeCpu = storage.cpus.size();
                for (StorageDto.CpuEntry cpu : storage.cpus) {
                    if (cpu.isBusy) busyCpu++;
                }
            }
        }

        int gtMachineCount = 0;
        int gtActiveCount = 0;
        if (machines != null && machines.machines != null) {
            gtMachineCount = machines.machines.size();
            for (GtMachineDto m : machines.machines) {
                if (m.isActive) gtActiveCount++;
            }
        }

        double cpuBusyRatio = activeCpu > 0 ? (double) busyCpu / (double) activeCpu : 0.0;

        record.addSample(
            new NetworkMetricSample(
                now,
                itemCount,
                fluidCount,
                essentiaCount,
                bytesUsed,
                bytesMax,
                bytesPercent,
                itemTotal,
                fluidTotal,
                activeCpu,
                busyCpu,
                cpuBusyRatio,
                gtMachineCount,
                gtActiveCount));

        record.sampleTrackedPins(storage, machines, now);
    }

    public void markActive(String playerUuid, int networkId) {
        String key = playerUuid + ":" + networkId;
        NetworkMetricRecord record = records.get(key);
        if (record == null) {
            long windowMs = (long) Config.webMetricSampleWindowSeconds * 1000L;
            if (windowMs <= 0) windowMs = 300_000L;
            record = new NetworkMetricRecord(windowMs);
            records.put(key, record);
        }
        record.lastAccessTime = System.currentTimeMillis();
    }

    public NetworkMetricHistoryDto getHistory(String playerUuid, int networkId) {
        String key = playerUuid + ":" + networkId;
        NetworkMetricRecord record = records.get(key);
        if (record == null) return null;
        record.lastAccessTime = System.currentTimeMillis();
        return record.toDto(networkId);
    }

    /**
     * @return error message when over limit, or {@code null} on success.
     */
    public String registerTrackedFluids(String playerUuid, int networkId, List<String> fluidNames) {
        if (fluidNames == null || fluidNames.isEmpty()) {
            return null;
        }
        ensureRecord(playerUuid, networkId);
        NetworkMetricRecord record = records.get(playerUuid + ":" + networkId);
        if (record == null) {
            return "Sampler record unavailable";
        }
        return record.registerFluids(fluidNames, countTracksForPlayer(playerUuid), maxFluidTracks(), maxGlobalTracks());
    }

    public NetworkMetricFluidHistoryDto getFluidHistory(String playerUuid, int networkId) {
        NetworkMetricRecord record = records.get(playerUuid + ":" + networkId);
        if (record == null) {
            return null;
        }
        record.lastAccessTime = System.currentTimeMillis();
        return record.toFluidDto(networkId);
    }

    /**
     * @return error message when over limit, or {@code null} on success.
     */
    public String registerTrackedItems(String playerUuid, int networkId, List<String> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return null;
        }
        ensureRecord(playerUuid, networkId);
        NetworkMetricRecord record = records.get(playerUuid + ":" + networkId);
        if (record == null) {
            return "Sampler record unavailable";
        }
        return record.registerItems(itemIds, countTracksForPlayer(playerUuid), maxItemTracks(), maxGlobalTracks());
    }

    public NetworkMetricItemHistoryDto getItemHistory(String playerUuid, int networkId) {
        NetworkMetricRecord record = records.get(playerUuid + ":" + networkId);
        if (record == null) {
            return null;
        }
        record.lastAccessTime = System.currentTimeMillis();
        return record.toItemDto(networkId);
    }

    /**
     * @param fieldByEntity entityKey → optional metric field (null = default)
     * @return error message when over limit, or {@code null} on success.
     */
    public String registerTrackedEntities(String playerUuid, int networkId, Map<String, String> fieldByEntity) {
        if (fieldByEntity == null || fieldByEntity.isEmpty()) {
            return null;
        }
        ensureRecord(playerUuid, networkId);
        NetworkMetricRecord record = records.get(playerUuid + ":" + networkId);
        if (record == null) {
            return "Sampler record unavailable";
        }
        return record
            .registerEntities(fieldByEntity, countTracksForPlayer(playerUuid), maxEntityTracks(), maxGlobalTracks());
    }

    public NetworkMetricEntityHistoryDto getEntityHistory(String playerUuid, int networkId) {
        NetworkMetricRecord record = records.get(playerUuid + ":" + networkId);
        if (record == null) {
            return null;
        }
        record.lastAccessTime = System.currentTimeMillis();
        return record.toEntityDto(networkId);
    }

    private void ensureRecord(String playerUuid, int networkId) {
        markActive(playerUuid, networkId);
    }

    private int countTracksForPlayer(String playerUuid) {
        int total = 0;
        String prefix = playerUuid + ":";
        for (java.util.Map.Entry<String, NetworkMetricRecord> entry : records.entrySet()) {
            if (entry.getKey()
                .startsWith(prefix)) {
                total += entry.getValue()
                    .trackedCount();
            }
        }
        return total;
    }

    private static int maxItemTracks() {
        return Config.webDashboardMaxItemTracks > 0 ? Config.webDashboardMaxItemTracks : 16;
    }

    private static int maxFluidTracks() {
        return Config.webDashboardMaxFluidTracks > 0 ? Config.webDashboardMaxFluidTracks : 16;
    }

    private static int maxEntityTracks() {
        return Config.webDashboardMaxEntityTracks > 0 ? Config.webDashboardMaxEntityTracks : 16;
    }

    private static int maxGlobalTracks() {
        return Config.webDashboardMaxTracksGlobal > 0 ? Config.webDashboardMaxTracksGlobal : 32;
    }

    // ---- inner types ----

    public static final class NetworkMetricSample {

        public final long ts;
        public final int itemCount;
        public final int fluidCount;
        public final int essentiaCount;
        public final long bytesUsed;
        public final long bytesMax;
        public final double bytesPercent;
        public final long itemTotal;
        public final long fluidTotal;
        public final int activeCpu;
        public final int busyCpu;
        public final double cpuBusyRatio;
        public final int gtMachineCount;
        public final int gtActiveCount;

        public NetworkMetricSample(long ts, int itemCount, int fluidCount, int essentiaCount, long bytesUsed,
            long bytesMax, double bytesPercent, long itemTotal, long fluidTotal, int activeCpu, int busyCpu,
            double cpuBusyRatio, int gtMachineCount, int gtActiveCount) {
            this.ts = ts;
            this.itemCount = itemCount;
            this.fluidCount = fluidCount;
            this.essentiaCount = essentiaCount;
            this.bytesUsed = bytesUsed;
            this.bytesMax = bytesMax;
            this.bytesPercent = bytesPercent;
            this.itemTotal = itemTotal;
            this.fluidTotal = fluidTotal;
            this.activeCpu = activeCpu;
            this.busyCpu = busyCpu;
            this.cpuBusyRatio = cpuBusyRatio;
            this.gtMachineCount = gtMachineCount;
            this.gtActiveCount = gtActiveCount;
        }
    }

    public static final class NetworkMetricRecord {

        private final Deque<NetworkMetricSample> samples = new ArrayDeque<NetworkMetricSample>();
        private final Map<String, Deque<LongSamplePoint>> fluidSamples = new HashMap<String, Deque<LongSamplePoint>>();
        private final LinkedHashSet<String> trackedFluidKeys = new LinkedHashSet<String>();
        private final Map<String, Deque<LongSamplePoint>> itemSamples = new HashMap<String, Deque<LongSamplePoint>>();
        private final LinkedHashSet<String> trackedItemKeys = new LinkedHashSet<String>();
        private final Map<String, Deque<DoubleSamplePoint>> entitySamples = new HashMap<String, Deque<DoubleSamplePoint>>();
        private final Map<String, String> entityFields = new HashMap<String, String>();
        private final LinkedHashSet<String> trackedEntityKeys = new LinkedHashSet<String>();
        private final long windowMs;
        private volatile long lastAccessTime;

        public NetworkMetricRecord(long windowMs) {
            this.windowMs = windowMs;
            this.lastAccessTime = System.currentTimeMillis();
        }

        public synchronized int trackedCount() {
            return trackedFluidKeys.size() + trackedItemKeys.size() + trackedEntityKeys.size();
        }

        public synchronized String registerFluids(List<String> fluidNames, int playerTrackCount, int maxCategory,
            int maxGlobal) {
            for (String name : fluidNames) {
                if (name == null) {
                    continue;
                }
                String key = name.trim()
                    .toLowerCase();
                if (key.isEmpty() || trackedFluidKeys.contains(key)) {
                    continue;
                }
                if (trackedFluidKeys.size() >= maxCategory) {
                    return "Fluid track limit reached (" + maxCategory
                        + "). Raise webConsole.dashboardMaxFluidTracks or remove pins.";
                }
                if (playerTrackCount + 1 > maxGlobal) {
                    return "Global track limit reached (" + maxGlobal
                        + "). Raise webConsole.dashboardMaxTracksGlobal or remove pins.";
                }
                trackedFluidKeys.add(key);
                playerTrackCount++;
                if (!fluidSamples.containsKey(key)) {
                    fluidSamples.put(key, new ArrayDeque<LongSamplePoint>());
                }
            }
            return null;
        }

        public synchronized String registerItems(List<String> itemIds, int playerTrackCount, int maxCategory,
            int maxGlobal) {
            for (String raw : itemIds) {
                if (raw == null) {
                    continue;
                }
                String key = normalizeItemKey(raw);
                if (key.isEmpty() || trackedItemKeys.contains(key)) {
                    continue;
                }
                if (trackedItemKeys.size() >= maxCategory) {
                    return "Item track limit reached (" + maxCategory
                        + "). Raise webConsole.dashboardMaxItemTracks or remove pins.";
                }
                if (playerTrackCount + 1 > maxGlobal) {
                    return "Global track limit reached (" + maxGlobal
                        + "). Raise webConsole.dashboardMaxTracksGlobal or remove pins.";
                }
                trackedItemKeys.add(key);
                playerTrackCount++;
                if (!itemSamples.containsKey(key)) {
                    itemSamples.put(key, new ArrayDeque<LongSamplePoint>());
                }
            }
            return null;
        }

        public synchronized String registerEntities(Map<String, String> fieldByEntity, int playerTrackCount,
            int maxCategory, int maxGlobal) {
            for (Map.Entry<String, String> e : fieldByEntity.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                String key = e.getKey()
                    .trim();
                if (key.isEmpty() || trackedEntityKeys.contains(key)) {
                    if (!key.isEmpty() && e.getValue() != null && !e.getValue()
                        .isEmpty()) {
                        entityFields.put(key, e.getValue()
                            .trim());
                    }
                    continue;
                }
                if (!key.startsWith("cpu:") && !key.startsWith("gt:")) {
                    continue;
                }
                if (trackedEntityKeys.size() >= maxCategory) {
                    return "Entity track limit reached (" + maxCategory
                        + "). Raise webConsole.dashboardMaxEntityTracks or remove pins.";
                }
                if (playerTrackCount + 1 > maxGlobal) {
                    return "Global track limit reached (" + maxGlobal
                        + "). Raise webConsole.dashboardMaxTracksGlobal or remove pins.";
                }
                trackedEntityKeys.add(key);
                playerTrackCount++;
                String field = e.getValue();
                if (field == null || field.trim()
                    .isEmpty()) {
                    field = key.startsWith("cpu:") ? "craftingProgress" : "progressPercent";
                } else {
                    field = field.trim();
                }
                entityFields.put(key, field);
                if (!entitySamples.containsKey(key)) {
                    entitySamples.put(key, new ArrayDeque<DoubleSamplePoint>());
                }
            }
            return null;
        }

        public synchronized void sampleTrackedPins(StorageDto storage, GtMachineListDto machines, long now) {
            long cutoff = now - windowMs;
            if (!trackedFluidKeys.isEmpty() && storage != null) {
                for (String fluidKey : trackedFluidKeys) {
                    Deque<LongSamplePoint> deque = ensureLongDeque(fluidSamples, fluidKey);
                    deque.addLast(new LongSamplePoint(now, findFluidAmount(storage, fluidKey)));
                    trimLong(deque, cutoff);
                }
            }
            if (!trackedItemKeys.isEmpty()) {
                Map<String, Long> amountById = buildItemAmountMap(storage);
                for (String itemKey : trackedItemKeys) {
                    Deque<LongSamplePoint> deque = ensureLongDeque(itemSamples, itemKey);
                    Long amt = amountById.get(itemKey);
                    deque.addLast(new LongSamplePoint(now, amt != null ? amt.longValue() : 0L));
                    trimLong(deque, cutoff);
                }
            }
            if (!trackedEntityKeys.isEmpty()) {
                for (String entityKey : trackedEntityKeys) {
                    Deque<DoubleSamplePoint> deque = entitySamples.get(entityKey);
                    if (deque == null) {
                        deque = new ArrayDeque<DoubleSamplePoint>();
                        entitySamples.put(entityKey, deque);
                    }
                    String field = entityFields.get(entityKey);
                    double value = resolveEntityValue(entityKey, field, storage, machines);
                    deque.addLast(new DoubleSamplePoint(now, value));
                    while (!deque.isEmpty() && deque.peekFirst().ts < cutoff) {
                        deque.pollFirst();
                    }
                }
            }
        }

        private static Deque<LongSamplePoint> ensureLongDeque(Map<String, Deque<LongSamplePoint>> map, String key) {
            Deque<LongSamplePoint> deque = map.get(key);
            if (deque == null) {
                deque = new ArrayDeque<LongSamplePoint>();
                map.put(key, deque);
            }
            return deque;
        }

        private static void trimLong(Deque<LongSamplePoint> deque, long cutoff) {
            while (!deque.isEmpty() && deque.peekFirst().ts < cutoff) {
                deque.pollFirst();
            }
        }

        private static String normalizeItemKey(String raw) {
            return raw.trim();
        }

        private static Map<String, Long> buildItemAmountMap(StorageDto storage) {
            Map<String, Long> map = new HashMap<String, Long>();
            if (storage == null || storage.items == null) {
                return map;
            }
            for (ItemEntry item : storage.items) {
                if (item == null) {
                    continue;
                }
                if (item.itemId != null && !item.itemId.isEmpty()) {
                    Long prev = map.get(item.itemId);
                    map.put(item.itemId, Long.valueOf((prev != null ? prev.longValue() : 0L) + item.amount));
                }
                if (item.registryName != null && !item.registryName.isEmpty()) {
                    String regKey = item.meta > 0 ? item.registryName + ":" + item.meta : item.registryName;
                    Long prev = map.get(regKey);
                    map.put(regKey, Long.valueOf((prev != null ? prev.longValue() : 0L) + item.amount));
                    if (item.meta == 0) {
                        String withZero = item.registryName + ":0";
                        Long prev0 = map.get(withZero);
                        map.put(withZero, Long.valueOf((prev0 != null ? prev0.longValue() : 0L) + item.amount));
                    }
                }
            }
            return map;
        }

        private static long findFluidAmount(StorageDto storage, String needle) {
            if (storage.fluids == null || needle == null || needle.isEmpty()) {
                return 0L;
            }
            long total = 0L;
            for (FluidEntry fluid : storage.fluids) {
                if (fluid == null || fluid.fluidName == null) {
                    continue;
                }
                if (fluid.fluidName.toLowerCase()
                    .contains(needle) || fluid.fluidName.equalsIgnoreCase(needle)) {
                    total += fluid.amount;
                }
            }
            return total;
        }

        private static double resolveEntityValue(String entityKey, String field, StorageDto storage,
            GtMachineListDto machines) {
            if (entityKey.startsWith("cpu:")) {
                String name = entityKey.substring(4);
                CpuEntry cpu = findCpu(storage, name);
                if (cpu == null) {
                    return 0.0;
                }
                return readCpuField(cpu, field);
            }
            if (entityKey.startsWith("gt:")) {
                GtMachineDto m = findGt(machines, entityKey.substring(3));
                if (m == null) {
                    return 0.0;
                }
                return readGtField(m, field);
            }
            return 0.0;
        }

        private static CpuEntry findCpu(StorageDto storage, String name) {
            if (storage == null || storage.cpus == null || name == null) {
                return null;
            }
            for (CpuEntry cpu : storage.cpus) {
                if (cpu != null && name.equals(cpu.name)) {
                    return cpu;
                }
            }
            return null;
        }

        private static GtMachineDto findGt(GtMachineListDto machines, String coordKey) {
            if (machines == null || machines.machines == null || coordKey == null) {
                return null;
            }
            // coordKey = dim:x:y:z
            String[] parts = coordKey.split(":");
            if (parts.length != 4) {
                return null;
            }
            try {
                int dim = Integer.parseInt(parts[0]);
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                for (GtMachineDto m : machines.machines) {
                    if (m != null && m.dim == dim && m.x == x && m.y == y && m.z == z) {
                        return m;
                    }
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
            return null;
        }

        private static double readCpuField(CpuEntry cpu, String field) {
            if (field == null) {
                return cpu.craftingProgress;
            }
            if ("craftingProgress".equals(field) || "progress".equals(field)) {
                return cpu.craftingProgress;
            }
            if ("storedItems".equals(field)) {
                return (double) cpu.storedItems;
            }
            if ("maxItems".equals(field)) {
                return (double) cpu.maxItems;
            }
            if ("usedStorage".equals(field)) {
                return (double) cpu.usedStorage;
            }
            if ("availableStorage".equals(field)) {
                return (double) cpu.availableStorage;
            }
            if ("coProcessors".equals(field)) {
                return (double) cpu.coProcessors;
            }
            if ("elapsedTime".equals(field)) {
                return (double) cpu.elapsedTime;
            }
            if ("isBusy".equals(field)) {
                return cpu.isBusy ? 1.0 : 0.0;
            }
            if ("finalOutputAmount".equals(field)) {
                return (double) cpu.finalOutputAmount;
            }
            return cpu.craftingProgress;
        }

        private static double readGtField(GtMachineDto m, String field) {
            if (field == null || "progressPercent".equals(field) || "progress".equals(field)) {
                return m.progressPercent;
            }
            if ("storedEU".equals(field)) {
                return (double) m.storedEU;
            }
            if ("euCapacity".equals(field)) {
                return (double) m.euCapacity;
            }
            if ("isActive".equals(field)) {
                return m.isActive ? 1.0 : 0.0;
            }
            if ("parallelCount".equals(field)) {
                return (double) m.parallelCount;
            }
            if ("progressTime".equals(field)) {
                return (double) m.progressTime;
            }
            if ("maxProgressTime".equals(field)) {
                return (double) m.maxProgressTime;
            }
            return m.progressPercent;
        }

        public synchronized NetworkMetricFluidHistoryDto toFluidDto(int networkId) {
            NetworkMetricFluidHistoryDto dto = new NetworkMetricFluidHistoryDto();
            dto.networkId = networkId;
            for (String fluidKey : trackedFluidKeys) {
                Deque<LongSamplePoint> deque = fluidSamples.get(fluidKey);
                if (deque == null || deque.isEmpty()) {
                    continue;
                }
                NetworkMetricFluidHistoryDto.FluidSeries series = new NetworkMetricFluidHistoryDto.FluidSeries();
                for (LongSamplePoint p : deque) {
                    series.timestamps.add(Long.valueOf(p.ts));
                    series.amounts.add(Long.valueOf(p.amount));
                }
                dto.fluids.put(fluidKey, series);
            }
            return dto;
        }

        public synchronized NetworkMetricItemHistoryDto toItemDto(int networkId) {
            NetworkMetricItemHistoryDto dto = new NetworkMetricItemHistoryDto();
            dto.networkId = networkId;
            for (String itemKey : trackedItemKeys) {
                Deque<LongSamplePoint> deque = itemSamples.get(itemKey);
                if (deque == null || deque.isEmpty()) {
                    continue;
                }
                NetworkMetricItemHistoryDto.ItemSeries series = new NetworkMetricItemHistoryDto.ItemSeries();
                for (LongSamplePoint p : deque) {
                    series.timestamps.add(Long.valueOf(p.ts));
                    series.amounts.add(Long.valueOf(p.amount));
                }
                dto.items.put(itemKey, series);
            }
            return dto;
        }

        public synchronized NetworkMetricEntityHistoryDto toEntityDto(int networkId) {
            NetworkMetricEntityHistoryDto dto = new NetworkMetricEntityHistoryDto();
            dto.networkId = networkId;
            for (String entityKey : trackedEntityKeys) {
                Deque<DoubleSamplePoint> deque = entitySamples.get(entityKey);
                if (deque == null || deque.isEmpty()) {
                    continue;
                }
                NetworkMetricEntityHistoryDto.EntitySeries series = new NetworkMetricEntityHistoryDto.EntitySeries();
                series.field = entityFields.get(entityKey);
                for (DoubleSamplePoint p : deque) {
                    series.timestamps.add(Long.valueOf(p.ts));
                    series.values.add(Double.valueOf(p.value));
                }
                dto.entities.put(entityKey, series);
            }
            return dto;
        }

        public synchronized void addSample(NetworkMetricSample sample) {
            samples.addLast(sample);
            long cutoff = sample.ts - windowMs;
            while (!samples.isEmpty() && samples.peekFirst().ts < cutoff) {
                samples.pollFirst();
            }
        }

        public synchronized NetworkMetricHistoryDto toDto(int networkId) {
            NetworkMetricHistoryDto dto = new NetworkMetricHistoryDto();
            dto.networkId = networkId;
            int n = samples.size();
            List<Long> ts = new ArrayList<Long>(n);
            List<Integer> itemCount = new ArrayList<Integer>(n);
            List<Integer> fluidCount = new ArrayList<Integer>(n);
            List<Integer> essentiaCount = new ArrayList<Integer>(n);
            List<Long> bytesUsed = new ArrayList<Long>(n);
            List<Long> bytesMax = new ArrayList<Long>(n);
            List<Double> bytesPercent = new ArrayList<Double>(n);
            List<Long> itemTotal = new ArrayList<Long>(n);
            List<Long> fluidTotal = new ArrayList<Long>(n);
            List<Integer> activeCpu = new ArrayList<Integer>(n);
            List<Integer> busyCpu = new ArrayList<Integer>(n);
            List<Double> cpuBusyRatio = new ArrayList<Double>(n);
            List<Integer> gtMachineCount = new ArrayList<Integer>(n);
            List<Integer> gtActiveCount = new ArrayList<Integer>(n);
            for (NetworkMetricSample s : samples) {
                ts.add(Long.valueOf(s.ts));
                itemCount.add(Integer.valueOf(s.itemCount));
                fluidCount.add(Integer.valueOf(s.fluidCount));
                essentiaCount.add(Integer.valueOf(s.essentiaCount));
                bytesUsed.add(Long.valueOf(s.bytesUsed));
                bytesMax.add(Long.valueOf(s.bytesMax));
                bytesPercent.add(Double.valueOf(s.bytesPercent));
                itemTotal.add(Long.valueOf(s.itemTotal));
                fluidTotal.add(Long.valueOf(s.fluidTotal));
                activeCpu.add(Integer.valueOf(s.activeCpu));
                busyCpu.add(Integer.valueOf(s.busyCpu));
                cpuBusyRatio.add(Double.valueOf(s.cpuBusyRatio));
                gtMachineCount.add(Integer.valueOf(s.gtMachineCount));
                gtActiveCount.add(Integer.valueOf(s.gtActiveCount));
            }
            dto.timestamps = ts;
            dto.itemCountHistory = itemCount;
            dto.fluidCountHistory = fluidCount;
            dto.essentiaCountHistory = essentiaCount;
            dto.bytesUsedHistory = bytesUsed;
            dto.bytesMaxHistory = bytesMax;
            dto.bytesPercentHistory = bytesPercent;
            dto.itemTotalHistory = itemTotal;
            dto.fluidTotalHistory = fluidTotal;
            dto.activeCpuHistory = activeCpu;
            dto.busyCpuHistory = busyCpu;
            dto.cpuBusyRatioHistory = cpuBusyRatio;
            dto.gtMachineCountHistory = gtMachineCount;
            dto.gtActiveCountHistory = gtActiveCount;
            return dto;
        }
    }

    static final class LongSamplePoint {

        final long ts;
        final long amount;

        LongSamplePoint(long ts, long amount) {
            this.ts = ts;
            this.amount = amount;
        }
    }

    static final class DoubleSamplePoint {

        final long ts;
        final double value;

        DoubleSamplePoint(long ts, double value) {
            this.ts = ts;
            this.value = value;
        }
    }
}
