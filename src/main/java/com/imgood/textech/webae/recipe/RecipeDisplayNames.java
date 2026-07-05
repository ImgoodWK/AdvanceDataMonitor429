package com.imgood.textech.webae.recipe;

import net.minecraft.client.resources.I18n;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-side handler / recipe type labels with Minecraft lang resolution.
 */
@SideOnly(Side.CLIENT)
public final class RecipeDisplayNames {

    private RecipeDisplayNames() {}

    /**
     * Format like {@code 合金炉 (gt.recipe.alloysmelter)} when a lang entry exists.
     *
     * @param langKey         unlocalized key (e.g. {@code gt.recipe.alloysmelter})
     * @param fallbackDisplay NEI/GT display or handler id when key is missing
     */
    public static String formatHandlerLabel(String langKey, String fallbackDisplay) {
        String key = firstNonEmpty(langKey, fallbackDisplay);
        if (key == null || key.isEmpty()) {
            return fallbackDisplay != null ? fallbackDisplay : "";
        }
        String localized = localize(key);
        if (isTranslated(key, localized)) {
            return localized + " (" + key + ")";
        }
        if (fallbackDisplay != null && !fallbackDisplay.isEmpty() && !fallbackDisplay.equals(key)) {
            String locFallback = localize(fallbackDisplay);
            if (isTranslated(fallbackDisplay, locFallback)) {
                return locFallback + " (" + key + ")";
            }
        }
        return key;
    }

    private static String localize(String key) {
        try {
            return I18n.format(key);
        } catch (Throwable ignored) {
            return key;
        }
    }

    private static boolean isTranslated(String key, String localized) {
        return localized != null && !localized.isEmpty() && !localized.equals(key);
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.isEmpty()) return a;
        if (b != null && !b.isEmpty()) return b;
        return null;
    }
}
