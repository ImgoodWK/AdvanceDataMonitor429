package com.imgood.textech.handler;

/**
 * Upgrade install/remove validation rules for the Dimensional Pocket.
 *
 * Space upgrade removal: the slot with the most items in any page needs N items;
 * baseline is 1 free slot, so at least maxItems-1 space upgrades must remain.
 * Page upgrade removal: the highest page index that contains items needs K page
 * upgrades to exist (page index 0 = base page needs 0 upgrades); so at least
 * highestUsedPage page upgrades must remain.
 *
 * Stack upgrade: max 8, each doubles the per-slot stack limit (2^n).
 * Infinite stack upgrade: max 1, sets per-slot limit to Integer.MAX_VALUE.
 */
public final class PocketUpgradeRules {

    private PocketUpgradeRules() {}

    public static boolean hasStoredItems(PocketState state) {
        return state != null && state.hasStoredItems();
    }

    /** When the pocket holds any item, no upgrade card may be removed. */
    public static boolean canRemoveUpgradeCards(PocketState state) {
        return !hasStoredItems(state);
    }

    public static int computeMinSpaceUpgrades(PocketState state) {
        if (state == null) return 0;
        int maxItemsInAnyPage = 0;
        int pageCount = state.getPageCount();
        for (int p = 0; p < pageCount; p++) {
            int n = state.countItemsInPage(p);
            if (n > maxItemsInAnyPage) maxItemsInAnyPage = n;
        }
        // baseline 1 free slot; N items need N slots => N-1 upgrades
        return Math.max(0, maxItemsInAnyPage - 1);
    }

    public static int computeMinPageUpgrades(PocketState state) {
        if (state == null) return 0;
        int pageCount = state.getPageCount();
        int highestUsedPage = 0;
        for (int p = 0; p < pageCount; p++) {
            if (state.countItemsInPage(p) > 0) highestUsedPage = p;
        }
        // page index 0 is the base page (needs 0 page upgrades);
        // page index K needs K page upgrades to exist.
        return highestUsedPage;
    }

    public static boolean canRemoveSpaceUpgrade(PocketState state, int amountToRemove) {
        if (state == null || amountToRemove <= 0) return false;
        if (!canRemoveUpgradeCards(state)) return false;
        int current = state.getSpaceUpgrades();
        int remaining = current - amountToRemove;
        int minForItems = computeMinSpaceUpgrades(state);
        if (remaining < minForItems) return false;
        if (current >= PocketState.MAX_SPACE_UPGRADES && remaining < PocketState.MAX_SPACE_UPGRADES) {
            int pagesWithContent = computeMinPageUpgrades(state);
            if (pagesWithContent > 0) return false;
        }
        return true;
    }

    public static boolean canRemovePageUpgrade(PocketState state, int amountToRemove) {
        if (state == null || amountToRemove <= 0) return false;
        if (!canRemoveUpgradeCards(state)) return false;
        int current = state.getPageUpgrades();
        int min = computeMinPageUpgrades(state);
        return (current - amountToRemove) >= min;
    }

    public static boolean canAddSpaceUpgrade(PocketState state, int amountToAdd) {
        if (state == null) return false;
        return state.getSpaceUpgrades() + amountToAdd <= PocketState.MAX_SPACE_UPGRADES;
    }

    public static boolean canAddPageUpgrade(PocketState state, int amountToAdd) {
        if (state == null) return false;
        if (state.getSpaceUpgrades() < PocketState.MAX_SPACE_UPGRADES) return false;
        return state.getPageUpgrades() + amountToAdd <= PocketState.MAX_PAGE_UPGRADES;
    }

    public static boolean canAddStackUpgrade(PocketState state, int amountToAdd) {
        if (state == null) return false;
        return state.getStackUpgrades() + amountToAdd <= PocketState.MAX_STACK_UPGRADES;
    }

    public static boolean canRemoveStackUpgrade(PocketState state, int amountToRemove) {
        if (state == null || amountToRemove <= 0) return false;
        if (!canRemoveUpgradeCards(state)) return false;
        return state.getStackUpgrades() >= amountToRemove;
    }

    public static boolean canRemoveInfiniteStackUpgrade(PocketState state) {
        if (state == null) return false;
        if (!canRemoveUpgradeCards(state)) return false;
        return state.isInfiniteStackUpgrade();
    }

    public static boolean canAddInfiniteStackUpgrade(PocketState state) {
        if (state == null) return false;
        return !state.isInfiniteStackUpgrade();
    }
}
