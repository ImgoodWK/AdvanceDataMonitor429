package com.imgood.textech.assistant;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.items.ItemAdvancePlanner;
import com.imgood.textech.items.PlannerEntry;

/**
 * Server-side service for AI assistant to interact with the Advanced Planner item in the player's inventory.
 */
public final class PlannerServerService {

    private PlannerServerService() {}

    /**
     * Find the first Advanced Planner item in the player's inventory.
     * Returns null if no planner is found.
     */
    public static ItemStack findPlanner(EntityPlayerMP player) {
        if (player == null || player.inventory == null) {
            return null;
        }
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemAdvancePlanner) {
                return stack;
            }
        }
        return null;
    }

    /**
     * Find the inventory slot index of the first planner.
     */
    public static int findPlannerSlot(EntityPlayerMP player) {
        if (player == null || player.inventory == null) {
            return -1;
        }
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemAdvancePlanner) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Add a new entry to the player's first planner.
     */
    public static String addEntry(EntityPlayerMP player, String rawText, String content, String locale) {
        boolean zh = isChinese(locale);
        ItemStack planner = findPlanner(player);
        if (planner == null) {
            return zh ? "你没有高级计划器。请先在背包中放入一个高级计划器。"
                : "You don't have an Advanced Planner. Please put one in your inventory first.";
        }
        String text = content != null && !content.trim()
            .isEmpty() ? content.trim() : rawText;
        if (text.isEmpty()) {
            return zh ? "计划内容不能为空。请说明你想添加什么计划。" : "Plan content cannot be empty. Please specify what to add.";
        }
        int slotIndex = ItemAdvancePlanner.addEntry(planner, text);
        syncPlanner(player, planner);
        AdvanceDataMonitor.LOG.info(
            "[ADM Assistant] Planner entry added: player={}, slotIndex={}, text='{}'",
            player.getCommandSenderName(),
            slotIndex,
            safe(text));
        return zh ? "已添加计划 #" + slotIndex + "：" + text : "Plan added #" + slotIndex + ": " + text;
    }

    /**
     * List all entries from the player's first planner.
     */
    public static String listEntries(EntityPlayerMP player, String locale) {
        boolean zh = isChinese(locale);
        ItemStack planner = findPlanner(player);
        if (planner == null) {
            return zh ? "你没有高级计划器。请先在背包中放入一个高级计划器。"
                : "You don't have an Advanced Planner. Please put one in your inventory first.";
        }
        List<PlannerEntry> entries = ItemAdvancePlanner.getAllEntries(planner);
        if (entries.isEmpty()) {
            return zh ? "计划器中暂无条目。" : "No entries in the planner.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(zh ? "计划/待办列表：" : "Plan/Todo list:");
        for (PlannerEntry entry : entries) {
            sb.append("\n");
            sb.append(entry.getSlotIndex())
                .append(". ");
            if (entry.isCompleted()) {
                sb.append(zh ? "[已完成] " : "[Done] ");
            } else {
                sb.append(zh ? "[待办] " : "[Todo] ");
            }
            sb.append(entry.getText());
            sb.append(" (")
                .append(entry.getFormattedTime())
                .append(")");
        }
        int completed = ItemAdvancePlanner.getCompletedCount(planner);
        int total = entries.size();
        sb.append("\n")
            .append(zh ? "已完成 " + completed + "/" + total : "Completed " + completed + "/" + total);
        return sb.toString();
    }

    /**
     * Complete a plan entry by its slot index.
     */
    public static String completeEntry(EntityPlayerMP player, int slotIndex, String locale) {
        boolean zh = isChinese(locale);
        ItemStack planner = findPlanner(player);
        if (planner == null) {
            return zh ? "你没有高级计划器。请先在背包中放入一个高级计划器。"
                : "You don't have an Advanced Planner. Please put one in your inventory first.";
        }
        PlannerEntry entry = ItemAdvancePlanner.getEntry(planner, slotIndex);
        if (entry == null) {
            return zh ? "找不到编号为 " + slotIndex + " 的计划条目。" : "No plan entry found with index " + slotIndex + ".";
        }
        ItemAdvancePlanner.toggleCompleted(planner, slotIndex);
        syncPlanner(player, planner);
        boolean nowCompleted = !entry.isCompleted(); // toggleCompleted flips it
        String status = nowCompleted ? (zh ? "[已完成]" : "[Done]") : (zh ? "[已重新打开]" : "[Reopened]");
        AdvanceDataMonitor.LOG.info(
            "[ADM Assistant] Planner entry completed: player={}, slotIndex={}, text='{}', completed={}",
            player.getCommandSenderName(),
            slotIndex,
            safe(entry.getText()),
            nowCompleted);
        return zh ? status + " 计划 #" + slotIndex + "：" + entry.getText()
            : status + " Plan #" + slotIndex + ": " + entry.getText();
    }

    /**
     * Delete a plan entry by its slot index.
     */
    public static String deleteEntry(EntityPlayerMP player, int slotIndex, String locale) {
        boolean zh = isChinese(locale);
        ItemStack planner = findPlanner(player);
        if (planner == null) {
            return zh ? "你没有高级计划器。请先在背包中放入一个高级计划器。"
                : "You don't have an Advanced Planner. Please put one in your inventory first.";
        }
        PlannerEntry entry = ItemAdvancePlanner.getEntry(planner, slotIndex);
        if (entry == null) {
            return zh ? "找不到编号为 " + slotIndex + " 的计划条目。" : "No plan entry found with index " + slotIndex + ".";
        }
        String removedText = entry.getText();
        ItemAdvancePlanner.removeEntry(planner, slotIndex);
        syncPlanner(player, planner);
        AdvanceDataMonitor.LOG.info(
            "[ADM Assistant] Planner entry deleted: player={}, slotIndex={}, text='{}'",
            player.getCommandSenderName(),
            slotIndex,
            safe(removedText));
        return zh ? "已删除计划 #" + slotIndex + "：" + removedText : "Deleted plan #" + slotIndex + ": " + removedText;
    }

    /**
     * Modify a plan entry's content by its slot index.
     */
    public static String modifyEntry(EntityPlayerMP player, int slotIndex, String newContent, String locale) {
        boolean zh = isChinese(locale);
        ItemStack planner = findPlanner(player);
        if (planner == null) {
            return zh ? "你没有高级计划器。请先在背包中放入一个高级计划器。"
                : "You don't have an Advanced Planner. Please put one in your inventory first.";
        }
        if (newContent == null || newContent.trim()
            .isEmpty()) {
            return zh ? "修改内容不能为空。" : "Modified content cannot be empty.";
        }
        PlannerEntry entry = ItemAdvancePlanner.getEntry(planner, slotIndex);
        if (entry == null) {
            return zh ? "找不到编号为 " + slotIndex + " 的计划条目。" : "No plan entry found with index " + slotIndex + ".";
        }
        String oldText = entry.getText();
        ItemAdvancePlanner.setEntry(planner, slotIndex, newContent, entry.isCompleted());
        syncPlanner(player, planner);
        AdvanceDataMonitor.LOG.info(
            "[ADM Assistant] Planner entry modified: player={}, slotIndex={}, old='{}', new='{}'",
            player.getCommandSenderName(),
            slotIndex,
            safe(oldText),
            safe(newContent.trim()));
        return zh ? "已修改计划 #" + slotIndex + "：" + oldText + " → " + newContent.trim()
            : "Modified plan #" + slotIndex + ": " + oldText + " → " + newContent.trim();
    }

    private static void syncPlanner(EntityPlayerMP player, ItemStack planner) {
        int slot = findPlannerSlot(player);
        if (slot >= 0) {
            player.inventory.setInventorySlotContents(slot, planner);
        }
    }

    private static boolean isChinese(String locale) {
        return locale == null || locale.trim()
            .isEmpty()
            || locale.toLowerCase()
                .startsWith("zh");
    }

    private static String safe(String text) {
        if (text == null) return "";
        return text.replace((char) 10, ' ')
            .replace((char) 13, ' ');
    }
}
