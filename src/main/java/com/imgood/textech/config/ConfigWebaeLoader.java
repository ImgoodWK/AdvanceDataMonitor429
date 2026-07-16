package com.imgood.textech.config;

import net.minecraftforge.common.config.Configuration;

import com.imgood.textech.AdvanceDataMonitor;
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
        Config.webRecipeKeepMemoryAfterUpload = configuration.getBoolean(
            "recipeKeepMemoryAfterUpload",
            "webConsole",
            Config.webRecipeKeepMemoryAfterUpload,
            ConfigDescriptions.get("webConsole", "recipeKeepMemoryAfterUpload"));
        Config.webRecipeSyncChunkSize = configuration.getInt(
            "recipeSyncChunkSize",
            "webConsole",
            Config.webRecipeSyncChunkSize,
            50,
            2000,
            ConfigDescriptions.get("webConsole", "recipeSyncChunkSize"));
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
        if (Config.webMetricSampleIntervalMs < 30000) {
            AdvanceDataMonitor.LOG.warn(
                "[WebAE] metricSampleIntervalMs={} is too low for stable TPS — overriding to 30000 ms. "
                    + "Update your config to avoid this warning.",
                Config.webMetricSampleIntervalMs);
            Config.webMetricSampleIntervalMs = 30000;
        }
        Config.webMetricSampleWindowSeconds = configuration.getInt(
            "metricSampleWindowSeconds",
            "webConsole",
            Config.webMetricSampleWindowSeconds,
            60,
            3600,
            ConfigDescriptions.get("webConsole", "metricSampleWindowSeconds"));
        Config.webDashboardMaxTracksPerWidget = configuration.getInt(
            "dashboardMaxTracksPerWidget",
            "webConsole",
            Config.webDashboardMaxTracksPerWidget,
            1,
            50,
            ConfigDescriptions.get("webConsole", "dashboardMaxTracksPerWidget"));
        Config.webDashboardMaxTracksGlobal = configuration.getInt(
            "dashboardMaxTracksGlobal",
            "webConsole",
            Config.webDashboardMaxTracksGlobal,
            1,
            256,
            ConfigDescriptions.get("webConsole", "dashboardMaxTracksGlobal"));
        Config.webDashboardMaxItemTracks = configuration.getInt(
            "dashboardMaxItemTracks",
            "webConsole",
            Config.webDashboardMaxItemTracks,
            1,
            64,
            ConfigDescriptions.get("webConsole", "dashboardMaxItemTracks"));
        Config.webDashboardMaxFluidTracks = configuration.getInt(
            "dashboardMaxFluidTracks",
            "webConsole",
            Config.webDashboardMaxFluidTracks,
            1,
            64,
            ConfigDescriptions.get("webConsole", "dashboardMaxFluidTracks"));
        Config.webDashboardMaxEntityTracks = configuration.getInt(
            "dashboardMaxEntityTracks",
            "webConsole",
            Config.webDashboardMaxEntityTracks,
            1,
            64,
            ConfigDescriptions.get("webConsole", "dashboardMaxEntityTracks"));
        Config.webGtDefaultScanRadius = configuration.getInt(
            "gtDefaultScanRadius",
            "webConsole",
            Config.webGtDefaultScanRadius,
            1,
            256,
            ConfigDescriptions.get("webConsole", "gtDefaultScanRadius"));
        if (Config.webGtDefaultScanRadius > 8) {
            AdvanceDataMonitor.LOG.warn(
                "[WebAE] gtDefaultScanRadius={} may cause high TPS overhead with many GT machines — ",
                Config.webGtDefaultScanRadius);
        }
        Config.webRefreshIntervalMs = configuration.getInt(
            "refreshIntervalMs",
            "webConsole",
            Config.webRefreshIntervalMs,
            1000,
            60000,
            ConfigDescriptions.get("webConsole", "refreshIntervalMs"));
        // Auto-migrate dangerously fast defaults to TPS-safe values
        if (Config.webRefreshIntervalMs < 10000) {
            AdvanceDataMonitor.LOG.warn(
                "[WebAE] refreshIntervalMs={} is too low for stable TPS — overriding to 10000 ms. "
                    + "Update your config to avoid this warning.",
                Config.webRefreshIntervalMs);
            Config.webRefreshIntervalMs = 10000;
        }
        Config.webGtRefreshIntervalMs = configuration.getInt(
            "gtRefreshIntervalMs",
            "webConsole",
            Config.webGtRefreshIntervalMs,
            1000,
            60000,
            ConfigDescriptions.get("webConsole", "gtRefreshIntervalMs"));
        if (Config.webGtRefreshIntervalMs < 30000) {
            AdvanceDataMonitor.LOG.warn(
                "[WebAE] gtRefreshIntervalMs={} is too low for stable TPS — overriding to 30000 ms.",
                Config.webGtRefreshIntervalMs);
            Config.webGtRefreshIntervalMs = 30000;
        }
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
        Config.webAdminGrantDays = configuration.getInt(
            "adminGrantDays",
            "webConsole",
            Config.webAdminGrantDays,
            0,
            3650,
            ConfigDescriptions.get("webConsole", "adminGrantDays"));
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
        Config.webIconLazyCaptureEnabled = configuration.getBoolean(
            "iconLazyCaptureEnabled",
            "webConsole",
            Config.webIconLazyCaptureEnabled,
            ConfigDescriptions.get("webConsole", "iconLazyCaptureEnabled"));
        Config.webIconLazyPreferOpOnly = configuration.getBoolean(
            "iconLazyPreferOpOnly",
            "webConsole",
            Config.webIconLazyPreferOpOnly,
            ConfigDescriptions.get("webConsole", "iconLazyPreferOpOnly"));
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
        Config.webIconDirectRenderEnabled = configuration.getBoolean(
            "iconDirectRenderEnabled",
            "webConsole",
            Config.webIconDirectRenderEnabled,
            ConfigDescriptions.get("webConsole", "iconDirectRenderEnabled"));
        Config.webIconDirectRenderTimeoutMs = configuration.getInt(
            "iconDirectRenderTimeoutMs",
            "webConsole",
            Config.webIconDirectRenderTimeoutMs,
            500,
            15000,
            ConfigDescriptions.get("webConsole", "iconDirectRenderTimeoutMs"));
        Config.webIconDirectRenderPerTick = configuration.getInt(
            "iconDirectRenderPerTick",
            "webConsole",
            Config.webIconDirectRenderPerTick,
            1,
            32,
            ConfigDescriptions.get("webConsole", "iconDirectRenderPerTick"));
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
        if (Config.webPatternCacheTtlMs < 120000) {
            AdvanceDataMonitor.LOG.warn(
                "[WebAE] patternCacheTtlMs={} is too low — overriding to 120000 ms.",
                Config.webPatternCacheTtlMs);
            Config.webPatternCacheTtlMs = 120000;
        }
        Config.webTopologyEnabled = configuration.getBoolean(
            "topologyEnabled",
            "webConsole",
            Config.webTopologyEnabled,
            ConfigDescriptions.get("webConsole", "topologyEnabled"));
        Config.webTopologyCacheTtlMs = configuration.getInt(
            "topologyCacheTtlMs",
            "webConsole",
            Config.webTopologyCacheTtlMs,
            1000,
            3600000,
            ConfigDescriptions.get("webConsole", "topologyCacheTtlMs"));
        if (Config.webTopologyCacheTtlMs < 5000) {
            AdvanceDataMonitor.LOG.warn(
                "[WebAE] topologyCacheTtlMs={} is too low — overriding to 30000 ms.",
                Config.webTopologyCacheTtlMs);
            Config.webTopologyCacheTtlMs = 30000;
        }
        Config.webTopologySnapshotPersist = configuration.getBoolean(
            "topologySnapshotPersist",
            "webConsole",
            Config.webTopologySnapshotPersist,
            ConfigDescriptions.get("webConsole", "topologySnapshotPersist"));
        Config.webTopologySimulatedEnabled = configuration.getBoolean(
            "topologySimulatedEnabled",
            "webConsole",
            Config.webTopologySimulatedEnabled,
            ConfigDescriptions.get("webConsole", "topologySimulatedEnabled"));
        Config.webDynmapBaseUrl = configuration.getString(
            "dynmapBaseUrl",
            "webConsole",
            Config.webDynmapBaseUrl,
            ConfigDescriptions.get("webConsole", "dynmapBaseUrl"));
        Config.webWorldMapEnabled = configuration.getBoolean(
            "worldMapEnabled",
            "webConsole",
            Config.webWorldMapEnabled,
            ConfigDescriptions.get("webConsole", "worldMapEnabled"));
        Config.webWorldMapTilePx = configuration.getInt(
            "worldMapTilePx",
            "webConsole",
            Config.webWorldMapTilePx,
            32,
            512,
            ConfigDescriptions.get("webConsole", "worldMapTilePx"));
        Config.webWorldMapMaxQualityTier = configuration.getString(
            "worldMapMaxQualityTier",
            "webConsole",
            Config.webWorldMapMaxQualityTier,
            ConfigDescriptions.get("webConsole", "worldMapMaxQualityTier"));
        Config.webWorldMapDefaultQualityTier = configuration.getString(
            "worldMapDefaultQualityTier",
            "webConsole",
            Config.webWorldMapDefaultQualityTier,
            ConfigDescriptions.get("webConsole", "worldMapDefaultQualityTier"));
        Config.webWorldMapBoundsPaddingChunks = configuration.getInt(
            "worldMapBoundsPaddingChunks",
            "webConsole",
            Config.webWorldMapBoundsPaddingChunks,
            0,
            16,
            ConfigDescriptions.get("webConsole", "worldMapBoundsPaddingChunks"));
        Config.webWorldMapTileBudgetPerTick = configuration.getInt(
            "worldMapTileBudgetPerTick",
            "webConsole",
            Config.webWorldMapTileBudgetPerTick,
            1,
            32,
            ConfigDescriptions.get("webConsole", "worldMapTileBudgetPerTick"));
        Config.webWorldMapMaxChunks = configuration.getInt(
            "worldMapMaxChunks",
            "webConsole",
            Config.webWorldMapMaxChunks,
            16,
            4096,
            ConfigDescriptions.get("webConsole", "worldMapMaxChunks"));
        Config.webWorldMapRequireNetworkScope = configuration.getBoolean(
            "worldMapRequireNetworkScope",
            "webConsole",
            Config.webWorldMapRequireNetworkScope,
            ConfigDescriptions.get("webConsole", "worldMapRequireNetworkScope"));
        Config.webWorldMapViewsEnabled = configuration.getString(
            "worldMapViewsEnabled",
            "webConsole",
            Config.webWorldMapViewsEnabled,
            ConfigDescriptions.get("webConsole", "worldMapViewsEnabled"));
        Config.webWorldMapObliqueEnabled = configuration.getBoolean(
            "worldMapObliqueEnabled",
            "webConsole",
            Config.webWorldMapObliqueEnabled,
            ConfigDescriptions.get("webConsole", "worldMapObliqueEnabled"));
        Config.webWorldMapClientHdEnabled = configuration.getBoolean(
            "worldMapClientHdEnabled",
            "webConsole",
            Config.webWorldMapClientHdEnabled,
            ConfigDescriptions.get("webConsole", "worldMapClientHdEnabled"));
        Config.webWorldMapClientHdBudgetPerTick = configuration.getInt(
            "worldMapClientHdBudgetPerTick",
            "webConsole",
            Config.webWorldMapClientHdBudgetPerTick,
            1,
            8,
            ConfigDescriptions.get("webConsole", "worldMapClientHdBudgetPerTick"));
        Config.worldMapClientCaptureMode = configuration.getString(
            "worldMapClientCaptureMode",
            "webConsole",
            Config.worldMapClientCaptureMode,
            ConfigDescriptions.get("webConsole", "worldMapClientCaptureMode"));
        Config.worldMapClientCaptureRadius = configuration.getInt(
            "worldMapClientCaptureRadius",
            "webConsole",
            Config.worldMapClientCaptureRadius,
            0,
            8,
            ConfigDescriptions.get("webConsole", "worldMapClientCaptureRadius"));
        Config.worldMapClientCaptureBudgetPerTick = configuration.getInt(
            "worldMapClientCaptureBudgetPerTick",
            "webConsole",
            Config.worldMapClientCaptureBudgetPerTick,
            0,
            4,
            ConfigDescriptions.get("webConsole", "worldMapClientCaptureBudgetPerTick"));
        Config.webWorldMapProgressiveFallback = configuration.getBoolean(
            "worldMapProgressiveFallback",
            "webConsole",
            Config.webWorldMapProgressiveFallback,
            ConfigDescriptions.get("webConsole", "worldMapProgressiveFallback"));
        Config.webWorldMapClientHdTimeoutMs = configuration.getInt(
            "worldMapClientHdTimeoutMs",
            "webConsole",
            Config.webWorldMapClientHdTimeoutMs,
            1000,
            30000,
            ConfigDescriptions.get("webConsole", "worldMapClientHdTimeoutMs"));
        Config.webWorldMapAeOverlayEnabled = configuration.getBoolean(
            "worldMapAeOverlayEnabled",
            "webConsole",
            Config.webWorldMapAeOverlayEnabled,
            ConfigDescriptions.get("webConsole", "worldMapAeOverlayEnabled"));
        Config.webWorldMapAeOverlayIncludeCables = configuration.getBoolean(
            "worldMapAeOverlayIncludeCables",
            "webConsole",
            Config.webWorldMapAeOverlayIncludeCables,
            ConfigDescriptions.get("webConsole", "worldMapAeOverlayIncludeCables"));
        Config.webWorldMapRenderEngine = configuration.getString(
            "worldMapRenderEngine",
            "webConsole",
            Config.webWorldMapRenderEngine,
            ConfigDescriptions.get("webConsole", "worldMapRenderEngine"));
        Config.webWorldMapObliqueEngine = configuration.getString(
            "worldMapObliqueEngine",
            "webConsole",
            Config.webWorldMapObliqueEngine,
            ConfigDescriptions.get("webConsole", "worldMapObliqueEngine"));
        Config.webWorldMapChunkPadding = configuration.getInt(
            "worldMapChunkPadding",
            "webConsole",
            Config.webWorldMapChunkPadding,
            0,
            4,
            ConfigDescriptions.get("webConsole", "worldMapChunkPadding"));
        Config.webWorldMapTextureCacheMax = configuration.getInt(
            "worldMapTextureCacheMax",
            "webConsole",
            Config.webWorldMapTextureCacheMax,
            256,
            8192,
            ConfigDescriptions.get("webConsole", "worldMapTextureCacheMax"));
        Config.webWorldMapRayBudgetPerTick = configuration.getInt(
            "worldMapRayBudgetPerTick",
            "webConsole",
            Config.webWorldMapRayBudgetPerTick,
            1,
            32,
            ConfigDescriptions.get("webConsole", "worldMapRayBudgetPerTick"));
        Config.webWorldMapRenderThreads = configuration.getInt(
            "worldMapRenderThreads",
            "webConsole",
            Config.webWorldMapRenderThreads,
            0,
            32,
            ConfigDescriptions.get("webConsole", "worldMapRenderThreads"));
        Config.webWorldMapMaxRayDepth = configuration.getInt(
            "worldMapMaxRayDepth",
            "webConsole",
            Config.webWorldMapMaxRayDepth,
            1,
            8,
            ConfigDescriptions.get("webConsole", "worldMapMaxRayDepth"));
        Config.webWorldMapLowTierObliqueEngine = configuration.getString(
            "worldMapLowTierObliqueEngine",
            "webConsole",
            Config.webWorldMapLowTierObliqueEngine,
            ConfigDescriptions.get("webConsole", "worldMapLowTierObliqueEngine"));
        Config.webWorldMapZoomLevels = configuration.getInt(
            "worldMapZoomLevels",
            "webConsole",
            Config.webWorldMapZoomLevels,
            1,
            6,
            ConfigDescriptions.get("webConsole", "worldMapZoomLevels"));
        Config.webWorldMapZoomBudgetPerTick = configuration.getInt(
            "worldMapZoomBudgetPerTick",
            "webConsole",
            Config.webWorldMapZoomBudgetPerTick,
            1,
            64,
            ConfigDescriptions.get("webConsole", "worldMapZoomBudgetPerTick"));
        Config.webWorldMapBlockPatchesEnabled = configuration.getBoolean(
            "worldMapBlockPatchesEnabled",
            "webConsole",
            Config.webWorldMapBlockPatchesEnabled,
            ConfigDescriptions.get("webConsole", "worldMapBlockPatchesEnabled"));
        Config.webWorldMapAeQualityBoost = configuration.getBoolean(
            "worldMapAeQualityBoost",
            "webConsole",
            Config.webWorldMapAeQualityBoost,
            ConfigDescriptions.get("webConsole", "worldMapAeQualityBoost"));
        Config.worldMapAeOverlayQualityTier = configuration.getString(
            "worldMapAeOverlayQualityTier",
            "webConsole",
            Config.worldMapAeOverlayQualityTier,
            ConfigDescriptions.get("webConsole", "worldMapAeOverlayQualityTier"));
        Config.webWorldMapServerAtlasEnabled = configuration.getBoolean(
            "worldMapServerAtlasEnabled",
            "webConsole",
            Config.webWorldMapServerAtlasEnabled,
            ConfigDescriptions.get("webConsole", "worldMapServerAtlasEnabled"));
        Config.webWorldMapServerAtlasPx = configuration.getInt(
            "worldMapServerAtlasPx",
            "webConsole",
            Config.webWorldMapServerAtlasPx,
            256,
            4096,
            ConfigDescriptions.get("webConsole", "worldMapServerAtlasPx"));
        Config.worldMapTerrainSource = configuration.getString(
            "worldMapTerrainSource",
            "webConsole",
            Config.worldMapTerrainSource,
            ConfigDescriptions.get("webConsole", "worldMapTerrainSource"));
        Config.worldMapDynmapTileRoot = configuration.getString(
            "worldMapDynmapTileRoot",
            "webConsole",
            Config.worldMapDynmapTileRoot,
            ConfigDescriptions.get("webConsole", "worldMapDynmapTileRoot"));
        Config.worldMapSnapshotMode = configuration.getString(
            "worldMapSnapshotMode",
            "webConsole",
            Config.worldMapSnapshotMode,
            ConfigDescriptions.get("webConsole", "worldMapSnapshotMode"));
        Config.worldMapJourneyMapEnabled = configuration.getBoolean(
            "worldMapJourneyMapEnabled",
            "webConsole",
            Config.worldMapJourneyMapEnabled,
            ConfigDescriptions.get("webConsole", "worldMapJourneyMapEnabled"));
        Config.worldMapJourneyMapDataRoot = configuration.getString(
            "worldMapJourneyMapDataRoot",
            "webConsole",
            Config.worldMapJourneyMapDataRoot,
            ConfigDescriptions.get("webConsole", "worldMapJourneyMapDataRoot"));
        Config.worldMapConsentRadiusChunks = configuration.getInt(
            "worldMapConsentRadiusChunks",
            "webConsole",
            Config.worldMapConsentRadiusChunks,
            1,
            64,
            ConfigDescriptions.get("webConsole", "worldMapConsentRadiusChunks"));
        Config.worldMapConsentTimeoutSec = configuration.getInt(
            "worldMapConsentTimeoutSec",
            "webConsole",
            Config.worldMapConsentTimeoutSec,
            30,
            600,
            ConfigDescriptions.get("webConsole", "worldMapConsentTimeoutSec"));
        Config.worldMapSnapshotCooldownMs = configuration.getInt(
            "worldMapSnapshotCooldownMs",
            "webConsole",
            Config.worldMapSnapshotCooldownMs,
            1000,
            3600000,
            ConfigDescriptions.get("webConsole", "worldMapSnapshotCooldownMs"));
        Config.worldMapOwnerSkipConsent = configuration.getBoolean(
            "worldMapOwnerSkipConsent",
            "webConsole",
            Config.worldMapOwnerSkipConsent,
            ConfigDescriptions.get("webConsole", "worldMapOwnerSkipConsent"));
        Config.worldMapClientFallbackQuality = configuration.getString(
            "worldMapClientFallbackQuality",
            "webConsole",
            Config.worldMapClientFallbackQuality,
            ConfigDescriptions.get("webConsole", "worldMapClientFallbackQuality"));
        Config.worldMapClientDownloadBudgetPerTick = configuration.getInt(
            "worldMapClientDownloadBudgetPerTick",
            "webConsole",
            Config.worldMapClientDownloadBudgetPerTick,
            1,
            32,
            ConfigDescriptions.get("webConsole", "worldMapClientDownloadBudgetPerTick"));
        Config.worldMapBrowserCacheEnabled = configuration.getBoolean(
            "worldMapBrowserCacheEnabled",
            "webConsole",
            Config.worldMapBrowserCacheEnabled,
            ConfigDescriptions.get("webConsole", "worldMapBrowserCacheEnabled"));
        Config.worldMapLegacyServerRender = configuration.getBoolean(
            "worldMapLegacyServerRender",
            "webConsole",
            Config.worldMapLegacyServerRender,
            ConfigDescriptions.get("webConsole", "worldMapLegacyServerRender"));
        Config.worldMapSnapshotSourcePriority = configuration.getString(
            "worldMapSnapshotSourcePriority",
            "webConsole",
            Config.worldMapSnapshotSourcePriority,
            ConfigDescriptions.get("webConsole", "worldMapSnapshotSourcePriority"));
        Config.worldMapDynmapCaptureEnabled = configuration.getBoolean(
            "worldMapDynmapCaptureEnabled",
            "webConsole",
            Config.worldMapDynmapCaptureEnabled,
            ConfigDescriptions.get("webConsole", "worldMapDynmapCaptureEnabled"));
        Config.worldMapJourneyMapCaptureEnabled = configuration.getBoolean(
            "worldMapJourneyMapCaptureEnabled",
            "webConsole",
            Config.worldMapJourneyMapCaptureEnabled,
            ConfigDescriptions.get("webConsole", "worldMapJourneyMapCaptureEnabled"));
        Config.worldMapClientGlCaptureEnabled = configuration.getBoolean(
            "worldMapClientGlCaptureEnabled",
            "webConsole",
            Config.worldMapClientGlCaptureEnabled,
            ConfigDescriptions.get("webConsole", "worldMapClientGlCaptureEnabled"));
        Config.worldMapSpDirectServe = configuration.getBoolean(
            "worldMapSpDirectServe",
            "webConsole",
            Config.worldMapSpDirectServe,
            ConfigDescriptions.get("webConsole", "worldMapSpDirectServe"));
        Config.worldMapSpDirectCacheTtlSec = configuration.getInt(
            "worldMapSpDirectCacheTtlSec",
            "webConsole",
            Config.worldMapSpDirectCacheTtlSec,
            5,
            600,
            ConfigDescriptions.get("webConsole", "worldMapSpDirectCacheTtlSec"));
        Config.worldMapDynmapClientFetchUrl = configuration.getString(
            "worldMapDynmapClientFetchUrl",
            "webConsole",
            Config.worldMapDynmapClientFetchUrl,
            ConfigDescriptions.get("webConsole", "worldMapDynmapClientFetchUrl"));
        Config.worldMapAeCableWidthBlocks = parseDoubleClamped(
            configuration.getString(
                "worldMapAeCableWidthBlocks",
                "webConsole",
                String.valueOf(Config.worldMapAeCableWidthBlocks),
                ConfigDescriptions.get("webConsole", "worldMapAeCableWidthBlocks")),
            Config.worldMapAeCableWidthBlocks,
            0.125D,
            1.0D);
        Config.worldMapAePartWidthBlocks = parseDoubleClamped(
            configuration.getString(
                "worldMapAePartWidthBlocks",
                "webConsole",
                String.valueOf(Config.worldMapAePartWidthBlocks),
                ConfigDescriptions.get("webConsole", "worldMapAePartWidthBlocks")),
            Config.worldMapAePartWidthBlocks,
            0.0D,
            1.0D);
        Config.webQuestEnabled = configuration.getBoolean(
            "questEnabled",
            "webConsole",
            Config.webQuestEnabled,
            ConfigDescriptions.get("webConsole", "questEnabled"));
        Config.webQuestSubmitEnabled = configuration.getBoolean(
            "questSubmitEnabled",
            "webConsole",
            Config.webQuestSubmitEnabled,
            ConfigDescriptions.get("webConsole", "questSubmitEnabled"));
        Config.webQuestChainSubmitEnabled = configuration.getBoolean(
            "questChainSubmitEnabled",
            "webConsole",
            Config.webQuestChainSubmitEnabled,
            ConfigDescriptions.get("webConsole", "questChainSubmitEnabled"));
        Config.webQuestSubmitMaxStacks = configuration.getInt(
            "questSubmitMaxStacks",
            "webConsole",
            Config.webQuestSubmitMaxStacks,
            1,
            512,
            ConfigDescriptions.get("webConsole", "questSubmitMaxStacks"));
        Config.webQuestCraftWaitTimeoutMs = configuration.getInt(
            "questCraftWaitTimeoutMs",
            "webConsole",
            (int) Config.webQuestCraftWaitTimeoutMs,
            5000,
            600000,
            ConfigDescriptions.get("webConsole", "questCraftWaitTimeoutMs"));
        Config.webQuestEscrowEnabled = configuration.getBoolean(
            "questEscrowEnabled",
            "webConsole",
            Config.webQuestEscrowEnabled,
            ConfigDescriptions.get("webConsole", "questEscrowEnabled"));
        Config.webQuestEscrowTimeoutMs = configuration.getInt(
            "questEscrowTimeoutMs",
            "webConsole",
            (int) Config.webQuestEscrowTimeoutMs,
            5000,
            600000,
            ConfigDescriptions.get("webConsole", "questEscrowTimeoutMs"));
        Config.webQuestCacheTtlSec = configuration.getInt(
            "questCacheTtlSec",
            "webConsole",
            Config.webQuestCacheTtlSec,
            30,
            3600,
            ConfigDescriptions.get("webConsole", "questCacheTtlSec"));
    }

    private static double parseDoubleClamped(String raw, double defaultValue, double min, double max) {
        if (raw == null || raw.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            double value = Double.parseDouble(raw.trim());
            if (value < min) {
                return min;
            }
            if (value > max) {
                return max;
            }
            return value;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
