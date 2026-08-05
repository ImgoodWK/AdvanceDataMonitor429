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
    private static boolean splitFullActive = false;
    private static int splitFullPageCount = 0;
    private static int splitFullSlotsPerPage = 0;
    private static int splitFullNextPage = 0;

    private PocketClientCache() {}

    /**
     * Apply a packet and report whether it was a normal single-page navigation.
     * Split full-snapshot fragments intentionally return false because they
     * populate a page without changing the user's current page.
     */
    public static boolean apply(PacketPocketSync message) {
        if (message == null) return false;

        if (message.kind == PacketPocketSync.KIND_METADATA
            && message.pageIndex == PacketPocketSync.FULL_SNAPSHOT_PAGE_INDEX) {
            if (!isValidDimensions(message.pageCount, message.slotsPerPage)) {
                resetSplitFull();
                return false;
            }
            applyMetadata(message);
            pages.clear();
            splitFullActive = true;
            splitFullPageCount = message.pageCount;
            splitFullSlotsPerPage = message.slotsPerPage;
            splitFullNextPage = 0;
            ensurePageShape();
            received = true;
            return false;
        }

        if (!isValidDimensions(message.pageCount, message.slotsPerPage)) {
            resetSplitFull();
            return false;
        }

        if (splitFullActive && message.kind == PacketPocketSync.KIND_SINGLE_PAGE) {
            if (message.pageCount != splitFullPageCount
                || message.slotsPerPage != splitFullSlotsPerPage
                || message.pageIndex != splitFullNextPage
                || message.pages.size() != 1
                || message.pages.get(0) == null
                || message.pages.get(0).pageIndex != splitFullNextPage
                || message.pages.get(0).slots == null
                || message.pages.get(0).slots.length != splitFullSlotsPerPage) {
                resetSplitFull();
                return false;
            }
            applyMetadata(message);
            ensurePageShape();
            applyPagePayload(message.pages.get(0));
            splitFullNextPage++;
            if (splitFullNextPage >= splitFullPageCount) {
                resetSplitFull();
            }
            received = true;
            return false;
        }

        // A complete snapshot, ordinary metadata packet, or an unexpected
        // packet type terminates an in-flight split so a later single page is
        // never permanently classified as a fragment.
        if (message.kind != PacketPocketSync.KIND_SINGLE_PAGE) {
            resetSplitFull();
        }

        applyMetadata(message);
        boolean replacePages = message.kind == PacketPocketSync.KIND_FULL;
        if (replacePages) {
            pages.clear();
        }
        ensurePageShape();

        if (message.kind == PacketPocketSync.KIND_SINGLE_PAGE) {
            if (message.pages.size() != 1 || message.pages.get(0) == null
                || message.pages.get(0).pageIndex != message.pageIndex
                || message.pageIndex < 0 || message.pageIndex >= pageCount
                || message.pages.get(0).slots == null
                || message.pages.get(0).slots.length != slotsPerPage) {
                return false;
            }
            applyPagePayload(message.pages.get(0));
            setCurrentPage(message.pageIndex);
            received = true;
            return true;
        }

        for (PacketPocketSync.PagePayload payload : message.pages) {
            if (payload == null || payload.pageIndex < 0 || payload.pageIndex >= pageCount
                || payload.slots == null || payload.slots.length != slotsPerPage) {
                continue;
            }
            applyPagePayload(payload);
        }
        received = true;
        if (currentPage >= pageCount) currentPage = Math.max(0, pageCount - 1);
        return false;
    }

    private static boolean isValidDimensions(int newPageCount, int newSlotsPerPage) {
        return newPageCount >= 1 && newPageCount <= PocketState.PAGES_CAP
            && newSlotsPerPage >= 1 && newSlotsPerPage <= PocketState.SLOTS_PER_PAGE_CAP;
    }

    private static void applyMetadata(PacketPocketSync message) {
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
    }

    private static void ensurePageShape() {
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
        if (currentPage >= pageCount) currentPage = Math.max(0, pageCount - 1);
    }

    private static void applyPagePayload(PacketPocketSync.PagePayload payload) {
        ItemStack[] arr = new ItemStack[slotsPerPage];
        for (int s = 0; s < slotsPerPage; s++) {
            arr[s] = payload.slots[s];
        }
        pages.put(payload.pageIndex, arr);
    }

    private static void resetSplitFull() {
        splitFullActive = false;
        splitFullPageCount = 0;
        splitFullSlotsPerPage = 0;
        splitFullNextPage = 0;
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
