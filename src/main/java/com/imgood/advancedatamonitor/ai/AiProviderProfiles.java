package com.imgood.advancedatamonitor.ai;

import com.imgood.advancedatamonitor.Config;

public final class AiProviderProfiles {

    public static final String MODE_AUTO = "auto";
    public static final String MODE_OFF = "off";
    public static final String MODE_OPENAI = "openai";
    public static final String MODE_OPENROUTER = "openrouter";
    public static final String MODE_DASHSCOPE = "dashscope";
    public static final String MODE_ZHIPU = "zhipu";
    public static final String MODE_GENERIC_TOOLS = "generic-tools";
    public static final String MODE_UNSUPPORTED = "unsupported";

    private static final ProviderProfile[] PROFILES = {
        new ProviderProfile(
            "deepseek",
            "DeepSeek",
            "https://api.deepseek.com",
            "deepseek-chat",
            MODE_UNSUPPORTED,
            new String[] { "deepseek-chat", "deepseek-reasoner" }),
        new ProviderProfile(
            "openai",
            "OpenAI",
            "https://api.openai.com",
            "gpt-4o-mini",
            MODE_OPENAI,
            new String[] { "gpt-4o-mini", "gpt-4o", "gpt-4.1-mini", "gpt-4.1", "o4-mini", "o3-mini" }),
        new ProviderProfile(
            "openrouter",
            "OpenRouter",
            "https://openrouter.ai/api",
            "openai/gpt-4o-mini",
            MODE_OPENROUTER,
            new String[] { "openai/gpt-4o-mini", "openai/gpt-4o", "anthropic/claude-3.5-sonnet",
                "google/gemini-2.0-flash-001", "deepseek/deepseek-chat", "deepseek/deepseek-r1" }),
        new ProviderProfile(
            "dashscope",
            "Alibaba DashScope",
            "https://dashscope.aliyuncs.com/compatible-mode",
            "qwen-plus",
            MODE_DASHSCOPE,
            new String[] { "qwen-plus", "qwen-turbo", "qwen-max", "qwen2.5-72b-instruct", "qwen2.5-32b-instruct" }),
        new ProviderProfile(
            "zhipu",
            "Zhipu GLM",
            "https://open.bigmodel.cn/api/paas",
            "glm-4-flash",
            MODE_ZHIPU,
            new String[] { "glm-4-flash", "glm-4-air", "glm-4-plus" }),
        new ProviderProfile(
            "kimi",
            "Moonshot Kimi",
            "https://api.moonshot.cn",
            "moonshot-v1-8k",
            MODE_UNSUPPORTED,
            new String[] { "moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k", "kimi-k2-instruct" }),
        new ProviderProfile(
            "volcengine",
            "Volcengine Ark",
            "https://ark.cn-beijing.volces.com/api",
            "doubao-1-5-lite-32k",
            MODE_GENERIC_TOOLS,
            new String[] { "doubao-1-5-lite-32k", "doubao-1-5-pro-32k" }),
        new ProviderProfile(
            "siliconflow",
            "SiliconFlow",
            "https://api.siliconflow.cn",
            "Qwen/Qwen2.5-7B-Instruct",
            MODE_GENERIC_TOOLS,
            new String[] { "Qwen/Qwen2.5-7B-Instruct", "Qwen/Qwen2.5-72B-Instruct", "deepseek-ai/DeepSeek-V3" }),
        new ProviderProfile(
            "minimax",
            "MiniMax",
            "https://api.minimax.chat",
            "abab6.5s-chat",
            MODE_UNSUPPORTED,
            new String[] { "abab6.5s-chat", "abab6.5g-chat" }),
        new ProviderProfile(
            "groq",
            "Groq",
            "https://api.groq.com/openai",
            "llama-3.1-8b-instant",
            MODE_UNSUPPORTED,
            new String[] { "llama-3.1-8b-instant", "llama-3.3-70b-versatile", "mixtral-8x7b-32768" }),
        new ProviderProfile(
            "mistral",
            "Mistral",
            "https://api.mistral.ai",
            "mistral-small-latest",
            MODE_UNSUPPORTED,
            new String[] { "mistral-small-latest", "mistral-large-latest" }),
        new ProviderProfile(
            "gemini",
            "Google Gemini",
            "https://generativelanguage.googleapis.com",
            "gemini-2.0-flash",
            MODE_UNSUPPORTED,
            new String[] { "gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-pro" }),
        new ProviderProfile(
            "anthropic",
            "Anthropic",
            "https://api.anthropic.com",
            "claude-3-5-sonnet-latest",
            MODE_UNSUPPORTED,
            new String[] { "claude-3-5-sonnet-latest", "claude-3-7-sonnet-latest", "claude-sonnet-4-0" }) };

    private AiProviderProfiles() {}

    public static ProviderProfile[] allProfiles() {
        return PROFILES.clone();
    }

    public static String[] providerIds() {
        String[] ids = new String[PROFILES.length];
        for (int i = 0; i < PROFILES.length; i++) {
            ids[i] = PROFILES[i].id;
        }
        return ids;
    }

    public static ProviderProfile findProfile(String idOrName) {
        String query = normalize(idOrName);
        for (ProviderProfile profile : PROFILES) {
            if (profile.id.equals(query) || normalize(profile.displayName).equals(query)) {
                return profile;
            }
        }
        return null;
    }

    public static ProviderProfile detectProfile() {
        return detectProfile(Config.aiApiBaseUrl, Config.aiModel);
    }

