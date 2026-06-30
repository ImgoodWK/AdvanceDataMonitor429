package com.imgood.textech.config;

import net.minecraftforge.common.config.Configuration;

import com.imgood.textech.Config;
import com.imgood.textech.ConfigDescriptions;

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
