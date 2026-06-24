package com.imgood.advancedatamonitor.config;

import net.minecraftforge.common.config.Configuration;

import com.imgood.advancedatamonitor.Config;
import com.imgood.advancedatamonitor.ConfigDescriptions;

public final class ConfigDebugLoader {

    private ConfigDebugLoader() {}

    public static void load(Configuration configuration) {
        Config.debugGeneral = configuration
            .getBoolean("general", "debug", Config.debugGeneral, ConfigDescriptions.get("debug", "general"));
        Config.debugGuiNetworkLink = configuration.getBoolean(
            "guiNetworkLink",
            "debug",
            Config.debugGuiNetworkLink,
            ConfigDescriptions.get("debug", "guiNetworkLink"));
        Config.debugMonitorTestMode = configuration.getBoolean(
            "monitorTestMode",
            "debug",
            Config.debugMonitorTestMode,
            ConfigDescriptions.get("debug", "monitorTestMode"));
    }
}
