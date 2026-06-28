package com.imgood.advancedatamonitor.gui.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.imgood.advancedatamonitor.handler.PocketState;
import com.imgood.advancedatamonitor.handler.PocketStore;
import com.imgood.advancedatamonitor.items.ItemInfiniteStackUpgradeCard;
import com.imgood.advancedatamonitor.items.ItemPageUpgradeCard;
import com.imgood.advancedatamonitor.items.ItemSpaceUpgradeCard;
import com.imgood.advancedatamonitor.items.ItemStackUpgradeCard;

/**
 * Container for the Dimensional Pocket config GUI. Holds the player inventory
 * plus four upgrade slots (space, page, stack, infinite stack). The upgrade
 * slots are backed by a small virtual IInventory whose stack sizes mirror the
 * player-bound PocketState upgrade counts. On container close, the upgrade
 * counts are committed back to PocketState via PacketPocketAction semantics
 * (the slot setStack callbacks already mutated PocketStore directly here on
 * the server side).
 */
public class ContainerDimensionalPocket extends Container {

    public static final int SPACE_UPGRADE_SLOT = 0;
    public static final int PAGE_UPGRADE_SLOT = 1;
    public static final int STACK_UPGRADE_SLOT = 2;
    public static final int INFINITE_STACK_UPGRADE_SLOT = 3;
    private static final int UPGRADE_SLOTS_COUNT = 4;

    private final EntityPlayer player;
    private final UpgradeSlotInventory upgradeInventory;

