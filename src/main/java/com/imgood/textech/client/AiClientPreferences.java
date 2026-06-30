package com.imgood.textech.client;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.ConfigDescriptions;
import com.imgood.textech.assistant.ai.AiProviderProfiles.ProviderProfile;
import com.imgood.textech.config.ConfigAiMutators;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Per-player AI / voice settings stored only on the game client.
 * API keys and HTTP calls never touch the dedicated server.
 */
@SideOnly(Side.CLIENT)
public final class AiClientPreferences {

    private static final String LOCAL_FILE = "ai-client-local.cfg";

    private static File preferencesFile;

    private static String apiKey = "";
    private static String apiBaseUrl = "https://api.deepseek.com";
    private static String model = "deepseek-chat";
    private static boolean networkEnabled = true;
    private static boolean webSearchEnabled = false;
    private static String webSearchMode = "auto";
    private static boolean debugLogging = false;
    private static boolean streamingEnabled = false;
    private static boolean privacyConfirmed = false;
    private static String recentModels = "";
    private static int timeoutSeconds = 60;
    private static int maxTokens = 1024;
    private static double temperature = 0.7D;

    private static boolean voiceEnabled = false;
    private static boolean voicePrivacyConfirmed = false;
    private static String voiceSttMode = Config.VOICE_STT_MODE_EMBEDDED_VOSK;
    private static String voiceSttBaseUrl = "";
    private static String voiceSttApiKey = "";
    private static String voiceSttModel = "zh-small";
    private static int voiceSttTimeoutSeconds = 60;

    private AiClientPreferences() {}

    public static void initialize(File configDir, Configuration sharedConfiguration) {
        File dir = configDir == null ? new File("config", AdvanceDataMonitor.MODID) : configDir;
        if (!dir.exists()) {
            dir.mkdirs();
        }
        preferencesFile = new File(dir, LOCAL_FILE);

        if (!preferencesFile.exists()) {
            migrateLegacyFromShared(sharedConfiguration);
            saveLocal();
        } else {
            loadLocal();
        }
        applyToConfig();
        scrubSharedAiVoiceKeys(sharedConfiguration);
    }

