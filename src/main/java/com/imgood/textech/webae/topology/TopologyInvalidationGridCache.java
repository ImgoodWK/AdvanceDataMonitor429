package com.imgood.textech.webae.topology;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridCache;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridStorage;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkStorageEvent;

/**
 * Per-AE-grid cache listener. Topology snapshots are manual-only; this cache intentionally
 * does not invalidate stored snapshots on network mutations.
 */
public final class TopologyInvalidationGridCache implements IGridCache {

    private final IGrid grid;

    public TopologyInvalidationGridCache(IGrid grid) {
        this.grid = grid;
    }

    @MENetworkEventSubscribe
    public void onCellUpdate(MENetworkCellArrayUpdate event) {
        /* manual snapshot only */
    }

    @MENetworkEventSubscribe
    public void onCraftingPatternChange(MENetworkCraftingPatternChange event) {
        /* manual snapshot only */
    }

    @MENetworkEventSubscribe
    public void onStorageEvent(MENetworkStorageEvent event) {
        /* manual snapshot only */
    }

    @Override
    public void onUpdateTick() {
        /* no-op */
    }

    @Override
    public void removeNode(IGridNode node, IGridHost host) {
        /* manual snapshot only */
    }

    @Override
    public void addNode(IGridNode node, IGridHost host) {
        /* manual snapshot only */
    }

    @Override
    public void onSplit(IGridStorage storage) {
        /* manual snapshot only */
    }

    @Override
    public void onJoin(IGridStorage storage) {
        /* manual snapshot only */
    }

    @Override
    public void populateGridStorage(IGridStorage storage) {
        /* no-op */
    }
}
