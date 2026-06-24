package com.imgood.advancedatamonitor.config;

import com.imgood.advancedatamonitor.Config;

public final class ConfigAiMutators {

    private ConfigAiMutators() {}

    public static void setAiModelInMemory(String model) {
        Config.aiModel = model == null || model.trim()
            .isEmpty() ? "deepseek-chat" : model.trim();
        addRecentAiModel(Config.aiModel);
    }

    private static void addRecentAiModel(String model) {
        if (model == null || model.trim()
            .isEmpty()) {
            return;
        }
        String normalized = model.trim();
        StringBuilder builder = new StringBuilder(normalized);
        int count = 1;
        String recentModels = Config.aiRecentModels == null ? "" : Config.aiRecentModels;
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
        Config.aiRecentModels = builder.toString();
    }
}
