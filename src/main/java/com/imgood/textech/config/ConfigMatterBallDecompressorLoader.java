package com.imgood.textech.config;

import net.minecraftforge.common.config.Configuration;

import com.imgood.textech.Config;
import com.imgood.textech.ConfigDescriptions;

public final class ConfigMatterBallDecompressorLoader {

    private ConfigMatterBallDecompressorLoader() {}

    public static void load(Configuration configuration) {
        Config.matterBallDecompressorItemsPerSecond = configuration.getFloat(
            "itemsPerSecond",
            "matterBallDecompressor",
            (float) Config.matterBallDecompressorItemsPerSecond,
            0.1F,
            1000000.0F,
            ConfigDescriptions.get("matterBallDecompressor", "itemsPerSecond"));
    }
}
