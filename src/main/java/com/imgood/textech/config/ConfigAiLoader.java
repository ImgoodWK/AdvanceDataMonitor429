package com.imgood.textech.config;

import net.minecraftforge.common.config.Configuration;

import com.imgood.textech.Config;
import com.imgood.textech.ConfigDescriptions;
import com.imgood.textech.assistant.ai.AiProviderProfiles.ProviderProfile;

public final class ConfigAiLoader {

    private ConfigAiLoader() {}

    public static void load(Configuration configuration) {
        Config.aiApiBaseUrl = configuration
            .getString("apiBaseUrl", "ai", Config.aiApiBaseUrl, ConfigDescriptions.get("ai", "apiBaseUrl"));
        Config.aiApiKey = configuration
            .getString("apiKey", "ai", Config.aiApiKey, ConfigDescriptions.get("ai", "apiKey"));
        Config.aiModel = configuration.getString("model", "ai", Config.aiModel, ConfigDescriptions.get("ai", "model"));
        Config.aiNetworkEnabled = configuration.getBoolean(
            "networkEnabled",
            "ai",
            Config.aiNetworkEnabled,
            ConfigDescriptions.get("ai", "networkEnabled"));
        Config.aiWebSearchEnabled = configuration.getBoolean(
            "webSearchEnabled",
            "ai",
            Config.aiWebSearchEnabled,
            ConfigDescriptions.get("ai", "webSearchEnabled"));
        Config.aiWebSearchMode = configuration
            .getString("webSearchMode", "ai", Config.aiWebSearchMode, ConfigDescriptions.get("ai", "webSearchMode"));
        Config.aiDebugLogging = configuration
            .getBoolean("debugLogging", "ai", Config.aiDebugLogging, ConfigDescriptions.get("ai", "debugLogging"));
        Config.aiStreamingEnabled = configuration.getBoolean(
            "streamingEnabled",
            "ai",
            Config.aiStreamingEnabled,
            ConfigDescriptions.get("ai", "streamingEnabled"));
        Config.aiPrivacyConfirmed = configuration.getBoolean(
            "privacyConfirmed",
            "ai",
            Config.aiPrivacyConfirmed,
            ConfigDescriptions.get("ai", "privacyConfirmed"));
        Config.aiRecentModels = configuration
            .getString("recentModels", "ai", Config.aiRecentModels, ConfigDescriptions.get("ai", "recentModels"));
        Config.aiTimeoutSeconds = configuration.getInt(
            "timeoutSeconds",
            "ai",
            Config.aiTimeoutSeconds,
            5,
            300,
            ConfigDescriptions.get("ai", "timeoutSeconds"));
        Config.aiMaxTokens = configuration
            .getInt("maxTokens", "ai", Config.aiMaxTokens, 1, 8192, ConfigDescriptions.get("ai", "maxTokens"));
        Config.aiTemperature = configuration.getFloat(
            "temperature",
            "ai",
            (float) Config.aiTemperature,
            0.0F,
            2.0F,
            ConfigDescriptions.get("ai", "temperature"));
    }

    public static void save(Configuration configuration) {
        configuration.get("ai", "apiBaseUrl", Config.aiApiBaseUrl)
            .set(Config.aiApiBaseUrl);
        configuration.get("ai", "apiKey", Config.aiApiKey)
            .set(Config.aiApiKey);
        configuration.get("ai", "model", Config.aiModel)
            .set(Config.aiModel);
        configuration.get("ai", "networkEnabled", Config.aiNetworkEnabled)
            .set(Config.aiNetworkEnabled);
        configuration.get("ai", "webSearchEnabled", Config.aiWebSearchEnabled)
            .set(Config.aiWebSearchEnabled);
        configuration.get("ai", "webSearchMode", Config.aiWebSearchMode)
            .set(Config.aiWebSearchMode);
        configuration.get("ai", "debugLogging", Config.aiDebugLogging)
            .set(Config.aiDebugLogging);
        configuration.get("ai", "streamingEnabled", Config.aiStreamingEnabled)
            .set(Config.aiStreamingEnabled);
        configuration.get("ai", "privacyConfirmed", Config.aiPrivacyConfirmed)
            .set(Config.aiPrivacyConfirmed);
        configuration.get("ai", "recentModels", Config.aiRecentModels)
            .set(Config.aiRecentModels);
        configuration.get("ai", "timeoutSeconds", Config.aiTimeoutSeconds)
            .set(Config.aiTimeoutSeconds);
        configuration.get("ai", "maxTokens", Config.aiMaxTokens)
            .set(Config.aiMaxTokens);
        configuration.get("ai", "temperature", Config.aiTemperature)
            .set(Config.aiTemperature);
    }

    public static void applyProviderProfile(ProviderProfile profile) {
        if (profile == null) {
            return;
        }
        Config.aiApiBaseUrl = profile.baseUrl;
        ConfigAiMutators.setAiModelInMemory(profile.defaultModel);
        Config.aiWebSearchMode = profile.defaultSearchMode;
        Config.aiWebSearchEnabled = !"unsupported".equals(profile.defaultSearchMode)
            && !"off".equals(profile.defaultSearchMode);
    }
}
