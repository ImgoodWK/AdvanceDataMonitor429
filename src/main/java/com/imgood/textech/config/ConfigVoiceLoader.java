package com.imgood.textech.config;

import net.minecraftforge.common.config.Configuration;

import com.imgood.textech.Config;
import com.imgood.textech.ConfigDescriptions;

public final class ConfigVoiceLoader {

    private ConfigVoiceLoader() {}

    public static void load(Configuration configuration) {
        Config.voiceAssistantEnabled = configuration
            .getBoolean("enabled", "voice", Config.voiceAssistantEnabled, ConfigDescriptions.get("voice", "enabled"));
        Config.voicePrivacyConfirmed = configuration.getBoolean(
            "privacyConfirmed",
            "voice",
            Config.voicePrivacyConfirmed,
            ConfigDescriptions.get("voice", "privacyConfirmed"));
        Config.voiceSttMode = Config.normalizeVoiceSttMode(
            configuration
                .getString("sttMode", "voice", Config.voiceSttMode, ConfigDescriptions.get("voice", "sttMode")));
        Config.voiceSttBaseUrl = configuration
            .getString("sttBaseUrl", "voice", Config.voiceSttBaseUrl, ConfigDescriptions.get("voice", "sttBaseUrl"));
        Config.voiceSttApiKey = configuration
            .getString("sttApiKey", "voice", Config.voiceSttApiKey, ConfigDescriptions.get("voice", "sttApiKey"));
        String configuredVoiceSttModel = configuration
            .getString("sttModel", "voice", Config.voiceSttModel, ConfigDescriptions.get("voice", "sttModel"));
        Config.voiceSttModel = Config.normalizeVoiceSttModel(configuredVoiceSttModel);
        if (!Config.voiceSttModel.equals(configuredVoiceSttModel)) {
            configuration.get("voice", "sttModel", configuredVoiceSttModel)
                .set(Config.voiceSttModel);
        }
        Config.voiceSttTimeoutSeconds = configuration.getInt(
            "sttTimeoutSeconds",
            "voice",
            Config.voiceSttTimeoutSeconds,
            5,
            300,
            ConfigDescriptions.get("voice", "sttTimeoutSeconds"));
    }

    public static void save(Configuration configuration) {
        configuration.get("voice", "enabled", Config.voiceAssistantEnabled)
            .set(Config.voiceAssistantEnabled);
        configuration.get("voice", "privacyConfirmed", Config.voicePrivacyConfirmed)
            .set(Config.voicePrivacyConfirmed);
        configuration.get("voice", "sttMode", Config.voiceSttMode)
            .set(Config.voiceSttMode);
        configuration.get("voice", "sttBaseUrl", Config.voiceSttBaseUrl)
            .set(Config.voiceSttBaseUrl);
        configuration.get("voice", "sttApiKey", Config.voiceSttApiKey)
            .set(Config.voiceSttApiKey);
        configuration.get("voice", "sttModel", Config.voiceSttModel)
            .set(Config.voiceSttModel);
        configuration.get("voice", "sttTimeoutSeconds", Config.voiceSttTimeoutSeconds)
            .set(Config.voiceSttTimeoutSeconds);
    }
}
