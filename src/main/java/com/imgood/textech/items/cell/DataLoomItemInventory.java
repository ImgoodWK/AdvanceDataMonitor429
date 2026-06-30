package com.imgood.textech.items.cell;

import net.minecraft.item.ItemStack;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;

/**
 * AE-facing read/write adapter. Weaving is handled exclusively by {@link DataLoomWeaveEngine}.
 */
public class DataLoomItemInventory implements IMEInventoryHandler {

    private final ItemStack cellStack;
    private final ISaveProvider saveProvider;
    private final AbstractDataLoomItemCell cellItem;
    private final int hostSlot;

    public DataLoomItemInventory(ItemStack cellStack, ISaveProvider saveProvider, AbstractDataLoomItemCell cellItem) {
        this.cellStack = cellStack;
        this.saveProvider = saveProvider;
        this.cellItem = cellItem;
        DataLoomCellUtil.ensureInstanceId(cellStack);
        this.hostSlot = DataLoomCellUtil.resolveHostSlot(cellStack, saveProvider);
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public StorageChannel getChannel() {
        return StorageChannel.ITEMS;
    }

    public int getCellStatus() {
        ItemStack live = resolveLiveStack();
        DataLoomCellStorage.ItemAccumState state = DataLoomCellStorage.readItemState(live);
        if (DataLoomCellUtil.resolveMarkedItems(live)
            .isEmpty()) {
            return 0;
        }
        return DataLoomCellCapacity.getItemCellStatus(state.storedTypes, state.storedCount);
    }

    @Override
    public IAEStack injectItems(IAEStack input, Actionable mode, BaseActionSource src) {
        return input;
    }

    @Override
    public IAEStack extractItems(IAEStack request, Actionable mode, BaseActionSource src) {
        if (!(request instanceof IAEItemStack)) {
            return null;
        }
        ItemStack live = resolveLiveStack();
        if (mode != Actionable.MODULATE) {
            IAEItemStack simulated = DataLoomCellStorage.findAvailableItem(live, (IAEItemStack) request);
            if (simulated == null) {
                return null;
            }
            long toExtract = Math.min(simulated.getStackSize(), request.getStackSize());
            if (toExtract <= 0L) {
                return null;
            }
            IAEItemStack result = simulated.copy();
            result.setStackSize(toExtract);
            return result;
        }

        IAEItemStack extracted = DataLoomCellStorage.extractItem(live, (IAEItemStack) request);
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
        DataLoomCellStorage.addAvailableItems(resolveLiveStack(), out);
        return out;
    }

    private ItemStack resolveLiveStack() {
        return DataLoomCellUtil.resolveLiveCellStack(cellStack, saveProvider, hostSlot);
    }

    @Override
    public IAEStack getAvailableItem(IAEStack request, int iteration) {
        if (!(request instanceof IAEItemStack)) {
            return null;
        }
        return DataLoomCellStorage.findAvailableItem(resolveLiveStack(), (IAEItemStack) request);
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
