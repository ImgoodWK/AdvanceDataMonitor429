package com.imgood.advancedatamonitor.items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.Config;
import com.imgood.advancedatamonitor.client.ItemClientGui;
import com.imgood.advancedatamonitor.network.packet.PacketPlannerSync;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Display names / 显示名称:
 * - EN: Advance Planner
 * - ZH: 高级计划器
 * Lang keys: item.advancePlanner.name, adm.planner.title
 */
public class ItemAdvancePlanner extends Item {

    private static final String NBT_KEY_ENTRIES = "plannerEntries";
    private static final String NBT_KEY_NEXT_SLOT = "nextSlotIndex";
    private static final String NBT_KEY_HUD_ENABLED = "hudEnabled";
    private static final String NBT_KEY_HUD_MAX_DISPLAY = "hudMaxDisplay";
    private static final String NBT_KEY_HUD_POS_X = "hudPosX";
    private static final String NBT_KEY_HUD_POS_Y = "hudPosY";
    private static final String NBT_KEY_HUD_SCALE = "hudScale";
    private static final String NBT_KEY_HUD_WIDTH = "hudWidth";
    private static final String NBT_KEY_HUD_TITLE = "hudTitle";
    private static final int DEFAULT_HUD_MAX_DISPLAY = 5;
    private static final float DEFAULT_HUD_POS_X = 1.0f;
    private static final float DEFAULT_HUD_POS_Y = 0.0f;
    private static final float DEFAULT_HUD_SCALE = 1.0f;
    private static final int DEFAULT_HUD_WIDTH = 160;

