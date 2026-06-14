package com.imgood.advancedatamonitor;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.imgood.advancedatamonitor.ai.AiProviderProfiles.ProviderProfile;

public class Config {

    private static File activeConfigFile;

    public static String aiApiBaseUrl = "https://api.deepseek.com";
    public static String aiApiKey = "";
    public static String aiModel = "deepseek-chat";
    public static boolean aiNetworkEnabled = true;
    public static boolean aiWebSearchEnabled = false;
    public static String aiWebSearchMode = "auto";
    public static boolean aiDebugLogging = false;
    public static boolean aiStreamingEnabled = false;
    public static boolean aiPrivacyConfirmed = false;
    public static String aiRecentModels = "";
    public static int aiTimeoutSeconds = 60;
    public static int aiMaxTokens = 1024;
    public static double aiTemperature = 0.7D;
    public static final String VOICE_STT_MODE_EMBEDDED_VOSK = "embedded-vosk";
    public static final String VOICE_STT_MODE_HTTP = "http";
    public static boolean voiceAssistantEnabled = false;
    public static boolean voicePrivacyConfirmed = false;
    public static String voiceSttMode = VOICE_STT_MODE_EMBEDDED_VOSK;
    public static String voiceSttBaseUrl = "";
    public static String voiceSttApiKey = "";
    public static String voiceSttModel = "zh-small";
    public static int voiceSttTimeoutSeconds = 60;
    public static int assistantMaxOrderAmount = 4096;
    public static int assistantMaxWithdrawAmount = 4096;
    public static int assistantCraftJobTimeoutSeconds = 30;
    public static int assistantMaxConcurrentCraftJobs = 2;
    public static int plannerHudMinMaxDisplay = 1;
    public static int plannerHudMaxMaxDisplay = 20;
    public static float plannerHudMinPosX = 0.0F;
    public static float plannerHudMaxPosX = 1.0F;
    public static float plannerHudMinPosY = 0.0F;
    public static float plannerHudMaxPosY = 1.0F;
    public static float plannerHudMinScale = 0.5F;
    public static float plannerHudMaxScale = 3.0F;
    public static int plannerHudMinWidth = 80;
    public static int plannerHudMaxWidth = 600;

