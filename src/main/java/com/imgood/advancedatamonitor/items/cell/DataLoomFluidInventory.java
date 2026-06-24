package com.imgood.advancedatamonitor.items.cell;

import net.minecraft.item.ItemStack;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;

/**
 * AE-facing read/write adapter. Weaving is handled exclusively by {@link DataLoomWeaveEngine}.
 */
public class DataLoomFluidInventory implements IMEInventoryHandler {

    private final ItemStack cellStack;
    private final ISaveProvider saveProvider;
    private final AbstractDataLoomFluidCell cellItem;
    private final int hostSlot;

    public DataLoomFluidInventory(ItemStack cellStack, ISaveProvider saveProvider) {
        this.cellStack = cellStack;
        this.saveProvider = saveProvider;
        this.cellItem = (AbstractDataLoomFluidCell) cellStack.getItem();
        DataLoomCellUtil.ensureInstanceId(cellStack);
        this.hostSlot = DataLoomCellUtil.resolveHostSlot(cellStack, saveProvider);
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public StorageChannel getChannel() {
        return StorageChannel.FLUIDS;
    }

    public int getCellStatus() {
        ItemStack live = resolveLiveStack();
        DataLoomCellStorage.FluidAccumState state = DataLoomCellStorage.readFluidState(live);
        if (DataLoomCellUtil.resolveMarkedFluids(live)
            .isEmpty()) {
            return 0;
        }
        return DataLoomCellCapacity.getFluidCellStatus(state.storedTypes, state.storedMb, cellItem.getMaxFluidTypes());
    }

    @Override
    public IAEStack injectItems(IAEStack input, Actionable mode, BaseActionSource src) {
        return input;
    }

    @Override
    public IAEStack extractItems(IAEStack request, Actionable mode, BaseActionSource src) {
        if (!(request instanceof IAEFluidStack)) {
            return null;
        }
        ItemStack live = resolveLiveStack();
        if (mode != Actionable.MODULATE) {
            IAEFluidStack simulated = DataLoomCellStorage.findAvailableFluid(live, (IAEFluidStack) request);
            if (simulated == null) {
                return null;
            }
            long toExtract = Math.min(simulated.getStackSize(), request.getStackSize());
            if (toExtract <= 0L) {
                return null;
            }
            IAEFluidStack result = simulated.copy();
            result.setStackSize(toExtract);
            return result;
        }

        IAEFluidStack extracted = DataLoomCellStorage.extractFluid(live, (IAEFluidStack) request);
        if (extracted != null) {
            persistHost();
        }
        return extracted;
    }

    @Override
    public IItemList getAvailableItems(IItemList out) {
        return getAvailableItems(out, 0);
    }

    @Override
    public IItemList getAvailableItems(IItemList out, int iteration) {
        DataLoomCellStorage.addAvailableFluids(resolveLiveStack(), out);
        return out;
    }

    private ItemStack resolveLiveStack() {
        return DataLoomCellUtil.resolveLiveCellStack(cellStack, saveProvider, hostSlot);
    }

    @Override
    public IAEStack getAvailableItem(IAEStack request, int iteration) {
        if (!(request instanceof IAEFluidStack)) {
            return null;
        }
        return DataLoomCellStorage.findAvailableFluid(resolveLiveStack(), (IAEFluidStack) request);
    }

    private void persistHost() {
        if (saveProvider != null) {
            saveProvider.saveChanges(this);
            if (saveProvider instanceof net.minecraft.tileentity.TileEntity) {
                ((net.minecraft.tileentity.TileEntity) saveProvider).markDirty();
            }
        }
    }

    @Override
    public boolean isPrioritized(IAEStack input) {
        return false;
    }

    @Override
    public boolean canAccept(IAEStack input) {
        return false;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public int getSlot() {
        return hostSlot >= 0 ? hostSlot : 0;
    }

    @Override
    public boolean validForPass(int pass) {
        return true;
    }
}
