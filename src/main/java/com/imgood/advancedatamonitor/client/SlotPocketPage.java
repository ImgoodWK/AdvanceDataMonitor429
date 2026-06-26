package com.imgood.advancedatamonitor.client;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A pseudo-Slot injected into the open Container's inventorySlots to represent
 * one cell of the Dimensional Pocket's current page. The slot reads from
 * PocketClientCache for display; putStack / decrStackSize send network actions
 * so the authoritative PocketState on the server stays in sync.
 *
 * Important: this slot's IInventory backend is a stub; actual item movement is
 * handled via windowClick on the host Container. The Slot here mostly provides
 * getStack/isItemValid/getSlotStackLimit so GuiContainer.drawSlot and the
 * vanilla packet flow can address these injected indices.
 */
@SideOnly(Side.CLIENT)
public class SlotPocketPage extends Slot {

    private final int slotIndexInPage;
    private final PocketOverlayHandler handler;

    public SlotPocketPage(PocketOverlayHandler handler, int slotIndexInPage) {
        super(STUB_INVENTORY, slotIndexInPage, 0, 0);
        this.handler = handler;
        this.slotIndexInPage = slotIndexInPage;
    }

    public int getSlotIndexInPage() {
        return slotIndexInPage;
    }

    @Override
    public ItemStack getStack() {
        int page = PocketClientCache.getCurrentPage();
        return PocketClientCache.getStack(page, slotIndexInPage);
    }

    @Override
    public boolean getHasStack() {
        return getStack() != null;
    }

    @Override
    public ItemStack decrStackSize(int amount) {
        // Delegated to the server via the host container's windowClick; nothing to mutate here.
        return null;
    }

    @Override
    public void putStack(ItemStack stack) {
        // Delegated to the server via the host container's windowClick.
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return true;
    }

    @Override
    public int getSlotStackLimit() {
        return 64;
    }

    @Override
    public boolean canTakeStack(EntityPlayer player) {
        return true;
    }

    private static final IInventory STUB_INVENTORY = new IInventory() {

        @Override
        public int getSizeInventory() {
            return 0;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return null;
        }

        @Override
        public ItemStack decrStackSize(int slot, int amount) {
            return null;
        }

        @Override
        public ItemStack getStackInSlotOnClosing(int slot) {
            return null;
        }

        @Override
        public void setInventorySlotContents(int slot, ItemStack stack) {}

        @Override
        public String getInventoryName() {
            return "adm.pocket.stub";
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
            return false;
        }

        @Override
        public void openInventory() {}

        @Override
        public void closeInventory() {}

        @Override
        public boolean isItemValidForSlot(int slot, ItemStack stack) {
            return true;
        }
    };
}
