package com.imgood.advancedatamonitor.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.imgood.advancedatamonitor.handler.PocketState;
import com.imgood.advancedatamonitor.network.packet.PacketPocketSync;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-side mirror of the player's PocketState, updated from PacketPocketSync.
 * The overlay handler, config GUI, and item tooltip read this cache so they
 * never touch the server-side PocketStore directly.
 */
@SideOnly(Side.CLIENT)
public final class PocketClientCache {

    private static int spaceUpgrades = 0;
    private static int pageUpgrades = 0;
    private static boolean enabled = false;
    private static float windowX = 0.02f;
    private static float windowY = 0.02f;
    private static boolean collapsed = false;
    private static int currentPage = 0;
    private static int pageCount = 1;
    private static int slotsPerPage = 1;
    private static final Map<Integer, ItemStack[]> pages = new HashMap<Integer, ItemStack[]>();
    private static boolean received = false;

    private PocketClientCache() {}

    public static void apply(PacketPocketSync message) {
        if (message == null) return;
        spaceUpgrades = message.spaceUpgrades;
        pageUpgrades = message.pageUpgrades;
        enabled = message.enabled;
        windowX = message.windowX;
        windowY = message.windowY;
        collapsed = message.collapsed;
        pageCount = message.pageCount;
        slotsPerPage = message.slotsPerPage;
        received = true;

        if (message.kind == PacketPocketSync.KIND_FULL) {
            pages.clear();
        }
        // Ensure page arrays match the latest slotsPerPage size.
        for (int p = 0; p < pageCount; p++) {
            ItemStack[] existing = pages.get(p);
            if (existing == null || existing.length != slotsPerPage) {
                pages.put(p, new ItemStack[slotsPerPage]);
            }
        }
        // Remove pages beyond current pageCount.
        pages.keySet()
            .removeIf(p -> p >= pageCount);

        for (PacketPocketSync.PagePayload payload : message.pages) {
            if (payload.pageIndex < 0 || payload.pageIndex >= pageCount) continue;
            ItemStack[] arr = new ItemStack[slotsPerPage];
            if (payload.slots != null) {
                int copy = Math.min(payload.slots.length, slotsPerPage);
                for (int s = 0; s < copy; s++) {
                    arr[s] = payload.slots[s];
                }
            }
            pages.put(payload.pageIndex, arr);
        }
        if (currentPage >= pageCount) currentPage = Math.max(0, pageCount - 1);
    }

    public static boolean isReceived() {
        return received;
    }

    public static int getSpaceUpgrades() {
        return spaceUpgrades;
    }

    public static int getPageUpgrades() {
        return pageUpgrades;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean v) {
        enabled = v;
    }

    public static float getWindowX() {
        return windowX;
    }

    public static float getWindowY() {
        return windowY;
    }

    public static boolean isCollapsed() {
        return collapsed;
    }

    public static int getPageCount() {
        return pageCount;
    }

    public static int getSlotsPerPage() {
        return slotsPerPage;
    }

    public static int getCurrentPage() {
        return currentPage;
    }

    public static void setCurrentPage(int page) {
        if (page < 0) page = 0;
        if (page >= pageCount) page = Math.max(0, pageCount - 1);
        currentPage = page;
    }

    public static ItemStack getStack(int page, int slot) {
        if (page < 0 || page >= pageCount) return null;
        ItemStack[] arr = pages.get(page);
        if (arr == null || slot < 0 || slot >= arr.length) return null;
        return arr[slot];
    }

    public static ItemStack[] getPage(int page) {
        if (page < 0 || page >= pageCount) return new ItemStack[0];
        ItemStack[] arr = pages.get(page);
        if (arr == null) {
            arr = new ItemStack[slotsPerPage];
            pages.put(page, arr);
        }
        return arr;
    }

    public static int getCapacityTotal() {
        return pageCount * slotsPerPage;
    }

    public static int getSlotsPerPageCap() {
        return PocketState.SLOTS_PER_PAGE_CAP;
    }

    public static int getMaxSpaceUpgrades() {
        return PocketState.MAX_SPACE_UPGRADES;
    }

    public static int getMaxPageUpgrades() {
        return PocketState.MAX_PAGE_UPGRADES;
    }
}
