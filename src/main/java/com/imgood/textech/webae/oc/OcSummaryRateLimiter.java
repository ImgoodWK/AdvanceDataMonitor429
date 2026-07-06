package com.imgood.textech.webae.oc;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-owner rate limiter for OC read-only summary API (1 request per second).
 */
public final class OcSummaryRateLimiter {

    private static final long MIN_INTERVAL_MS = 1000L;
    private static final ConcurrentHashMap<String, Long> LAST_REQUEST = new ConcurrentHashMap<String, Long>();

    private OcSummaryRateLimiter() {}

    public static boolean tryAcquire(String ownerUuid) {
        if (ownerUuid == null || ownerUuid.isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long prev = LAST_REQUEST.get(ownerUuid);
        if (prev != null && now - prev < MIN_INTERVAL_MS) {
            return false;
        }
        LAST_REQUEST.put(ownerUuid, now);
        return true;
    }

    public static long remainingCooldownMs(String ownerUuid) {
        Long prev = LAST_REQUEST.get(ownerUuid);
        if (prev == null) {
            return 0L;
        }
        long elapsed = System.currentTimeMillis() - prev;
        return elapsed >= MIN_INTERVAL_MS ? 0L : MIN_INTERVAL_MS - elapsed;
    }
}
