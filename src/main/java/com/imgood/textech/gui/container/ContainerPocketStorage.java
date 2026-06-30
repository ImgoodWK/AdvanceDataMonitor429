package com.imgood.textech.gui.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.client.PocketClientCache;
import com.imgood.textech.client.PocketPortalGuiRenderer;
import com.imgood.textech.handler.PocketInventory;
import com.imgood.textech.handler.PocketSlotInteraction;
import com.imgood.textech.handler.PocketState;
import com.imgood.textech.handler.PocketStore;
import com.imgood.textech.network.packet.PacketPocketSync;

/**
 * Native Container for the Dimensional Pocket storage GUI. Renders the pocket's
 * current page as a real IInventory-backed slot grid plus the player inventory,
 * so item movement flows through vanilla windowClick / shift-click without any
 * reflection-based slot injection into foreign containers (which AE2 etc. reject).
 *
 * Page switching is driven by {@link #setPage(int)} on the server side; the
 * container then broadcasts a PacketPocketSync so the client GuiPocketStorage
 * stays in step. Slot contents themselves are synced by vanilla
 * Container.detectAndSendChanges.
 *
 * Stack upgrades bypass vanilla {@link ItemStack#getMaxStackSize()} when moving
 * items into pocket slots â€?same pattern as Science Not Leisure portable infinity chest.
 */
public class ContainerPocketStorage extends Container {

    private final EntityPlayer player;
    private final PocketState state;
    private final PocketInventory pocketInv;
    private int currentPage = 0;

    /**
     * Server-side constructor: reads authoritative slotsPerPage/pageCount from
     * PocketState.
     */
    public ContainerPocketStorage(EntityPlayer player) {
        this(player, -1, -1);
    }

    /**
     * Constructor with explicit slot dimensions. Used by the client via
     * GuiHandler so it can build the same slot grid as the server without
     * depending on PocketClientCache being already synced. When slotsPerPage
     * is < 0 the values are read from PocketState (server) / PocketClientCache
     * (client fallback).
     */
    public ContainerPocketStorage(EntityPlayer player, int slotsPerPage, int pageCount) {
        this.player = player;
        boolean server = !player.worldObj.isRemote;
        PocketState s;
        if (server) {
            s = PocketStore.instance()
                .getOrCreate((EntityPlayerMP) player);
        } else {
            s = buildClientMirrorState(slotsPerPage, pageCount);
        }
        this.state = s;
        this.pocketInv = new PocketInventory(s, player.getUniqueID()
            .toString(), server);
        this.pocketInv.setCurrentPage(0);
        layoutSlots();
    }

    private static void applyUpgradeFieldsFromCache(PocketState mirror) {
        mirror.setStackUpgrades(PocketClientCache.getStackUpgrades());
        mirror.setInfiniteStackUpgrade(PocketClientCache.isInfiniteStackUpgrade());
    }

    private PocketState buildClientMirrorState(int slotsPerPage, int pageCount) {
        PocketState mirror = new PocketState();
        if (slotsPerPage > 0 && pageCount > 0) {
            int space = Math.max(0, slotsPerPage - 1);
            if (space > PocketState.MAX_SPACE_UPGRADES - 2) space = PocketState.MAX_SPACE_UPGRADES - 2;
            if (slotsPerPage >= PocketState.SLOTS_PER_PAGE_CAP) space = PocketState.MAX_SPACE_UPGRADES - 2;
            mirror.setSpaceUpgrades(space);
            if (pageCount > 1) {
                mirror.setSpaceUpgrades(PocketState.MAX_SPACE_UPGRADES);
                mirror.setPageUpgrades(Math.min(PocketState.MAX_PAGE_UPGRADES, pageCount - 1));
            }
            applyUpgradeFieldsFromCache(mirror);
            return mirror;
        }
        int space = PocketClientCache.getSpaceUpgrades();
        int page = PocketClientCache.getPageUpgrades();
        mirror.setSpaceUpgrades(space);
        mirror.setPageUpgrades(page);
        applyUpgradeFieldsFromCache(mirror);
        return mirror;
    }

