package com.imgood.textech.compat.bq;

import com.imgood.textech.Config;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

/**
 * Detects BetterQuesting + Standard Expansion at runtime.
 */
public final class BqCompat {

    private static Boolean loaded;
    private static Boolean standardLoaded;

    private BqCompat() {}

    public static boolean isModLoaded() {
        if (loaded == null) {
            loaded = Boolean.valueOf(Loader.isModLoaded("betterquesting"));
        }
        return loaded.booleanValue();
    }

    public static boolean isStandardExpansionLoaded() {
        if (standardLoaded == null) {
            standardLoaded = Boolean.valueOf(Loader.isModLoaded("bq_standard"));
        }
        return standardLoaded.booleanValue();
    }

    public static boolean isFeatureEnabled() {
        return Config.webQuestEnabled && isModLoaded();
    }

    public static String readModVersion() {
        if (!isModLoaded()) {
            return "";
        }
        try {
            ModContainer container = Loader.instance()
                .getIndexedModList()
                .get("betterquesting");
            if (container != null && container.getVersion() != null) {
                return container.getVersion();
            }
        } catch (Throwable ignored) {}
        return "unknown";
    }
}
