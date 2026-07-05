package com.imgood.textech.config;

import net.minecraftforge.common.config.Configuration;

import com.imgood.textech.Config;
import com.imgood.textech.ConfigDescriptions;

/**
 * Loader for the {@code [debug]} section WebAE per-feature debug-log switches.
 * Each gate controls a separate {@code logs/textech/webae-<feature>.log} file
 * via {@link com.imgood.textech.webae.debug.WebAeDebugLog}. All default to
 * {@code false} so production servers pay no logging overhead.
 */
public final class ConfigWebaeDebugLoader {

    private ConfigWebaeDebugLoader() {}

    public static void load(Configuration configuration) {
        Config.webDebugIcons = configuration
            .getBoolean("webaeIcons", "debug", Config.webDebugIcons, ConfigDescriptions.get("debug", "webaeIcons"));
        Config.webDebugChat = configuration
            .getBoolean("webaeChat", "debug", Config.webDebugChat, ConfigDescriptions.get("debug", "webaeChat"));
        Config.webDebugDashboard = configuration.getBoolean(
            "webaeDashboard",
            "debug",
            Config.webDebugDashboard,
            ConfigDescriptions.get("debug", "webaeDashboard"));
        Config.webDebugSynthesis = configuration.getBoolean(
            "webaeSynthesis",
            "debug",
            Config.webDebugSynthesis,
            ConfigDescriptions.get("debug", "webaeSynthesis"));
        Config.webDebugPatterns = configuration.getBoolean(
            "webaePatterns",
            "debug",
            Config.webDebugPatterns,
            ConfigDescriptions.get("debug", "webaePatterns"));
    }
}
