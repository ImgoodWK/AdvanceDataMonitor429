package com.imgood.textech.webae.power;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.assistant.WirelessPowerQuery;
import com.imgood.textech.assistant.WirelessSteamQuery;
import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.webae.cache.SnapshotCache;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.dto.PowerDto;
import com.imgood.textech.webae.metric.MetricDownsampleUtil;

/**
 * Wireless power / steam sampler with sliding-window rate calculation.
 * Singleton. Hooked into HandlerTick for periodic sampling.
 *
 * Key methods:
 * - onServerTick() — called from HandlerTick (main thread) every server tick
 * - collectBlocking(playerUuid, networkId) — blocking collect for HTTP handlers with CountDownLatch
 * - getLatestSnapshot(playerUuid, networkId) — cached read from SnapshotCache
 */
public class PowerSampler {

    private static final PowerSampler INSTANCE = new PowerSampler();
    private static final long SAMPLE_INTERVAL_MS = 5_000L;
    private static final long COLLECT_TIMEOUT_MS = 10_000L;

    private final ConcurrentHashMap<String, PowerSnapshotRecord> records = new ConcurrentHashMap<String, PowerSnapshotRecord>();
    private SnapshotCache snapshotCache;
    private long lastSampleTime;

    private PowerSampler() {}

    public static PowerSampler getInstance() {
        return INSTANCE;
    }

    public void setSnapshotCache(SnapshotCache cache) {
        this.snapshotCache = cache;
    }

