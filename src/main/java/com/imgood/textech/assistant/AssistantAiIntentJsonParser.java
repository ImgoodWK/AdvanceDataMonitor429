package com.imgood.textech.assistant;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class AssistantAiIntentJsonParser {

    public static final int MAX_TASKS = 16;
    public static final double MIN_CONFIDENCE = 0.5D;

    public AssistantIntentPlan parse(String modelOutput) {
        return parse("", modelOutput);
    }

    public AssistantIntentPlan parse(String rawText, String modelOutput) {
        String json = extractFirstJsonObject(modelOutput);
        if (json.isEmpty()) {
            return AssistantIntentPlan.empty();
        }
        try {
            JsonElement rootElement = new JsonParser().parse(json);
            if (!rootElement.isJsonObject()) {
                return AssistantIntentPlan.empty();
            }
            JsonObject root = rootElement.getAsJsonObject();
            JsonElement tasksElement = root.get("tasks");
            if (tasksElement == null || !tasksElement.isJsonArray()) {
                return AssistantIntentPlan.empty();
            }
            JsonArray tasksArray = tasksElement.getAsJsonArray();
            List<AssistantIntentTask> tasks = new ArrayList<AssistantIntentTask>();
            for (int i = 0; i < tasksArray.size() && tasks.size() < MAX_TASKS; i++) {
                JsonElement taskElement = tasksArray.get(i);
                AssistantIntentTask task = parseTask(taskElement);
                if (task == null) {
                    return AssistantIntentPlan.empty();
                }
                tasks.add(task);
            }
            return tasks.isEmpty() ? AssistantIntentPlan.empty() : new AssistantIntentPlan(tasks);
        } catch (RuntimeException ignored) {
            return AssistantIntentPlan.empty();
        }
    }

    public String extractFirstJsonObject(String modelOutput) {
        if (modelOutput == null || modelOutput.isEmpty()) {
            return "";
        }
        int start = modelOutput.indexOf('{');
        if (start < 0) {
            return "";
        }
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = start; i < modelOutput.length(); i++) {
            char ch = modelOutput.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return modelOutput.substring(start, i + 1);
                }
                if (depth < 0) {
                    return "";
                }
            }
        }
        return "";
    }

    private AssistantIntentTask parseTask(JsonElement taskElement) {
        if (taskElement == null || !taskElement.isJsonObject()) {
            return null;
        }
        JsonObject task = taskElement.getAsJsonObject();
        AssistantIntentType type = parseType(stringValue(task, "type", ""));
        if (type == null) {
            return null;
        }
        String target = stringValue(task, "target", "").trim();
        String storageScopeText = stringValue(task, "storageScope", "all");
        if (!AssistantIntentTask.isValidStorageScope(storageScopeText)) {
            return null;
        }
        double confidence = doubleValue(task, "confidence", 1.0D);
        if (confidence < MIN_CONFIDENCE || confidence > 1.0D) {
            return null;
        }
        long amount = longValue(task, "amount", defaultAmount(type));
        int optionNumber = intValue(task, "optionNumber", -1);
        int storageScope = AssistantIntentTask.storageScopeFromString(storageScopeText);
        if ((type == AssistantIntentType.ORDER_ITEM || type == AssistantIntentType.WITHDRAW_ITEM) && target.isEmpty()) {
            return null;
        }
        amount = normalizeAmount(type, amount);
        return new AssistantIntentTask(type, target, amount, optionNumber, storageScope, confidence);
    }

    private AssistantIntentType parseType(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim()
            .toUpperCase();
        if (normalized.isEmpty() || "ORDER_BATCH".equals(normalized) || "WITHDRAW_BATCH".equals(normalized)) {
            return null;
        }
        try {
            AssistantIntentType type = AssistantIntentType.valueOf(normalized);
            switch (type) {
                case QUERY_RECIPE:
                case QUERY_STORAGE:
                case QUERY_POWER:
                case QUERY_STEAM:
                case QUERY_WEATHER:
                case QUERY_TIME:
                case QUERY_POSITION:
                case QUERY_BIOME:
                case QUERY_INVENTORY:
                case QUERY_NETWORK:
                case QUERY_JOBS:
                case ORDER_ITEM:
                case WITHDRAW_ITEM:
                case CONFIRM_OPTION:
                case PLAN_CREATE:
                case PLAN_ADD:
                case PLAN_LIST:
                case PLAN_COMPLETE:
                case PLAN_DELETE:
                case PLAN_MODIFY:
                case TELEPORT:
                case TELEPORT_LIST:
                case CANCEL:
                case CLARIFY:
                case CHAT:
                case QUERY_ITEM_COUNT:
                case QUERY_BYTES:
                    return type;
                default:
                    return null;
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private long defaultAmount(AssistantIntentType type) {
        switch (type) {
            case ORDER_ITEM:
            case WITHDRAW_ITEM:
            case QUERY_RECIPE:
            case QUERY_STORAGE:
                return 1L;
            default:
                return 0L;
        }
    }

    private long normalizeAmount(AssistantIntentType type, long amount) {
        switch (type) {
            case ORDER_ITEM:
            case WITHDRAW_ITEM:
            case QUERY_RECIPE:
            case QUERY_STORAGE:
                return Math.max(1L, amount);
            default:
                return Math.max(0L, amount);
        }
    }

    private String stringValue(JsonObject object, String name, String fallback) {
        JsonElement element = object.get(name);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsString();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private long longValue(JsonObject object, String name, long fallback) {
        JsonElement element = object.get(name);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsLong();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private int intValue(JsonObject object, String name, int fallback) {
        JsonElement element = object.get(name);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private double doubleValue(JsonObject object, String name, double fallback) {
        JsonElement element = object.get(name);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsDouble();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
