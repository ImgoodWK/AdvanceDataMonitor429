package com.imgood.textech.webae.pattern;

import com.imgood.textech.webae.debug.WebAeDebugLog;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridCache;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridStorage;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.events.MENetworkEventSubscribe;

/**
 * Per-AE-grid cache that listens for {@link MENetworkCraftingPatternChange} and
 * clears {@link PatternBrowseService} browse caches so WebAE pattern lists stay fresh
 * after in-game interface / crafting-grid mutations (not only Web PUT/DELETE/inject).
 */
public final class PatternBrowseInvalidationGridCache implements IGridCache {

    private final IGrid grid;

    public PatternBrowseInvalidationGridCache(IGrid grid) {
        this.grid = grid;
    }

    @MENetworkEventSubscribe
    public void onCraftingPatternChange(MENetworkCraftingPatternChange event) {
        PatternBrowseService.invalidateAll();
        if (WebAeDebugLog.isEnabled(WebAeDebugLog.Feature.PATTERNS)) {
            WebAeDebugLog.info(
                WebAeDebugLog.Feature.PATTERNS,
                "Pattern browse cache invalidated (grid={}, provider={})",
                grid != null ? Integer.toHexString(System.identityHashCode(grid)) : "null",
                event != null && event.provider != null ? event.provider.getClass()
                    .getSimpleName() : "null");
        }
    }

    @Override
    public void onUpdateTick() {
        /* no-op */
    }

    @Override
    public void removeNode(IGridNode node, IGridHost host) {
        /* no-op */
    }

    @Override
    public void addNode(IGridNode node, IGridHost host) {
        /* no-op */
    }

    @Override
    public void onSplit(IGridStorage storage) {
        PatternBrowseService.invalidateAll();
    }

    @Override
    public void onJoin(IGridStorage storage) {
        PatternBrowseService.invalidateAll();
    }

    @Override
    public void populateGridStorage(IGridStorage storage) {
        /* no-op */
    }
}
