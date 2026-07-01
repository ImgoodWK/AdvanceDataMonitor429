package com.imgood.textech;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.imgood.textech.assistant.ai.AiProviderProfiles.ProviderProfile;
import com.imgood.textech.config.ConfigAssistantLoader;
import com.imgood.textech.config.ConfigCompatLoader;
import com.imgood.textech.config.ConfigDataLoomLoader;
import com.imgood.textech.config.ConfigDebugLoader;
import com.imgood.textech.config.ConfigGrappleLoader;
import com.imgood.textech.config.ConfigMatterBallDecompressorLoader;
import com.imgood.textech.config.ConfigPlannerHudLoader;
import com.imgood.textech.config.ConfigSuperOrangeLoader;

import cpw.mods.fml.common.FMLCommonHandler;

/**
 * Public configuration facade. Field values are loaded by {@code config/Config*Loader} classes.
 */
public class Config {

    private static File activeConfigFile;

    // --- compat ---
    /** {@code auto}, {@code legacy}, or {@code native} —forces AE integration profile when not auto. */
    public static String compatAeProfileOverride = "auto";

    // --- debug (defaults false) ---
    public static boolean debugGeneral = false;
    public static boolean debugGuiNetworkLink = false;
    /** When true, data monitors refresh random chart data every tick (debug only). */
    public static boolean debugMonitorTestMode = false;

    // --- ai ---
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

    // --- voice ---
    public static final String VOICE_STT_MODE_EMBEDDED_VOSK = "embedded-vosk";
    public static final String VOICE_STT_MODE_HTTP = "http";
    public static boolean voiceAssistantEnabled = false;
    public static boolean voicePrivacyConfirmed = false;
    public static String voiceSttMode = VOICE_STT_MODE_EMBEDDED_VOSK;
    public static String voiceSttBaseUrl = "";
    public static String voiceSttApiKey = "";
    public static String voiceSttModel = "zh-small";
    public static int voiceSttTimeoutSeconds = 60;

    // --- assistant ---
    public static int assistantMaxOrderAmount = 4096;
    public static int assistantMaxWithdrawAmount = 4096;
    public static int assistantCraftJobTimeoutSeconds = 30;
    public static int assistantMaxConcurrentCraftJobs = 2;
    public static int assistantQueryCandidateBatchSize = 1000;
    public static int assistantMaxQueryCandidates = 2000;
    public static int assistantLinkSearchRadius = 32;

    // --- data loom ---
    public static double dataDustLoomCellItemRatePerSecond = 1.0D;
    public static double dataFormLoomCellItemRatePerSecond = 1.0D;
    public static int dataFlowCellFluidRatePerSecond = 1000;
    public static int dataSourceLoomCellEssentiaRatePerSecond = 1000;
    public static int dataLoomCellSyncIntervalSeconds = 5;
    public static boolean dataLoomCellDebugLogging = false;
    public static double dataLoomCellEnergyDrainPerTick = 999999.0D;
    public static double weaveAmplifierRateMultiplier = 4.0D;
    public static double superWeaveAmplifierRateMultiplier = 16.0D;

    // --- planner hud limits ---
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

    // --- super orange ---
    public static boolean superOrangeDroneEnabled = true;
    public static boolean superOrangeHeadEffectsEnabled = true;
    public static boolean superOrangeDropMultiplierEnabled = true;
    public static int superOrangeDropMultiplier = 2;
    public static int superOrangeDropMultiplierMax = 2;
    public static boolean superOrangeProjectileImmunityEnabled = true;
    public static double superOrangeDroneAttackRange = 15.0D;
    public static double superOrangeDroneAttackDamage = 1.0D;
    public static int superOrangeDroneAttacksPerSecond = 5;
    public static int superOrangeDroneMaxClones = 3;
    public static double superOrangeDroneFollowHeight = 0.5D;

    // --- matter ball decompressor ---
    public static double matterBallDecompressorItemsPerSecond = 16.0D;

    // --- grapple ---
    public static int grappleHintRange = 24;
    public static int grappleInteractRange = 12;
    public static int grappleScanChunkRadius = 8;
    public static int grappleMaxTravelChunkRadius = 8;
    public static double grappleMoveSpeed = 3.75D;
    public static int grappleSnapRadiusPx = 72;
    public static float grappleTravelSnapDegrees = 40.0F;
    public static float grappleAttachSnapDegrees = 22.0F;
    public static int grappleMaxTravelQueueSize = 20;
    public static int grappleMaxSavedRoutes = 64;
    public static int grappleMaxNodesPerRoute = 128;

