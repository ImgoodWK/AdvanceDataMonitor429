package com.imgood.advancedatamonitor.handler;

/**
 * Upgrade install/remove validation rules for the Dimensional Pocket.
 *
 * Space upgrade removal: the slot with the most items in any page needs N items;
 * baseline is 1 free slot, so at least maxItems-1 space upgrades must remain.
 * Page upgrade removal: the highest page index that contains items needs K page
 * upgrades to exist (page index 0 = base page needs 0 upgrades); so at least
 * highestUsedPage page upgrades must remain.
 */
public final class PocketUpgradeRules {

    private PocketUpgradeRules() {}

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
        if (state == null) return false;
        int current = state.getSpaceUpgrades();
        int min = computeMinSpaceUpgrades(state);
        return (current - amountToRemove) >= min;
    }

    public static boolean canRemovePageUpgrade(PocketState state, int amountToRemove) {
        if (state == null) return false;
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
        // Page upgrades require space upgrades to be fully stacked (64).
        if (state.getSpaceUpgrades() < PocketState.MAX_SPACE_UPGRADES) return false;
        return state.getPageUpgrades() + amountToAdd <= PocketState.MAX_PAGE_UPGRADES;
    }
}
