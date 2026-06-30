package com.imgood.textech.items.cell;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;

/**
 * Best-effort AE network notification after background weaving. Does not inject into cells or alter AE handlers.
 */
public final class DataLoomGridNotify {

    private DataLoomGridNotify() {}

    public static void publishItemDelta(TileEntity host, DataLoomCellStorage.ItemAccumState before,
        DataLoomCellStorage.ItemAccumState after) {
        IItemList changes = AEApi.instance()
            .storage()
            .createItemList();
        boolean changed = false;

        for (DataLoomCellStorage.StoredItem stored : after.items.values()) {
            if (stored.stack == null || stored.amount <= 0L) {
                continue;
            }
            long previous = findItemAmount(before, stored.stack);
            long delta = stored.amount - previous;
            if (delta <= 0L) {
                continue;
            }
            IAEItemStack added = AEItemStack.create(stored.stack);
            added.setStackSize(delta);
            changes.add(added);
            changed = true;
        }

        if (!changed) {
            return;
        }
        postItems(host, changes);
    }

    public static void publishFluidDelta(TileEntity host, DataLoomCellStorage.FluidAccumState before,
        DataLoomCellStorage.FluidAccumState after) {
        IItemList changes = AEApi.instance()
            .storage()
            .createFluidList();
        boolean changed = false;

        for (DataLoomCellStorage.StoredFluid stored : after.fluids.values()) {
            if (stored.fluid == null || stored.amountMb <= 0L) {
                continue;
            }
            long previous = findFluidAmount(before, stored.fluid);
            long delta = stored.amountMb - previous;
            if (delta <= 0L) {
                continue;
            }
            IAEFluidStack added = AEFluidStack.create(stored.fluid);
            added.setStackSize(delta);
            changes.add(added);
            changed = true;
        }

        if (!changed) {
            return;
        }
        postFluids(host, changes);
    }

    private static long findItemAmount(DataLoomCellStorage.ItemAccumState state, ItemStack stack) {
        DataLoomCellStorage.StoredItem stored = state.items.get(DataLoomCellStorage.itemKey(stack));
        return stored == null ? 0L : stored.amount;
    }

    private static long findFluidAmount(DataLoomCellStorage.FluidAccumState state,
        net.minecraftforge.fluids.FluidStack fluid) {
        DataLoomCellStorage.StoredFluid stored = state.fluids.get(DataLoomCellStorage.fluidKey(fluid));
        return stored == null ? 0L : stored.amountMb;
    }

    private static void postItems(TileEntity host, IItemList changes) {
        IStorageGrid storageGrid = resolveStorageGrid(host);
        MachineSource source = actionSource(host);
        if (storageGrid == null || source == null) {
            return;
        }
        storageGrid.postAlterationOfStoredItems(StorageChannel.ITEMS, changes, source);
    }

    private static void postFluids(TileEntity host, IItemList changes) {
        IStorageGrid storageGrid = resolveStorageGrid(host);
        MachineSource source = actionSource(host);
        if (storageGrid == null || source == null) {
            return;
        }
        storageGrid.postAlterationOfStoredItems(StorageChannel.FLUIDS, changes, source);
    }

    private static IStorageGrid resolveStorageGrid(TileEntity host) {
        if (!(host instanceof IGridHost)) {
            return null;
        }
        try {
            IGridNode node = ((IGridHost) host).getGridNode(net.minecraftforge.common.util.ForgeDirection.UNKNOWN);
            if (node == null) {
                return null;
            }
            IGrid grid = node.getGrid();
            if (grid == null) {
                return null;
            }
            return grid.getCache(IStorageGrid.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static MachineSource actionSource(TileEntity host) {
        if (host instanceof IActionHost) {
            return new MachineSource((IActionHost) host);
        }
        return null;
    }
}