    public ContainerDimensionalPocket(EntityPlayer player) {
        this.player = player;
        this.upgradeInventory = new UpgradeSlotInventory(player);

        // Space upgrade slot (row 1, col 1)
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
        // Page upgrade slot (row 1, col 2)
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
        // Stack upgrade slot (row 2, col 1)
        addSlotToContainer(new Slot(upgradeInventory, STACK_UPGRADE_SLOT, 18, 54) {

            @Override
            public boolean isItemValid(ItemStack stack) {
                return stack != null && stack.getItem() instanceof ItemStackUpgradeCard;
            }

            @Override
            public int getSlotStackLimit() {
                return PocketState.MAX_STACK_UPGRADES;
            }
        });
        // Infinite stack upgrade slot (row 2, col 2)
        addSlotToContainer(new Slot(upgradeInventory, INFINITE_STACK_UPGRADE_SLOT, 18 + 18 + 4, 54) {

            @Override
            public boolean isItemValid(ItemStack stack) {
                return stack != null && stack.getItem() instanceof ItemInfiniteStackUpgradeCard;
            }

            @Override
            public int getSlotStackLimit() {
                return 1;
            }
        });

        // Player main inventory (pushed down to make room for stats text between upgrade slots and inventory)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                addSlotToContainer(new Slot(player.inventory, col + row * 9 + 9, 8 + col * 18, 126 + row * 18));
            }
        }
        // Player hotbar
        for (int col = 0; col < 9; ++col) {
            addSlotToContainer(new Slot(player.inventory, col, 8 + col * 18, 184));
        }
    }

    public int getSpaceUpgradeCount() {
        return upgradeInventory.getSpaceUpgradeCount();
    }

    public int getPageUpgradeCount() {
        return upgradeInventory.getPageUpgradeCount();
    }

    public int getStackUpgradeCount() {
        return upgradeInventory.getStackUpgradeCount();
    }

    public boolean hasInfiniteStackUpgrade() {
        return upgradeInventory.hasInfiniteStackUpgrade();
    }

    /**
     * Client-side hook used by GuiDimensionalPocketConfig.updateScreen to keep
     * the upgrade slot display in sync with PocketClientCache (which is updated
     * from PacketPocketSync).
     */
    public void refreshUpgradeDisplayFromClientCache() {
        upgradeInventory.refreshFromClientCache();
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
                // From player inventory: route cards into their slots, else fail.
                if (stack.getItem() instanceof ItemSpaceUpgradeCard) {
                    if (!this.mergeItemStack(stack, SPACE_UPGRADE_SLOT, SPACE_UPGRADE_SLOT + 1, false)) return null;
                } else if (stack.getItem() instanceof ItemPageUpgradeCard) {
                    if (!this.mergeItemStack(stack, PAGE_UPGRADE_SLOT, PAGE_UPGRADE_SLOT + 1, false)) return null;
                } else if (stack.getItem() instanceof ItemStackUpgradeCard) {
                    if (!this.mergeItemStack(stack, STACK_UPGRADE_SLOT, STACK_UPGRADE_SLOT + 1, false)) return null;
                } else if (stack.getItem() instanceof ItemInfiniteStackUpgradeCard) {
                    if (!this.mergeItemStack(stack, INFINITE_STACK_UPGRADE_SLOT, INFINITE_STACK_UPGRADE_SLOT + 1, false)) return null;
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
        if (!playerIn.worldObj.isRemote) {
            upgradeInventory.commitToStateAndReturnExtras(playerIn);
        }
    }

    /**
     * Tiny IInventory backing the four upgrade slots. Each slot holds at most
     * one ItemStack whose stackSize is the upgrade count.
     */
    private static final class UpgradeSlotInventory implements IInventory {

        private final EntityPlayer player;
        private ItemStack spaceStack;
        private ItemStack pageStack;
        private ItemStack stackStack;
        private ItemStack infiniteStack;

        UpgradeSlotInventory(EntityPlayer player) {
            this.player = player;
            if (!player.worldObj.isRemote) {
                // Server: authoritative counts come from PocketState.
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
                if (state.getStackUpgrades() > 0) {
                    stackStack = new ItemStack(
                        com.imgood.advancedatamonitor.loader.LoaderItem.stackUpgradeCard,
                        state.getStackUpgrades());
                }
                if (state.isInfiniteStackUpgrade()) {
                    infiniteStack = new ItemStack(
                        com.imgood.advancedatamonitor.loader.LoaderItem.infiniteStackUpgradeCard,
                        1);
                }
            } else {
                // Client: mirror from PocketClientCache.
                int space = com.imgood.advancedatamonitor.client.PocketClientCache.getSpaceUpgrades();
                int page = com.imgood.advancedatamonitor.client.PocketClientCache.getPageUpgrades();
                int stack = com.imgood.advancedatamonitor.client.PocketClientCache.getStackUpgrades();
                boolean infinite = com.imgood.advancedatamonitor.client.PocketClientCache.isInfiniteStackUpgrade();
                if (space > 0) {
                    spaceStack = new ItemStack(
                        com.imgood.advancedatamonitor.loader.LoaderItem.spaceUpgradeCard,
                        Math.min(space, PocketState.MAX_SPACE_UPGRADES));
                }
                if (page > 0) {
                    pageStack = new ItemStack(
                        com.imgood.advancedatamonitor.loader.LoaderItem.pageUpgradeCard,
                        Math.min(page, PocketState.MAX_PAGE_UPGRADES));
                }
                if (stack > 0) {
                    stackStack = new ItemStack(
                        com.imgood.advancedatamonitor.loader.LoaderItem.stackUpgradeCard,
                        Math.min(stack, PocketState.MAX_STACK_UPGRADES));
                }
                if (infinite) {
                    infiniteStack = new ItemStack(
                        com.imgood.advancedatamonitor.loader.LoaderItem.infiniteStackUpgradeCard,
                        1);
                }
            }
        }

        int getSpaceUpgradeCount() {
            return spaceStack == null ? 0 : spaceStack.stackSize;
        }

        int getPageUpgradeCount() {
            return pageStack == null ? 0 : pageStack.stackSize;
        }

        int getStackUpgradeCount() {
            return stackStack == null ? 0 : stackStack.stackSize;
        }

        boolean hasInfiniteStackUpgrade() {
            return infiniteStack != null;
        }

        void refreshFromClientCache() {
            if (!player.worldObj.isRemote) return;
            int space = com.imgood.advancedatamonitor.client.PocketClientCache.getSpaceUpgrades();
            int page = com.imgood.advancedatamonitor.client.PocketClientCache.getPageUpgrades();
            int stack = com.imgood.advancedatamonitor.client.PocketClientCache.getStackUpgrades();
            boolean infinite = com.imgood.advancedatamonitor.client.PocketClientCache.isInfiniteStackUpgrade();

            spaceStack = buildStackOrNull(com.imgood.advancedatamonitor.loader.LoaderItem.spaceUpgradeCard, space, PocketState.MAX_SPACE_UPGRADES);
            pageStack = buildStackOrNull(com.imgood.advancedatamonitor.loader.LoaderItem.pageUpgradeCard, page, PocketState.MAX_PAGE_UPGRADES);
            stackStack = buildStackOrNull(com.imgood.advancedatamonitor.loader.LoaderItem.stackUpgradeCard, stack, PocketState.MAX_STACK_UPGRADES);
            infiniteStack = infinite ? new ItemStack(com.imgood.advancedatamonitor.loader.LoaderItem.infiniteStackUpgradeCard, 1) : null;
        }

        private static ItemStack buildStackOrNull(net.minecraft.item.Item item, int count, int max) {
            if (count <= 0) return null;
            return new ItemStack(item, Math.min(count, max));
        }

        @Override
        public int getSizeInventory() {
            return UPGRADE_SLOTS_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            switch (slot) {
                case SPACE_UPGRADE_SLOT: return spaceStack;
                case PAGE_UPGRADE_SLOT: return pageStack;
                case STACK_UPGRADE_SLOT: return stackStack;
                case INFINITE_STACK_UPGRADE_SLOT: return infiniteStack;
                default: return null;
            }
        }

        @Override
        public ItemStack decrStackSize(int slot, int amount) {
            ItemStack stack = getStackInSlot(slot);
            if (stack == null) return null;
            int take = Math.min(amount, stack.stackSize);
            ItemStack result = stack.splitStack(take);
            if (stack.stackSize <= 0) {
                setSlotNull(slot);
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
            switch (slot) {
                case SPACE_UPGRADE_SLOT: spaceStack = stack; break;
                case PAGE_UPGRADE_SLOT: pageStack = stack; break;
                case STACK_UPGRADE_SLOT: stackStack = stack; break;
                case INFINITE_STACK_UPGRADE_SLOT: infiniteStack = stack; break;
            }
            commitToState();
        }

        private void setSlotNull(int slot) {
            switch (slot) {
                case SPACE_UPGRADE_SLOT: spaceStack = null; break;
                case PAGE_UPGRADE_SLOT: pageStack = null; break;
                case STACK_UPGRADE_SLOT: stackStack = null; break;
                case INFINITE_STACK_UPGRADE_SLOT: infiniteStack = null; break;
            }
        }

        private void commitToState() {
            if (player.worldObj.isRemote) return;
            PocketState state = PocketStore.instance()
                .getOrCreate((net.minecraft.entity.player.EntityPlayerMP) player);
            int newSpace = spaceStack == null ? 0 : Math.min(spaceStack.stackSize, PocketState.MAX_SPACE_UPGRADES);
            int newPage = pageStack == null ? 0 : Math.min(pageStack.stackSize, PocketState.MAX_PAGE_UPGRADES);
            int newStack = stackStack == null ? 0 : Math.min(stackStack.stackSize, PocketState.MAX_STACK_UPGRADES);
            boolean newInfinite = infiniteStack != null;

            // Enforce page-upgrade prerequisite: page upgrades only when space upgrades are maxed.
            if (newSpace < PocketState.MAX_SPACE_UPGRADES && newPage > 0) {
                ItemStack toReturn = new ItemStack(
                    com.imgood.advancedatamonitor.loader.LoaderItem.pageUpgradeCard,
                    newPage);
                if (!player.inventory.addItemStackToInventory(toReturn)) {
                    player.dropPlayerItemWithRandomChoice(toReturn, false);
                }
                newPage = 0;
                if (pageStack != null) {
                    pageStack.stackSize = 0;
                    pageStack = null;
                }
            }
            // Trim spaceStack
            if (spaceStack != null && spaceStack.stackSize > newSpace) {
                spaceStack.stackSize = newSpace;
                if (spaceStack.stackSize <= 0) spaceStack = null;
            }
            // Enforce stack upgrade cap
            if (newStack > PocketState.MAX_STACK_UPGRADES) {
                newStack = PocketState.MAX_STACK_UPGRADES;
                if (stackStack != null) stackStack.stackSize = PocketState.MAX_STACK_UPGRADES;
            }
            state.setSpaceUpgrades(newSpace);
            state.setPageUpgrades(newPage);
            state.setStackUpgrades(newStack);
            state.setInfiniteStackUpgrade(newInfinite);
            PocketStore.instance()
                .save((net.minecraft.entity.player.EntityPlayerMP) player);
            com.imgood.advancedatamonitor.AdvanceDataMonitor.ADMCHANEL
                .sendTo(com.imgood.advancedatamonitor.network.packet.PacketPocketSync.fullState(state),
                    (net.minecraft.entity.player.EntityPlayerMP) player);
        }

        void commitToStateAndReturnExtras(EntityPlayer playerIn) {
            commitToState();
            spaceStack = null;
            pageStack = null;
            stackStack = null;
            infiniteStack = null;
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
            if (stack == null) return true;
            switch (slot) {
                case SPACE_UPGRADE_SLOT: return stack.getItem() instanceof ItemSpaceUpgradeCard;
                case PAGE_UPGRADE_SLOT: return stack.getItem() instanceof ItemPageUpgradeCard;
                case STACK_UPGRADE_SLOT: return stack.getItem() instanceof ItemStackUpgradeCard;
                case INFINITE_STACK_UPGRADE_SLOT: return stack.getItem() instanceof ItemInfiniteStackUpgradeCard;
                default: return false;
            }
        }
    }
}
