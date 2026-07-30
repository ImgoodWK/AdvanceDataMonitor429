package com.imgood.textech.config;

import net.minecraftforge.common.config.Configuration;

import com.imgood.textech.Config;
import com.imgood.textech.ConfigDescriptions;

public final class ConfigCardBattleLoader {

    private ConfigCardBattleLoader() {}

    public static void load(Configuration configuration) {
        Config.cardBattleExternalApiBaseUrl = configuration.getString(
            "externalApiBaseUrl",
            "cardBattle",
            Config.cardBattleExternalApiBaseUrl,
            ConfigDescriptions.get("cardBattle", "externalApiBaseUrl"));
        Config.cardBattleBridgeToken = configuration.getString(
            "bridgeToken",
            "cardBattle",
            Config.cardBattleBridgeToken,
            ConfigDescriptions.get("cardBattle", "bridgeToken"));
    }
}
