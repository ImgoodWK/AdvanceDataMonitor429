package com.imgood.textech.gui.manual;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.ConfigDescriptions;
import com.imgood.textech.gui.manual.ManualPage.ManualItemEntry;

/**
 * Loads manual content from JSON assets and provides config metadata.
 */
public class ManualDataLoader {

    private static final Gson GSON = new Gson();
    private static List<ManualChapter> chapters = null;

    /**
     * Load all chapters from assets.
     */
    public static List<ManualChapter> loadChapters() {
        if (chapters != null) return chapters;

        chapters = new ArrayList<>();
        try {
            InputStreamReader reader = new InputStreamReader(
                Minecraft.getMinecraft()
                    .getResourceManager()
                    .getResource(new ResourceLocation(AdvanceDataMonitor.MODID, "manual/index.json"))
                    .getInputStream(),
                "UTF-8");

            JsonObject index = new JsonParser().parse(reader)
                .getAsJsonObject();
            reader.close();

            JsonArray chapterArr = index.getAsJsonArray("chapters");
            for (JsonElement elem : chapterArr) {
                JsonObject chObj = elem.getAsJsonObject();
                String id = chObj.get("id")
                    .getAsString();
                String titleKey = chObj.get("titleKey")
                    .getAsString();
                String icon = chObj.get("icon")
                    .getAsString();

                ManualChapter chapter = new ManualChapter(id, titleKey, icon);
                loadChapterPages(chapter, id);
                chapters.add(chapter);
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("Failed to load manual index: {}", e.getMessage());
        }

        return chapters;
    }

    private static void loadChapterPages(ManualChapter chapter, String chapterId) {
        try {
            InputStreamReader reader = new InputStreamReader(
                Minecraft.getMinecraft()
                    .getResourceManager()
                    .getResource(
                        new ResourceLocation(AdvanceDataMonitor.MODID, "manual/chapters/" + chapterId + ".json"))
                    .getInputStream(),
                "UTF-8");

            JsonObject chapterJson = new JsonParser().parse(reader)
                .getAsJsonObject();
            reader.close();

            chapter.chapterTitleKey = chapterJson.get("chapterTitleKey")
                .getAsString();
            JsonArray pagesArr = chapterJson.getAsJsonArray("pages");
            for (JsonElement elem : pagesArr) {
                JsonObject pageObj = elem.getAsJsonObject();
                ManualPage page = GSON.fromJson(pageObj, ManualPage.class);

                // Parse items array for item_showcase pages
                if (page.isItemShowcase() && pageObj.has("items")) {
                    JsonArray itemsArr = pageObj.getAsJsonArray("items");
                    page.items = new ArrayList<>();
                    for (JsonElement itemElem : itemsArr) {
                        ManualItemEntry entry = GSON.fromJson(itemElem, ManualItemEntry.class);
                        page.items.add(entry);
                    }
                }

                chapter.pages.add(page);
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("Failed to load chapter {}: {}", chapterId, e.getMessage());
        }
    }

    /**
     * Config metadata for the config_reference pages.
     * Organized by config category.
     */
    @SuppressWarnings("serial")
    public static Map<String, List<ConfigEntry>> getConfigMetadata() {
        Map<String, List<ConfigEntry>> map = new LinkedHashMap<>();

        List<ConfigEntry> aiEntries = new ArrayList<>();
        aiEntries.add(entry("ai", "apiBaseUrl", "String", "https://api.deepseek.com"));
        aiEntries.add(entry("ai", "apiKey", "String", "(empty)"));
        aiEntries.add(entry("ai", "model", "String", "deepseek-chat"));
        aiEntries.add(entry("ai", "networkEnabled", "boolean", "true"));
        aiEntries.add(entry("ai", "webSearchEnabled", "boolean", "false"));
        aiEntries.add(entry("ai", "webSearchMode", "String", "auto"));
        aiEntries.add(entry("ai", "debugLogging", "boolean", "false"));
        aiEntries.add(entry("ai", "streamingEnabled", "boolean", "false"));
        aiEntries.add(entry("ai", "privacyConfirmed", "boolean", "false"));
        aiEntries.add(entry("ai", "recentModels", "String", "(empty)"));
        aiEntries.add(entry("ai", "timeoutSeconds", "int", "60"));
        aiEntries.add(entry("ai", "maxTokens", "int", "1024"));
        aiEntries.add(entry("ai", "temperature", "float", "0.7"));
        map.put("ai", aiEntries);

        List<ConfigEntry> voiceEntries = new ArrayList<>();
        voiceEntries.add(entry("voice", "enabled", "boolean", "false"));
        voiceEntries.add(entry("voice", "privacyConfirmed", "boolean", "false"));
        voiceEntries.add(entry("voice", "sttMode", "String", "embedded-vosk"));
        voiceEntries.add(entry("voice", "sttBaseUrl", "String", "(empty)"));
        voiceEntries.add(entry("voice", "sttApiKey", "String", "(empty)"));
        voiceEntries.add(entry("voice", "sttModel", "String", "zh-small"));
        voiceEntries.add(entry("voice", "sttTimeoutSeconds", "int", "60"));
        map.put("voice", voiceEntries);

        List<ConfigEntry> assistantEntries = new ArrayList<>();
        assistantEntries.add(entry("assistant", "maxOrderAmount", "int", "4096"));
        assistantEntries.add(entry("assistant", "maxWithdrawAmount", "int", "4096"));
        assistantEntries.add(entry("assistant", "craftJobTimeoutSeconds", "int", "30"));
        assistantEntries.add(entry("assistant", "maxConcurrentCraftJobs", "int", "2"));
        map.put("assistant", assistantEntries);

        List<ConfigEntry> hudEntries = new ArrayList<>();
        hudEntries.add(entry("plannerHudLimits", "minMaxDisplay", "int", "1"));
        hudEntries.add(entry("plannerHudLimits", "maxMaxDisplay", "int", "20"));
        hudEntries.add(entry("plannerHudLimits", "minPosX", "float", "0.0"));
        hudEntries.add(entry("plannerHudLimits", "maxPosX", "float", "1.0"));
        hudEntries.add(entry("plannerHudLimits", "minPosY", "float", "0.0"));
        hudEntries.add(entry("plannerHudLimits", "maxPosY", "float", "1.0"));
        hudEntries.add(entry("plannerHudLimits", "minScale", "float", "0.5"));
        hudEntries.add(entry("plannerHudLimits", "maxScale", "float", "3.0"));
        hudEntries.add(entry("plannerHudLimits", "minWidth", "int", "80"));
        hudEntries.add(entry("plannerHudLimits", "maxWidth", "int", "600"));
        map.put("plannerHudLimits", hudEntries);

        List<ConfigEntry> dataLoomEntries = new ArrayList<>();
        dataLoomEntries.add(entry("dataLoomCell", "syncIntervalSeconds", "int", "5"));
        dataLoomEntries.add(entry("dataLoomCell", "energyDrainPerTick", "float", "999999"));
        map.put("dataLoomCell", dataLoomEntries);

        List<ConfigEntry> dataDustLoomEntries = new ArrayList<>();
        dataDustLoomEntries.add(entry("dataDustLoomCell", "itemRatePerSecond", "float", "1.0"));
        map.put("dataDustLoomCell", dataDustLoomEntries);

        List<ConfigEntry> dataFormLoomEntries = new ArrayList<>();
        dataFormLoomEntries.add(entry("dataFormLoomCell", "itemRatePerSecond", "float", "1.0"));
        map.put("dataFormLoomCell", dataFormLoomEntries);

        List<ConfigEntry> dataFlowEntries = new ArrayList<>();
        dataFlowEntries.add(entry("dataFlowCell", "fluidRatePerSecond", "int", "1000"));
        map.put("dataFlowCell", dataFlowEntries);

        List<ConfigEntry> dataSourceLoomEntries = new ArrayList<>();
        dataSourceLoomEntries.add(entry("dataSourceLoomCell", "essentiaRatePerSecond", "int", "1000"));
        map.put("dataSourceLoomCell", dataSourceLoomEntries);

        List<ConfigEntry> superOrangeEntries = new ArrayList<>();
        superOrangeEntries.add(entry("superOrange", "droneEnabled", "boolean", "true"));
        superOrangeEntries.add(entry("superOrange", "headEffectsEnabled", "boolean", "true"));
        superOrangeEntries.add(entry("superOrange", "dropMultiplierEnabled", "boolean", "true"));
        superOrangeEntries.add(entry("superOrange", "dropMultiplier", "int", "2"));
        superOrangeEntries.add(entry("superOrange", "projectileImmunityEnabled", "boolean", "true"));
        superOrangeEntries.add(entry("superOrange", "droneAttackRange", "float", "15.0"));
        superOrangeEntries.add(entry("superOrange", "droneAttackDamage", "float", "1.0"));
        superOrangeEntries.add(entry("superOrange", "droneAttacksPerSecond", "int", "5"));
        superOrangeEntries.add(entry("superOrange", "droneMaxClones", "int", "3"));
        superOrangeEntries.add(entry("superOrange", "droneFollowHeight", "float", "0.5"));
        map.put("superOrange", superOrangeEntries);

        return map;
    }

    private static ConfigEntry entry(String category, String key, String type, String defaultValue) {
        return new ConfigEntry(category, key, type, defaultValue, ConfigDescriptions.get(category, key));
    }

    /**
     * A single config entry for display in the manual.
     */
    public static class ConfigEntry {

        public final String category;
        public final String key;
        public final String type;
        public final String defaultValue;
        public final String description;

        public ConfigEntry(String category, String key, String type, String defaultValue, String description) {
            this.category = category;
            this.key = key;
            this.type = type;
            this.defaultValue = defaultValue;
            this.description = description;
        }
    }
}
