package com.imgood.advancedatamonitor.handler;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

/**
 * IInventory implementation that acts as the backend for the Dimensional Pocket's
 * extension slots. It proxies reads/writes to the current page of the player's
 * PocketState, so all slot interactions on the wrapped Container flow back to
 * the per-player JSON file via PocketStore.save().
 */
public class PocketInventory implements IInventory {

    private final PocketState state;
    private final String playerUuid;
    private int currentPage = 0;
    private final boolean serverSide;

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
        return 64;
    }

    @Override
    public void markDirty() {
        if (serverSide) {
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
