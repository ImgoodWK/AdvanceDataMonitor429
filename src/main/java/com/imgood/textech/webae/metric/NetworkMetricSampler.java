package com.imgood.textech.webae.metric;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.cache.SnapshotCache;
import com.imgood.textech.webae.cache.SnapshotScheduler;
import com.imgood.textech.webae.dto.GtMachineDto;
import com.imgood.textech.webae.dto.GtMachineListDto;
import com.imgood.textech.webae.dto.NetworkMetricHistoryDto;
import com.imgood.textech.webae.dto.StorageDto;

/**
 * Network-wide scalar metric sampler for the WebAE dashboard trend charts.
 *
 * <p>
 * Singleton modeled after {@link com.imgood.textech.webae.power.PowerSampler} and
 * {@link com.imgood.textech.webae.player.PlayerOnlineSampler}. Hooked into
 * {@link com.imgood.textech.handler.HandlerTick#onServerTick} (called after
 * {@code PowerSampler}).
 * </p>
 *
 * <p>
 * Unlike {@code PowerSampler}, this sampler does <em>not</em> query AE directly.
 * Instead it reads already-collected {@link StorageDto} / {@link GtMachineListDto}
 * snapshots from {@link SnapshotCache} (kept fresh by {@code SnapshotScheduler}) and
 * extracts scalar metrics (item/fluid/essentia counts, bytes usage, CPU busy ratio,
 * GT machine active count) into a sliding window per (player, network).
 * </p>
 *
 * <p>
 * Sampling cadence: {@link Config#webMetricSampleIntervalMs} (default 10s).
 * Rolling window: {@link Config#webMetricSampleWindowSeconds} (default 300s, 60–3600s).
 * Idle cleanup: records untouched for 120s are evicted (same policy as PowerSampler).
 * </p>
 *
 * <p>
 * Thread safety: sampling runs on the server thread; HTTP reads run on worker
 * threads. Per-record {@link Deque} is wrapped with synchronized blocks; the
 * records map is a {@link ConcurrentHashMap}.
 * </p>
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

    /**
     * Called from HandlerTick (main thread) every server tick.
     * Periodically samples all active records from the snapshot cache.
     */
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

    /**
     * Read the latest cached storage + GT snapshots and append a scalar sample.
     * Missing snapshots are skipped (no point recorded) to keep trend lines honest.
     */
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
    }

    /**
     * Mark a (player, network) as actively viewed so the sampler keeps sampling it.
     */
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

    /**
     * @return history for the given (player, network), or {@code null} if no record yet.
     *         The returned DTO is a snapshot copy safe to serialize on a worker thread.
     */
    public NetworkMetricHistoryDto getHistory(String playerUuid, int networkId) {
        String key = playerUuid + ":" + networkId;
        NetworkMetricRecord record = records.get(key);
        if (record == null) return null;
        record.lastAccessTime = System.currentTimeMillis();
        return record.toDto(networkId);
    }

    // ---- inner types ----

    /**
     * A single scalar sampling point for a network.
     */
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

    /**
     * Per-(player, network) sliding window of scalar samples.
     */
    public static final class NetworkMetricRecord {

        private final Deque<NetworkMetricSample> samples = new ArrayDeque<NetworkMetricSample>();
        private final long windowMs;
        private volatile long lastAccessTime;

        public NetworkMetricRecord(long windowMs) {
            this.windowMs = windowMs;
            this.lastAccessTime = System.currentTimeMillis();
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
                ts.add(s.ts);
                itemCount.add(s.itemCount);
                fluidCount.add(s.fluidCount);
                essentiaCount.add(s.essentiaCount);
                bytesUsed.add(s.bytesUsed);
                bytesMax.add(s.bytesMax);
                bytesPercent.add(s.bytesPercent);
                itemTotal.add(s.itemTotal);
                fluidTotal.add(s.fluidTotal);
                activeCpu.add(s.activeCpu);
                busyCpu.add(s.busyCpu);
                cpuBusyRatio.add(s.cpuBusyRatio);
                gtMachineCount.add(s.gtMachineCount);
                gtActiveCount.add(s.gtActiveCount);
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
}
