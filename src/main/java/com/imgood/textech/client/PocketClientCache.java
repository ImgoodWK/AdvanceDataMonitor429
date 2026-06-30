package com.imgood.textech.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.imgood.textech.handler.PocketState;
import com.imgood.textech.network.packet.PacketPocketSync;

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
    private static int stackUpgrades = 0;
    private static boolean infiniteStackUpgrade = false;
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
        stackUpgrades = message.stackUpgrades;
        infiniteStackUpgrade = message.infiniteStackUpgrade;
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
        if (message.kind == PacketPocketSync.KIND_METADATA) {
            if (currentPage >= pageCount) currentPage = Math.max(0, pageCount - 1);
            return;
        }
        for (int p = 0; p < pageCount; p++) {
            ItemStack[] existing = pages.get(p);
            if (existing == null || existing.length != slotsPerPage) {
                pages.put(p, new ItemStack[slotsPerPage]);
            }
        }
        java.util.Iterator<Integer> it = pages.keySet()
            .iterator();
        while (it.hasNext()) {
            if (it.next() >= pageCount) it.remove();
        }

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
        if (message.kind == PacketPocketSync.KIND_SINGLE_PAGE) {
            setCurrentPage(message.pageIndex);
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

    public static int getStackUpgrades() {
        return stackUpgrades;
    }

    public static boolean isInfiniteStackUpgrade() {
        return infiniteStackUpgrade;
    }

    public static int getStackMultiplier() {
        if (infiniteStackUpgrade) return Integer.MAX_VALUE;
        if (stackUpgrades == 0) return 1;
        return 1 << stackUpgrades;
    }

    public static int getStackLimit() {
        if (infiniteStackUpgrade) return Integer.MAX_VALUE;
        int mult = getStackMultiplier();
        if (mult == Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return Math.min(64 * mult, Integer.MAX_VALUE);
    }

    public static boolean isEnabled() {
        return true;
    }

    public static void setEnabled(boolean v) {
        enabled = true;
    }

    public static void setCollapsed(boolean v) {
        collapsed = v;
    }

    public static float getWindowX() {
        return windowX;
    }

    public static float getWindowY() {
        return windowY;
    }

    /**
     * Optimistic local update of the cached window position. Called from
     * {@link GuiPocketOverlay#onDragFinished()} so the next attach() reads the
     * just-released position instead of the stale pre-drag value while the
     * server's PacketPocketSync round-trip is still in flight. Without this,
     * attach() snaps the panel back to the old position for one frame (the
     * "flash at the original start position" on drag release).
     */
    public static void setWindowPos(float x, float y) {
        windowX = x;
        windowY = y;
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

    public static int getMaxStackUpgrades() {
        return PocketState.MAX_STACK_UPGRADES;
    }

    /** True if any synced page contains a non-empty slot. */
    public static boolean hasStoredItems() {
        for (int p = 0; p < pageCount; p++) {
            ItemStack[] arr = pages.get(p);
            if (arr == null) continue;
            for (int s = 0; s < arr.length; s++) {
                if (arr[s] != null) return true;
            }
        }
        return false;
    }
}
