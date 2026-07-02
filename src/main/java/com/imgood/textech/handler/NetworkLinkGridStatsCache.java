package com.imgood.textech.handler;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.compat.ae.AeCompat;
import com.imgood.textech.compat.ae.AeStorageStatsAccumulator;
import com.imgood.textech.tileentity.TileEntityAdvanceNetworkLink;

import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.util.DimensionalCoord;
import appeng.me.GridAccessException;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;

/**
 * Shared per-AE-grid storage statistics for {@link TileEntityAdvanceNetworkLink}.
 * Avoids N independent full-network scans when multiple links sit on the same grid.
 */
public final class NetworkLinkGridStatsCache {

    public static final class StatsSnapshot {

        public final long itemTotalBytes;
        public final long itemUsedBytes;
        public final int itemTotalTypes;
        public final int itemUsedTypes;
        public final long fluidTotalBytes;
        public final long fluidUsedBytes;
        public final int fluidTotalTypes;
        public final int fluidUsedTypes;

        private StatsSnapshot(AeStorageStatsAccumulator stats) {
            this.itemTotalBytes = stats.itemBytes[0];
            this.itemUsedBytes = stats.itemBytes[1];
            this.itemTotalTypes = stats.itemTypes[0];
            this.itemUsedTypes = stats.itemTypes[1];
            this.fluidTotalBytes = stats.fluidBytes[0];
            this.fluidUsedBytes = stats.fluidBytes[1];
            this.fluidTotalTypes = stats.fluidTypes[0];
            this.fluidUsedTypes = stats.fluidTypes[1];
        }

        public boolean equalsValues(long itemTotalBytes, long itemUsedBytes, int itemTotalTypes, int itemUsedTypes,
            long fluidTotalBytes, long fluidUsedBytes, int fluidTotalTypes, int fluidUsedTypes) {
            return this.itemTotalBytes == itemTotalBytes && this.itemUsedBytes == itemUsedBytes
                && this.itemTotalTypes == itemTotalTypes && this.itemUsedTypes == itemUsedTypes
                && this.fluidTotalBytes == fluidTotalBytes && this.fluidUsedBytes == fluidUsedBytes
                && this.fluidTotalTypes == fluidTotalTypes && this.fluidUsedTypes == fluidUsedTypes;
        }
    }

    private static final class GridEntry {

        final WeakReference<IGrid> gridRef;
        StatsSnapshot snapshot;
        long lastComputeTick = -1L;

        GridEntry(IGrid grid) {
            this.gridRef = new WeakReference<IGrid>(grid);
        }
    }

    private static final Map<IGrid, GridEntry> ENTRIES = new IdentityHashMap<IGrid, GridEntry>();
    private static final List<WeakReference<TileEntityAdvanceNetworkLink>> PENDING_LINKS = new ArrayList<>();

    private NetworkLinkGridStatsCache() {}

    public static void invalidate(IGrid grid) {
        if (grid == null) {
            return;
        }
        synchronized (ENTRIES) {
            ENTRIES.remove(grid);
        }
    }

    public static void scheduleRefresh(TileEntityAdvanceNetworkLink link) {
        if (link == null || link.getWorldObj() == null || link.getWorldObj().isRemote) {
            return;
        }
        synchronized (PENDING_LINKS) {
            for (WeakReference<TileEntityAdvanceNetworkLink> ref : PENDING_LINKS) {
                TileEntityAdvanceNetworkLink existing = ref.get();
                if (existing == link) {
                    return;
                }
            }
            PENDING_LINKS.add(new WeakReference<TileEntityAdvanceNetworkLink>(link));
        }
        ConnectorTickService.requestNetworkLinkFlush();
    }

    static void flushPendingLinks(long worldTick) {
        List<TileEntityAdvanceNetworkLink> links = new ArrayList<>();
        synchronized (PENDING_LINKS) {
            Iterator<WeakReference<TileEntityAdvanceNetworkLink>> it = PENDING_LINKS.iterator();
            while (it.hasNext()) {
                TileEntityAdvanceNetworkLink link = it.next()
                    .get();
                it.remove();
                if (link != null && link.getWorldObj() != null && !link.getWorldObj().isRemote) {
                    links.add(link);
                }
            }
        }
        for (TileEntityAdvanceNetworkLink link : links) {
            link.refreshFromSharedCache(worldTick);
        }
    }

    public static StatsSnapshot getOrCompute(IGrid grid, World world, long worldTick) {
        if (grid == null || world == null) {
            return null;
        }
        synchronized (ENTRIES) {
            GridEntry entry = ENTRIES.get(grid);
            if (entry != null && entry.snapshot != null && entry.lastComputeTick == worldTick) {
                return entry.snapshot;
            }
            if (entry == null) {
                entry = new GridEntry(grid);
                ENTRIES.put(grid, entry);
            }
            AeStorageStatsAccumulator stats = computeStats(grid, world);
            entry.snapshot = new StatsSnapshot(stats);
            entry.lastComputeTick = worldTick;
            return entry.snapshot;
        }
    }

    private static AeStorageStatsAccumulator computeStats(IGrid grid, World world) {
        AeStorageStatsAccumulator stats = new AeStorageStatsAccumulator();
        try {
            for (Class<? extends IGridHost> clazz : grid.getMachinesClasses()) {
                if (!IChestOrDrive.class.isAssignableFrom(clazz)) {
                    continue;
                }
                for (IGridNode node : grid.getMachines(clazz)) {
                    TileEntity tile = getBaseTileEntity(node.getGridBlock()
                        .getLocation(), world);
                    if (tile instanceof TileDrive) {
                        TileDrive drive = (TileDrive) tile;
                        for (int i = 0; i < drive.getInternalInventory()
                            .getSizeInventory(); i++) {
                            net.minecraft.item.ItemStack stack = drive.getInternalInventory()
                                .getStackInSlot(i);
                            if (stack != null) {
                                AeCompat.cells()
                                    .accumulateStorageStack(stack, stats);
                            }
                        }
                    } else if (tile instanceof TileChest) {
                        TileChest chest = (TileChest) tile;
                        net.minecraft.item.ItemStack stack = chest.getInternalInventory()
                            .getStackInSlot(0);
                        if (stack != null) {
                            AeCompat.cells()
                                .accumulateStorageStack(stack, stats);
                        }
                    }
                }
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("Error computing shared network link stats: " + e.getMessage());
        }
        return stats;
    }

    private static TileEntity getBaseTileEntity(DimensionalCoord coord, World fallbackWorld) {
        if (coord == null) {
            return null;
        }
        World world = coord.getWorld();
        if (world == null) {
            world = fallbackWorld;
        }
        if (world == null) {
            return null;
        }
        return world.getTileEntity(coord.x, coord.y, coord.z);
    }

    public static IGrid resolveGrid(TileEntityAdvanceNetworkLink link) {
        if (link == null) {
            return null;
        }
        try {
            return link.getProxy()
                .getGrid();
        } catch (GridAccessException e) {
            return null;
        }
    }
}
