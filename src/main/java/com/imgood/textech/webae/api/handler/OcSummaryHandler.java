package com.imgood.textech.webae.api.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.webae.cache.SnapshotCache;
import com.imgood.textech.webae.dto.StorageDto;
import com.imgood.textech.webae.health.ServerHealthSampler;
import com.imgood.textech.webae.oc.OcSummaryRateLimiter;

import fi.iki.elonen.NanoHTTPD;

/**
 * GET /api/oc/summary — compact read-only snapshot for OpenComputers Internet Card polling.
 */
public final class OcSummaryHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private OcSummaryHandler() {}

    public static NanoHTTPD.Response handle(String ownerUuid) {
        if (!OcSummaryRateLimiter.tryAcquire(ownerUuid)) {
            long wait = OcSummaryRateLimiter.remainingCooldownMs(ownerUuid);
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.TOO_MANY_REQUESTS,
                "application/json",
                "{\"success\":false,\"code\":\"rate_limited\",\"retryAfterMs\":" + wait + "}");
        }
        SummaryJson json = new SummaryJson();
        json.success = true;
        json.storageItemCount = countStorageItemTypes(ownerUuid);
        json.cpuBusy = isAnyCpuBusy(ownerUuid);
        json.activeOrders = OrderHandler.countActiveOrdersForOwner(ownerUuid);
        ServerHealthSampler.HealthSnapshot health = ServerHealthSampler.instance()
            .snapshot();
        json.tps = Math.round(health.tps * 10.0) / 10.0;
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", GSON.toJson(json));
    }

    private static int countStorageItemTypes(final String ownerUuid) {
        final int[] total = new int[] { 0 };
        SnapshotCache.instance()
            .forEachStorageSnapshotForOwner(ownerUuid, new SnapshotCache.StorageSnapshotConsumer() {

                @Override
                public void accept(StorageDto dto) {
                    if (dto != null && dto.items != null) {
                        total[0] += dto.items.size();
                    }
                }
            });
        return total[0];
    }

    private static boolean isAnyCpuBusy(final String ownerUuid) {
        final boolean[] busy = new boolean[] { false };
        SnapshotCache.instance()
            .forEachStorageSnapshotForOwner(ownerUuid, new SnapshotCache.StorageSnapshotConsumer() {

                @Override
                public void accept(StorageDto dto) {
                    if (busy[0] || dto == null || dto.cpus == null) {
                        return;
                    }
                    for (StorageDto.CpuEntry cpu : dto.cpus) {
                        if (cpu != null && cpu.isBusy) {
                            busy[0] = true;
                            return;
                        }
                    }
                }
            });
        return busy[0];
    }

    private static final class SummaryJson {

        boolean success;
        int storageItemCount;
        boolean cpuBusy;
        int activeOrders;
        double tps;
    }
}
