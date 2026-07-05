package com.imgood.textech.webae.search;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-owner rate limiter for aggregated Web search (no secrets logged).
 */
public final class WebSearchRateLimiter {

    private static final long MIN_INTERVAL_MS = 500L;
    private static final ConcurrentHashMap<String, Long> lastRequest = new ConcurrentHashMap<String, Long>();

    private WebSearchRateLimiter() {}

    public static boolean tryAcquire(String ownerUuid) {
        if (ownerUuid == null || ownerUuid.isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long prev = lastRequest.get(ownerUuid);
        if (prev != null && now - prev < MIN_INTERVAL_MS) {
            return false;
        }
        lastRequest.put(ownerUuid, now);
        return true;
    }

    public static long remainingCooldownMs(String ownerUuid) {
        Long prev = lastRequest.get(ownerUuid);
        if (prev == null) {
            return 0L;
        }
        long elapsed = System.currentTimeMillis() - prev;
        return elapsed >= MIN_INTERVAL_MS ? 0L : MIN_INTERVAL_MS - elapsed;
    }
}