    /**
     * Called from HandlerTick (main thread) every server tick.
     * Periodically samples all active records.
     */
    public void onServerTick() {
        long now = System.currentTimeMillis();
        if (now - lastSampleTime < SAMPLE_INTERVAL_MS) {
            return;
        }
        lastSampleTime = now;

        for (java.util.Map.Entry<String, PowerSnapshotRecord> entry : records.entrySet()) {
            String recordKey = entry.getKey();
            PowerSnapshotRecord record = entry.getValue();
            if (now - record.lastAccessTime > 120_000L) {
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

            EntityPlayerMP player = WebAeOwnerContext.getOwnerPlayerOrFake(playerUuid);
            if (player == null) continue;

            try {
                sampleAndCache(player, playerUuid, networkId, record);
            } catch (Throwable t) {
                AdvanceDataMonitor.LOG.error("[WebAE] Power sample failed for player={}", playerUuid, t);
            }
        }
    }

    /**
     * Blocking power collection for HTTP handlers.
     * Enqueues on the server thread via HandlerTick and waits with timeout.
     */
    public PowerDto collectBlocking(String playerUuid, int networkId, long timeoutMs) {
        final PowerDto[] holder = new PowerDto[1];
        final CountDownLatch latch = new CountDownLatch(1);

        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                try {
                    EntityPlayerMP player = WebAeOwnerContext.getOwnerPlayerOrFake(playerUuid);
                    if (player == null) {
                        holder[0] = null;
                        return;
                    }

                    String recordKey = playerUuid + ":" + networkId;
                    PowerSnapshotRecord record = records.get(recordKey);
                    if (record == null) {
                        record = new PowerSnapshotRecord((long) Config.webPowerSampleWindowSeconds * 1000L);
                        records.put(recordKey, record);
                    }
                    record.lastAccessTime = System.currentTimeMillis();

                    sampleAndCache(player, playerUuid, networkId, record);
                    holder[0] = record.toDto(networkId);
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG.error("[WebAE] Power collection failed", t);
                    holder[0] = null;
                } finally {
                    latch.countDown();
                }
            }
        });

        try {
            if (latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                return holder[0];
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
        }
        AdvanceDataMonitor.LOG.warn("[WebAE] Power collection timed out player={} network={}", playerUuid, networkId);
        return null;
    }

    /**
     * Default-timeout convenience overload.
     */
    public PowerDto collectBlocking(String playerUuid, int networkId) {
        return collectBlocking(playerUuid, networkId, COLLECT_TIMEOUT_MS);
    }

    /**
     * Get latest cached snapshot from SnapshotCache.
     */
    @SuppressWarnings("unchecked")
    public PowerDto getLatestSnapshot(String playerUuid, int networkId) {
        if (snapshotCache == null) return null;
        return snapshotCache.get(playerUuid, networkId, "power");
    }

    /**
     * Merge the given player:network into the active records for periodic sampling.
     */
    public void markActive(String playerUuid, int networkId) {
        String key = playerUuid + ":" + networkId;
        PowerSnapshotRecord record = records.get(key);
        if (record == null) {
            record = new PowerSnapshotRecord((long) Config.webPowerSampleWindowSeconds * 1000L);
            records.put(key, record);
        }
        record.lastAccessTime = System.currentTimeMillis();
    }

    // ---- internal ----

    private void sampleAndCache(EntityPlayerMP player, String playerUuid, int networkId, PowerSnapshotRecord record) {
        BigInteger eu = WirelessPowerQuery.queryEuStored(player);
        BigInteger euMax = WirelessPowerQuery.queryEuMaxCapacity(player);
        BigInteger steam = WirelessSteamQuery.querySteamStored(player);

        long euVal = eu != null ? eu.longValue() : -1L;
        long euMaxVal = euMax != null ? euMax.longValue() : -1L;
        long steamVal = steam != null ? steam.longValue() : -1L;
        if (steam != null) {
            record.steamSupported = true;
        }

        if (euVal >= 0 || steamVal >= 0) {
            record.addSample(
                euVal >= 0 ? euVal : record.lastEuValue,
                steamVal >= 0 ? steamVal : record.lastSteamValue,
                euMaxVal);
        }

        PowerDto dto = record.toDto(networkId);
        if (snapshotCache != null) {
            snapshotCache.put(playerUuid, networkId, "power", dto);
        }
    }

    // ---- inner types ----

    /**
     * A single sampling point in the sliding window.
     */
    public static class PowerSamplePoint {

        public final long timestamp;
        public final long euValue;
        public final long steamValue;

        public PowerSamplePoint(long timestamp, long euValue, long steamValue) {
            this.timestamp = timestamp;
            this.euValue = euValue;
            this.steamValue = steamValue;
        }
    }

    /**
     * Per-player-network record with sliding window of samples.
     */
    public static class PowerSnapshotRecord {

        private final Deque<PowerSamplePoint> samples = new ConcurrentLinkedDeque<PowerSamplePoint>();
        private final long windowMs;
        volatile long lastAccessTime;
        volatile long lastEuValue;
        volatile long lastSteamValue;
        volatile long lastEuMax = -1L;
        volatile boolean steamSupported;

        public PowerSnapshotRecord(long windowMs) {
            this.windowMs = windowMs;
            this.lastAccessTime = System.currentTimeMillis();
        }

        public void addSample(long euValue, long steamValue, long euMax) {
            long now = System.currentTimeMillis();
            samples.addLast(new PowerSamplePoint(now, euValue, steamValue));
            lastEuValue = euValue;
            lastSteamValue = steamValue;
            if (euMax >= 0) {
                lastEuMax = euMax;
            }

            long cutoff = now - windowMs;
            while (!samples.isEmpty() && samples.getFirst().timestamp < cutoff) {
                samples.removeFirst();
            }
        }

        public PowerDto toDto(int networkId) {
            PowerDto dto = new PowerDto();
            dto.networkId = networkId;
            dto.timestamp = System.currentTimeMillis();

            PowerSamplePoint latest = samples.peekLast();
            PowerSamplePoint oldest = samples.peekFirst();

            dto.euStored = latest != null ? latest.euValue : 0L;
            dto.euMax = lastEuMax >= 0 ? lastEuMax : 0L;
            dto.steamStored = latest != null ? latest.steamValue : 0L;
            dto.steamMax = 0L;
            dto.steamSupported = steamSupported;
            // SNL 0.2.5 exposes stored steam but no authoritative team capacity.
            dto.steamCapacityKnown = false;

            if (latest != null && oldest != null && latest.timestamp > oldest.timestamp) {
                double deltaSeconds = (latest.timestamp - oldest.timestamp) / 1000.0;
                if (deltaSeconds > 0) {
                    double euNetRate = (latest.euValue - oldest.euValue) / deltaSeconds / 20.0;
                    double steamNetRate = (latest.steamValue - oldest.steamValue) / deltaSeconds / 20.0;
                    if (euNetRate >= 0) {
                        dto.euInRate = euNetRate;
                        dto.euOutRate = 0.0;
                    } else {
                        dto.euInRate = 0.0;
                        dto.euOutRate = -euNetRate;
                    }
                    if (steamNetRate >= 0) {
                        dto.steamInRate = steamNetRate;
                        dto.steamOutRate = 0.0;
                    } else {
                        dto.steamInRate = 0.0;
                        dto.steamOutRate = -steamNetRate;
                    }
                }
            }

            List<Double> euHist = new ArrayList<Double>();
            List<Double> steamHist = new ArrayList<Double>();
            List<Long> euTs = new ArrayList<Long>();
            List<Long> steamTs = new ArrayList<Long>();
            for (PowerSamplePoint sp : samples) {
                euHist.add((double) sp.euValue);
                steamHist.add((double) sp.steamValue);
                euTs.add(sp.timestamp);
                steamTs.add(sp.timestamp);
            }
            if (euHist.size() > MetricDownsampleUtil.DEFAULT_MAX_POINTS) {
                euTs = MetricDownsampleUtil.downsampleTimestamps(euTs, MetricDownsampleUtil.DEFAULT_MAX_POINTS);
                euHist = MetricDownsampleUtil.downsampleValuesMax(euHist, MetricDownsampleUtil.DEFAULT_MAX_POINTS);
                steamHist = MetricDownsampleUtil
                    .downsampleValuesMax(steamHist, MetricDownsampleUtil.DEFAULT_MAX_POINTS);
            }
            dto.euHistory = euHist;
            dto.steamHistory = steamHist;
            dto.euHistoryTimestamps = euTs;
            dto.steamHistoryTimestamps = steamTs;

            return dto;
        }
    }
}
