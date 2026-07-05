package com.imgood.textech.webae.icon;

import java.util.ArrayList;
import java.util.List;

import com.imgood.textech.webae.recipe.RecipeItemEntries;

/**
 * Canonical icon cache keys — must match {@link RecipeItemEntries#buildItemId}
 * and the frontend {@code iconLookupIds} helper.
 */
public final class IconItemId {

    public static final String FLUID_PREFIX = "fluid:";

    private IconItemId() {}

    public static String build(String registryName, int meta) {
        return RecipeItemEntries.buildItemId(registryName, meta);
    }

    /**
     * Ordered lookup candidates: exact id first, then registry-only when meta was specified.
     */
    public static List<String> lookupCandidates(String itemId) {
        List<String> out = new ArrayList<String>();
        if (itemId == null || itemId.isEmpty()) return out;
        out.add(itemId);
        if (itemId.startsWith(FLUID_PREFIX)) return out;
        int colon = itemId.lastIndexOf(':');
        if (colon > 0) {
            String suffix = itemId.substring(colon + 1);
            if (suffix.matches("\\d+")) {
                String base = itemId.substring(0, colon);
                if (!base.isEmpty() && !out.contains(base)) {
                    out.add(base);
                }
            }
        }
        return out;
    }
}
