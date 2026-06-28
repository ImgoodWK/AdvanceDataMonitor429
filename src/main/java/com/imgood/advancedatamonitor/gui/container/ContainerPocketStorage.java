package com.imgood.advancedatamonitor.gui.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.handler.PocketInventory;
import com.imgood.advancedatamonitor.handler.PocketState;
import com.imgood.advancedatamonitor.handler.PocketStore;
import com.imgood.advancedatamonitor.network.packet.PacketPocketSync;

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
 * Modeled after the GTNH pattern used by portable container mods (Ender Pouch,
 * Travelers Backpack style): a self-contained Container with its own slots,
 * not an overlay on top of someone else's GUI.
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

    private PocketState buildClientMirrorState(int slotsPerPage, int pageCount) {
        // Prefer the explicitly passed dimensions (from GuiHandler encoding); fall back
        // to PocketClientCache if not provided. The server is authoritative for actual
        // item contents, which arrive via vanilla container sync.
        PocketState mirror = new PocketState();
        if (slotsPerPage > 0 && pageCount > 0) {
            // Reconstruct upgrade counts that would yield the requested dimensions.
            // slotsPerPage = min(CAP, 1 + min(space, MAX-2)); solve for space.
            int space = Math.max(0, slotsPerPage - 1);
            if (space > PocketState.MAX_SPACE_UPGRADES - 2) space = PocketState.MAX_SPACE_UPGRADES - 2;
            if (slotsPerPage >= PocketState.SLOTS_PER_PAGE_CAP) space = PocketState.MAX_SPACE_UPGRADES - 2;
            mirror.setSpaceUpgrades(space);
            // pageCount = space>=MAX ? min(CAP, 1 + min(page, MAX)) : 1
            if (pageCount > 1) {
                mirror.setSpaceUpgrades(PocketState.MAX_SPACE_UPGRADES);
                mirror.setPageUpgrades(Math.min(PocketState.MAX_PAGE_UPGRADES, pageCount - 1));
            }
            return mirror;
        }
        // Fallback to client cache.
        int space = com.imgood.advancedatamonitor.client.PocketClientCache.getSpaceUpgrades();
        int page = com.imgood.advancedatamonitor.client.PocketClientCache.getPageUpgrades();
        mirror.setSpaceUpgrades(space);
        mirror.setPageUpgrades(page);
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
            addSlotToContainer(new Slot(pocketInv, i, startX + col * 18, startY + row * 18));
        }
        // Player main inventory below the max-size pocket grid.
        int playerY = startY + maxRows * 18 + 14;
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                addSlotToContainer(new Slot(player.inventory, col + row * 9 + 9, startX + col * 18, playerY + row * 18));
            }
        }
        // Hotbar
        int hotbarY = playerY + 3 * 18 + 4;
        for (int col = 0; col < 9; ++col) {
            addSlotToContainer(new Slot(player.inventory, col, startX + col * 18, hotbarY));
        }
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

    /**
     * Server-side page switch. Updates the PocketInventory's current page so the
     * slot grid reads from the new page, then broadcasts a PacketPocketSync so
     * the client cache (overlay/tooltip) stays consistent. Vanilla
     * detectAndSendChanges will push the changed slot contents to the client
     * on the next tick — no need to call onSlotChanged on every individual slot.
     */
    public void setPage(int page) {
        if (player.worldObj.isRemote) return;
        int pageCount = state.getPageCount();
        if (page < 0) page = 0;
        if (page >= pageCount) page = Math.max(0, pageCount - 1);
        this.currentPage = page;
        pocketInv.setCurrentPage(page);
        // Save the pocket state once (page switch itself doesn't mutate items,
        // but we flush in case previous mutations hadn't been persisted yet).
        pocketInv.flush();
        AdvanceDataMonitor.ADMCHANEL
            .sendTo(PacketPocketSync.singlePage(state, page), (EntityPlayerMP) player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        ItemStack result = null;
        Slot slot = (Slot) this.inventorySlots.get(index);
        if (slot != null && slot.getHasStack()) {
            ItemStack stack = slot.getStack();
            result = stack.copy();
            int pocketSlots = state.getSlotsPerPage();
            if (index < pocketSlots) {
                // Move from pocket to player inventory.
                if (!this.mergeItemStack(stack, pocketSlots, this.inventorySlots.size(), true)) return null;
            } else {
                // Move from player inventory to pocket.
                if (!this.mergeItemStack(stack, 0, pocketSlots, false)) return null;
            }
            if (stack.stackSize == 0) slot.putStack(null);
            else slot.onSlotChanged();
        }
        return result;
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        // Always allow — the pocket is bound to the player, not a tile entity.
        return true;
    }

    @Override
    public void onContainerClosed(EntityPlayer playerIn) {
        super.onContainerClosed(playerIn);
        // PocketInventory writes through to PocketState on every mutation, and
        // markDirty uses a cooldown to avoid thrashing the disk. Force-flush on
        // close so no mutation is lost, then save the PocketStore.
        pocketInv.flush();
        if (!playerIn.worldObj.isRemote) {
            PocketStore.instance()
                .save((EntityPlayerMP) playerIn);
        }
    }
}
