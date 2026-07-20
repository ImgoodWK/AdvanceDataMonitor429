package com.imgood.textech.command;

import java.util.Arrays;
import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.EnumChatFormatting;

import com.imgood.textech.Config;
import com.imgood.textech.assistant.ai.AiProviderProfiles;
import com.imgood.textech.assistant.ai.AiProviderProfiles.ProviderProfile;
import com.imgood.textech.assistant.ai.AiProviderProfiles.SearchCapability;
import com.imgood.textech.assistant.ai.WebSearchService;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class CommandAIConfig extends TeXTechCommandBase {

    private static final String[] ACTIONS = { "key", "model", "base", "provider", "network", "search", "search-key",
        "searchKey", "search-base", "searchBase", "debug", "stream", "status", "clear-key", "clearKey", "help" };
    private static final String[] NETWORK_OPTIONS = { "on", "off", "toggle", "true", "false" };
    private static final String[] SEARCH_OPTIONS = { "on", "off", "toggle", "true", "false", "auto", "tavily_keyless",
        "duckduckgo", "tavily", "brave", "serper", "searxng" };
    private static final String[] TOGGLE_OPTIONS = { "on", "off", "toggle", "true", "false", "enable", "enabled",
        "disable", "disabled" };
    private static final String[] PROVIDERS = AiProviderProfiles.providerIds();
    private static final String[] BASE_URLS = { "https://api.deepseek.com", "https://api.openai.com",
        "https://openrouter.ai/api", "https://api.siliconflow.cn", "https://api.moonshot.cn",
        "https://open.bigmodel.cn/api/paas", "https://dashscope.aliyuncs.com/compatible-mode",
        "https://ark.cn-beijing.volces.com/api", "https://api.minimax.chat", "https://api.groq.com/openai",
        "https://api.mistral.ai", "https://generativelanguage.googleapis.com", "https://api.anthropic.com" };
    private static final String[] MODELS = { "deepseek-chat", "deepseek-reasoner", "gpt-4o", "gpt-4o-mini", "gpt-4.1",
        "gpt-4.1-mini", "gpt-4.1-nano", "o3", "o3-mini", "o4-mini", "claude-3-5-sonnet-latest",
        "claude-3-7-sonnet-latest", "claude-sonnet-4-0", "claude-opus-4-0", "gemini-1.5-pro", "gemini-1.5-flash",
        "gemini-2.0-flash", "gemini-2.5-pro", "gemini-2.5-flash", "qwen-plus", "qwen-turbo", "qwen-max",
        "qwen2.5-72b-instruct", "qwen2.5-32b-instruct", "qwen2.5-14b-instruct", "qwen2.5-7b-instruct", "moonshot-v1-8k",
        "moonshot-v1-32k", "moonshot-v1-128k", "kimi-k2-instruct", "glm-4-plus", "glm-4-air", "glm-4-flash",
        "doubao-1-5-pro-32k", "doubao-1-5-lite-32k", "abab6.5s-chat", "abab6.5g-chat", "llama-3.1-8b-instant",
        "llama-3.3-70b-versatile", "mixtral-8x7b-32768", "mistral-large-latest", "mistral-small-latest",
        "openai/gpt-4o", "openai/gpt-4o-mini", "anthropic/claude-3.5-sonnet", "anthropic/claude-3.7-sonnet",
        "google/gemini-2.5-pro", "google/gemini-2.0-flash-001", "deepseek/deepseek-chat", "deepseek/deepseek-r1",
        "qwen/qwen-2.5-72b-instruct", "meta-llama/llama-3.3-70b-instruct" };
    private static final int HELP_LINES = 13;

    @Override
    public String getCommandName() {
        return "admai";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return translate("adm.command.admai.usage");
    }

    @Override
    public List<String> getCommandAliases() {
        return Arrays.asList("adm-ai", "aicfg");
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sendUsage(sender);
            return;
        }

        String action = normalizeAction(args[0]);
        switch (action) {
            case "key":
                setKey(sender, args);
                break;
            case "clearkey":
            case "clear-key":
                Config.setAiApiKey("");
                sendLocalized(sender, EnumChatFormatting.GREEN, "adm.command.admai.key_cleared");
                break;
            case "model":
                setModel(sender, args);
                break;
            case "base":
                setBaseUrl(sender, args);
                break;
            case "provider":
                setProvider(sender, args);
                break;
            case "network":
                setNetworkEnabled(sender, args);
                break;
            case "search":
                setWebSearch(sender, args);
                break;
            case "searchkey":
            case "search-key":
                setSearchKey(sender, args);
                break;
            case "searchbase":
            case "search-base":
                setSearchBase(sender, args);
                break;
            case "debug":
                setDebug(sender, args);
                break;
            case "stream":
                setStream(sender, args);
                break;
            case "status":
                sendStatus(sender);
                break;
            default:
                sendUsage(sender);
                break;
        }
    }

    /** Lowercase; map camelCase leftovers already covered by toLowerCase. */
    private static String normalizeAction(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase();
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, ACTIONS);
        }
        if (args.length == 2) {
            String action = args[0].toLowerCase();
            if ("model".equals(action)) {
                return getListOfStringsMatchingLastWord(args, MODELS);
            }
            if ("base".equals(action)) {
                return getListOfStringsMatchingLastWord(args, BASE_URLS);
            }
            if ("provider".equals(action)) {
                return getListOfStringsMatchingLastWord(args, PROVIDERS);
            }
            if ("network".equals(action) || "debug".equals(action) || "stream".equals(action)) {
                return getListOfStringsMatchingLastWord(args, TOGGLE_OPTIONS);
            }
            if ("search".equals(action)) {
                return getListOfStringsMatchingLastWord(args, SEARCH_OPTIONS);
            }
        }
        return null;
    }

    private void setKey(ICommandSender sender, String[] args) {
        String value = joinArgs(args, 1);
        if (value.isEmpty()) {
            sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admai.usage_key");
            return;
        }
        Config.setAiApiKey(value);
        sendLocalized(sender, EnumChatFormatting.GREEN, "adm.command.admai.key_saved", maskKey(value));
    }

    private void setModel(ICommandSender sender, String[] args) {
        String value = joinArgs(args, 1);
        if (value.isEmpty()) {
            sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admai.usage_model");
            return;
        }
        Config.setAiModel(value);
        sendLocalized(sender, EnumChatFormatting.GREEN, "adm.command.admai.model_set", Config.aiModel);
    }

    private void setBaseUrl(ICommandSender sender, String[] args) {
        String value = joinArgs(args, 1);
        if (value.isEmpty()) {
            sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admai.usage_base");
            return;
        }
        Config.setAiApiBaseUrl(value);
        sendLocalized(sender, EnumChatFormatting.GREEN, "adm.command.admai.base_set", Config.aiApiBaseUrl);
    }

    private void setProvider(ICommandSender sender, String[] args) {
        String value = joinArgs(args, 1).toLowerCase();
        if (value.isEmpty()) {
            sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admai.usage_provider");
            return;
        }
        ProviderProfile profile = AiProviderProfiles.findProfile(value);
        if (profile == null) {
            sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admai.provider_unknown", value);
            return;
        }
        Config.applyAiProviderProfile(profile);
        sendLocalized(sender, EnumChatFormatting.GREEN, "adm.command.admai.provider_set", profile.displayName);
        sendLocalized(sender, EnumChatFormatting.GREEN, "adm.command.admai.provider_base", Config.aiApiBaseUrl);
        sendLocalized(sender, EnumChatFormatting.GREEN, "adm.command.admai.provider_model", Config.aiModel);
        sendLocalized(
            sender,
            EnumChatFormatting.GREEN,
            "adm.command.admai.provider_search",
            Config.aiWebSearchMode,
            Config.aiWebSearchEnabled ? statusEnabled() : statusDisabled());
    }

    private void setNetworkEnabled(ICommandSender sender, String[] args) {
        String value = joinArgs(args, 1).toLowerCase();
        if (value.isEmpty()) {
            sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admai.usage_network");
            return;
        }
        if (!applyToggle(value, new ToggleTarget() {

            @Override
            public void set(boolean enabled) {
                Config.setAiNetworkEnabled(enabled);
            }

            @Override
            public void toggle() {
                Config.toggleAiNetworkEnabled();
            }
        })) {
            sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admai.usage_network");
            return;
        }
        sendLocalized(
            sender,
            EnumChatFormatting.GREEN,
            Config.aiNetworkEnabled ? "adm.command.admai.network_enabled" : "adm.command.admai.network_disabled");
    }

    private void setWebSearch(ICommandSender sender, String[] args) {
        String value = joinArgs(args, 1).toLowerCase();
        if (value.isEmpty()) {
            sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admai.usage_search");
            return;
        }
        if ("toggle".equals(value)) {
            Config.toggleAiWebSearchEnabled();
        } else if ("on".equals(value) || "true".equals(value) || "enable".equals(value) || "enabled".equals(value)) {
            Config.setAiWebSearchEnabled(true);
        } else
            if ("off".equals(value) || "false".equals(value) || "disable".equals(value) || "disabled".equals(value)) {
                Config.setAiWebSearchEnabled(false);
            } else if (WebSearchService.isProvider(value)) {
                Config.setAiWebSearchMode(value);
                Config.setAiWebSearchEnabled(true);
            } else {
                sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admai.usage_search");
                return;
            }
        sendLocalized(
            sender,
            EnumChatFormatting.GREEN,
            "adm.command.admai.search_status",
            Config.aiWebSearchEnabled ? statusEnabled() : statusDisabled(),
            Config.aiWebSearchMode);
    }

    private void setSearchKey(ICommandSender sender, String[] args) {
        String value = joinArgs(args, 1);
        if (value.isEmpty()) {
            sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admai.usage_searchkey");
            return;
        }
        Config.setAiSearchApiKey(value);
        sendLocalized(sender, EnumChatFormatting.GREEN, "adm.command.admai.searchkey_saved", maskKey(value));
    }

    private void setSearchBase(ICommandSender sender, String[] args) {
        String value = joinArgs(args, 1);
        if (value.isEmpty()) {
            sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admai.usage_searchbase");
            return;
        }
        Config.setAiSearchBaseUrl(value);
        sendLocalized(sender, EnumChatFormatting.GREEN, "adm.command.admai.searchbase_set", Config.aiSearchBaseUrl);
    }

    private void setDebug(ICommandSender sender, String[] args) {
        String value = joinArgs(args, 1).toLowerCase();
        if (value.isEmpty()) {
            sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admai.usage_debug");
            return;
        }
        if (!applyToggle(value, new ToggleTarget() {

            @Override
            public void set(boolean enabled) {
                Config.setAiDebugLogging(enabled);
            }

            @Override
            public void toggle() {
                Config.setAiDebugLogging(!Config.aiDebugLogging);
            }
        })) {
            sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admai.usage_debug");
            return;
        }
        sendLocalized(
            sender,
            EnumChatFormatting.GREEN,
            Config.aiDebugLogging ? "adm.command.admai.debug_enabled" : "adm.command.admai.debug_disabled");
    }

    private void setStream(ICommandSender sender, String[] args) {
        String value = joinArgs(args, 1).toLowerCase();
        if (value.isEmpty()) {
            sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admai.usage_stream");
            return;
        }
        if (!applyToggle(value, new ToggleTarget() {

            @Override
            public void set(boolean enabled) {
                Config.setAiStreamingEnabled(enabled);
            }

            @Override
            public void toggle() {
                Config.setAiStreamingEnabled(!Config.aiStreamingEnabled);
            }
        })) {
            sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admai.usage_stream");
            return;
        }
        sendLocalized(
            sender,
            EnumChatFormatting.GREEN,
            Config.aiStreamingEnabled ? "adm.command.admai.stream_enabled" : "adm.command.admai.stream_disabled");
    }

    private boolean applyToggle(String value, ToggleTarget target) {
        return parseOnOffToggle(value, target);
    }

    private void sendStatus(ICommandSender sender) {
        String key = Config.getAiApiKey();
        SearchCapability capability = AiProviderProfiles.currentSearchCapability();
        sendHelpHeader(sender, "adm.command.admai.status.title");
        sendLocalized(
            sender,
            EnumChatFormatting.AQUA,
            "adm.command.admai.status.network",
            Config.aiNetworkEnabled ? statusEnabled() : statusDisabled());
        sendLocalized(sender, EnumChatFormatting.AQUA, "adm.command.admai.status.base", Config.aiApiBaseUrl);
        sendLocalized(
            sender,
            EnumChatFormatting.AQUA,
            "adm.command.admai.status.provider",
            capability.profile.displayName);
        sendLocalized(sender, EnumChatFormatting.AQUA, "adm.command.admai.status.model", Config.aiModel);
        sendLocalized(
            sender,
            EnumChatFormatting.AQUA,
            "adm.command.admai.status.search",
            capability.enabled ? statusEnabled() : statusDisabled(),
            capability.mode);
        sendLocalized(sender, EnumChatFormatting.AQUA, "adm.command.admai.status.search_detail", capability.message);
        sendLocalized(
            sender,
            EnumChatFormatting.AQUA,
            "adm.command.admai.status.search_key",
            Config.getAiSearchApiKey()
                .isEmpty() ? statusNotSet() : maskKey(Config.getAiSearchApiKey()));
        sendLocalized(
            sender,
            EnumChatFormatting.AQUA,
            "adm.command.admai.status.search_base",
            Config.aiSearchBaseUrl == null || Config.aiSearchBaseUrl.isEmpty() ? statusNotSet()
                : Config.aiSearchBaseUrl);
        sendLocalized(
            sender,
            EnumChatFormatting.AQUA,
            "adm.command.admai.status.search_max",
            Config.aiSearchMaxResults,
            Config.aiSearchFallback ? statusEnabled() : statusDisabled());
        sendLocalized(
            sender,
            EnumChatFormatting.AQUA,
            "adm.command.admai.status.stream",
            Config.aiStreamingEnabled ? statusEnabled() : statusDisabled());
        sendLocalized(
            sender,
            EnumChatFormatting.AQUA,
            "adm.command.admai.status.debug",
            Config.aiDebugLogging ? statusEnabled() : statusDisabled());
        sendLocalized(
            sender,
            EnumChatFormatting.AQUA,
            "adm.command.admai.status.privacy",
            Config.aiPrivacyConfirmed ? statusYes() : statusNo());
        sendLocalized(
            sender,
            EnumChatFormatting.AQUA,
            "adm.command.admai.status.key",
            key.isEmpty() ? statusNotSet() : maskKey(key));
    }

    private void sendUsage(ICommandSender sender) {
        sendHelpHeader(sender, "adm.command.admai.title");
        sendUsageSummary(sender, "adm.command.admai.usage");
        sendHelpLines(sender, "adm.command.admai.help", HELP_LINES);
    }

    private static String translate(String key) {
        return net.minecraft.util.StatCollector.translateToLocal(key);
    }

    private static String statusEnabled() {
        return translate("adm.command.admai.status.enabled");
    }

    private static String statusDisabled() {
        return translate("adm.command.admai.status.disabled");
    }

    private static String statusNotSet() {
        return translate("adm.command.admai.status.not_set");
    }

    private static String statusYes() {
        return translate("adm.command.admai.status.yes");
    }

    private static String statusNo() {
        return translate("adm.command.admai.status.no");
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }
}
