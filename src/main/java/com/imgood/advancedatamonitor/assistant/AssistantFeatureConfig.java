package com.imgood.advancedatamonitor.assistant;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.advancedatamonitor.AdvanceDataMonitor;

public final class AssistantFeatureConfig {

    private static final String FILE_NAME = "assistant-features.json";
    private static final String RESOURCE_PATH = "/assets/advancedatamonitor/config/assistant-features.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();

    private static FeatureConfigData data;
    private static boolean loaded;

    private AssistantFeatureConfig() {}

    public static synchronized FeatureConfigData get() {
        if (!loaded) {
            reload();
        }
        return data;
    }

    public static synchronized void reload() {
        File file = AssistantDataFiles.dataFile(FILE_NAME);
        ensureFile(file);
        FeatureConfigData fallback = resourceDefaults();
        FeatureConfigData loadedData = null;
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8")) {
            loadedData = GSON.fromJson(reader, FeatureConfigData.class);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("Failed to load assistant features config; using bundled defaults", e);
        }
        data = loadedData != null && loadedData.features != null && !loadedData.features.isEmpty() ? loadedData
            : fallback;
        loaded = true;
    }

    private static void ensureFile(File file) {
        if (file.exists()) {
            return;
        }
        try {
            InputStream stream = AssistantFeatureConfig.class.getResourceAsStream(RESOURCE_PATH);
            if (stream != null) {
                try (InputStreamReader reader = new InputStreamReader(stream, "UTF-8");
                    java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(
                        new FileOutputStream(file),
                        "UTF-8")) {
                    char[] buffer = new char[4096];
                    int read;
                    while ((read = reader.read(buffer)) >= 0) {
                        writer.write(buffer, 0, read);
                    }
                }
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("Failed to copy default assistant features config", e);
        }
    }

    private static FeatureConfigData resourceDefaults() {
        try {
            InputStream stream = AssistantFeatureConfig.class.getResourceAsStream(RESOURCE_PATH);
            if (stream != null) {
                try (InputStreamReader reader = new InputStreamReader(stream, "UTF-8")) {
                    return GSON.fromJson(reader, FeatureConfigData.class);
                }
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("Failed to load bundled assistant features defaults", e);
        }
        return new FeatureConfigData();
    }

    /**
     * Build the AI system prompt section listing all available features and their instructions.
     */
    public static String buildFeaturesInstruction(String locale) {
        FeatureConfigData cfg = get();
        boolean zh = locale != null && locale.trim()
            .toLowerCase()
            .startsWith("zh");
        StringBuilder sb = new StringBuilder();
        sb.append(
            zh ? "以下是你可以识别并返回的意图类型及其说明（按优先级排列）：\n"
                : "Here are the intent types you can recognize and return with their descriptions (in priority order):\n");
        if (cfg.features != null) {
            int index = 1;
            for (FeatureEntry feature : cfg.features) {
                String instruction = zh && feature.aiInstruction.containsKey("zh_CN")
                    ? feature.aiInstruction.get("zh_CN")
                    : (!zh && feature.aiInstruction.containsKey("en_US") ? feature.aiInstruction.get("en_US")
                        : feature.key);
                sb.append(index++)
                    .append(") ")
                    .append(instruction)
                    .append("\n");
            }
            sb.append("\n");
        }
        sb.append(
            zh ? "CONFIRM_OPTION: 确认选择的候选项，设置optionNumber为序号；CLARIFY: 意图不明确时请求澄清；CHAT: 普通对话不触发任何工具操作。\n"
                : "CONFIRM_OPTION: confirm selected candidate, set optionNumber; CLARIFY: request clarification when intent is unclear; CHAT: ordinary conversation without tool actions.\n");
        sb.append(
            zh ? "\n重要：返回一个JSON对象，不要输出markdown、注释或代码块。使用此精确格式：{\"tasks\":[{\"type\":\"...\",\"target\":\"...\",\"amount\":1,\"optionNumber\":-1,\"storageScope\":\"all|items|fluids\",\"confidence\":0.9}]}。最多8个任务。ORDER_ITEM和WITHDRAW_ITEM必须包含非空target。"
                : "\nImportant: return exactly one JSON object with no markdown, comments, or code fences. Use this exact schema: {\"tasks\":[{\"type\":\"...\",\"target\":\"...\",\"amount\":1,\"optionNumber\":-1,\"storageScope\":\"all|items|fluids\",\"confidence\":0.9}]}. Max 8 tasks. ORDER_ITEM and WITHDRAW_ITEM must have non-empty target.");
        return sb.toString();
    }

    /**
     * Build the feature menu text for display in the chat dialog.
     */
    public static String buildFeatureMenu(String locale) {
        FeatureConfigData cfg = get();
        boolean zh = locale != null && locale.trim()
            .toLowerCase()
            .startsWith("zh");
        StringBuilder sb = new StringBuilder();
        sb.append(zh ? "=== 功能菜单 ===\n输入数字选择功能：\n" : "=== Feature Menu ===\nEnter number to select a feature:\n");
        if (cfg.features != null) {
            int index = 1;
            for (FeatureEntry feature : cfg.features) {
                String name = zh && feature.displayName.containsKey("zh_CN") ? feature.displayName.get("zh_CN")
                    : (!zh && feature.displayName.containsKey("en_US") ? feature.displayName.get("en_US")
                        : feature.key);
                String desc = zh && feature.description.containsKey("zh_CN") ? feature.description.get("zh_CN")
                    : (!zh && feature.description.containsKey("en_US") ? feature.description.get("en_US") : "");
                sb.append(index)
                    .append(". ")
                    .append(name);
                if (!desc.isEmpty()) {
                    sb.append(" - ")
                        .append(desc);
                }
                sb.append("\n");
                index++;
            }
        }
        sb.append("\n")
            .append(
                zh ? "选择后请直接输入具体内容，AI将精准调用对应功能。"
                    : "After selection, enter your specific content and AI will precisely call the corresponding feature.");
        return sb.toString();
    }

    /**
     * Find the feature entry by its 1-based menu index.
     */
    public static FeatureEntry getFeatureByMenuIndex(int menuIndex) {
        FeatureConfigData cfg = get();
        if (cfg.features == null || menuIndex < 1 || menuIndex > cfg.features.size()) {
            return null;
        }
        return cfg.features.get(menuIndex - 1);
    }

    /**
     * Build an AI context prefix that tells the AI which feature to use.
     */
    public static String buildFeatureContextPrefix(FeatureEntry feature, String locale) {
        if (feature == null) {
            return "";
        }
        boolean zh = locale != null && locale.trim()
            .toLowerCase()
            .startsWith("zh");
        String name = zh && feature.displayName.containsKey("zh_CN") ? feature.displayName.get("zh_CN")
            : (!zh && feature.displayName.containsKey("en_US") ? feature.displayName.get("en_US") : feature.key);
        return (zh ? "[功能已选定：" : "[Feature selected: ") + name
            + (zh ? "] 请以该意图处理以下内容：" : "] Process the following with this intent: ");
    }

    /**
     * Build a map from feature key to its keywords for quick lookup.
     */
    public static Map<String, List<String>> buildKeywordsMap() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        FeatureConfigData cfg = get();
        if (cfg.features != null) {
            for (FeatureEntry feature : cfg.features) {
                if (feature.keywords != null && !feature.keywords.isEmpty()) {
                    map.put(feature.key, new ArrayList<>(feature.keywords));
                }
            }
        }
        return map;
    }

    public static final class FeatureConfigData {

        public List<FeatureEntry> features = new ArrayList<>();
    }

    public static final class FeatureEntry {

        public String key;
        public Map<String, String> displayName = new LinkedHashMap<>();
        public Map<String, String> description = new LinkedHashMap<>();
        public List<String> keywords = new ArrayList<>();
        public Map<String, String> aiInstruction = new LinkedHashMap<>();
    }
}
