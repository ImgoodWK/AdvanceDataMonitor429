package com.imgood.advancedatamonitor.config;

import net.minecraftforge.common.config.Configuration;

import com.imgood.advancedatamonitor.Config;
import com.imgood.advancedatamonitor.ConfigDescriptions;

public final class ConfigPlannerHudLoader {

    private ConfigPlannerHudLoader() {}

    public static void load(Configuration configuration) {
        Config.plannerHudMinMaxDisplay = configuration.getInt(
            "minMaxDisplay",
            "plannerHudLimits",
            Config.plannerHudMinMaxDisplay,
            1,
            100,
            ConfigDescriptions.get("plannerHudLimits", "minMaxDisplay"));
        Config.plannerHudMaxMaxDisplay = configuration.getInt(
            "maxMaxDisplay",
            "plannerHudLimits",
            Config.plannerHudMaxMaxDisplay,
            Config.plannerHudMinMaxDisplay,
            100,
            ConfigDescriptions.get("plannerHudLimits", "maxMaxDisplay"));
        Config.plannerHudMinPosX = configuration.getFloat(
            "minPosX",
            "plannerHudLimits",
            Config.plannerHudMinPosX,
            -10.0F,
            10.0F,
            ConfigDescriptions.get("plannerHudLimits", "minPosX"));
        Config.plannerHudMaxPosX = configuration.getFloat(
            "maxPosX",
            "plannerHudLimits",
            Config.plannerHudMaxPosX,
            Config.plannerHudMinPosX,
            10.0F,
            ConfigDescriptions.get("plannerHudLimits", "maxPosX"));
        Config.plannerHudMinPosY = configuration.getFloat(
            "minPosY",
            "plannerHudLimits",
            Config.plannerHudMinPosY,
            -10.0F,
            10.0F,
            ConfigDescriptions.get("plannerHudLimits", "minPosY"));
        Config.plannerHudMaxPosY = configuration.getFloat(
            "maxPosY",
            "plannerHudLimits",
            Config.plannerHudMaxPosY,
            Config.plannerHudMinPosY,
            10.0F,
            ConfigDescriptions.get("plannerHudLimits", "maxPosY"));
        Config.plannerHudMinScale = configuration.getFloat(
            "minScale",
            "plannerHudLimits",
            Config.plannerHudMinScale,
            0.1F,
            20.0F,
            ConfigDescriptions.get("plannerHudLimits", "minScale"));
        Config.plannerHudMaxScale = configuration.getFloat(
            "maxScale",
            "plannerHudLimits",
            Config.plannerHudMaxScale,
            Config.plannerHudMinScale,
            20.0F,
            ConfigDescriptions.get("plannerHudLimits", "maxScale"));
        Config.plannerHudMinWidth = configuration.getInt(
            "minWidth",
            "plannerHudLimits",
            Config.plannerHudMinWidth,
            1,
            2000,
            ConfigDescriptions.get("plannerHudLimits", "minWidth"));
        Config.plannerHudMaxWidth = configuration.getInt(
            "maxWidth",
            "plannerHudLimits",
            Config.plannerHudMaxWidth,
            Config.plannerHudMinWidth,
            2000,
            ConfigDescriptions.get("plannerHudLimits", "maxWidth"));
    }
}
