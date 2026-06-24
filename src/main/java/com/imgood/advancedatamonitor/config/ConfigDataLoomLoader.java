package com.imgood.advancedatamonitor.config;

import net.minecraftforge.common.config.Configuration;

import com.imgood.advancedatamonitor.Config;
import com.imgood.advancedatamonitor.ConfigDescriptions;
import com.imgood.advancedatamonitor.items.cell.DataLoomAmplifierRates;
import com.imgood.advancedatamonitor.items.cell.DataLoomDebugLog;

public final class ConfigDataLoomLoader {

    private ConfigDataLoomLoader() {}

    public static void load(Configuration configuration) {
        Config.dataDustLoomCellItemRatePerSecond = configuration.getFloat(
            "itemRatePerSecond",
            "dataDustLoomCell",
            (float) Config.dataDustLoomCellItemRatePerSecond,
            0.01F,
            1000000.0F,
            ConfigDescriptions.get("dataDustLoomCell", "itemRatePerSecond"));
        Config.dataFormLoomCellItemRatePerSecond = configuration.getFloat(
            "itemRatePerSecond",
            "dataFormLoomCell",
            (float) Config.dataFormLoomCellItemRatePerSecond,
            0.01F,
            1000000.0F,
            ConfigDescriptions.get("dataFormLoomCell", "itemRatePerSecond"));
        Config.dataFlowCellFluidRatePerSecond = configuration.getInt(
            "fluidRatePerSecond",
            "dataFlowCell",
            Config.dataFlowCellFluidRatePerSecond,
            1,
            1000000000,
            ConfigDescriptions.get("dataFlowCell", "fluidRatePerSecond"));
        Config.dataSourceLoomCellEssentiaRatePerSecond = configuration.getInt(
            "essentiaRatePerSecond",
            "dataSourceLoomCell",
            Config.dataSourceLoomCellEssentiaRatePerSecond,
            1,
            1000000000,
            ConfigDescriptions.get("dataSourceLoomCell", "essentiaRatePerSecond"));
        Config.dataLoomCellSyncIntervalSeconds = configuration.getInt(
            "syncIntervalSeconds",
            "dataLoomCell",
            Config.dataLoomCellSyncIntervalSeconds,
            1,
            300,
            ConfigDescriptions.get("dataLoomCell", "syncIntervalSeconds"));
        Config.dataLoomCellDebugLogging = configuration.getBoolean(
            "debugLogging",
            "dataLoomCell",
            Config.dataLoomCellDebugLogging,
            ConfigDescriptions.get("dataLoomCell", "debugLogging"));
        Config.dataLoomCellEnergyDrainPerTick = configuration.getFloat(
            "energyDrainPerTick",
            "dataLoomCell",
            (float) Config.dataLoomCellEnergyDrainPerTick,
            0.0F,
            1000000000.0F,
            ConfigDescriptions.get("dataLoomCell", "energyDrainPerTick"));
        Config.weaveAmplifierRateMultiplier = configuration.getFloat(
            "weaveAmplifierRateMultiplier",
            "dataLoomCell",
            (float) Config.weaveAmplifierRateMultiplier,
            1.0F,
            1000000.0F,
            ConfigDescriptions.get("dataLoomCell", "weaveAmplifierRateMultiplier"));
        Config.superWeaveAmplifierRateMultiplier = configuration.getFloat(
            "superWeaveAmplifierRateMultiplier",
            "dataLoomCell",
            (float) Config.superWeaveAmplifierRateMultiplier,
            1.0F,
            1000000.0F,
            ConfigDescriptions.get("dataLoomCell", "superWeaveAmplifierRateMultiplier"));

        DataLoomAmplifierRates.reloadFromConfig();
        DataLoomDebugLog.logStartupBannerIfNeeded();
    }
}
