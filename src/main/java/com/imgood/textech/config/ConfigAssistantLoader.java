package com.imgood.textech.config;

import net.minecraftforge.common.config.Configuration;

import com.imgood.textech.Config;
import com.imgood.textech.ConfigDescriptions;

public final class ConfigAssistantLoader {

    private ConfigAssistantLoader() {}

    public static void load(Configuration configuration) {
        Config.assistantMaxOrderAmount = configuration.getInt(
            "maxOrderAmount",
            "assistant",
            Config.assistantMaxOrderAmount,
            1,
            1000000,
            ConfigDescriptions.get("assistant", "maxOrderAmount"));
        Config.assistantMaxWithdrawAmount = configuration.getInt(
            "maxWithdrawAmount",
            "assistant",
            Config.assistantMaxWithdrawAmount,
            1,
            1000000,
            ConfigDescriptions.get("assistant", "maxWithdrawAmount"));
        Config.assistantCraftJobTimeoutSeconds = configuration.getInt(
            "craftJobTimeoutSeconds",
            "assistant",
            Config.assistantCraftJobTimeoutSeconds,
            1,
            300,
            ConfigDescriptions.get("assistant", "craftJobTimeoutSeconds"));
        Config.assistantMaxConcurrentCraftJobs = configuration.getInt(
            "maxConcurrentCraftJobs",
            "assistant",
            Config.assistantMaxConcurrentCraftJobs,
            1,
            16,
            ConfigDescriptions.get("assistant", "maxConcurrentCraftJobs"));
        Config.assistantQueryCandidateBatchSize = configuration.getInt(
            "queryCandidateBatchSize",
            "assistant",
            Config.assistantQueryCandidateBatchSize,
            100,
            5000,
            ConfigDescriptions.get("assistant", "queryCandidateBatchSize"));
        Config.assistantMaxQueryCandidates = configuration.getInt(
            "maxQueryCandidates",
            "assistant",
            Config.assistantMaxQueryCandidates,
            1000,
            50000,
            ConfigDescriptions.get("assistant", "maxQueryCandidates"));
        Config.assistantLinkSearchRadius = configuration.getInt(
            "linkSearchRadius",
            "assistant",
            Config.assistantLinkSearchRadius,
            4,
            128,
            ConfigDescriptions.get("assistant", "linkSearchRadius"));
    }

    public static void save(Configuration configuration) {
        configuration.get("assistant", "maxOrderAmount", Config.assistantMaxOrderAmount)
            .set(Config.assistantMaxOrderAmount);
        configuration.get("assistant", "maxWithdrawAmount", Config.assistantMaxWithdrawAmount)
            .set(Config.assistantMaxWithdrawAmount);
        configuration.get("assistant", "craftJobTimeoutSeconds", Config.assistantCraftJobTimeoutSeconds)
            .set(Config.assistantCraftJobTimeoutSeconds);
        configuration.get("assistant", "maxConcurrentCraftJobs", Config.assistantMaxConcurrentCraftJobs)
            .set(Config.assistantMaxConcurrentCraftJobs);
        configuration.get("assistant", "queryCandidateBatchSize", Config.assistantQueryCandidateBatchSize)
            .set(Config.assistantQueryCandidateBatchSize);
        configuration.get("assistant", "maxQueryCandidates", Config.assistantMaxQueryCandidates)
            .set(Config.assistantMaxQueryCandidates);
        configuration.get("assistant", "linkSearchRadius", Config.assistantLinkSearchRadius)
            .set(Config.assistantLinkSearchRadius);
    }
}