    private void layoutSlots() {
        int slotsPerPage = state.getSlotsPerPage();
        int cols = 9;
        int maxRows = (PocketState.SLOTS_PER_PAGE_CAP + cols - 1) / cols;
        int startX = 8;
        int startY = 18;
        for (int i = 0; i < slotsPerPage; i++) {
            int row = i / cols;
            int col = i % cols;
            addSlotToContainer(new PocketStorageSlot(pocketInv, i, startX + col * 18, startY + row * 18));
        }
        int playerY = startY + maxRows * 18 + 14 + PocketPortalGuiRenderer.STORAGE_PLAYER_INV_EXTRA_Y;
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                addSlotToContainer(new Slot(player.inventory, col + row * 9 + 9, startX + col * 18, playerY + row * 18));
            }
        }
        int hotbarY = playerY + 3 * 18 + 4;
        for (int col = 0; col < 9; ++col) {
            addSlotToContainer(new Slot(player.inventory, col, startX + col * 18, hotbarY));
        }
    }

    private int pocketSlotCount() {
        return state.getSlotsPerPage();
    }

    private int pocketStackLimit() {
        return pocketInv.getInventoryStackLimit();
    }

    private static boolean stacksMergeable(ItemStack a, ItemStack b) {
        return a != null && b != null && a.isItemEqual(b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    private boolean isPocketSlotIndex(int slotIndex) {
        return slotIndex >= 0 && slotIndex < pocketSlotCount();
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getPageCount() {
        return state.getPageCount();
    }

    public int getSlotsPerPage() {
        return state.getSlotsPerPage();
    }

    public PocketState getPocketState() {
        return state;
    }

    public void applyClientPage(int page) {
        if (!player.worldObj.isRemote) return;
        int pageCount = state.getPageCount();
        if (page < 0) page = 0;
        if (page >= pageCount) page = Math.max(0, pageCount - 1);
        this.currentPage = page;
        pocketInv.setCurrentPage(page);
    }

    /** Refresh stack-upgrade fields on the client mirror when metadata sync arrives. */
    public void applyClientUpgradeMetadata() {
        if (!player.worldObj.isRemote) return;
        applyUpgradeFieldsFromCache(state);
    }

    public void setPage(int page) {
        if (player.worldObj.isRemote) return;
        int pageCount = state.getPageCount();
        if (page < 0) page = 0;
        if (page >= pageCount) page = Math.max(0, pageCount - 1);
        this.currentPage = page;
        pocketInv.setCurrentPage(page);
        pocketInv.flush();
        AdvanceDataMonitor.ADMCHANEL
            .sendTo(PacketPocketSync.singlePage(state, page), (EntityPlayerMP) player);
    }

    /**
     * Shift-click into pocket slots: ignore item {@link ItemStack#getMaxStackSize()} and
     * merge up to {@link PocketInventory#getInventoryStackLimit()} per slot.
     */
    @Override
    protected boolean mergeItemStack(ItemStack stack, int startIndex, int endIndex, boolean reverse) {
        if (player.worldObj.isRemote) return false;
        if (!reverse && startIndex == 0 && endIndex <= pocketSlotCount()) {
            return mergeIntoPocketSlots(stack, startIndex, endIndex);
        }
        if (reverse && startIndex >= pocketSlotCount()) {
            return PocketSlotInteraction.mergeOneStackBatchIntoPlayerInventory(stack, player);
        }
        return super.mergeItemStack(stack, startIndex, endIndex, reverse);
    }

    private boolean mergeIntoPocketSlots(ItemStack stack, int startIndex, int endIndex) {
        if (stack == null || stack.stackSize <= 0) return false;
        int limit = pocketStackLimit();
        boolean merged = false;
        for (int i = startIndex; i < endIndex && stack.stackSize > 0; i++) {
            Slot slot = (Slot) inventorySlots.get(i);
            ItemStack existing = slot.getStack();
            if (existing == null || !stacksMergeable(existing, stack)) continue;
            int space = limit - existing.stackSize;
            if (space <= 0) continue;
            int transfer = Math.min(space, stack.stackSize);
            existing.stackSize += transfer;
            stack.stackSize -= transfer;
            slot.onSlotChanged();
            merged = true;
        }
        for (int i = startIndex; i < endIndex && stack.stackSize > 0; i++) {
            Slot slot = (Slot) inventorySlots.get(i);
            if (slot.getStack() != null) continue;
            int place = Math.min(stack.stackSize, limit);
            ItemStack copy = stack.copy();
            copy.stackSize = place;
            slot.putStack(copy);
            stack.stackSize -= place;
            merged = true;
        }
        return merged;
    }

    /**
     * Left/right click on pocket slots: placing and merging respect pocket stack limit,
     * not the item's vanilla max stack size.
     */
    @Override
    public ItemStack slotClick(int slotId, int mouseButton, int clickType, EntityPlayer playerIn) {
        if (isPocketSlotIndex(slotId) && clickType == 1) {
            if (playerIn.worldObj.isRemote) {
                return null;
            }
            Slot slot = (Slot) inventorySlots.get(slotId);
            if (slot != null && slot.getHasStack()) {
                ItemStack result = slot.getStack()
                    .copy();
                if (PocketSlotInteraction.quickMoveFromPocketToPlayer(state, currentPage, slotId, playerIn)) {
                    slot.putStack(state.getStack(currentPage, slotId));
                    slot.onSlotChanged();
                    return result;
                }
            }
            return null;
        }
        if (!isPocketSlotIndex(slotId) || clickType != 0) {
            return super.slotClick(slotId, mouseButton, clickType, playerIn);
        }
        if (playerIn.worldObj.isRemote) {
            return super.slotClick(slotId, mouseButton, clickType, playerIn);
        }

        Slot slot = (Slot) inventorySlots.get(slotId);
        if (PocketSlotInteraction.applySlotClick(state, currentPage, slotId, mouseButton, playerIn)) {
            slot.onSlotChanged();
        }
        return playerIn.inventory.getItemStack();
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        if (!playerIn.worldObj.isRemote && isPocketSlotIndex(index)) {
            Slot slot = (Slot) inventorySlots.get(index);
            if (slot != null && slot.getHasStack()) {
                ItemStack before = slot.getStack();
                ItemStack result = before.copy();
                if (PocketSlotInteraction.quickMoveFromPocketToPlayer(state, currentPage, index, playerIn)) {
                    ItemStack remaining = state.getStack(currentPage, index);
                    slot.putStack(remaining);
                    return result;
                }
            }
            return null;
        }

        ItemStack result = null;
        Slot slot = (Slot) this.inventorySlots.get(index);
        if (slot != null && slot.getHasStack()) {
            ItemStack stack = slot.getStack();
            result = stack.copy();
            int pocketSlots = state.getSlotsPerPage();
            if (index < pocketSlots) {
                if (!this.mergeItemStack(stack, pocketSlots, this.inventorySlots.size(), true)) return null;
            } else {
                if (!this.mergeItemStack(stack, 0, pocketSlots, false)) return null;
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
        pocketInv.flush();
        if (!playerIn.worldObj.isRemote) {
            PocketStore.instance()
                .save((EntityPlayerMP) playerIn);
        }
    }

    private static final class PocketStorageSlot extends Slot {

        PocketStorageSlot(PocketInventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public int getSlotStackLimit() {
            return ((PocketInventory) inventory).getInventoryStackLimit();
        }
    }
}
