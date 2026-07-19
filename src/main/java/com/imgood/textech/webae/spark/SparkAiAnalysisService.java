package com.imgood.textech.webae.spark;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.imgood.textech.webae.assistant.WebAiCompletionService;

/** Optional external-AI interpretation of bounded, already-aggregated Spark data. */
public final class SparkAiAnalysisService {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final int MAX_HOTSPOTS = 20;
    private static final int MAX_CATEGORIES = 16;
    private static final int MAX_THREADS = 12;

    private SparkAiAnalysisService() {}

    public static AnalysisResult analyze(List<String> ids, String locale) throws IOException {
        PreparedRequest prepared = prepare(ids, locale);
        com.imgood.textech.webae.assistant.WebAiCompletionService.CompletionResult completion =
            WebAiCompletionService.completeWithFailover(prepared.systemPrompt, prepared.userPrompt);
        AnalysisResult result = new AnalysisResult();
        result.analysis = completion.content;
        result.providerId = completion.providerId;
        result.model = completion.model;
        result.profileIds = prepared.profileIds;
        result.comparison = prepared.comparison;
        result.dataPolicy = prepared.dataPolicy;
        return result;
    }

    /** Builds the secret-free bounded request used by browsers in per-browser key mode. */
    public static PreparedRequest prepare(List<String> ids, String locale) {
        if (ids == null || ids.isEmpty() || ids.size() > 2) {
            throw new IllegalArgumentException("Provide one or two Spark profile ids.");
        }
        List<SparkProfile> profiles = new ArrayList<SparkProfile>();
        for (String id : ids) {
            SparkProfile profile = SparkProfileStore.instance().find(id);
            if (profile == null) throw new IllegalArgumentException("Spark profile not found: " + id);
            if (!"ready".equals(profile.analysisStatus)) {
                throw new IllegalArgumentException("Spark profile does not contain ready local analysis: " + id);
            }
            profiles.add(profile);
        }
        boolean zh = locale == null || !locale.toLowerCase().startsWith("en");
        String system = zh
            ? "你是 Minecraft GTNH 服务器性能诊断专家。只能依据给出的 Spark 聚合数据判断，不得编造未出现的模组、类、方法或因果关系。按证据强弱区分确定事实、可能原因和建议。重点解释哪个类/方法/线程/类别占用性能。若有 A/B 两条记录，所有变化严格按 B-A 解读，正数是占用增加，负数是改善。用简洁中文输出：结论、证据、可能根因、验证步骤、优化建议。"
            : "You are a Minecraft GTNH server performance diagnostician. Use only the supplied aggregated Spark data; never invent mods, classes, methods, or causality. Separate facts, likely causes, and recommendations by evidence strength. Explain which class, method, thread, or category consumes performance. For A/B input, interpret every change as B minus A: positive means more impact and negative means improvement. Respond concisely with conclusion, evidence, likely root cause, validation steps, and recommendations.";
        JsonObject payload = new JsonObject();
        payload.addProperty("comparisonRule", profiles.size() == 2 ? "B_MINUS_A" : "SINGLE_PROFILE");
        JsonArray records = new JsonArray();
        for (int i = 0; i < profiles.size(); i++) {
            records.add(boundedProfile(profiles.get(i), i == 0 ? "A" : "B"));
        }
        payload.add("profiles", records);
        String userPrompt = GSON.toJson(payload);
        String searchQuery = zh
            ? "Minecraft GTNH Spark 性能 " + profiles.get(0).mode + " lag MSPT"
            : "Minecraft GTNH Spark profiler " + profiles.get(0).mode + " lag MSPT";
        userPrompt = WebAiCompletionService.maybeAugmentUserPrompt(userPrompt, searchQuery);
        PreparedRequest request = new PreparedRequest();
        request.systemPrompt = system;
        request.userPrompt = userPrompt;
        request.profileIds = new ArrayList<String>(ids);
        request.comparison = profiles.size() == 2;
        request.dataPolicy = "bounded-aggregates-only";
        return request;
    }

    static JsonObject boundedProfile(SparkProfile profile, String label) {
        JsonObject value = new JsonObject();
        value.addProperty("label", label);
        value.addProperty("id", profile.id);
        value.addProperty("mode", profile.mode);
        value.addProperty("durationSeconds", profile.durationSeconds);
        value.addProperty("intervalMillis", profile.intervalMillis);
        value.addProperty("onlyTicksOverMillis", profile.onlyTicksOverMillis);
        value.addProperty("sampledTimeMillis", profile.sampledTimeMillis);
        value.addProperty("sampleCount", profile.sampleCount);
        value.addProperty("analyzedNodeCount", profile.analyzedNodeCount);

        JsonArray categories = new JsonArray();
        if (profile.categories != null) {
            int count = Math.min(MAX_CATEGORIES, profile.categories.size());
            for (int i = 0; i < count; i++) {
                SparkProfile.CategoryImpact category = profile.categories.get(i);
                JsonObject item = new JsonObject();
                item.addProperty("id", category.id);
                item.addProperty("percent", category.percent);
                item.addProperty("timeMillis", category.timeMillis);
                item.addProperty("topClassName", category.topClassName);
                item.addProperty("topMethodName", category.topMethodName);
                categories.add(item);
            }
        }
        value.add("categories", categories);

        JsonArray hotspots = new JsonArray();
        if (profile.hotspots != null) {
            int count = Math.min(MAX_HOTSPOTS, profile.hotspots.size());
            for (int i = 0; i < count; i++) {
                SparkProfile.Hotspot hotspot = profile.hotspots.get(i);
                JsonObject item = new JsonObject();
                item.addProperty("className", hotspot.className);
                item.addProperty("methodName", hotspot.methodName);
                item.addProperty("lineNumber", hotspot.lineNumber);
                item.addProperty("category", hotspot.category);
                item.addProperty("dominantThread", hotspot.dominantThread);
                item.addProperty("percent", hotspot.percent);
                item.addProperty("selfTimeMillis", hotspot.selfTimeMillis);
                item.addProperty("totalTimeMillis", hotspot.totalTimeMillis);
                hotspots.add(item);
            }
        }
        value.add("hotspots", hotspots);

        JsonArray threads = new JsonArray();
        if (profile.threads != null) {
            int count = Math.min(MAX_THREADS, profile.threads.size());
            for (int i = 0; i < count; i++) {
                SparkProfile.ThreadImpact thread = profile.threads.get(i);
                JsonObject item = new JsonObject();
                item.addProperty("name", thread.name);
                item.addProperty("percent", thread.percent);
                item.addProperty("timeMillis", thread.timeMillis);
                threads.add(item);
            }
        }
        value.add("threads", threads);
        return value;
    }

    public static final class AnalysisResult {
        public String analysis;
        public String providerId;
        public String model;
        public List<String> profileIds;
        public boolean comparison;
        public String dataPolicy;
    }

    public static final class PreparedRequest {
        public String systemPrompt;
        public String userPrompt;
        public List<String> profileIds;
        public boolean comparison;
        public String dataPolicy;
    }
}
