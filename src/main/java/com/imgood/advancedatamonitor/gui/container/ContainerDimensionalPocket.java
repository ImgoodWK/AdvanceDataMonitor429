package com.imgood.advancedatamonitor.gui.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.imgood.advancedatamonitor.handler.PocketState;
import com.imgood.advancedatamonitor.handler.PocketStore;
import com.imgood.advancedatamonitor.items.ItemPageUpgradeCard;
import com.imgood.advancedatamonitor.items.ItemSpaceUpgradeCard;

/**
 * Container for the Dimensional Pocket config GUI. Holds the player inventory
 * plus two upgrade slots (space upgrade card, page upgrade card). The upgrade
 * slots are backed by a small virtual IInventory whose stack sizes mirror the
 * player-bound PocketState upgrade counts. On container close, the upgrade
 * counts are committed back to PocketState via PacketPocketAction semantics
 * (the slot setStack callbacks already mutated PocketStore directly here on
 * the server side).
 */
public class ContainerDimensionalPocket extends Container {

    public static final int SPACE_UPGRADE_SLOT = 0;
    public static final int PAGE_UPGRADE_SLOT = 1;
    private static final int UPGRADE_SLOTS_COUNT = 2;

    private final EntityPlayer player;
    private final UpgradeSlotInventory upgradeInventory;

