package com.imgood.textech.items.cell;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.me.storage.MEInventoryHandler;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEFluidStackType;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;
import appeng.util.prioitylist.FuzzyPriorityList;
import appeng.util.prioitylist.PrecisePriorityList;

/**
 * ME wrapper for data loom cells. Applies Cell Workbench partition to inject routing only;
 * generated contents remain readable/extractable across the network.
 */
public final class DataLoomCellInventoryHandler extends MEInventoryHandler {

    private final ItemStack cellStack;

    private DataLoomCellInventoryHandler(IMEInventoryHandler internal, ItemStack cellStack, IAEStackType stackType) {
        super(internal, stackType);
        this.cellStack = cellStack;
        applyPartitionFromConfig();
    }

    public IMEInventoryHandler getDelegate() {
        return (IMEInventoryHandler) getInternal();
    }

    public static IMEInventoryHandler wrap(IMEInventoryHandler internal, ItemStack cellStack, IAEStackType stackType) {
        if (internal == null || cellStack == null || stackType == null) {
            return internal;
        }
        return new DataLoomCellInventoryHandler(internal, cellStack, stackType);
    }

    private void applyPartitionFromConfig() {
        IAEStackInventory config = null;
        FuzzyMode fuzzyMode = FuzzyMode.IGNORE_ALL;

        if (cellStack.getItem() instanceof AbstractDataLoomItemCell) {
            AbstractDataLoomItemCell itemCell = (AbstractDataLoomItemCell) cellStack.getItem();
            config = itemCell.getConfigAEInventory(cellStack);
            fuzzyMode = itemCell.getFuzzyMode(cellStack);
        } else if (cellStack.getItem() instanceof AbstractDataLoomFluidCell) {
            AbstractDataLoomFluidCell fluidCell = (AbstractDataLoomFluidCell) cellStack.getItem();
            config = fluidCell.getConfigAEInventory(cellStack);
            fuzzyMode = fluidCell.getFuzzyMode(cellStack);
        }

        if (config == null) {
            return;
        }

        boolean hasInverter = false;
        boolean hasFuzzy = false;
        IInventory upgrades = null;
        if (cellStack.getItem() instanceof appeng.api.storage.ICellWorkbenchItem) {
            upgrades = ((appeng.api.storage.ICellWorkbenchItem) cellStack.getItem()).getUpgradesInventory(cellStack);
        }

        if (upgrades != null) {
            for (int slot = 0; slot < upgrades.getSizeInventory(); slot++) {
                ItemStack upgradeStack = upgrades.getStackInSlot(slot);
                if (upgradeStack != null && upgradeStack.getItem() instanceof IUpgradeModule) {
                    Upgrades upgrade = ((IUpgradeModule) upgradeStack.getItem()).getType(upgradeStack);
                    if (upgrade == Upgrades.FUZZY) {
                        hasFuzzy = true;
                    } else if (upgrade == Upgrades.INVERTER) {
                        hasInverter = true;
                    }
                }
            }
        }

        setWhitelist(hasInverter ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST);

        IAEStackType type = getStackType();
        if (type == AEItemStackType.ITEM_STACK_TYPE) {
            appeng.api.storage.data.IItemList priorityList = AEApi.instance()
                .storage()
                .createItemList();
            for (int slot = 0; slot < config.getSizeInventory(); slot++) {
                IAEStack aes = config.getAEStackInSlot(slot);
                if (aes instanceof IAEItemStack) {
                    priorityList.add(((IAEItemStack) aes).copy());
                }
            }
            if (!priorityList.isEmpty()) {
                if (hasFuzzy) {
                    setPartitionList(new FuzzyPriorityList(priorityList, fuzzyMode));
                } else {
                    setPartitionList(new PrecisePriorityList(priorityList));
                }
            }
        } else if (type == AEFluidStackType.FLUID_STACK_TYPE) {
            appeng.api.storage.data.IItemList priorityList = AEApi.instance()
                .storage()
                .createFluidList();
            for (int slot = 0; slot < config.getSizeInventory(); slot++) {
                IAEStack aes = config.getAEStackInSlot(slot);
                if (aes instanceof IAEFluidStack) {
                    priorityList.add(((IAEFluidStack) aes).copy());
                    continue;
                }
                if (aes instanceof IAEItemStack) {
                    ItemStack marker = ((IAEItemStack) aes).getItemStack();
                    if (marker == null) {
                        continue;
                    }
                    net.minecraftforge.fluids.FluidStack fluid = DataLoomCellUtil.resolveMarkerFluid(marker);
                    if (fluid != null) {
                        priorityList.add(AEFluidStack.create(fluid));
                    }
                }
            }
            if (!priorityList.isEmpty()) {
                if (hasFuzzy) {
                    setPartitionList(new FuzzyPriorityList(priorityList, fuzzyMode));
                } else {
                    setPartitionList(new PrecisePriorityList(priorityList));
                }
            }
        }
    }

    @Override
    public IAEStack injectItems(IAEStack input, appeng.api.config.Actionable mode,
        appeng.api.networking.security.BaseActionSource src) {
        return super.injectItems(input, mode, src);
    }
}
