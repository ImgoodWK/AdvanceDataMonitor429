package com.imgood.advancedatamonitor.config;

import net.minecraftforge.common.config.Configuration;

import com.imgood.advancedatamonitor.Config;
import com.imgood.advancedatamonitor.ConfigDescriptions;

public final class ConfigCompatLoader {

    private ConfigCompatLoader() {}

    public static void load(Configuration configuration) {
        Config.compatAeProfileOverride = configuration.getString(
            "aeProfileOverride",
            "compat",
            Config.compatAeProfileOverride,
            ConfigDescriptions.get("compat", "aeProfileOverride"));
    }
}
