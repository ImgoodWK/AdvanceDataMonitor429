package com.imgood.textech.handler;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;

/**
 * Shared pocket slot click logic for {@link com.imgood.textech.gui.container.ContainerPocketStorage}
 * and overlay {@link com.imgood.textech.network.packet.PacketPocketAction} handlers.
 * Withdrawals respect vanilla cursor limits ({@link ItemStack#getMaxStackSize()}), not the pocket stack upgrade cap.
 * Quick-transfer (shift-click / overlay shift) follows Science Not Leisure portable infinity chest: at most one
 * standard stack per action, merged into player inventory with normal per-slot caps.
 */
public final class PocketSlotInteraction {

    private PocketSlotInteraction() {}

    public static int getStackLimit(PocketState state) {
        if (state.isInfiniteStackUpgrade()) return Integer.MAX_VALUE;
        int mult = state.getStackMultiplier();
        if (mult == Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return Math.min(64 * mult, Integer.MAX_VALUE);
    }

    private static boolean stacksMergeable(ItemStack a, ItemStack b) {
        return a != null && b != null && a.isItemEqual(b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    /**
     * Shift-click (or overlay shift+click) out of a pocket slot: move at most one item max-stack into the
     * player inventory, leaving the remainder in the pocket. Mirrors
     * {@code ContainerPortableInfinityChest#mergeItemStack} reverse behaviour with a single-batch cap.
     *
     * @return true if at least one item was moved
     */
    public static boolean quickMoveFromPocketToPlayer(PocketState state, int page, int slot, EntityPlayer player) {
        if (player == null || !state.isValid(page, slot)) return false;
        ItemStack pocketStack = state.getStack(page, slot);
        if (pocketStack == null || pocketStack.stackSize <= 0) return false;

        ItemStack batch = pocketStack.copy();
        batch.stackSize = MathHelper.clamp_int(pocketStack.stackSize, 1, batch.getMaxStackSize());
        int before = batch.stackSize;
        mergeOneStackBatchIntoPlayerInventory(batch, player);
        int moved = before - batch.stackSize;
        if (moved <= 0) return false;

        pocketStack.stackSize -= moved;
        if (pocketStack.stackSize <= 0) {
            state.setStack(page, slot, null);
        } else {
            state.setStack(page, slot, pocketStack);
        }
        return true;
    }

    /**
     * Used by {@link com.imgood.textech.gui.container.ContainerPocketStorage#mergeItemStack} when
     * shift-transferring from pocket slots into the player inventory region.
     */
    public static boolean mergeOneStackBatchIntoPlayerInventory(ItemStack stack, EntityPlayer player) {
        if (stack == null || stack.stackSize <= 0 || player == null) return false;
        int maxBatch = MathHelper.clamp_int(stack.stackSize, 1, stack.getMaxStackSize());
        if (stack.stackSize > maxBatch) stack.stackSize = maxBatch;

        boolean changed = false;
        // Hotbar first (matches container reverse iteration ending at hotbar slots).
        for (int i = 0; i < 9 && stack.stackSize > 0; i++) {
            if (tryMergeIntoPlayerSlot(stack, player.inventory.mainInventory[i])) changed = true;
        }
        for (int i = 35; i >= 9 && stack.stackSize > 0; i--) {
            if (tryMergeIntoPlayerSlot(stack, player.inventory.mainInventory[i])) changed = true;
        }
        for (int i = 0; i < 9 && stack.stackSize > 0; i++) {
            if (player.inventory.mainInventory[i] == null) {
                int place = MathHelper.clamp_int(stack.stackSize, 1, stack.getMaxStackSize());
                ItemStack copy = stack.copy();
                copy.stackSize = place;
                player.inventory.mainInventory[i] = copy;
                stack.stackSize -= place;
                changed = true;
            }
        }
        for (int i = 9; i < 36 && stack.stackSize > 0; i++) {
            if (player.inventory.mainInventory[i] == null) {
                int place = MathHelper.clamp_int(stack.stackSize, 1, stack.getMaxStackSize());
                ItemStack copy = stack.copy();
                copy.stackSize = place;
                player.inventory.mainInventory[i] = copy;
                stack.stackSize -= place;
                changed = true;
            }
        }
        return changed;
    }

    private static boolean tryMergeIntoPlayerSlot(ItemStack moving, ItemStack existing) {
        if (moving == null || moving.stackSize <= 0) return false;
        if (existing == null) return false;
        if (!stacksMergeable(existing, moving)) return false;
        if (existing.stackSize >= existing.getMaxStackSize()) return false;
        int transfer = MathHelper.clamp_int(moving.stackSize, 1, existing.getMaxStackSize() - existing.stackSize);
        existing.stackSize += transfer;
        moving.stackSize -= transfer;
        return transfer > 0;
    }

    /**
     * Applies the same left/right click rules as {@code ContainerPocketStorage#slotClick} on the server.
     *
     * @return true if pocket contents or player cursor changed
     */
    public static boolean applySlotClick(PocketState state, int page, int slot, int mouseButton, EntityPlayer player) {
        if (player == null || !state.isValid(page, slot)) return false;
        if (mouseButton != 0 && mouseButton != 1) return false;

        ItemStack slotStack = state.getStack(page, slot);
        ItemStack held = player.inventory.getItemStack();
        int limit = getStackLimit(state);
        boolean changed = false;

        if (mouseButton == 0) {
            if (slotStack == null) {
                if (held != null) {
                    ItemStack placed = held.copy();
                    if (placed.stackSize > limit) placed.stackSize = limit;
                    state.setStack(page, slot, placed);
                    held.stackSize -= placed.stackSize;
                    if (held.stackSize <= 0) player.inventory.setItemStack(null);
                    changed = true;
                }
            } else if (held == null) {
                changed = withdrawPartialToCursor(state, page, slot, slotStack, player);
            } else if (stacksMergeable(slotStack, held)) {
                long combined = (long) slotStack.stackSize + held.stackSize;
                if (combined <= limit) {
                    slotStack.stackSize += held.stackSize;
                    player.inventory.setItemStack(null);
                } else {
                    int space = limit - slotStack.stackSize;
                    if (space > 0) {
                        slotStack.stackSize += space;
                        held.stackSize -= space;
                        if (held.stackSize <= 0) player.inventory.setItemStack(null);
                    }
                }
                changed = true;
            }
            return changed;
        }

        if (held == null) {
            if (slotStack != null) {
                changed = withdrawPartialToCursor(state, page, slot, slotStack, player);
            }
        } else if (slotStack == null) {
            ItemStack one = held.copy();
            one.stackSize = 1;
            state.setStack(page, slot, one);
            held.stackSize--;
            if (held.stackSize <= 0) player.inventory.setItemStack(null);
            changed = true;
        } else if (stacksMergeable(slotStack, held) && slotStack.stackSize < limit) {
            slotStack.stackSize++;
            held.stackSize--;
            if (held.stackSize <= 0) player.inventory.setItemStack(null);
            changed = true;
        }
        return changed;
    }

    private static boolean withdrawPartialToCursor(PocketState state, int page, int slot, ItemStack slotStack,
        EntityPlayer player) {
        ItemStack pickup = slotStack.copy();
        int take = MathHelper.clamp_int(slotStack.stackSize, 1, pickup.getMaxStackSize());
        pickup.stackSize = take;
        slotStack.stackSize -= take;
        if (slotStack.stackSize <= 0) {
            state.setStack(page, slot, null);
        } else {
            state.setStack(page, slot, slotStack);
        }
        player.inventory.setItemStack(pickup);
        return true;
    }
}
