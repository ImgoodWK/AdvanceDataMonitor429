package com.imgood.textech.config;

import net.minecraftforge.common.config.Configuration;

import com.imgood.textech.Config;
import com.imgood.textech.ConfigDescriptions;

public final class ConfigSuperOrangeLoader {

    private ConfigSuperOrangeLoader() {}

    public static void load(Configuration configuration) {
        Config.superOrangeDroneEnabled = configuration.getBoolean(
            "droneEnabled",
            "superOrange",
            Config.superOrangeDroneEnabled,
            ConfigDescriptions.get("superOrange", "droneEnabled"));
        Config.superOrangeHeadEffectsEnabled = configuration.getBoolean(
            "headEffectsEnabled",
            "superOrange",
            Config.superOrangeHeadEffectsEnabled,
            ConfigDescriptions.get("superOrange", "headEffectsEnabled"));
        Config.superOrangeDropMultiplierEnabled = configuration.getBoolean(
            "dropMultiplierEnabled",
            "superOrange",
            Config.superOrangeDropMultiplierEnabled,
            ConfigDescriptions.get("superOrange", "dropMultiplierEnabled"));
        Config.superOrangeDropMultiplier = configuration.getInt(
            "dropMultiplier",
            "superOrange",
            Config.superOrangeDropMultiplier,
            1,
            1000000,
            ConfigDescriptions.get("superOrange", "dropMultiplier"));
        Config.superOrangeProjectileImmunityEnabled = configuration.getBoolean(
            "projectileImmunityEnabled",
            "superOrange",
            Config.superOrangeProjectileImmunityEnabled,
            ConfigDescriptions.get("superOrange", "projectileImmunityEnabled"));
        Config.superOrangeDroneAttackRange = configuration.getFloat(
            "droneAttackRange",
            "superOrange",
            (float) Config.superOrangeDroneAttackRange,
            1.0F,
            100.0F,
            ConfigDescriptions.get("superOrange", "droneAttackRange"));
        Config.superOrangeDroneAttackDamage = configuration.getFloat(
            "droneAttackDamage",
            "superOrange",
            (float) Config.superOrangeDroneAttackDamage,
            0.1F,
            10000.0F,
            ConfigDescriptions.get("superOrange", "droneAttackDamage"));
        Config.superOrangeDroneAttacksPerSecond = configuration.getInt(
            "droneAttacksPerSecond",
            "superOrange",
            Config.superOrangeDroneAttacksPerSecond,
            1,
            20,
            ConfigDescriptions.get("superOrange", "droneAttacksPerSecond"));
        Config.superOrangeDroneMaxClones = configuration.getInt(
            "droneMaxClones",
            "superOrange",
            Config.superOrangeDroneMaxClones,
            0,
            20,
            ConfigDescriptions.get("superOrange", "droneMaxClones"));
        Config.superOrangeDroneFollowHeight = configuration.getFloat(
            "droneFollowHeight",
            "superOrange",
            (float) Config.superOrangeDroneFollowHeight,
            0.0F,
            5.0F,
            ConfigDescriptions.get("superOrange", "droneFollowHeight"));
    }
}