    public static void synchronizeConfiguration(File configFile) {
        activeConfigFile = configFile;
        Configuration configuration = new Configuration(configFile);

        aiApiBaseUrl = configuration.getString(
            "apiBaseUrl",
            "ai",
            aiApiBaseUrl,
            "OpenAI-compatible chat API base URL. DeepSeek default is https://api.deepseek.com");
        aiApiKey = configuration.getString(
            "apiKey",
            "ai",
            aiApiKey,
            "API key for the chat provider. You can also set the DEEPSEEK_API_KEY environment variable.");
        aiModel = configuration.getString("model", "ai", aiModel, "Chat model name, for example deepseek-chat.");
        aiNetworkEnabled = configuration
            .getBoolean("networkEnabled", "ai", aiNetworkEnabled, "Allow AI chat to send network requests.");
        aiWebSearchEnabled = configuration.getBoolean(
            "webSearchEnabled",
            "ai",
            aiWebSearchEnabled,
            "Allow the AI chat request to ask supported providers for web search.");
        aiWebSearchMode = configuration.getString(
            "webSearchMode",
            "ai",
            aiWebSearchMode,
            "Web search request format: auto, openai, openrouter, dashscope, zhipu, generic-tools, or off.");
        aiDebugLogging = configuration
            .getBoolean("debugLogging", "ai", aiDebugLogging, "Write sanitized AI request diagnostics to the mod log.");
        aiStreamingEnabled = configuration.getBoolean(
            "streamingEnabled",
            "ai",
            aiStreamingEnabled,
            "Use streaming chat responses when the provider supports it.");
        aiPrivacyConfirmed = configuration.getBoolean(
            "privacyConfirmed",
            "ai",
            aiPrivacyConfirmed,
            "Whether the player has confirmed that AI chat sends prompts to the configured provider.");
        aiRecentModels = configuration
            .getString("recentModels", "ai", aiRecentModels, "Comma-separated recently used AI model names.");
        aiTimeoutSeconds = configuration
            .getInt("timeoutSeconds", "ai", aiTimeoutSeconds, 5, 300, "HTTP timeout in seconds.");
        aiMaxTokens = configuration
            .getInt("maxTokens", "ai", aiMaxTokens, 1, 8192, "Maximum tokens returned by the model.");
        aiTemperature = configuration
            .getFloat("temperature", "ai", (float) aiTemperature, 0.0F, 2.0F, "Sampling temperature.");
        voiceAssistantEnabled = configuration
            .getBoolean("enabled", "voice", voiceAssistantEnabled, "Enable the voice assistant hotkey and STT flow.");
        voicePrivacyConfirmed = configuration.getBoolean(
            "privacyConfirmed",
            "voice",
            voicePrivacyConfirmed,
            "Whether the player has confirmed the configured voice recognition mode.");
        voiceSttMode = normalizeVoiceSttMode(
            configuration.getString("sttMode", "voice", voiceSttMode, "Speech-to-text mode: embedded-vosk or http."));
        voiceSttBaseUrl = configuration.getString(
            "sttBaseUrl",
            "voice",
            voiceSttBaseUrl,
            "OpenAI-compatible STT API base URL. Used only when sttMode=http. Empty uses ai.apiBaseUrl.");
        voiceSttApiKey = configuration.getString(
            "sttApiKey",
            "voice",
            voiceSttApiKey,
            "API key for HTTP STT. Empty uses VOICE_STT_API_KEY or ai.apiKey. Not needed for embedded-vosk.");
        String configuredVoiceSttModel = configuration.getString(
            "sttModel",
            "voice",
            voiceSttModel,
            "Speech-to-text model. embedded-vosk default is zh-small; http default is whisper-1.");
        voiceSttModel = normalizeVoiceSttModel(configuredVoiceSttModel);
        if (!voiceSttModel.equals(configuredVoiceSttModel)) {
            configuration.get("voice", "sttModel", configuredVoiceSttModel)
                .set(voiceSttModel);
        }
        voiceSttTimeoutSeconds = configuration
            .getInt("sttTimeoutSeconds", "voice", voiceSttTimeoutSeconds, 5, 300, "STT timeout in seconds.");
        assistantMaxOrderAmount = configuration.getInt(
            "maxOrderAmount",
            "assistant",
            assistantMaxOrderAmount,
            1,
            1000000,
            "Maximum amount accepted for one voice crafting order.");
        assistantMaxWithdrawAmount = configuration.getInt(
            "maxWithdrawAmount",
            "assistant",
            assistantMaxWithdrawAmount,
            1,
            1000000,
            "Maximum amount accepted for one AE2 storage withdraw into player inventory.");
        assistantCraftJobTimeoutSeconds = configuration.getInt(
            "craftJobTimeoutSeconds",
            "assistant",
            assistantCraftJobTimeoutSeconds,
            1,
            300,
            "Maximum seconds to wait for an AE2 crafting calculation before cancelling it.");
        assistantMaxConcurrentCraftJobs = configuration.getInt(
            "maxConcurrentCraftJobs",
            "assistant",
            assistantMaxConcurrentCraftJobs,
            1,
            16,
            "Maximum concurrent assistant AE2 crafting calculations per player.");
        plannerHudMinMaxDisplay = configuration.getInt(
            "minMaxDisplay",
            "plannerHudLimits",
            plannerHudMinMaxDisplay,
            1,
            100,
            "Minimum allowed planner HUD displayed entry count.");
        plannerHudMaxMaxDisplay = configuration.getInt(
            "maxMaxDisplay",
            "plannerHudLimits",
            plannerHudMaxMaxDisplay,
            plannerHudMinMaxDisplay,
            100,
            "Maximum allowed planner HUD displayed entry count.");
        plannerHudMinPosX = configuration.getFloat(
            "minPosX",
            "plannerHudLimits",
            plannerHudMinPosX,
            -10.0F,
            10.0F,
            "Minimum allowed planner HUD horizontal position ratio.");
        plannerHudMaxPosX = configuration.getFloat(
            "maxPosX",
            "plannerHudLimits",
            plannerHudMaxPosX,
            plannerHudMinPosX,
            10.0F,
            "Maximum allowed planner HUD horizontal position ratio.");
        plannerHudMinPosY = configuration.getFloat(
            "minPosY",
            "plannerHudLimits",
            plannerHudMinPosY,
            -10.0F,
            10.0F,
            "Minimum allowed planner HUD vertical position ratio.");
        plannerHudMaxPosY = configuration.getFloat(
            "maxPosY",
            "plannerHudLimits",
            plannerHudMaxPosY,
            plannerHudMinPosY,
            10.0F,
            "Maximum allowed planner HUD vertical position ratio.");
        plannerHudMinScale = configuration.getFloat(
            "minScale",
            "plannerHudLimits",
            plannerHudMinScale,
            0.1F,
            20.0F,
            "Minimum allowed planner HUD scale.");
        plannerHudMaxScale = configuration.getFloat(
            "maxScale",
            "plannerHudLimits",
            plannerHudMaxScale,
            plannerHudMinScale,
            20.0F,
            "Maximum allowed planner HUD scale.");
        plannerHudMinWidth = configuration.getInt(
            "minWidth",
            "plannerHudLimits",
            plannerHudMinWidth,
            1,
            2000,
            "Minimum allowed planner HUD text width.");
        plannerHudMaxWidth = configuration.getInt(
            "maxWidth",
            "plannerHudLimits",
            plannerHudMaxWidth,
            plannerHudMinWidth,
            2000,
            "Maximum allowed planner HUD text width.");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    public static void setAiApiKey(String apiKey) {
        aiApiKey = apiKey == null ? "" : apiKey.trim();
        saveAiConfiguration();
    }

    public static void setAiModel(String model) {
        setAiModelInMemory(model);
        saveAiConfiguration();
    }

    public static void setAiApiBaseUrl(String apiBaseUrl) {
        aiApiBaseUrl = apiBaseUrl == null || apiBaseUrl.trim()
            .isEmpty() ? "https://api.deepseek.com" : apiBaseUrl.trim();
        saveAiConfiguration();
    }

    public static void setAiNetworkEnabled(boolean networkEnabled) {
        aiNetworkEnabled = networkEnabled;
        saveAiConfiguration();
    }

    public static void toggleAiNetworkEnabled() {
        setAiNetworkEnabled(!aiNetworkEnabled);
    }

    public static void setAiWebSearchEnabled(boolean webSearchEnabled) {
        aiWebSearchEnabled = webSearchEnabled;
        saveAiConfiguration();
    }

    public static void toggleAiWebSearchEnabled() {
        setAiWebSearchEnabled(!aiWebSearchEnabled);
    }

    public static void setAiWebSearchMode(String webSearchMode) {
        aiWebSearchMode = webSearchMode == null || webSearchMode.trim()
            .isEmpty() ? "auto" : webSearchMode.trim();
        saveAiConfiguration();
    }

    public static void applyAiProviderProfile(ProviderProfile profile) {
        if (profile == null) {
            return;
        }
        aiApiBaseUrl = profile.baseUrl;
        setAiModelInMemory(profile.defaultModel);
        aiWebSearchMode = profile.defaultSearchMode;
        aiWebSearchEnabled = !"unsupported".equals(profile.defaultSearchMode)
            && !"off".equals(profile.defaultSearchMode);
        saveAiConfiguration();
    }

    public static void setAiDebugLogging(boolean debugLogging) {
        aiDebugLogging = debugLogging;
        saveAiConfiguration();
    }

    public static void setAiStreamingEnabled(boolean streamingEnabled) {
        aiStreamingEnabled = streamingEnabled;
        saveAiConfiguration();
    }

    public static void setAiPrivacyConfirmed(boolean privacyConfirmed) {
        aiPrivacyConfirmed = privacyConfirmed;
        saveAiConfiguration();
    }

    public static void saveAiSettings(String apiKey, String baseUrl, String model, String webSearchMode,
        boolean webSearchEnabled, boolean networkEnabled, boolean debugLogging, boolean streamingEnabled,
        int timeoutSeconds, int maxTokens, double temperature) {
        aiApiKey = apiKey == null ? "" : apiKey.trim();
        aiApiBaseUrl = baseUrl == null || baseUrl.trim()
            .isEmpty() ? "https://api.deepseek.com" : baseUrl.trim();
        setAiModelInMemory(model);
        aiWebSearchMode = webSearchMode == null || webSearchMode.trim()
            .isEmpty() ? "auto" : webSearchMode.trim();
        aiWebSearchEnabled = webSearchEnabled;
        aiNetworkEnabled = networkEnabled;
        aiDebugLogging = debugLogging;
        aiStreamingEnabled = streamingEnabled;
        aiTimeoutSeconds = Math.max(5, Math.min(300, timeoutSeconds));
        aiMaxTokens = Math.max(1, Math.min(8192, maxTokens));
        aiTemperature = Math.max(0.0D, Math.min(2.0D, temperature));
        saveAiConfiguration();
    }

    private static void setAiModelInMemory(String model) {
        aiModel = model == null || model.trim()
            .isEmpty() ? "deepseek-chat" : model.trim();
        addRecentAiModel(aiModel);
    }

    private static void addRecentAiModel(String model) {
        if (model == null || model.trim()
            .isEmpty()) {
            return;
        }
        String normalized = model.trim();
        StringBuilder builder = new StringBuilder(normalized);
        int count = 1;
        String recentModels = aiRecentModels == null ? "" : aiRecentModels;
        for (String entry : recentModels.split(",")) {
            String existing = entry.trim();
            if (existing.isEmpty() || existing.equalsIgnoreCase(normalized)) {
                continue;
            }
            builder.append(',')
                .append(existing);
            if (++count >= 8) {
                break;
            }
        }
        aiRecentModels = builder.toString();
    }

    public static String[] getRecentAiModels() {
        return aiRecentModels == null || aiRecentModels.trim()
            .isEmpty() ? new String[0] : aiRecentModels.split(",");
    }

    public static void saveAiConfiguration() {
        if (activeConfigFile == null) {
            return;
        }
        Configuration configuration = new Configuration(activeConfigFile);
        configuration.load();
        configuration.get("ai", "apiBaseUrl", aiApiBaseUrl)
            .set(aiApiBaseUrl);
        configuration.get("ai", "apiKey", aiApiKey)
            .set(aiApiKey);
        configuration.get("ai", "model", aiModel)
            .set(aiModel);
        configuration.get("ai", "networkEnabled", aiNetworkEnabled)
            .set(aiNetworkEnabled);
        configuration.get("ai", "webSearchEnabled", aiWebSearchEnabled)
            .set(aiWebSearchEnabled);
        configuration.get("ai", "webSearchMode", aiWebSearchMode)
            .set(aiWebSearchMode);
        configuration.get("ai", "debugLogging", aiDebugLogging)
            .set(aiDebugLogging);
        configuration.get("ai", "streamingEnabled", aiStreamingEnabled)
            .set(aiStreamingEnabled);
        configuration.get("ai", "privacyConfirmed", aiPrivacyConfirmed)
            .set(aiPrivacyConfirmed);
        configuration.get("ai", "recentModels", aiRecentModels)
            .set(aiRecentModels);
        configuration.get("ai", "timeoutSeconds", aiTimeoutSeconds)
            .set(aiTimeoutSeconds);
        configuration.get("ai", "maxTokens", aiMaxTokens)
            .set(aiMaxTokens);
        configuration.get("ai", "temperature", aiTemperature)
            .set(aiTemperature);
        configuration.get("voice", "enabled", voiceAssistantEnabled)
            .set(voiceAssistantEnabled);
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
        configuration.get("assistant", "maxOrderAmount", assistantMaxOrderAmount)
            .set(assistantMaxOrderAmount);
        configuration.get("assistant", "maxWithdrawAmount", assistantMaxWithdrawAmount)
            .set(assistantMaxWithdrawAmount);
        configuration.save();
    }

    public static void saveVoiceSettings(boolean enabled, boolean privacyConfirmed, String sttBaseUrl, String sttApiKey,
        String sttModel, int timeoutSeconds) {
        saveVoiceSettings(enabled, privacyConfirmed, voiceSttMode, sttBaseUrl, sttApiKey, sttModel, timeoutSeconds);
    }

    public static void saveVoiceSettings(boolean enabled, boolean privacyConfirmed, String sttMode, String sttBaseUrl,
        String sttApiKey, String sttModel, int timeoutSeconds) {
        voiceAssistantEnabled = enabled;
        voicePrivacyConfirmed = privacyConfirmed;
        voiceSttMode = normalizeVoiceSttMode(sttMode);
        voiceSttBaseUrl = sttBaseUrl == null ? "" : sttBaseUrl.trim();
        voiceSttApiKey = sttApiKey == null ? "" : sttApiKey.trim();
        voiceSttModel = normalizeVoiceSttModel(sttModel);
        voiceSttTimeoutSeconds = Math.max(5, Math.min(300, timeoutSeconds));
        saveAiConfiguration();
    }

    public static boolean isEmbeddedVoskVoiceMode() {
        return VOICE_STT_MODE_EMBEDDED_VOSK.equalsIgnoreCase(voiceSttMode);
    }

    public static boolean isHttpVoiceMode() {
        return VOICE_STT_MODE_HTTP.equalsIgnoreCase(voiceSttMode);
    }

    public static String normalizeVoiceSttMode(String sttMode) {
        if (sttMode != null && VOICE_STT_MODE_HTTP.equalsIgnoreCase(sttMode.trim())) {
            return VOICE_STT_MODE_HTTP;
        }
        return VOICE_STT_MODE_EMBEDDED_VOSK;
    }

    public static String normalizeVoiceSttModel(String sttModel) {
        if (sttModel != null && !sttModel.trim()
            .isEmpty()) {
            String normalized = sttModel.trim();
            if (isEmbeddedVoskVoiceMode() && isLegacyWhisperModelName(normalized)) {
                return "zh-small";
            }
            return normalized;
        }
        return isHttpVoiceMode() ? "whisper-1" : "zh-small";
    }

    private static boolean isLegacyWhisperModelName(String sttModel) {
        return "whisper-1".equalsIgnoreCase(sttModel) || "tiny".equalsIgnoreCase(sttModel)
            || "base".equalsIgnoreCase(sttModel)
            || "small".equalsIgnoreCase(sttModel)
            || "medium".equalsIgnoreCase(sttModel)
            || "large".equalsIgnoreCase(sttModel)
            || sttModel.toLowerCase()
                .startsWith("whisper-");
    }

    public static File getVoiceDataDirectory() {
        File configDir = activeConfigFile == null ? new File("config") : activeConfigFile.getParentFile();
        return new File(configDir != null ? configDir : new File("config"), "voice");
    }

    public static void setVoicePrivacyConfirmed(boolean confirmed) {
        voicePrivacyConfirmed = confirmed;
        saveAiConfiguration();
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
        return getAiApiKey();
    }

    public static String getAiApiKey() {
        if (aiApiKey != null && !aiApiKey.trim()
            .isEmpty()) {
            return aiApiKey.trim();
        }
        String envKey = System.getenv("DEEPSEEK_API_KEY");
        return envKey == null ? "" : envKey.trim();
    }
}
