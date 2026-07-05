package com.imgood.textech.config;

import net.minecraftforge.common.config.Configuration;

import com.imgood.textech.Config;
import com.imgood.textech.ConfigDescriptions;

public final class ConfigWebaeLoader {

    private ConfigWebaeLoader() {}

    public static void load(Configuration configuration) {
        Config.webConsoleEnabled = configuration.getBoolean(
            "enabled",
            "webConsole",
            Config.webConsoleEnabled,
            ConfigDescriptions.get("webConsole", "enabled"));
        Config.webConsolePort = configuration.getInt(
            "port",
            "webConsole",
            Config.webConsolePort,
            1024,
            65535,
            ConfigDescriptions.get("webConsole", "port"));
        Config.webConsoleBindAddress = configuration.getString(
            "bindAddress",
            "webConsole",
            Config.webConsoleBindAddress,
            ConfigDescriptions.get("webConsole", "bindAddress"));
        Config.webConsoleSnapshotIntervalSeconds = configuration.getInt(
            "snapshotIntervalSeconds",
            "webConsole",
            Config.webConsoleSnapshotIntervalSeconds,
            0,
            3600,
            ConfigDescriptions.get("webConsole", "snapshotIntervalSeconds"));
        Config.webRecipeUploadEnabled = configuration.getBoolean(
            "recipeUploadEnabled",
            "webConsole",
            Config.webRecipeUploadEnabled,
            ConfigDescriptions.get("webConsole", "recipeUploadEnabled"));
        Config.webRecipeCacheMode = configuration.getString(
            "recipeCacheMode",
            "webConsole",
            Config.webRecipeCacheMode,
            ConfigDescriptions.get("webConsole", "recipeCacheMode"));
        Config.webMaxRecipeCacheMB = configuration.getInt(
            "maxRecipeCacheMB",
            "webConsole",
            Config.webMaxRecipeCacheMB,
            1,
            2048,
            ConfigDescriptions.get("webConsole", "maxRecipeCacheMB"));
        Config.webRecipeUploadBatchesPerTick = configuration.getInt(
            "recipeUploadBatchesPerTick",
            "webConsole",
            Config.webRecipeUploadBatchesPerTick,
            1,
            32,
            ConfigDescriptions.get("webConsole", "recipeUploadBatchesPerTick"));
        Config.webRecipeSearchMinIntervalMs = configuration.getInt(
            "recipeSearchMinIntervalMs",
            "webConsole",
            Config.webRecipeSearchMinIntervalMs,
            0,
            5000,
            ConfigDescriptions.get("webConsole", "recipeSearchMinIntervalMs"));
        Config.webNesqlRepositoryPath = configuration.getString(
            "nesqlRepositoryPath",
            "webConsole",
            Config.webNesqlRepositoryPath,
            ConfigDescriptions.get("webConsole", "nesqlRepositoryPath"));
        Config.webNeiDeepScanItemsPerTick = configuration.getInt(
            "neiDeepScanItemsPerTick",
            "webConsole",
            Config.webNeiDeepScanItemsPerTick,
            0,
            512,
            ConfigDescriptions.get("webConsole", "neiDeepScanItemsPerTick"));
        Config.webIconMissingDispatchPerTick = configuration.getInt(
            "iconMissingDispatchPerTick",
            "webConsole",
            Config.webIconMissingDispatchPerTick,
            1,
            64,
            ConfigDescriptions.get("webConsole", "iconMissingDispatchPerTick"));
        Config.webPowerSampleWindowSeconds = configuration.getInt(
            "powerSampleWindowSeconds",
            "webConsole",
            Config.webPowerSampleWindowSeconds,
            10,
            600,
            ConfigDescriptions.get("webConsole", "powerSampleWindowSeconds"));
        Config.webMetricSampleIntervalMs = configuration.getInt(
            "metricSampleIntervalMs",
            "webConsole",
            Config.webMetricSampleIntervalMs,
            1000,
            60000,
            ConfigDescriptions.get("webConsole", "metricSampleIntervalMs"));
        Config.webMetricSampleWindowSeconds = configuration.getInt(
            "metricSampleWindowSeconds",
            "webConsole",
            Config.webMetricSampleWindowSeconds,
            60,
            3600,
            ConfigDescriptions.get("webConsole", "metricSampleWindowSeconds"));
        Config.webGtDefaultScanRadius = configuration.getInt(
            "gtDefaultScanRadius",
            "webConsole",
            Config.webGtDefaultScanRadius,
            1,
            256,
            ConfigDescriptions.get("webConsole", "gtDefaultScanRadius"));
        Config.webRefreshIntervalMs = configuration.getInt(
            "refreshIntervalMs",
            "webConsole",
            Config.webRefreshIntervalMs,
            1000,
            60000,
            ConfigDescriptions.get("webConsole", "refreshIntervalMs"));
        Config.webGtRefreshIntervalMs = configuration.getInt(
            "gtRefreshIntervalMs",
            "webConsole",
            Config.webGtRefreshIntervalMs,
            1000,
            60000,
            ConfigDescriptions.get("webConsole", "gtRefreshIntervalMs"));
        Config.webMaxNetworksDisplayed = configuration.getInt(
            "maxNetworksDisplayed",
            "webConsole",
            Config.webMaxNetworksDisplayed,
            1,
            20,
            ConfigDescriptions.get("webConsole", "maxNetworksDisplayed"));
        Config.webTokenLifetimeHours = configuration.getInt(
            "tokenLifetimeHours",
            "webConsole",
            Config.webTokenLifetimeHours,
            0,
            8760,
            ConfigDescriptions.get("webConsole", "tokenLifetimeHours"));
        Config.webIconCacheEnabled = configuration.getBoolean(
            "iconCacheEnabled",
            "webConsole",
            Config.webIconCacheEnabled,
            ConfigDescriptions.get("webConsole", "iconCacheEnabled"));
        Config.webIconUploadEnabled = configuration.getBoolean(
            "iconUploadEnabled",
            "webConsole",
            Config.webIconUploadEnabled,
            ConfigDescriptions.get("webConsole", "iconUploadEnabled"));
        Config.webIconPackEnabled = configuration.getBoolean(
            "iconPackEnabled",
            "webConsole",
            Config.webIconPackEnabled,
            ConfigDescriptions.get("webConsole", "iconPackEnabled"));
        Config.webIconRenderPerTick = configuration.getInt(
            "iconRenderPerTick",
            "webConsole",
            Config.webIconRenderPerTick,
            8,
            512,
            ConfigDescriptions.get("webConsole", "iconRenderPerTick"));
        Config.webIconRenderPerTickAll = configuration.getInt(
            "iconRenderPerTickAll",
            "webConsole",
            Config.webIconRenderPerTickAll,
            4,
            256,
            ConfigDescriptions.get("webConsole", "iconRenderPerTickAll"));
        Config.webIconUploadChunksPerTick = configuration.getInt(
            "iconUploadChunksPerTick",
            "webConsole",
            Config.webIconUploadChunksPerTick,
            1,
            32,
            ConfigDescriptions.get("webConsole", "iconUploadChunksPerTick"));
        Config.webIconProgressChatIntervalMs = configuration.getInt(
            "iconProgressChatIntervalMs",
            "webConsole",
            Config.webIconProgressChatIntervalMs,
            500,
            60000,
            ConfigDescriptions.get("webConsole", "iconProgressChatIntervalMs"));
        Config.webPatternBrowsePageSize = configuration.getInt(
            "patternBrowsePageSize",
            "webConsole",
            Config.webPatternBrowsePageSize,
            20,
            200,
            ConfigDescriptions.get("webConsole", "patternBrowsePageSize"));
        Config.webPatternBrowseMaxTotal = configuration.getInt(
            "patternBrowseMaxTotal",
            "webConsole",
            Config.webPatternBrowseMaxTotal,
            1000,
            100000,
            ConfigDescriptions.get("webConsole", "patternBrowseMaxTotal"));
        Config.webPatternCacheTtlMs = configuration.getInt(
            "patternCacheTtlMs",
            "webConsole",
            Config.webPatternCacheTtlMs,
            5000,
            300000,
            ConfigDescriptions.get("webConsole", "patternCacheTtlMs"));
        Config.webTopologyEnabled = configuration.getBoolean(
            "topologyEnabled",
            "webConsole",
            Config.webTopologyEnabled,
            ConfigDescriptions.get("webConsole", "topologyEnabled"));
        Config.webTopologyCacheTtlMs = configuration.getInt(
            "topologyCacheTtlMs",
            "webConsole",
            Config.webTopologyCacheTtlMs,
            5000,
            300000,
            ConfigDescriptions.get("webConsole", "topologyCacheTtlMs"));
    }
}
