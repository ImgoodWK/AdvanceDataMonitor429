package com.imgood.textech.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import fox.spiteful.avaritia.items.ItemMatterCluster;
import fox.spiteful.avaritia.items.ItemStackWrapper;

/**
 * Shared helpers for inserting into and extracting from Avaritia matter clusters.
 */
public final class MatterBallClusterUtil {

    public static final int CLUSTER_CAPACITY = 16384;

    private MatterBallClusterUtil() {}

    public static boolean isMatterCluster(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemMatterCluster;
    }

    public static List<ItemStack> insertIntoPlayerClusters(EntityPlayer player, List<ItemStack> toInsert) {
        ArrayList<ItemStack> working = new ArrayList<>(toInsert);
        InventoryPlayer inv = player.inventory;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!isMatterCluster(stack)) {
                continue;
            }
            working = insertIntoCluster(stack, working);
            if (working.isEmpty()) {
                return working;
            }
        }
        while (!working.isEmpty()) {
            ItemStack newCluster = createClusterFromDrops(working);
            if (newCluster == null) {
                break;
            }
            if (!tryAddToInventory(player, newCluster)) {
                working.add(newCluster);
                break;
            }
            working = compactStacks(working);
        }
        return working;
    }

    public static ArrayList<ItemStack> insertIntoCluster(ItemStack cluster, ArrayList<ItemStack> items) {
        Map<ItemStackWrapper, Integer> data = ItemMatterCluster.getClusterData(cluster);
        if (data == null) {
            data = new HashMap<>();
        }
        int currentTotal = 0;
        for (int count : data.values()) {
            currentTotal += count;
        }
        int capacity = CLUSTER_CAPACITY;

        for (int idx = 0; idx < items.size(); idx++) {
            ItemStack item = items.get(idx);
            if (item == null || item.stackSize <= 0) {
                continue;
            }
            int space = capacity - currentTotal;
            if (space <= 0) {
                break;
            }
            int toAdd = Math.min(item.stackSize, space);
            ItemStackWrapper wrapper = new ItemStackWrapper(item);
            Integer existing = data.get(wrapper);
            if (existing != null) {
                data.put(wrapper, existing + toAdd);
            } else {
                data.put(wrapper, toAdd);
            }
            currentTotal += toAdd;
            if (toAdd >= item.stackSize) {
                items.set(idx, null);
            } else {
                item.stackSize -= toAdd;
            }
        }

        int newTotal = 0;
        for (int cnt : data.values()) {
            newTotal += cnt;
        }
        ItemMatterCluster.setClusterData(cluster, data, newTotal);
        return compactStacks(items);
    }

    public static ItemStack createClusterFromDrops(List<ItemStack> items) {
        Map<ItemStackWrapper, Integer> data = new HashMap<>();
        int total = 0;
        int capacity = CLUSTER_CAPACITY;

        for (ItemStack item : items) {
            if (item == null || item.stackSize <= 0) {
                continue;
            }
            int toAdd = Math.min(item.stackSize, capacity - total);
            if (toAdd <= 0) {
                break;
            }
            ItemStackWrapper wrapper = new ItemStackWrapper(item);
            Integer existing = data.get(wrapper);
            if (existing != null) {
                data.put(wrapper, existing + toAdd);
            } else {
                data.put(wrapper, toAdd);
            }
            total += toAdd;
            item.stackSize -= toAdd;
        }

        if (data.isEmpty()) {
            return null;
        }
        return ItemMatterCluster.makeCluster(data);
    }

    /**
     * Extract a single item stack (size 1..maxSize) from a matter cluster. Returns null if empty.
     */
    public static ItemStack extractOne(ItemStack cluster, int maxSize) {
        if (!isMatterCluster(cluster)) {
            return null;
        }
        Map<ItemStackWrapper, Integer> data = ItemMatterCluster.getClusterData(cluster);
        if (data == null || data.isEmpty()) {
            return null;
        }
        Map.Entry<ItemStackWrapper, Integer> entry = data.entrySet()
            .iterator()
            .next();
        ItemStackWrapper wrapper = entry.getKey();
        int count = entry.getValue();
        if (wrapper == null || wrapper.stack == null || count <= 0) {
            data.remove(wrapper);
            refreshCluster(cluster, data);
            return null;
        }
        int extract = Math.min(maxSize, count);
        ItemStack result = wrapper.stack.copy();
        result.stackSize = extract;
        int remaining = count - extract;
        if (remaining <= 0) {
            data.remove(wrapper);
        } else {
            data.put(wrapper, remaining);
        }
        refreshCluster(cluster, data);
        return result;
    }

    private static void refreshCluster(ItemStack cluster, Map<ItemStackWrapper, Integer> data) {
        if (data == null || data.isEmpty()) {
            cluster.setTagCompound(null);
            return;
        }
        int total = 0;
        for (int cnt : data.values()) {
            total += cnt;
        }
        ItemMatterCluster.setClusterData(cluster, data, total);
    }

    private static ArrayList<ItemStack> compactStacks(ArrayList<ItemStack> items) {
        ArrayList<ItemStack> remainders = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && item.stackSize > 0) {
                remainders.add(item);
            }
        }
        return remainders;
    }

    private static boolean tryAddToInventory(EntityPlayer player, ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) {
            return true;
        }
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack slot = player.inventory.mainInventory[i];
            if (slot != null && slot.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(slot, stack)) {
                int space = slot.getMaxStackSize() - slot.stackSize;
                int add = Math.min(space, stack.stackSize);
                if (add > 0) {
                    slot.stackSize += add;
                    stack.stackSize -= add;
                }
                if (stack.stackSize <= 0) {
                    return true;
                }
            }
        }
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            if (player.inventory.mainInventory[i] == null) {
                player.inventory.mainInventory[i] = stack.copy();
                return true;
            }
        }
        return stack.stackSize <= 0;
    }
}