    public static void synchronizeConfiguration(File configFile) {
        activeConfigFile = configFile;
        Configuration configuration = new Configuration(configFile);

        ConfigDebugLoader.load(configuration);
        ConfigCompatLoader.load(configuration);
        ConfigAssistantLoader.load(configuration);
        ConfigPlannerHudLoader.load(configuration);
        ConfigDataLoomLoader.load(configuration);
        ConfigSuperOrangeLoader.load(configuration);
        ConfigMatterBallDecompressorLoader.load(configuration);
        ConfigGrappleLoader.load(configuration);

        if (!FMLCommonHandler.instance()
            .getSide()
            .isClient()) {
            clearClientOnlySettings();
        }

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    public static void clearClientOnlySettings() {
        aiApiKey = "";
        aiPrivacyConfirmed = false;
        voiceSttApiKey = "";
        voicePrivacyConfirmed = false;
    }

    private static boolean isClientSide() {
        return FMLCommonHandler.instance()
            .getSide()
            .isClient();
    }

    public static void setAiApiKey(String apiKey) {
        if (!isClientSide()) {
            return;
        }
        com.imgood.textech.client.AiClientPreferences.setApiKey(apiKey);
        com.imgood.textech.client.AiClientPreferences.saveLocal();
    }

    public static void setAiModel(String model) {
        if (!isClientSide()) {
            return;
        }
        com.imgood.textech.client.AiClientPreferences.setModel(model);
        com.imgood.textech.client.AiClientPreferences.saveLocal();
    }

    public static void setAiApiBaseUrl(String apiBaseUrl) {
        if (!isClientSide()) {
            return;
        }
        com.imgood.textech.client.AiClientPreferences.setApiBaseUrl(apiBaseUrl);
        com.imgood.textech.client.AiClientPreferences.saveLocal();
    }

    public static void setAiNetworkEnabled(boolean networkEnabled) {
        if (!isClientSide()) {
            return;
        }
        com.imgood.textech.client.AiClientPreferences.setNetworkEnabled(networkEnabled);
        com.imgood.textech.client.AiClientPreferences.saveLocal();
    }

    public static void toggleAiNetworkEnabled() {
        if (!isClientSide()) {
            return;
        }
        com.imgood.textech.client.AiClientPreferences.toggleNetworkEnabled();
        com.imgood.textech.client.AiClientPreferences.saveLocal();
    }

    public static void setAiWebSearchEnabled(boolean webSearchEnabled) {
        if (!isClientSide()) {
            return;
        }
        com.imgood.textech.client.AiClientPreferences.setWebSearchEnabled(webSearchEnabled);
        com.imgood.textech.client.AiClientPreferences.saveLocal();
    }

    public static void toggleAiWebSearchEnabled() {
        if (!isClientSide()) {
            return;
        }
        com.imgood.textech.client.AiClientPreferences.toggleWebSearchEnabled();
        com.imgood.textech.client.AiClientPreferences.saveLocal();
    }

    public static void setAiWebSearchMode(String webSearchMode) {
        if (!isClientSide()) {
            return;
        }
        com.imgood.textech.client.AiClientPreferences.setWebSearchMode(webSearchMode);
        com.imgood.textech.client.AiClientPreferences.saveLocal();
    }

    public static void applyAiProviderProfile(ProviderProfile profile) {
        if (!isClientSide()) {
            return;
        }
        com.imgood.textech.client.AiClientPreferences.applyProviderProfile(profile);
        com.imgood.textech.client.AiClientPreferences.saveLocal();
    }

    public static void setAiDebugLogging(boolean debugLogging) {
        if (!isClientSide()) {
            return;
        }
        com.imgood.textech.client.AiClientPreferences.setDebugLogging(debugLogging);
        com.imgood.textech.client.AiClientPreferences.saveLocal();
    }

    public static void setAiStreamingEnabled(boolean streamingEnabled) {
        if (!isClientSide()) {
            return;
        }
        com.imgood.textech.client.AiClientPreferences.setStreamingEnabled(streamingEnabled);
        com.imgood.textech.client.AiClientPreferences.saveLocal();
    }

    public static void setAiPrivacyConfirmed(boolean privacyConfirmed) {
        if (!isClientSide()) {
            return;
        }
        com.imgood.textech.client.AiClientPreferences.setPrivacyConfirmed(privacyConfirmed);
        com.imgood.textech.client.AiClientPreferences.saveLocal();
    }

    public static void saveAiSettings(String apiKey, String baseUrl, String model, String webSearchMode,
        boolean webSearchEnabled, boolean networkEnabled, boolean debugLogging, boolean streamingEnabled,
        int timeoutSeconds, int maxTokens, double temperature) {
        if (!isClientSide()) {
            return;
        }
        com.imgood.textech.client.AiClientPreferences.saveAllSettings(
            apiKey,
            baseUrl,
            model,
            webSearchMode,
            webSearchEnabled,
            networkEnabled,
            debugLogging,
            streamingEnabled,
            timeoutSeconds,
            maxTokens,
            temperature);
    }

    public static String[] getRecentAiModels() {
        if (isClientSide()) {
            return com.imgood.textech.client.AiClientPreferences.getRecentModels();
        }
        return new String[0];
    }

    public static void saveAiConfiguration() {
        if (!isClientSide()) {
            return;
        }
        com.imgood.textech.client.AiClientPreferences.saveLocal();
    }

    public static void saveVoiceSettings(boolean enabled, boolean privacyConfirmed, String sttBaseUrl, String sttApiKey,
        String sttModel, int timeoutSeconds) {
        saveVoiceSettings(enabled, privacyConfirmed, voiceSttMode, sttBaseUrl, sttApiKey, sttModel, timeoutSeconds);
    }

    public static void saveVoiceSettings(boolean enabled, boolean privacyConfirmed, String sttMode, String sttBaseUrl,
        String sttApiKey, String sttModel, int timeoutSeconds) {
        if (!isClientSide()) {
            return;
        }
        com.imgood.textech.client.AiClientPreferences
            .saveVoiceSettings(enabled, privacyConfirmed, sttMode, sttBaseUrl, sttApiKey, sttModel, timeoutSeconds);
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
        if (!isClientSide()) {
            return;
        }
        com.imgood.textech.client.AiClientPreferences.saveVoiceSettings(
            voiceAssistantEnabled,
            confirmed,
            voiceSttMode,
            voiceSttBaseUrl,
            voiceSttApiKey,
            voiceSttModel,
            voiceSttTimeoutSeconds);
    }

    public static String getVoiceSttApiKey() {
        if (!isClientSide()) {
            return "";
        }
        return com.imgood.textech.client.AiClientPreferences.getVoiceSttApiKey();
    }

    public static String getAiApiKey() {
        if (!isClientSide()) {
            return "";
        }
        return com.imgood.textech.client.AiClientPreferences.getApiKey();
    }
}