    public ItemAdvancePlanner() {
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabTools);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) {
            if (player.isSneaking()) {
                boolean current = isHudEnabled(stack);
                setHudEnabled(stack, !current);
                syncPlannerToServer(player, stack);
                String msg = !current ? net.minecraft.client.resources.I18n.format("adm.planner.hud_enabled")
                    : net.minecraft.client.resources.I18n.format("adm.planner.hud_disabled");
                player.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + msg));
            } else {
                openPlannerGui(stack, player);
            }
        }
        return stack;
    }

    @SideOnly(Side.CLIENT)
    private void openPlannerGui(ItemStack stack, EntityPlayer player) {
        ItemClientGui.openPlannerGui(stack, player);
    }

    // ========== 核心数据操作 API ==========

    public static NBTTagCompound getOrCreatePlannerNBT(ItemStack stack) {
        if (stack == null) {
            return new NBTTagCompound();
        }
        NBTTagCompound root = stack.getTagCompound();
        if (root == null) {
            root = new NBTTagCompound();
            stack.setTagCompound(root);
        }
        if (!root.hasKey(NBT_KEY_ENTRIES)) {
            root.setTag(NBT_KEY_ENTRIES, new NBTTagList());
        }
        if (!root.hasKey(NBT_KEY_NEXT_SLOT)) {
            root.setInteger(NBT_KEY_NEXT_SLOT, 1);
        }
        return root;
    }

    public static NBTTagList getEntriesTagList(ItemStack stack) {
        NBTTagCompound root = getOrCreatePlannerNBT(stack);
        return root.getTagList(NBT_KEY_ENTRIES, 10);
    }

    public static List<PlannerEntry> getAllEntries(ItemStack stack) {
        NBTTagList list = getEntriesTagList(stack);
        List<PlannerEntry> entries = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) {
            PlannerEntry entry = PlannerEntry.fromNBT(list.getCompoundTagAt(i));
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    public static PlannerEntry getEntry(ItemStack stack, int slotIndex) {
        NBTTagList list = getEntriesTagList(stack);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            if (tag.getInteger("slotIndex") == slotIndex) {
                return PlannerEntry.fromNBT(tag);
            }
        }
        return null;
    }

    public static int getNextSlotIndex(ItemStack stack) {
        NBTTagCompound root = getOrCreatePlannerNBT(stack);
        return root.getInteger(NBT_KEY_NEXT_SLOT);
    }

    public static void setEntry(ItemStack stack, int slotIndex, String text, boolean completed) {
        NBTTagCompound root = getOrCreatePlannerNBT(stack);
        NBTTagList list = root.getTagList(NBT_KEY_ENTRIES, 10);

        boolean found = false;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            if (tag.getInteger("slotIndex") == slotIndex) {
                tag.setString("text", text == null ? "" : text);
                tag.setBoolean("completed", completed);
                found = true;
                break;
            }
        }

        if (!found) {
            PlannerEntry newEntry = new PlannerEntry(slotIndex, text);
            newEntry.setCompleted(completed);
            list.appendTag(newEntry.toNBT());
            updateNextSlotIndex(root, slotIndex);
        }

        root.setTag(NBT_KEY_ENTRIES, list);
        stack.setTagCompound(root);
    }

    public static int addEntry(ItemStack stack, String text) {
        int nextSlot = getNextSlotIndex(stack);
        setEntry(stack, nextSlot, text, false);
        NBTTagCompound root = getOrCreatePlannerNBT(stack);
        root.setInteger(NBT_KEY_NEXT_SLOT, nextSlot + 1);
        stack.setTagCompound(root);
        return nextSlot;
    }

    public static void removeEntry(ItemStack stack, int slotIndex) {
        NBTTagCompound root = getOrCreatePlannerNBT(stack);
        NBTTagList list = root.getTagList(NBT_KEY_ENTRIES, 10);
        NBTTagList newList = new NBTTagList();

        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            if (tag.getInteger("slotIndex") != slotIndex) {
                newList.appendTag(tag);
            }
        }

        // Renumber remaining entries to be consecutive starting from 1
        renumberEntries(newList);

        root.setTag(NBT_KEY_ENTRIES, newList);
        root.setInteger(NBT_KEY_NEXT_SLOT, newList.tagCount() + 1);
        stack.setTagCompound(root);
    }

    public static void toggleCompleted(ItemStack stack, int slotIndex) {
        NBTTagCompound root = getOrCreatePlannerNBT(stack);
        NBTTagList list = root.getTagList(NBT_KEY_ENTRIES, 10);

        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            if (tag.getInteger("slotIndex") == slotIndex) {
                tag.setBoolean("completed", !tag.getBoolean("completed"));
                break;
            }
        }

        root.setTag(NBT_KEY_ENTRIES, list);
        stack.setTagCompound(root);
    }

    public static void clearAllEntries(ItemStack stack) {
        NBTTagCompound root = getOrCreatePlannerNBT(stack);
        root.setTag(NBT_KEY_ENTRIES, new NBTTagList());
        root.setInteger(NBT_KEY_NEXT_SLOT, 1);
        stack.setTagCompound(root);
    }

    public static int getEntryCount(ItemStack stack) {
        return getEntriesTagList(stack).tagCount();
    }

    public static boolean hasEntry(ItemStack stack, int slotIndex) {
        return getEntry(stack, slotIndex) != null;
    }

    // ========== 同步辅助方法 ==========

    /**
     * Find the inventory slot containing the given planner stack.
     * Uses reference equality first (fast path), then falls back to item type comparison
     * in case the reference was replaced by an inventory sync from the server.
     */
    public static int findSlotInInventory(EntityPlayer player, ItemStack target) {
        if (player == null || player.inventory == null) return -1;

        // Fast path: reference equality
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            if (player.inventory.getStackInSlot(i) == target) {
                return i;
            }
        }

        // Fallback: search by item type (handles cases where the reference was replaced)
        if (target != null && target.getItem() instanceof ItemAdvancePlanner) {
            for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
                ItemStack slotStack = player.inventory.getStackInSlot(i);
                if (slotStack != null && slotStack.getItem() instanceof ItemAdvancePlanner) {
                    return i;
                }
            }
        }

        return -1;
    }

    public static void syncPlannerToServer(EntityPlayer player, ItemStack stack) {
        if (player == null || stack == null) return;
        int slot = findSlotInInventory(player, stack);
        if (slot >= 0) {
            NBTTagCompound nbt = stack.getTagCompound();
            AdvanceDataMonitor.ADMCHANEL.sendToServer(
                new PacketPlannerSync(slot, nbt != null ? (NBTTagCompound) nbt.copy() : new NBTTagCompound()));
        }
    }

    // ========== 查询 API ==========

    public static int getCompletedCount(ItemStack stack) {
        int count = 0;
        for (PlannerEntry entry : getAllEntries(stack)) {
            if (entry.isCompleted()) {
                count++;
            }
        }
        return count;
    }

    public static int getPendingCount(ItemStack stack) {
        int count = 0;
        for (PlannerEntry entry : getAllEntries(stack)) {
            if (!entry.isCompleted()) {
                count++;
            }
        }
        return count;
    }

    public static List<PlannerEntry> getEntriesSorted(ItemStack stack, PlannerMergeMode mode) {
        List<PlannerEntry> entries = getAllEntries(stack);
        switch (mode) {
            case BY_TIME:
                Collections.sort(entries, new Comparator<PlannerEntry>() {

                    @Override
                    public int compare(PlannerEntry a, PlannerEntry b) {
                        return Long.compare(a.getTimestamp(), b.getTimestamp());
                    }
                });
                break;
            case BY_INDEX:
                Collections.sort(entries, new Comparator<PlannerEntry>() {

                    @Override
                    public int compare(PlannerEntry a, PlannerEntry b) {
                        return Integer.compare(a.getSlotIndex(), b.getSlotIndex());
                    }
                });
                break;
        }
        return entries;
    }

    // ========== 整合/合并 API ==========

    public static ItemStack mergePlanners(ItemStack source1, ItemStack source2, PlannerMergeMode mode) {
        if (source1 == null && source2 == null) {
            return null;
        }
        if (source1 == null) {
            return source2.copy();
        }
        if (source2 == null) {
            return source1.copy();
        }

        ItemStack result = source1.copy();
        List<PlannerEntry> entries1 = getAllEntries(source1);
        List<PlannerEntry> entries2 = getAllEntries(source2);

        List<PlannerEntry> merged;
        switch (mode) {
            case BY_TIME:
                merged = mergeByTime(entries1, entries2);
                break;
            case BY_INDEX:
            default:
                merged = mergeByIndex(entries1, entries2);
                break;
        }

        clearAllEntries(result);
        for (PlannerEntry entry : merged) {
            int newSlot = addEntry(result, entry.getText());
            setEntry(result, newSlot, entry.getText(), entry.isCompleted());
            overwriteTimestamp(result, newSlot, entry.getTimestamp());
        }

        return result;
    }

    public static ItemStack mergeMultiplePlanners(List<ItemStack> stacks, PlannerMergeMode mode) {
        if (stacks == null || stacks.isEmpty()) {
            return null;
        }
        if (stacks.size() == 1) {
            return stacks.get(0)
                .copy();
        }

        ItemStack result = stacks.get(0)
            .copy();
        for (int i = 1; i < stacks.size(); i++) {
            result = mergePlanners(result, stacks.get(i), mode);
        }
        return result;
    }

    public static int countPlannerEntriesInInventory(EntityPlayer player) {
        int total = 0;
        for (ItemStack stack : getPlannerStacksInInventory(player)) {
            total += getEntryCount(stack);
        }
        return total;
    }

    public static List<ItemStack> getPlannerStacksInInventory(EntityPlayer player) {
        List<ItemStack> planners = new ArrayList<>();
        if (player == null || player.inventory == null) {
            return planners;
        }
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemAdvancePlanner) {
                planners.add(stack);
            }
        }
        return planners;
    }

    public static int countPlannersInInventory(EntityPlayer player) {
        return getPlannerStacksInInventory(player).size();
    }

    // ========== HUD API ==========

    public static boolean isHudEnabled(ItemStack stack) {
        NBTTagCompound root = getOrCreatePlannerNBT(stack);
        return root.getBoolean(NBT_KEY_HUD_ENABLED);
    }

    public static void setHudEnabled(ItemStack stack, boolean enabled) {
        NBTTagCompound root = getOrCreatePlannerNBT(stack);
        root.setBoolean(NBT_KEY_HUD_ENABLED, enabled);
        stack.setTagCompound(root);
    }

    public static int getHudMaxDisplay(ItemStack stack) {
        NBTTagCompound root = getOrCreatePlannerNBT(stack);
        int val = root.getInteger(NBT_KEY_HUD_MAX_DISPLAY);
        return val > 0 ? val : DEFAULT_HUD_MAX_DISPLAY;
    }

    public static void setHudMaxDisplay(ItemStack stack, int maxDisplay) {
        NBTTagCompound root = getOrCreatePlannerNBT(stack);
        root.setInteger(
            NBT_KEY_HUD_MAX_DISPLAY,
            clamp(maxDisplay, Config.plannerHudMinMaxDisplay, Config.plannerHudMaxMaxDisplay));
        stack.setTagCompound(root);
    }

    public static float getHudPosX(ItemStack stack) {
        NBTTagCompound root = getOrCreatePlannerNBT(stack);
        return root.hasKey(NBT_KEY_HUD_POS_X) ? root.getFloat(NBT_KEY_HUD_POS_X) : DEFAULT_HUD_POS_X;
    }

    public static void setHudPosX(ItemStack stack, float value) {
        NBTTagCompound root = getOrCreatePlannerNBT(stack);
        root.setFloat(NBT_KEY_HUD_POS_X, clamp(value, Config.plannerHudMinPosX, Config.plannerHudMaxPosX));
        stack.setTagCompound(root);
    }

    public static float getHudPosY(ItemStack stack) {
        NBTTagCompound root = getOrCreatePlannerNBT(stack);
        return root.hasKey(NBT_KEY_HUD_POS_Y) ? root.getFloat(NBT_KEY_HUD_POS_Y) : DEFAULT_HUD_POS_Y;
    }

    public static void setHudPosY(ItemStack stack, float value) {
        NBTTagCompound root = getOrCreatePlannerNBT(stack);
        root.setFloat(NBT_KEY_HUD_POS_Y, clamp(value, Config.plannerHudMinPosY, Config.plannerHudMaxPosY));
        stack.setTagCompound(root);
    }

    public static float getHudScale(ItemStack stack) {
        NBTTagCompound root = getOrCreatePlannerNBT(stack);
        return root.hasKey(NBT_KEY_HUD_SCALE) ? root.getFloat(NBT_KEY_HUD_SCALE) : DEFAULT_HUD_SCALE;
    }

    public static void setHudScale(ItemStack stack, float value) {
        NBTTagCompound root = getOrCreatePlannerNBT(stack);
        root.setFloat(NBT_KEY_HUD_SCALE, clamp(value, Config.plannerHudMinScale, Config.plannerHudMaxScale));
        stack.setTagCompound(root);
    }

    public static int getHudWidth(ItemStack stack) {
        NBTTagCompound root = getOrCreatePlannerNBT(stack);
        return root.hasKey(NBT_KEY_HUD_WIDTH) ? root.getInteger(NBT_KEY_HUD_WIDTH) : DEFAULT_HUD_WIDTH;
    }

    public static void setHudWidth(ItemStack stack, int value) {
        NBTTagCompound root = getOrCreatePlannerNBT(stack);
        root.setInteger(NBT_KEY_HUD_WIDTH, clamp(value, Config.plannerHudMinWidth, Config.plannerHudMaxWidth));
        stack.setTagCompound(root);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static String getHudTitle(ItemStack stack) {
        NBTTagCompound root = getOrCreatePlannerNBT(stack);
        return root.getString(NBT_KEY_HUD_TITLE);
    }

    public static void setHudTitle(ItemStack stack, String title) {
        NBTTagCompound root = getOrCreatePlannerNBT(stack);
        root.setString(NBT_KEY_HUD_TITLE, title != null ? title : "");
        stack.setTagCompound(root);
    }

    // ========== 排序/移动 API ==========

    public static void swapEntries(ItemStack stack, int slotA, int slotB) {
        if (slotA == slotB) return;
        NBTTagCompound root = getOrCreatePlannerNBT(stack);
        NBTTagList list = root.getTagList(NBT_KEY_ENTRIES, 10);

        NBTTagCompound tagA = null;
        NBTTagCompound tagB = null;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            if (tag.getInteger("slotIndex") == slotA) {
                tagA = tag;
            } else if (tag.getInteger("slotIndex") == slotB) {
                tagB = tag;
            }
        }

        if (tagA != null && tagB != null) {
            int tempIndex = tagA.getInteger("slotIndex");
            tagA.setInteger("slotIndex", tagB.getInteger("slotIndex"));
            tagB.setInteger("slotIndex", tempIndex);
            root.setTag(NBT_KEY_ENTRIES, list);
            stack.setTagCompound(root);
        }
    }

    public static void moveEntryToTop(ItemStack stack, int slotIndex) {
        if (slotIndex <= 1) return;
        NBTTagCompound root = getOrCreatePlannerNBT(stack);
        NBTTagList list = root.getTagList(NBT_KEY_ENTRIES, 10);

        NBTTagCompound tagTarget = null;
        NBTTagCompound tagSlot1 = null;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            int idx = tag.getInteger("slotIndex");
            if (idx == slotIndex) {
                tagTarget = tag;
            } else if (idx == 1) {
                tagSlot1 = tag;
            }
        }

        if (tagTarget == null) return;

        tagTarget.setInteger("slotIndex", 1);
        if (tagSlot1 != null) {
            tagSlot1.setInteger("slotIndex", slotIndex);
        }

        root.setTag(NBT_KEY_ENTRIES, list);
        stack.setTagCompound(root);
    }

    // ========== 内部辅助方法 ==========

    private static void renumberEntries(NBTTagList list) {
        if (list.tagCount() <= 1) return;

        List<NBTTagCompound> sorted = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) {
            sorted.add(list.getCompoundTagAt(i));
        }
        Collections.sort(sorted, new Comparator<NBTTagCompound>() {

            @Override
            public int compare(NBTTagCompound a, NBTTagCompound b) {
                return Integer.compare(a.getInteger("slotIndex"), b.getInteger("slotIndex"));
            }
        });

        // Reassign consecutive indices starting from 1
        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i)
                .setInteger("slotIndex", i + 1);
        }
    }

    private static void overwriteTimestamp(ItemStack stack, int slotIndex, long timestamp) {
        NBTTagCompound root = getOrCreatePlannerNBT(stack);
        NBTTagList list = root.getTagList(NBT_KEY_ENTRIES, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            if (tag.getInteger("slotIndex") == slotIndex) {
                tag.setLong("timestamp", timestamp);
                break;
            }
        }
        root.setTag(NBT_KEY_ENTRIES, list);
        stack.setTagCompound(root);
    }

    private static void updateNextSlotIndex(NBTTagCompound root, int usedSlot) {
        int current = root.getInteger(NBT_KEY_NEXT_SLOT);
        if (usedSlot >= current) {
            root.setInteger(NBT_KEY_NEXT_SLOT, usedSlot + 1);
        }
    }

    private static List<PlannerEntry> mergeByTime(List<PlannerEntry> list1, List<PlannerEntry> list2) {
        List<PlannerEntry> merged = new ArrayList<>();
        merged.addAll(list1);
        merged.addAll(list2);
        Collections.sort(merged, new Comparator<PlannerEntry>() {

            @Override
            public int compare(PlannerEntry a, PlannerEntry b) {
                return Long.compare(a.getTimestamp(), b.getTimestamp());
            }
        });

        List<PlannerEntry> result = new ArrayList<>();
        int slot = 1;
        for (PlannerEntry entry : merged) {
            PlannerEntry newEntry = entry.copy();
            newEntry.setSlotIndex(slot++);
            result.add(newEntry);
        }
        return result;
    }

    private static List<PlannerEntry> mergeByIndex(List<PlannerEntry> list1, List<PlannerEntry> list2) {
        List<PlannerEntry> merged = new ArrayList<>();
        merged.addAll(list1);
        merged.addAll(list2);

        // Sort by original slotIndex first, then by timestamp for duplicate indices
        Collections.sort(merged, new Comparator<PlannerEntry>() {

            @Override
            public int compare(PlannerEntry a, PlannerEntry b) {
                int indexCmp = Integer.compare(a.getSlotIndex(), b.getSlotIndex());
                if (indexCmp != 0) {
                    return indexCmp;
                }
                return Long.compare(a.getTimestamp(), b.getTimestamp());
            }
        });

        // Reassign consecutive slot indices starting from 1
        List<PlannerEntry> result = new ArrayList<>();
        int slot = 1;
        for (PlannerEntry entry : merged) {
            PlannerEntry newEntry = entry.copy();
            newEntry.setSlotIndex(slot++);
            result.add(newEntry);
        }
        return result;
    }
}