    public static void loadLocal() {
        if (preferencesFile == null) {
            return;
        }
        Configuration configuration = new Configuration(preferencesFile);
        configuration.load();

        apiKey = configuration.getString("apiKey", "ai", apiKey, ConfigDescriptions.get("ai", "apiKey"));
        apiBaseUrl = configuration
            .getString("apiBaseUrl", "ai", apiBaseUrl, ConfigDescriptions.get("ai", "apiBaseUrl"));
        model = configuration.getString("model", "ai", model, ConfigDescriptions.get("ai", "model"));
        networkEnabled = configuration
            .getBoolean("networkEnabled", "ai", networkEnabled, ConfigDescriptions.get("ai", "networkEnabled"));
        webSearchEnabled = configuration
            .getBoolean("webSearchEnabled", "ai", webSearchEnabled, ConfigDescriptions.get("ai", "webSearchEnabled"));
        webSearchMode = configuration
            .getString("webSearchMode", "ai", webSearchMode, ConfigDescriptions.get("ai", "webSearchMode"));
        debugLogging = configuration
            .getBoolean("debugLogging", "ai", debugLogging, ConfigDescriptions.get("ai", "debugLogging"));
        streamingEnabled = configuration
            .getBoolean("streamingEnabled", "ai", streamingEnabled, ConfigDescriptions.get("ai", "streamingEnabled"));
        privacyConfirmed = configuration
            .getBoolean("privacyConfirmed", "ai", privacyConfirmed, ConfigDescriptions.get("ai", "privacyConfirmed"));
        recentModels = configuration
            .getString("recentModels", "ai", recentModels, ConfigDescriptions.get("ai", "recentModels"));
        timeoutSeconds = configuration
            .getInt("timeoutSeconds", "ai", timeoutSeconds, 5, 300, ConfigDescriptions.get("ai", "timeoutSeconds"));
        maxTokens = configuration
            .getInt("maxTokens", "ai", maxTokens, 1, 8192, ConfigDescriptions.get("ai", "maxTokens"));
        temperature = configuration.getFloat(
            "temperature",
            "ai",
            (float) temperature,
            0.0F,
            2.0F,
            ConfigDescriptions.get("ai", "temperature"));

        voiceEnabled = configuration
            .getBoolean("enabled", "voice", voiceEnabled, ConfigDescriptions.get("voice", "enabled"));
        voicePrivacyConfirmed = configuration.getBoolean(
            "privacyConfirmed",
            "voice",
            voicePrivacyConfirmed,
            ConfigDescriptions.get("voice", "privacyConfirmed"));
        voiceSttMode = Config.normalizeVoiceSttMode(
            configuration.getString("sttMode", "voice", voiceSttMode, ConfigDescriptions.get("voice", "sttMode")));
        voiceSttBaseUrl = configuration
            .getString("sttBaseUrl", "voice", voiceSttBaseUrl, ConfigDescriptions.get("voice", "sttBaseUrl"));
        voiceSttApiKey = configuration
            .getString("sttApiKey", "voice", voiceSttApiKey, ConfigDescriptions.get("voice", "sttApiKey"));
        voiceSttModel = Config.normalizeVoiceSttModel(
            configuration.getString("sttModel", "voice", voiceSttModel, ConfigDescriptions.get("voice", "sttModel")));
        voiceSttTimeoutSeconds = configuration.getInt(
            "sttTimeoutSeconds",
            "voice",
            voiceSttTimeoutSeconds,
            5,
            300,
            ConfigDescriptions.get("voice", "sttTimeoutSeconds"));

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    public static void saveLocal() {
        if (preferencesFile == null) {
            return;
        }
        Configuration configuration = new Configuration(preferencesFile);
        configuration.load();

        configuration.get("ai", "apiKey", apiKey)
            .set(apiKey);
        configuration.get("ai", "apiBaseUrl", apiBaseUrl)
            .set(apiBaseUrl);
        configuration.get("ai", "model", model)
            .set(model);
        configuration.get("ai", "networkEnabled", networkEnabled)
            .set(networkEnabled);
        configuration.get("ai", "webSearchEnabled", webSearchEnabled)
            .set(webSearchEnabled);
        configuration.get("ai", "webSearchMode", webSearchMode)
            .set(webSearchMode);
        configuration.get("ai", "debugLogging", debugLogging)
            .set(debugLogging);
        configuration.get("ai", "streamingEnabled", streamingEnabled)
            .set(streamingEnabled);
        configuration.get("ai", "privacyConfirmed", privacyConfirmed)
            .set(privacyConfirmed);
        configuration.get("ai", "recentModels", recentModels)
            .set(recentModels);
        configuration.get("ai", "timeoutSeconds", timeoutSeconds)
            .set(timeoutSeconds);
        configuration.get("ai", "maxTokens", maxTokens)
            .set(maxTokens);
        configuration.get("ai", "temperature", temperature)
            .set(temperature);

        configuration.get("voice", "enabled", voiceEnabled)
            .set(voiceEnabled);
        configuration.get("voice", "privacyConfirmed", voicePrivacyConfirmed)
            .set(voicePrivacyConfirmed);
        configuration.get("voice", "sttMode", voiceSttMode)
            .set(voiceSttMode);
        configuration.get("voice", "sttBaseUrl", voiceSttBaseUrl)
            .set(voiceSttBaseUrl);
        configuration.get("voice", "sttApiKey", voiceSttApiKey)
            .set(voiceSttApiKey);
        configuration.get("voice", "sttModel", voiceSttModel)
            .set(voiceSttModel);
        configuration.get("voice", "sttTimeoutSeconds", voiceSttTimeoutSeconds)
            .set(voiceSttTimeoutSeconds);

        configuration.save();
        applyToConfig();
    }

    private static void migrateLegacyFromShared(Configuration sharedConfiguration) {
        if (sharedConfiguration == null || !sharedConfiguration.hasCategory("ai")) {
            return;
        }
        String legacyKey = sharedConfiguration.get("ai", "apiKey", "")
            .getString();
        if (apiKey.isEmpty() && legacyKey != null
            && !legacyKey.trim()
                .isEmpty()) {
            apiKey = legacyKey.trim();
        }
        if (apiBaseUrl.equals("https://api.deepseek.com")) {
            apiBaseUrl = sharedConfiguration.get("ai", "apiBaseUrl", apiBaseUrl)
                .getString();
        }
        model = sharedConfiguration.get("ai", "model", model)
            .getString();
        networkEnabled = sharedConfiguration.get("ai", "networkEnabled", networkEnabled)
            .getBoolean();
        webSearchEnabled = sharedConfiguration.get("ai", "webSearchEnabled", webSearchEnabled)
            .getBoolean();
        webSearchMode = sharedConfiguration.get("ai", "webSearchMode", webSearchMode)
            .getString();
        debugLogging = sharedConfiguration.get("ai", "debugLogging", debugLogging)
            .getBoolean();
        streamingEnabled = sharedConfiguration.get("ai", "streamingEnabled", streamingEnabled)
            .getBoolean();
        privacyConfirmed = sharedConfiguration.get("ai", "privacyConfirmed", privacyConfirmed)
            .getBoolean();
        recentModels = sharedConfiguration.get("ai", "recentModels", recentModels)
            .getString();
        timeoutSeconds = sharedConfiguration.get("ai", "timeoutSeconds", timeoutSeconds)
            .getInt();
        maxTokens = sharedConfiguration.get("ai", "maxTokens", maxTokens)
            .getInt();
        temperature = sharedConfiguration.get("ai", "temperature", temperature)
            .getDouble();

        if (sharedConfiguration.hasCategory("voice")) {
            voiceEnabled = sharedConfiguration.get("voice", "enabled", voiceEnabled)
                .getBoolean();
            voicePrivacyConfirmed = sharedConfiguration.get("voice", "privacyConfirmed", voicePrivacyConfirmed)
                .getBoolean();
            voiceSttMode = Config.normalizeVoiceSttMode(
                sharedConfiguration.get("voice", "sttMode", voiceSttMode)
                    .getString());
            voiceSttBaseUrl = sharedConfiguration.get("voice", "sttBaseUrl", voiceSttBaseUrl)
                .getString();
            String legacyVoiceKey = sharedConfiguration.get("voice", "sttApiKey", voiceSttApiKey)
                .getString();
            if (voiceSttApiKey.isEmpty() && legacyVoiceKey != null
                && !legacyVoiceKey.trim()
                    .isEmpty()) {
                voiceSttApiKey = legacyVoiceKey.trim();
            }
            voiceSttModel = Config.normalizeVoiceSttModel(
                sharedConfiguration.get("voice", "sttModel", voiceSttModel)
                    .getString());
            voiceSttTimeoutSeconds = sharedConfiguration.get("voice", "sttTimeoutSeconds", voiceSttTimeoutSeconds)
                .getInt();
        }
    }

    private static void scrubSharedAiVoiceKeys(Configuration sharedConfiguration) {
        if (sharedConfiguration == null) {
            return;
        }
        if (sharedConfiguration.hasCategory("ai")) {
            sharedConfiguration.get("ai", "apiKey", "")
                .set("");
            sharedConfiguration.get("ai", "privacyConfirmed", false)
                .set(false);
        }
        if (sharedConfiguration.hasCategory("voice")) {
            sharedConfiguration.get("voice", "sttApiKey", "")
                .set("");
        }
        if (sharedConfiguration.hasChanged()) {
            sharedConfiguration.save();
        }
    }

    public static void applyToConfig() {
        Config.aiApiKey = apiKey;
        Config.aiApiBaseUrl = apiBaseUrl;
        Config.aiModel = model;
        Config.aiNetworkEnabled = networkEnabled;
        Config.aiWebSearchEnabled = webSearchEnabled;
        Config.aiWebSearchMode = webSearchMode;
        Config.aiDebugLogging = debugLogging;
        Config.aiStreamingEnabled = streamingEnabled;
        Config.aiPrivacyConfirmed = privacyConfirmed;
        Config.aiRecentModels = recentModels;
        Config.aiTimeoutSeconds = timeoutSeconds;
        Config.aiMaxTokens = maxTokens;
        Config.aiTemperature = temperature;

        Config.voiceAssistantEnabled = voiceEnabled;
        Config.voicePrivacyConfirmed = voicePrivacyConfirmed;
        Config.voiceSttMode = voiceSttMode;
        Config.voiceSttBaseUrl = voiceSttBaseUrl;
        Config.voiceSttApiKey = voiceSttApiKey;
        Config.voiceSttModel = voiceSttModel;
        Config.voiceSttTimeoutSeconds = voiceSttTimeoutSeconds;
    }

    public static String getApiKey() {
        if (apiKey != null && !apiKey.trim()
            .isEmpty()) {
            return apiKey.trim();
        }
        String envKey = System.getenv("DEEPSEEK_API_KEY");
        return envKey == null ? "" : envKey.trim();
    }

    public static String getVoiceSttApiKey() {
        if (voiceSttApiKey != null && !voiceSttApiKey.trim()
            .isEmpty()) {
            return voiceSttApiKey.trim();
        }
        String envKey = System.getenv("VOICE_STT_API_KEY");
        if (envKey != null && !envKey.trim()
            .isEmpty()) {
            return envKey.trim();
        }
        return getApiKey();
    }

    public static void setApiKey(String value) {
        apiKey = value == null ? "" : value.trim();
    }

    public static void setModel(String value) {
        model = value == null || value.trim()
            .isEmpty() ? "deepseek-chat" : value.trim();
        ConfigAiMutators.setAiModelInMemory(model);
        recentModels = Config.aiRecentModels;
    }

    public static void setApiBaseUrl(String value) {
        apiBaseUrl = value == null || value.trim()
            .isEmpty() ? "https://api.deepseek.com" : value.trim();
    }

    public static void setNetworkEnabled(boolean enabled) {
        networkEnabled = enabled;
    }

    public static void toggleNetworkEnabled() {
        networkEnabled = !networkEnabled;
    }

    public static void setWebSearchEnabled(boolean enabled) {
        webSearchEnabled = enabled;
    }

    public static void toggleWebSearchEnabled() {
        webSearchEnabled = !webSearchEnabled;
    }

    public static void setWebSearchMode(String modeValue) {
        webSearchMode = modeValue == null || modeValue.trim()
            .isEmpty() ? "auto" : modeValue.trim();
    }

    public static void setDebugLogging(boolean enabled) {
        debugLogging = enabled;
    }

    public static void setStreamingEnabled(boolean enabled) {
        streamingEnabled = enabled;
    }

    public static void setPrivacyConfirmed(boolean confirmed) {
        privacyConfirmed = confirmed;
    }

    public static void applyProviderProfile(ProviderProfile profile) {
        if (profile == null) {
            return;
        }
        apiBaseUrl = profile.baseUrl;
        setModel(profile.defaultModel);
        webSearchMode = profile.defaultSearchMode;
        webSearchEnabled = !"unsupported".equals(profile.defaultSearchMode) && !"off".equals(profile.defaultSearchMode);
    }

    public static void saveAllSettings(String key, String baseUrl, String modelValue, String searchModeValue,
        boolean searchEnabled, boolean networkOn, boolean debug, boolean streaming, int timeout, int tokens,
        double temp) {
        apiKey = key == null ? "" : key.trim();
        apiBaseUrl = baseUrl == null || baseUrl.trim()
            .isEmpty() ? "https://api.deepseek.com" : baseUrl.trim();
        setModel(modelValue);
        webSearchMode = searchModeValue == null || searchModeValue.trim()
            .isEmpty() ? "auto" : searchModeValue.trim();
        webSearchEnabled = searchEnabled;
        networkEnabled = networkOn;
        debugLogging = debug;
        streamingEnabled = streaming;
        timeoutSeconds = Math.max(5, Math.min(300, timeout));
        maxTokens = Math.max(1, Math.min(8192, tokens));
        temperature = Math.max(0.0D, Math.min(2.0D, temp));
        saveLocal();
    }

    public static void saveVoiceSettings(boolean enabled, boolean privacy, String sttMode, String sttBaseUrl,
        String sttKey, String sttModelValue, int timeout) {
        voiceEnabled = enabled;
        voicePrivacyConfirmed = privacy;
        voiceSttMode = Config.normalizeVoiceSttMode(sttMode);
        voiceSttBaseUrl = sttBaseUrl == null ? "" : sttBaseUrl.trim();
        voiceSttApiKey = sttKey == null ? "" : sttKey.trim();
        voiceSttModel = Config.normalizeVoiceSttModel(sttModelValue);
        voiceSttTimeoutSeconds = Math.max(5, Math.min(300, timeout));
        saveLocal();
    }

    public static String[] getRecentModels() {
        return recentModels == null || recentModels.trim()
            .isEmpty() ? new String[0] : recentModels.split(",");
    }
}
