package com.imgood.textech.config;

import net.minecraftforge.common.config.Configuration;

import com.imgood.textech.Config;
import com.imgood.textech.ConfigDescriptions;

public final class ConfigGrappleLoader {

    private ConfigGrappleLoader() {}

    public static void load(Configuration configuration) {
        Config.grappleHintRange = configuration.getInt(
            "hintRange",
            "grapple",
            Config.grappleHintRange,
            1,
            512,
            ConfigDescriptions.get("grapple", "hintRange"));
        Config.grappleInteractRange = configuration.getInt(
            "interactRange",
            "grapple",
            Config.grappleInteractRange,
            1,
            256,
            ConfigDescriptions.get("grapple", "interactRange"));
        Config.grappleScanChunkRadius = configuration.getInt(
            "scanChunkRadius",
            "grapple",
            Config.grappleScanChunkRadius,
            1,
            64,
            ConfigDescriptions.get("grapple", "scanChunkRadius"));
        Config.grappleMaxTravelChunkRadius = configuration.getInt(
            "maxTravelChunkRadius",
            "grapple",
            Config.grappleMaxTravelChunkRadius,
            1,
            64,
            ConfigDescriptions.get("grapple", "maxTravelChunkRadius"));
        Config.grappleMoveSpeed = configuration.getFloat(
            "moveSpeed",
            "grapple",
            (float) Config.grappleMoveSpeed,
            0.05F,
            10.0F,
            ConfigDescriptions.get("grapple", "moveSpeed"));
        Config.grappleSnapRadiusPx = configuration.getInt(
            "snapRadiusPx",
            "grapple",
            Config.grappleSnapRadiusPx,
            16,
            256,
            ConfigDescriptions.get("grapple", "snapRadiusPx"));
        Config.grappleTravelSnapDegrees = configuration.getFloat(
            "travelSnapDegrees",
            "grapple",
            Config.grappleTravelSnapDegrees,
            5.0F,
            90.0F,
            ConfigDescriptions.get("grapple", "travelSnapDegrees"));
        Config.grappleAttachSnapDegrees = configuration.getFloat(
            "attachSnapDegrees",
            "grapple",
            Config.grappleAttachSnapDegrees,
            5.0F,
            90.0F,
            ConfigDescriptions.get("grapple", "attachSnapDegrees"));
        Config.grappleMaxTravelQueueSize = configuration.getInt(
            "maxTravelQueueSize",
            "grapple",
            Config.grappleMaxTravelQueueSize,
            1,
            256,
            ConfigDescriptions.get("grapple", "maxTravelQueueSize"));
        Config.grappleMaxSavedRoutes = configuration.getInt(
            "maxSavedRoutes",
            "grapple",
            Config.grappleMaxSavedRoutes,
            1,
            512,
            ConfigDescriptions.get("grapple", "maxSavedRoutes"));
        Config.grappleMaxNodesPerRoute = configuration.getInt(
            "maxNodesPerRoute",
            "grapple",
            Config.grappleMaxNodesPerRoute,
            2,
            512,
            ConfigDescriptions.get("grapple", "maxNodesPerRoute"));
    }
}
