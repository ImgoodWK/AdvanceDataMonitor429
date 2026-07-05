package com.imgood.textech.webae.topology;

import com.imgood.textech.AdvanceDataMonitor;

import appeng.api.AEApi;

/**
 * Registers {@link TopologyInvalidationGridCache} on every AE grid.
 */
public final class TopologyGridCacheRegistrar {

    private static boolean registered;

    private TopologyGridCacheRegistrar() {}

    public static void register() {
        if (registered) {
            return;
        }
        try {
            AEApi.instance()
                .registries()
                .gridCache()
                .registerGridCache(TopologyInvalidationGridCache.class, TopologyInvalidationGridCache.class);
            registered = true;
            AdvanceDataMonitor.LOG.info("[WebAE] Registered topology grid cache invalidator.");
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG
                .warn("[WebAE] Failed to register topology grid cache invalidator: {}", t.getMessage());
        }
    }
}
