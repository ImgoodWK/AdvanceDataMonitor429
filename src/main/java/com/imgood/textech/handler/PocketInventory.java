package com.imgood.textech.handler;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

/**
 * IInventory implementation that acts as the backend for the Dimensional Pocket's
 * extension slots. It proxies reads/writes to the current page of the player's
 * PocketState, so all slot interactions on the wrapped Container flow back to
 * the per-player compressed NBT file via PocketStore.save().
 */
public class PocketInventory implements IInventory {

    private final PocketState state;
    private final String playerUuid;
    private int currentPage = 0;
    private final boolean serverSide;
    private long lastSaveMs = 0L;
    private static final long SAVE_COOLDOWN_MS = 2000L;

    public PocketInventory(PocketState state, String playerUuid, boolean serverSide) {
        this.state = state;
        this.playerUuid = playerUuid;
        this.serverSide = serverSide;
    }

    public void setCurrentPage(int page) {
        if (page < 0 || page >= state.getPageCount()) page = 0;
        this.currentPage = page;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    @Override
    public int getSizeInventory() {
        return state.getSlotsPerPage();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= getSizeInventory()) return null;
        return state.getStack(currentPage, slot);
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        ItemStack stack = getStackInSlot(slot);
        if (stack == null) return null;
        ItemStack result;
        if (stack.stackSize <= amount) {
            result = stack;
            state.setStack(currentPage, slot, null);
        } else {
            result = stack.splitStack(amount);
            if (stack.stackSize <= 0) {
                state.setStack(currentPage, slot, null);
            } else {
                state.setStack(currentPage, slot, stack);
            }
        }
        markDirty();
        return result;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        ItemStack stack = getStackInSlot(slot);
        if (stack != null) {
            state.setStack(currentPage, slot, null);
        }
        return stack;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot < 0 || slot >= getSizeInventory()) return;
        if (stack != null && stack.stackSize > getInventoryStackLimit()) {
            stack.stackSize = getInventoryStackLimit();
        }
        state.setStack(currentPage, slot, stack);
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return "adm.pocket.inventory";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        if (state.isInfiniteStackUpgrade()) return Integer.MAX_VALUE;
        int mult = state.getStackMultiplier();
        if (mult == Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return Math.min(64 * mult, Integer.MAX_VALUE);
    }

    @Override
    public void markDirty() {
        if (serverSide) {
            long now = System.currentTimeMillis();
            if (now - lastSaveMs >= SAVE_COOLDOWN_MS) {
                lastSaveMs = now;
                PocketStore.instance()
                    .save(playerUuid);
            }
        }
    }

    /**
     * Force an immediate save regardless of the cooldown. Called on container
     * close and from periodic tick to ensure data is not lost on crash.
     */
    public void flush() {
        if (serverSide) {
            lastSaveMs = System.currentTimeMillis();
            PocketStore.instance()
                .save(playerUuid);
        }
    }

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
        return true;
    }
}