    public static ProviderProfile detectProfile(String baseUrl, String model) {
        String normalizedBase = normalize(baseUrl);
        String normalizedModel = normalize(model);
        for (ProviderProfile profile : PROFILES) {
            if (!profile.baseUrl.isEmpty() && normalizedBase.contains(normalizeHost(profile.baseUrl))) {
                return profile;
            }
        }
        if (normalizedModel.startsWith("qwen") || normalizedModel.contains("qwen/")) {
            return findProfile("dashscope");
        }
        if (normalizedModel.startsWith("glm-")) {
            return findProfile("zhipu");
        }
        if (normalizedModel.startsWith("moonshot") || normalizedModel.startsWith("kimi")) {
            return findProfile("kimi");
        }
        if (normalizedModel.startsWith("gpt-") || normalizedModel.startsWith("o3")
            || normalizedModel.startsWith("o4")) {
            return findProfile("openai");
        }
        return new ProviderProfile(
            "custom",
            "Custom",
            safeTrim(baseUrl),
            safeTrim(model),
            MODE_GENERIC_TOOLS,
            new String[] { safeTrim(model) });
    }

    public static SearchCapability currentSearchCapability() {
        return searchCapability(Config.aiApiBaseUrl, Config.aiModel, Config.aiWebSearchMode, Config.aiWebSearchEnabled);
    }

    public static SearchCapability searchCapability(String baseUrl, String model, String configuredMode,
        boolean enabled) {
        ProviderProfile profile = detectProfile(baseUrl, model);
        String requestedMode = normalize(configuredMode);
        if (!enabled || MODE_OFF.equals(requestedMode)) {
            return new SearchCapability(profile, MODE_OFF, false, "Web search is off.");
        }
        String mode = requestedMode.isEmpty() || MODE_AUTO.equals(requestedMode) ? profile.defaultSearchMode
            : requestedMode;
        if (MODE_UNSUPPORTED.equals(mode)) {
            return new SearchCapability(profile, MODE_UNSUPPORTED, false, unsupportedSearchMessage(profile));
        }
        if (!isSearchMode(mode)) {
            return new SearchCapability(profile, MODE_UNSUPPORTED, false, "Unknown web search mode: " + configuredMode);
        }
        return new SearchCapability(profile, mode, true, "Web search mode: " + mode);
    }

    public static boolean isSearchMode(String value) {
        String mode = normalize(value);
        return MODE_AUTO.equals(mode) || MODE_OPENAI.equals(mode)
            || MODE_OPENROUTER.equals(mode)
            || MODE_DASHSCOPE.equals(mode)
            || MODE_ZHIPU.equals(mode)
            || MODE_GENERIC_TOOLS.equals(mode)
            || MODE_OFF.equals(mode)
            || MODE_UNSUPPORTED.equals(mode);
    }

    public static String[] fallbackModes(String firstMode) {
        String first = normalize(firstMode);
        if (MODE_OPENROUTER.equals(first)) {
            return new String[] { MODE_OPENROUTER, MODE_GENERIC_TOOLS };
        }
        if (MODE_DASHSCOPE.equals(first)) {
            return new String[] { MODE_DASHSCOPE, MODE_GENERIC_TOOLS };
        }
        if (MODE_ZHIPU.equals(first)) {
            return new String[] { MODE_ZHIPU, MODE_GENERIC_TOOLS };
        }
        if (MODE_OPENAI.equals(first)) {
            return new String[] { MODE_OPENAI, MODE_GENERIC_TOOLS };
        }
        if (MODE_GENERIC_TOOLS.equals(first)) {
            return new String[] { MODE_GENERIC_TOOLS };
        }
        return new String[0];
    }

    public static String statusSummary() {
        SearchCapability capability = currentSearchCapability();
        String search = capability.enabled ? capability.mode : "off";
        return capability.profile.displayName + " | " + Config.aiModel + " | search: " + search;
    }

    private static String unsupportedSearchMessage(ProviderProfile profile) {
        if ("deepseek".equals(profile.id)) {
            return "DeepSeek web chat supports search, but the public API does not provide a native web-search parameter. Use a search-capable gateway or inject your own search results.";
        }
        return profile.displayName + " is not known to support OpenAI-compatible web search.";
    }

    private static String normalizeHost(String value) {
        String normalized = normalize(value);
        normalized = normalized.replace("https://", "")
            .replace("http://", "");
        int slash = normalized.indexOf('/');
        return slash >= 0 ? normalized.substring(0, slash) : normalized;
    }

    private static String normalize(String value) {
        return value == null ? ""
            : value.trim()
                .toLowerCase();
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class ProviderProfile {

        public final String id;
        public final String displayName;
        public final String baseUrl;
        public final String defaultModel;
        public final String defaultSearchMode;
        public final String[] modelPresets;

        private ProviderProfile(String id, String displayName, String baseUrl, String defaultModel,
            String defaultSearchMode, String[] modelPresets) {
            this.id = id;
            this.displayName = displayName;
            this.baseUrl = baseUrl;
            this.defaultModel = defaultModel;
            this.defaultSearchMode = defaultSearchMode;
            this.modelPresets = modelPresets.clone();
        }
    }

    public static final class SearchCapability {

        public final ProviderProfile profile;
        public final String mode;
        public final boolean enabled;
        public final String message;

        private SearchCapability(ProviderProfile profile, String mode, boolean enabled, String message) {
            this.profile = profile;
            this.mode = mode;
            this.enabled = enabled;
            this.message = message;
        }
    }
}