    public ContainerDimensionalPocket(EntityPlayer player) {
        this.player = player;
        this.upgradeInventory = new UpgradeSlotInventory(player);

        // Space upgrade slot at top-left, page upgrade slot next to it.
        addSlotToContainer(new Slot(upgradeInventory, SPACE_UPGRADE_SLOT, 18, 22) {

            @Override
            public boolean isItemValid(ItemStack stack) {
                return stack != null && stack.getItem() instanceof ItemSpaceUpgradeCard;
            }

            @Override
            public int getSlotStackLimit() {
                return PocketState.MAX_SPACE_UPGRADES;
            }
        });
        addSlotToContainer(new Slot(upgradeInventory, PAGE_UPGRADE_SLOT, 18 + 18 + 4, 22) {

            @Override
            public boolean isItemValid(ItemStack stack) {
                return stack != null && stack.getItem() instanceof ItemPageUpgradeCard;
            }

            @Override
            public int getSlotStackLimit() {
                return PocketState.MAX_PAGE_UPGRADES;
            }
        });

        // Player main inventory
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                addSlotToContainer(new Slot(player.inventory, col + row * 9 + 9, 8 + col * 18, 70 + row * 18));
            }
        }
        // Player hotbar
        for (int col = 0; col < 9; ++col) {
            addSlotToContainer(new Slot(player.inventory, col, 8 + col * 18, 128));
        }
    }

    public int getSpaceUpgradeCount() {
        return upgradeInventory.getSpaceUpgradeCount();
    }

    public int getPageUpgradeCount() {
        return upgradeInventory.getPageUpgradeCount();
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        ItemStack result = null;
        Slot slot = (Slot) this.inventorySlots.get(index);
        if (slot != null && slot.getHasStack()) {
            ItemStack stack = slot.getStack();
            result = stack.copy();
            if (index < UPGRADE_SLOTS_COUNT) {
                // Move from upgrade slot to player inventory
                if (!this.mergeItemStack(stack, UPGRADE_SLOTS_COUNT, this.inventorySlots.size(), true)) return null;
            } else {
                // From player inventory: route space/page cards into their slots, else fail.
                if (stack.getItem() instanceof ItemSpaceUpgradeCard) {
                    if (!this.mergeItemStack(stack, SPACE_UPGRADE_SLOT, SPACE_UPGRADE_SLOT + 1, false)) return null;
                } else if (stack.getItem() instanceof ItemPageUpgradeCard) {
                    if (!this.mergeItemStack(stack, PAGE_UPGRADE_SLOT, PAGE_UPGRADE_SLOT + 1, false)) return null;
                } else {
                    return null;
                }
            }
            if (stack.stackSize == 0) slot.putStack(null);
            else slot.onSlotChanged();
        }
        return result;
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        return true;
    }

    @Override
    public void onContainerClosed(EntityPlayer playerIn) {
        super.onContainerClosed(playerIn);
        // Server side: persist final upgrade counts to PocketState, drop leftovers back to player.
        if (!playerIn.worldObj.isRemote) {
            upgradeInventory.commitToStateAndReturnExtras(playerIn);
        }
    }

    /**
     * Tiny IInventory backing the two upgrade slots. Each slot holds at most
     * one ItemStack whose stackSize is the upgrade count. The server keeps the
     * authoritative counts in PocketState; this inventory mirrors them on open
     * and commits back on close / on each setStack.
     */
    private static final class UpgradeSlotInventory implements IInventory {

        private final EntityPlayer player;
        private ItemStack spaceStack;
        private ItemStack pageStack;

        UpgradeSlotInventory(EntityPlayer player) {
            this.player = player;
            if (!player.worldObj.isRemote) {
                PocketState state = PocketStore.instance()
                    .getOrCreate((net.minecraft.entity.player.EntityPlayerMP) player);
                if (state.getSpaceUpgrades() > 0) {
                    spaceStack = new ItemStack(
                        com.imgood.advancedatamonitor.loader.LoaderItem.spaceUpgradeCard,
                        state.getSpaceUpgrades());
                }
                if (state.getPageUpgrades() > 0) {
                    pageStack = new ItemStack(
                        com.imgood.advancedatamonitor.loader.LoaderItem.pageUpgradeCard,
                        state.getPageUpgrades());
                }
            }
        }

        int getSpaceUpgradeCount() {
            return spaceStack == null ? 0 : spaceStack.stackSize;
        }

        int getPageUpgradeCount() {
            return pageStack == null ? 0 : pageStack.stackSize;
        }

        @Override
        public int getSizeInventory() {
            return UPGRADE_SLOTS_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == SPACE_UPGRADE_SLOT ? spaceStack : slot == PAGE_UPGRADE_SLOT ? pageStack : null;
        }

        @Override
        public ItemStack decrStackSize(int slot, int amount) {
            ItemStack stack = getStackInSlot(slot);
            if (stack == null) return null;
            int take = Math.min(amount, stack.stackSize);
            ItemStack result = stack.splitStack(take);
            if (stack.stackSize <= 0) {
                if (slot == SPACE_UPGRADE_SLOT) spaceStack = null;
                else if (slot == PAGE_UPGRADE_SLOT) pageStack = null;
            }
            commitToState();
            return result;
        }

        @Override
        public ItemStack getStackInSlotOnClosing(int slot) {
            ItemStack stack = getStackInSlot(slot);
            setInventorySlotContents(slot, null);
            return stack;
        }

        @Override
        public void setInventorySlotContents(int slot, ItemStack stack) {
            if (slot == SPACE_UPGRADE_SLOT) spaceStack = stack;
            else if (slot == PAGE_UPGRADE_SLOT) pageStack = stack;
            commitToState();
        }

        private void commitToState() {
            if (player.worldObj.isRemote) return;
            PocketState state = PocketStore.instance()
                .getOrCreate((net.minecraft.entity.player.EntityPlayerMP) player);
            int newSpace = spaceStack == null ? 0 : Math.min(spaceStack.stackSize, PocketState.MAX_SPACE_UPGRADES);
            int newPage = pageStack == null ? 0 : Math.min(pageStack.stackSize, PocketState.MAX_PAGE_UPGRADES);
            // Enforce page-upgrade prerequisite: page upgrades only when space upgrades are maxed.
            if (newSpace < PocketState.MAX_SPACE_UPGRADES) {
                newPage = 0;
                if (pageStack != null) {
                    // Drop page cards back into the slot so the player can grab them.
                    pageStack.stackSize = Math.min(pageStack.stackSize, 0);
                    if (pageStack.stackSize <= 0) pageStack = null;
                }
            }
            state.setSpaceUpgrades(newSpace);
            state.setPageUpgrades(newPage);
            PocketStore.instance()
                .save((net.minecraft.entity.player.EntityPlayerMP) player);
        }

        void commitToStateAndReturnExtras(EntityPlayer playerIn) {
            commitToState();
            // Return any leftover stacks to the player's inventory so they aren't deleted.
            ItemStack toReturnSpace = spaceStack;
            ItemStack toReturnPage = pageStack;
            spaceStack = null;
            pageStack = null;
            if (toReturnSpace != null && toReturnSpace.stackSize > 0) {
                if (!playerIn.inventory.addItemStackToInventory(toReturnSpace)) {
                    playerIn.dropPlayerItemWithRandomChoice(toReturnSpace, false);
                }
            }
            if (toReturnPage != null && toReturnPage.stackSize > 0) {
                if (!playerIn.inventory.addItemStackToInventory(toReturnPage)) {
                    playerIn.dropPlayerItemWithRandomChoice(toReturnPage, false);
                }
            }
        }

        @Override
        public String getInventoryName() {
            return "adm.pocket.upgrades";
        }

        @Override
        public boolean hasCustomInventoryName() {
            return false;
        }

        @Override
        public int getInventoryStackLimit() {
            return 64;
        }

        @Override
        public void markDirty() {}

        @Override
        public boolean isUseableByPlayer(EntityPlayer player) {
            return true;
        }

        @Override
        public void openInventory() {}

        @Override
        public void closeInventory() {}

        @Override
        public boolean isItemValidForSlot(int slot, ItemStack stack) {
            return stack != null && ((slot == SPACE_UPGRADE_SLOT && stack.getItem() instanceof ItemSpaceUpgradeCard)
                || (slot == PAGE_UPGRADE_SLOT && stack.getItem() instanceof ItemPageUpgradeCard));
        }
    }
}
