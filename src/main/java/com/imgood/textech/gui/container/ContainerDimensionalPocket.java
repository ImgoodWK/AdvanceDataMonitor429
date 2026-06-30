package com.imgood.textech.gui.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

import com.imgood.textech.client.PocketPortalGuiRenderer;
import com.imgood.textech.handler.PocketState;
import com.imgood.textech.handler.PocketStore;
import com.imgood.textech.handler.PocketUpgradeRules;
import com.imgood.textech.items.ItemInfiniteStackUpgradeCard;
import com.imgood.textech.items.ItemPageUpgradeCard;
import com.imgood.textech.items.ItemSpaceUpgradeCard;
import com.imgood.textech.items.ItemStackUpgradeCard;

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
        addSlotToContainer(new UpgradeCardSlot(upgradeInventory, SPACE_UPGRADE_SLOT,
            PocketPortalGuiRenderer.CONFIG_UPGRADE_ORIGIN_X, PocketPortalGuiRenderer.CONFIG_UPGRADE_ORIGIN_Y) {

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
        addSlotToContainer(new UpgradeCardSlot(upgradeInventory, PAGE_UPGRADE_SLOT,
            PocketPortalGuiRenderer.CONFIG_UPGRADE_ORIGIN_X + PocketPortalGuiRenderer.CONFIG_UPGRADE_COL_STEP,
            PocketPortalGuiRenderer.CONFIG_UPGRADE_ORIGIN_Y) {

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
        addSlotToContainer(new UpgradeCardSlot(upgradeInventory, STACK_UPGRADE_SLOT,
            PocketPortalGuiRenderer.CONFIG_UPGRADE_ORIGIN_X, PocketPortalGuiRenderer.CONFIG_UPGRADE_ROW2_Y) {

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
        addSlotToContainer(new UpgradeCardSlot(upgradeInventory, INFINITE_STACK_UPGRADE_SLOT,
            PocketPortalGuiRenderer.CONFIG_UPGRADE_ORIGIN_X + PocketPortalGuiRenderer.CONFIG_UPGRADE_COL_STEP,
            PocketPortalGuiRenderer.CONFIG_UPGRADE_ROW2_Y) {

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
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (!player.worldObj.isRemote) {
            upgradeInventory.flushToStateIfDirty();
        }
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (index < UPGRADE_SLOTS_COUNT && blocksUpgradeRemoval()) {
            notifyUpgradeRemovalBlocked(player);
            return null;
        }
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

    public boolean isUpgradeRemovalBlocked() {
        return blocksUpgradeRemoval();
    }

    private static void notifyUpgradeRemovalBlocked(EntityPlayer player) {
        if (player == null || player.worldObj.isRemote) return;
        player.addChatMessage(new ChatComponentTranslation("adm.error.pocket.cannotRemoveUpgradeWhileStored"));
    }

    private boolean blocksUpgradeRemoval() {
        if (player.worldObj.isRemote) {
            return com.imgood.textech.client.PocketClientCache.hasStoredItems();
        }
        PocketState state = PocketStore.instance()
            .getOrCreate((EntityPlayerMP) player);
        return PocketUpgradeRules.hasStoredItems(state);
    }

    private static class UpgradeCardSlot extends Slot {

        UpgradeCardSlot(IInventory inv, int index, int x, int y) {
            super(inv, index, x, y);
        }

        @Override
        public void onSlotChanged() {
            super.onSlotChanged();
            if (inventory instanceof UpgradeSlotInventory) {
                ((UpgradeSlotInventory) inventory).markUpgradeDirty();
            }
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
        private boolean dirty;

        UpgradeSlotInventory(EntityPlayer player) {
            this.player = player;
            if (!player.worldObj.isRemote) {
                // Server: authoritative counts come from PocketState.
                PocketState state = PocketStore.instance()
                    .getOrCreate((net.minecraft.entity.player.EntityPlayerMP) player);
                if (state.getSpaceUpgrades() > 0) {
                    spaceStack = new ItemStack(
                        com.imgood.textech.loader.LoaderItem.spaceUpgradeCard,
                        state.getSpaceUpgrades());
                }
                if (state.getPageUpgrades() > 0) {
                    pageStack = new ItemStack(
                        com.imgood.textech.loader.LoaderItem.pageUpgradeCard,
                        state.getPageUpgrades());
                }
                if (state.getStackUpgrades() > 0) {
                    stackStack = new ItemStack(
                        com.imgood.textech.loader.LoaderItem.stackUpgradeCard,
                        state.getStackUpgrades());
                }
                if (state.isInfiniteStackUpgrade()) {
                    infiniteStack = new ItemStack(
                        com.imgood.textech.loader.LoaderItem.infiniteStackUpgradeCard,
                        1);
                }
            } else {
                // Client: mirror from PocketClientCache.
                int space = com.imgood.textech.client.PocketClientCache.getSpaceUpgrades();
                int page = com.imgood.textech.client.PocketClientCache.getPageUpgrades();
                int stack = com.imgood.textech.client.PocketClientCache.getStackUpgrades();
                boolean infinite = com.imgood.textech.client.PocketClientCache.isInfiniteStackUpgrade();
                if (space > 0) {
                    spaceStack = new ItemStack(
                        com.imgood.textech.loader.LoaderItem.spaceUpgradeCard,
                        Math.min(space, PocketState.MAX_SPACE_UPGRADES));
                }
                if (page > 0) {
                    pageStack = new ItemStack(
                        com.imgood.textech.loader.LoaderItem.pageUpgradeCard,
                        Math.min(page, PocketState.MAX_PAGE_UPGRADES));
                }
                if (stack > 0) {
                    stackStack = new ItemStack(
                        com.imgood.textech.loader.LoaderItem.stackUpgradeCard,
                        Math.min(stack, PocketState.MAX_STACK_UPGRADES));
                }
                if (infinite) {
                    infiniteStack = new ItemStack(
                        com.imgood.textech.loader.LoaderItem.infiniteStackUpgradeCard,
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

        void markUpgradeDirty() {
            dirty = true;
        }

        void flushToStateIfDirty() {
            if (!dirty) return;
            dirty = false;
            commitToState();
        }

        void refreshFromClientCache() {
            if (!player.worldObj.isRemote) return;
            int space = com.imgood.textech.client.PocketClientCache.getSpaceUpgrades();
            int page = com.imgood.textech.client.PocketClientCache.getPageUpgrades();
            int stack = com.imgood.textech.client.PocketClientCache.getStackUpgrades();
            boolean infinite = com.imgood.textech.client.PocketClientCache.isInfiniteStackUpgrade();

            spaceStack = buildStackOrNull(com.imgood.textech.loader.LoaderItem.spaceUpgradeCard, space, PocketState.MAX_SPACE_UPGRADES);
            pageStack = buildStackOrNull(com.imgood.textech.loader.LoaderItem.pageUpgradeCard, page, PocketState.MAX_PAGE_UPGRADES);
            stackStack = buildStackOrNull(com.imgood.textech.loader.LoaderItem.stackUpgradeCard, stack, PocketState.MAX_STACK_UPGRADES);
            infiniteStack = infinite ? new ItemStack(com.imgood.textech.loader.LoaderItem.infiniteStackUpgradeCard, 1) : null;
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
            if (blocksUpgradeRemoval()) {
                notifyUpgradeRemovalBlocked(player);
                return null;
            }
            ItemStack stack = getStackInSlot(slot);
            if (stack == null) return null;
            int take = Math.min(amount, stack.stackSize);
            ItemStack result = stack.splitStack(take);
            if (stack.stackSize <= 0) {
                setSlotNull(slot);
            }
            markUpgradeDirty();
            return result;
        }

        @Override
        public ItemStack getStackInSlotOnClosing(int slot) {
            if (blocksUpgradeRemoval()) {
                notifyUpgradeRemovalBlocked(player);
                return null;
            }
            ItemStack stack = getStackInSlot(slot);
            setInventorySlotContents(slot, null);
            return stack;
        }

        @Override
        public void setInventorySlotContents(int slot, ItemStack stack) {
            int oldCount = countInSlot(slot);
            int newCount = stack == null ? 0 : (slot == INFINITE_STACK_UPGRADE_SLOT && stack.stackSize > 0 ? 1 : stack.stackSize);
            if (newCount < oldCount && blocksUpgradeRemoval()) {
                notifyUpgradeRemovalBlocked(player);
                return;
            }
            switch (slot) {
                case SPACE_UPGRADE_SLOT: spaceStack = stack; break;
                case PAGE_UPGRADE_SLOT: pageStack = stack; break;
                case STACK_UPGRADE_SLOT: stackStack = stack; break;
                case INFINITE_STACK_UPGRADE_SLOT: infiniteStack = stack; break;
            }
            markUpgradeDirty();
        }

        private int countInSlot(int slot) {
            switch (slot) {
                case SPACE_UPGRADE_SLOT: return spaceStack == null ? 0 : spaceStack.stackSize;
                case PAGE_UPGRADE_SLOT: return pageStack == null ? 0 : pageStack.stackSize;
                case STACK_UPGRADE_SLOT: return stackStack == null ? 0 : stackStack.stackSize;
                case INFINITE_STACK_UPGRADE_SLOT: return infiniteStack == null ? 0 : 1;
                default: return 0;
            }
        }

        private boolean blocksUpgradeRemoval() {
            if (player.worldObj.isRemote) {
                return com.imgood.textech.client.PocketClientCache.hasStoredItems();
            }
            PocketState state = PocketStore.instance()
                .getOrCreate((EntityPlayerMP) player);
            return PocketUpgradeRules.hasStoredItems(state);
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
            int prevSpace = state.getSpaceUpgrades();
            int prevPage = state.getPageUpgrades();
            int prevSlots = state.getSlotsPerPage();
            int prevPageCount = state.getPageCount();

            int prevStack = state.getStackUpgrades();
            boolean prevInfinite = state.isInfiniteStackUpgrade();

            int newSpace = spaceStack == null ? 0 : Math.min(spaceStack.stackSize, PocketState.MAX_SPACE_UPGRADES);
            int newPage = pageStack == null ? 0 : Math.min(pageStack.stackSize, PocketState.MAX_PAGE_UPGRADES);
            int newStack = stackStack == null ? 0 : Math.min(stackStack.stackSize, PocketState.MAX_STACK_UPGRADES);
            boolean newInfinite = infiniteStack != null;

            boolean reducing = newSpace < prevSpace || newPage < prevPage || newStack < prevStack
                || (prevInfinite && !newInfinite);
            if (reducing && PocketUpgradeRules.hasStoredItems(state)) {
                restoreUpgradeStacksFromState(state);
                notifyUpgradeRemovalBlocked(player);
                return;
            }

            refundOverflow(spaceStack, com.imgood.textech.loader.LoaderItem.spaceUpgradeCard, newSpace);
            refundOverflow(pageStack, com.imgood.textech.loader.LoaderItem.pageUpgradeCard, newPage);
            refundOverflow(stackStack, com.imgood.textech.loader.LoaderItem.stackUpgradeCard, newStack);

            if (newSpace < PocketState.MAX_SPACE_UPGRADES && newPage > 0) {
                returnCardsToPlayer(com.imgood.textech.loader.LoaderItem.pageUpgradeCard, newPage);
                newPage = 0;
                pageStack = null;
            }
            if (newStack < PocketState.MAX_STACK_UPGRADES && newInfinite) {
                returnCardsToPlayer(com.imgood.textech.loader.LoaderItem.infiniteStackUpgradeCard, 1);
                newInfinite = false;
                infiniteStack = null;
            }

            normalizeDisplayStack(spaceStack, newSpace);
            normalizeDisplayStack(pageStack, newPage);
            normalizeDisplayStack(stackStack, newStack);

            state.setSpaceUpgrades(newSpace);
            state.setPageUpgrades(newPage);
            state.setStackUpgrades(newStack);
            state.setInfiniteStackUpgrade(newInfinite);

            if (newSpace <= 0) spaceStack = null;
            else if (spaceStack != null) spaceStack.stackSize = newSpace;
            if (newPage <= 0) pageStack = null;
            else if (pageStack != null) pageStack.stackSize = newPage;
            if (newStack <= 0) stackStack = null;
            else if (stackStack != null) stackStack.stackSize = newStack;
            if (!newInfinite) infiniteStack = null;

            PocketStore.instance()
                .save((net.minecraft.entity.player.EntityPlayerMP) player);

            boolean layoutChanged = prevSpace != newSpace || prevPage != newPage
                || prevSlots != state.getSlotsPerPage() || prevPageCount != state.getPageCount();
            com.imgood.textech.network.packet.PacketPocketSync sync = layoutChanged
                ? com.imgood.textech.network.packet.PacketPocketSync.fullState(state)
                : com.imgood.textech.network.packet.PacketPocketSync.metadataState(state);
            com.imgood.textech.AdvanceDataMonitor.ADMCHANEL
                .sendTo(sync, (net.minecraft.entity.player.EntityPlayerMP) player);
        }

        private void restoreUpgradeStacksFromState(PocketState state) {
            spaceStack = buildStackOrNull(
                com.imgood.textech.loader.LoaderItem.spaceUpgradeCard,
                state.getSpaceUpgrades(),
                PocketState.MAX_SPACE_UPGRADES);
            pageStack = buildStackOrNull(
                com.imgood.textech.loader.LoaderItem.pageUpgradeCard,
                state.getPageUpgrades(),
                PocketState.MAX_PAGE_UPGRADES);
            stackStack = buildStackOrNull(
                com.imgood.textech.loader.LoaderItem.stackUpgradeCard,
                state.getStackUpgrades(),
                PocketState.MAX_STACK_UPGRADES);
            infiniteStack = state.isInfiniteStackUpgrade()
                ? new ItemStack(com.imgood.textech.loader.LoaderItem.infiniteStackUpgradeCard, 1)
                : null;
            dirty = false;
        }

        private void refundOverflow(ItemStack slotStack, net.minecraft.item.Item item, int allowed) {
            if (slotStack == null || slotStack.stackSize <= allowed) return;
            int excess = slotStack.stackSize - allowed;
            slotStack.stackSize = allowed;
            returnCardsToPlayer(item, excess);
        }

        private void normalizeDisplayStack(ItemStack slotStack, int count) {
            if (count <= 0) return;
            if (slotStack != null) slotStack.stackSize = count;
        }

        private void returnCardsToPlayer(net.minecraft.item.Item item, int count) {
            if (count <= 0) return;
            ItemStack toReturn = new ItemStack(item, count);
            if (!player.inventory.addItemStackToInventory(toReturn)) {
                player.dropPlayerItemWithRandomChoice(toReturn, false);
            }
        }

        void commitToStateAndReturnExtras(EntityPlayer playerIn) {
            dirty = true;
            flushToStateIfDirty();
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
