package com.imgood.textech.webae.search;

import java.util.concurrent.ConcurrentHashMap;

import com.imgood.textech.Config;

/**
 * Per-owner rate limiter for recipe fuzzy search ({@code /api/recipes/search?q=}).
 */
public final class RecipeSearchRateLimiter {

    private static final ConcurrentHashMap<String, Long> lastRequest = new ConcurrentHashMap<String, Long>();

    private RecipeSearchRateLimiter() {}

    public static boolean tryAcquire(String ownerUuid) {
        if (ownerUuid == null || ownerUuid.isEmpty()) {
            return false;
        }
        long minInterval = Config.webRecipeSearchMinIntervalMs;
        if (minInterval <= 0) minInterval = 300L;
        long now = System.currentTimeMillis();
        Long prev = lastRequest.get(ownerUuid);
        if (prev != null && now - prev < minInterval) {
            return false;
        }
        lastRequest.put(ownerUuid, now);
        return true;
    }

    public static long remainingCooldownMs(String ownerUuid) {
        long minInterval = Config.webRecipeSearchMinIntervalMs;
        if (minInterval <= 0) minInterval = 300L;
        Long prev = lastRequest.get(ownerUuid);
        if (prev == null) {
            return 0L;
        }
        long elapsed = System.currentTimeMillis() - prev;
        return elapsed >= minInterval ? 0L : minInterval - elapsed;
    }
}
