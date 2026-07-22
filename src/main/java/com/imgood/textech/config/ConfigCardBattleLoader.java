package com.imgood.textech.config;

import net.minecraftforge.common.config.Configuration;

import com.imgood.textech.Config;
import com.imgood.textech.ConfigDescriptions;

public final class ConfigCardBattleLoader {

    private ConfigCardBattleLoader() {}

    public static void load(Configuration configuration) {
        Config.cardBattleEnabled = configuration.getBoolean(
            "enabled",
            "cardBattle",
            Config.cardBattleEnabled,
            ConfigDescriptions.get("cardBattle", "enabled"));
        Config.cardBattlePort = configuration.getInt(
            "port",
            "cardBattle",
            Config.cardBattlePort,
            1024,
            65535,
            ConfigDescriptions.get("cardBattle", "port"));
        Config.cardBattleBindAddress = configuration.getString(
            "bindAddress",
            "cardBattle",
            Config.cardBattleBindAddress,
            ConfigDescriptions.get("cardBattle", "bindAddress"));
        Config.cardBattleDevToken = configuration.getString(
            "devToken",
            "cardBattle",
            Config.cardBattleDevToken,
            ConfigDescriptions.get("cardBattle", "devToken"));
        // Legacy keys retained so old cfg files still parse; values ignored at runtime.
        Config.cardBattleServerDir = configuration.getString(
            "serverDir",
            "cardBattle",
            Config.cardBattleServerDir,
            ConfigDescriptions.get("cardBattle", "serverDir"));
        Config.cardBattleFrontendDir = configuration.getString(
            "frontendDir",
            "cardBattle",
            Config.cardBattleFrontendDir,
            ConfigDescriptions.get("cardBattle", "frontendDir"));
        Config.cardBattleNodePath = configuration.getString(
            "nodePath",
            "cardBattle",
            Config.cardBattleNodePath,
            ConfigDescriptions.get("cardBattle", "nodePath"));
    }
}
