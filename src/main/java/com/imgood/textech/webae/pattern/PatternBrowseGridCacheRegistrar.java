package com.imgood.textech.webae.pattern;

import com.imgood.textech.AdvanceDataMonitor;

import appeng.api.AEApi;

/**
 * Registers {@link PatternBrowseInvalidationGridCache} on every AE grid so
 * {@code MENetworkCraftingPatternChange} invalidates browse TTL caches.
 */
public final class PatternBrowseGridCacheRegistrar {

    private static boolean registered;

    private PatternBrowseGridCacheRegistrar() {}

    /** Idempotent; safe to call from postInit after AE2 registries are ready. */
    public static void register() {
        if (registered) {
            return;
        }
        try {
            AEApi.instance()
                .registries()
                .gridCache()
                .registerGridCache(PatternBrowseInvalidationGridCache.class, PatternBrowseInvalidationGridCache.class);
            registered = true;
            AdvanceDataMonitor.LOG.info("[WebAE] Registered pattern browse grid cache invalidator.");
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG
                .warn("[WebAE] Failed to register pattern browse grid cache invalidator: {}", t.getMessage());
        }
    }
}
