package com.imgood.advancedatamonitor.command;

import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.imgood.advancedatamonitor.Config;
import com.imgood.advancedatamonitor.ai.AiProviderProfiles;
import com.imgood.advancedatamonitor.ai.AiProviderProfiles.ProviderProfile;
import com.imgood.advancedatamonitor.ai.AiProviderProfiles.SearchCapability;

public class CommandAIConfig extends CommandBase {

    private static final String[] ACTIONS = { "key", "model", "base", "provider", "network", "search", "status",
        "clearKey", "help" };
    private static final String[] NETWORK_OPTIONS = { "on", "off", "toggle", "true", "false" };
    private static final String[] SEARCH_OPTIONS = { "on", "off", "toggle", "true", "false", "auto", "openai",
        "openrouter", "dashscope", "zhipu", "generic-tools" };
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

    @Override
    public String getCommandName() {
        return "admai";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/admai <key|model|base|provider|network|search|status|clearKey> [value]";
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

        String action = args[0].toLowerCase();
        switch (action) {
            case "key":
                setKey(sender, args);
                break;
            case "clearkey":
                Config.setAiApiKey("");
                send(sender, EnumChatFormatting.GREEN + "AI API key cleared.");
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
            case "status":
                sendStatus(sender);
                break;
            default:
                sendUsage(sender);
                break;
        }
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, ACTIONS);
        }
        if (args.length == 2) {
            if ("model".equalsIgnoreCase(args[0])) {
                return getListOfStringsMatchingLastWord(args, MODELS);
            }
            if ("base".equalsIgnoreCase(args[0])) {
                return getListOfStringsMatchingLastWord(args, BASE_URLS);
            }
            if ("provider".equalsIgnoreCase(args[0])) {
                return getListOfStringsMatchingLastWord(args, PROVIDERS);
            }
            if ("network".equalsIgnoreCase(args[0])) {
                return getListOfStringsMatchingLastWord(args, NETWORK_OPTIONS);
            }
            if ("search".equalsIgnoreCase(args[0])) {
                return getListOfStringsMatchingLastWord(args, SEARCH_OPTIONS);
            }
        }
        return null;
    }

    private void setKey(ICommandSender sender, String[] args) {
        String value = joinArgs(args, 1);
        if (value.isEmpty()) {
            send(sender, EnumChatFormatting.RED + "Usage: /admai key <apiKey>");
            return;
        }
        Config.setAiApiKey(value);
        send(sender, EnumChatFormatting.GREEN + "AI API key saved. Current key: " + maskKey(value));
    }

    private void setModel(ICommandSender sender, String[] args) {
        String value = joinArgs(args, 1);
        if (value.isEmpty()) {
            send(sender, EnumChatFormatting.RED + "Usage: /admai model <modelName>");
            return;
        }
        Config.setAiModel(value);
        send(sender, EnumChatFormatting.GREEN + "AI model set to: " + Config.aiModel);
    }

    private void setBaseUrl(ICommandSender sender, String[] args) {
        String value = joinArgs(args, 1);
        if (value.isEmpty()) {
            send(sender, EnumChatFormatting.RED + "Usage: /admai base <apiBaseUrl>");
            return;
        }
        Config.setAiApiBaseUrl(value);
        send(sender, EnumChatFormatting.GREEN + "AI API base URL set to: " + Config.aiApiBaseUrl);
    }

    private void setProvider(ICommandSender sender, String[] args) {
        String value = joinArgs(args, 1).toLowerCase();
        if (value.isEmpty()) {
            send(
                sender,
                EnumChatFormatting.RED
                    + "Usage: /admai provider <deepseek|openai|openrouter|dashscope|zhipu|kimi|volcengine|siliconflow|...>");
            return;
        }
        ProviderProfile profile = AiProviderProfiles.findProfile(value);
        if (profile == null) {
            send(sender, EnumChatFormatting.RED + "Unknown AI provider: " + value);
            return;
        }
        Config.applyAiProviderProfile(profile);
        send(sender, EnumChatFormatting.GREEN + "AI provider set to: " + profile.displayName);
        send(sender, EnumChatFormatting.GREEN + "Base URL: " + Config.aiApiBaseUrl);
        send(sender, EnumChatFormatting.GREEN + "Model: " + Config.aiModel);
        send(
            sender,
            EnumChatFormatting.GREEN + "Web search mode: "
                + Config.aiWebSearchMode
                + (Config.aiWebSearchEnabled ? " enabled" : " disabled"));
    }

    private void setNetworkEnabled(ICommandSender sender, String[] args) {
        String value = joinArgs(args, 1).toLowerCase();
        if (value.isEmpty()) {
            send(sender, EnumChatFormatting.RED + "Usage: /admai network <on|off|toggle>");
            return;
        }
        if ("toggle".equals(value)) {
            Config.toggleAiNetworkEnabled();
        } else if ("on".equals(value) || "true".equals(value) || "enable".equals(value) || "enabled".equals(value)) {
            Config.setAiNetworkEnabled(true);
        } else
            if ("off".equals(value) || "false".equals(value) || "disable".equals(value) || "disabled".equals(value)) {
                Config.setAiNetworkEnabled(false);
            } else {
                send(sender, EnumChatFormatting.RED + "Usage: /admai network <on|off|toggle>");
                return;
            }
        send(
            sender,
            EnumChatFormatting.GREEN + "AI network requests: " + (Config.aiNetworkEnabled ? "enabled" : "disabled"));
    }

    private void setWebSearch(ICommandSender sender, String[] args) {
        String value = joinArgs(args, 1).toLowerCase();
        if (value.isEmpty()) {
            send(
                sender,
                EnumChatFormatting.RED
                    + "Usage: /admai search <on|off|toggle|auto|openai|openrouter|dashscope|zhipu|generic-tools>");
            return;
        }
        if ("toggle".equals(value)) {
            Config.toggleAiWebSearchEnabled();
        } else if ("on".equals(value) || "true".equals(value) || "enable".equals(value) || "enabled".equals(value)) {
            Config.setAiWebSearchEnabled(true);
        } else
            if ("off".equals(value) || "false".equals(value) || "disable".equals(value) || "disabled".equals(value)) {
                Config.setAiWebSearchEnabled(false);
            } else if (AiProviderProfiles.isSearchMode(value)) {
                Config.setAiWebSearchMode(value);
                Config.setAiWebSearchEnabled(
                    !AiProviderProfiles.MODE_OFF.equals(value) && !AiProviderProfiles.MODE_UNSUPPORTED.equals(value));
            } else {
                send(
                    sender,
                    EnumChatFormatting.RED
                        + "Usage: /admai search <on|off|toggle|auto|openai|openrouter|dashscope|zhipu|generic-tools>");
                return;
            }
        send(
            sender,
            EnumChatFormatting.GREEN + "AI web search: "
                + (Config.aiWebSearchEnabled ? "enabled" : "disabled")
                + ", mode: "
                + Config.aiWebSearchMode);
    }

    private void sendStatus(ICommandSender sender) {
        String key = Config.getAiApiKey();
        send(
            sender,
            EnumChatFormatting.AQUA + "AI network requests: " + (Config.aiNetworkEnabled ? "enabled" : "disabled"));
        send(sender, EnumChatFormatting.AQUA + "AI base URL: " + Config.aiApiBaseUrl);
        SearchCapability capability = AiProviderProfiles.currentSearchCapability();
        send(sender, EnumChatFormatting.AQUA + "AI provider: " + capability.profile.displayName);
        send(sender, EnumChatFormatting.AQUA + "AI model: " + Config.aiModel);
        send(
            sender,
            EnumChatFormatting.AQUA + "AI web search: "
                + (capability.enabled ? "enabled" : "disabled")
                + ", mode: "
                + capability.mode);
        send(sender, EnumChatFormatting.AQUA + "AI web search detail: " + capability.message);
        send(sender, EnumChatFormatting.AQUA + "AI streaming: " + (Config.aiStreamingEnabled ? "enabled" : "disabled"));
        send(sender, EnumChatFormatting.AQUA + "AI debug logging: " + (Config.aiDebugLogging ? "enabled" : "disabled"));
        send(sender, EnumChatFormatting.AQUA + "AI privacy confirmed: " + (Config.aiPrivacyConfirmed ? "yes" : "no"));
        send(sender, EnumChatFormatting.AQUA + "AI key: " + (key.isEmpty() ? "not set" : maskKey(key)));
    }

    private void sendUsage(ICommandSender sender) {
        send(sender, EnumChatFormatting.YELLOW + getCommandUsage(sender));
        send(sender, EnumChatFormatting.YELLOW + "/admai key sk-...  Set DeepSeek/OpenAI-compatible API key");
        send(sender, EnumChatFormatting.YELLOW + "/admai model deepseek-chat  Set model, supports tab completion");
        send(
            sender,
            EnumChatFormatting.YELLOW
                + "/admai base https://api.deepseek.com  Set API base URL, supports tab completion");
        send(
            sender,
            EnumChatFormatting.YELLOW + "/admai provider openrouter  Apply provider preset for base/model/search mode");
        send(sender, EnumChatFormatting.YELLOW + "/admai network on|off|toggle  Allow or block AI network requests");
        send(
            sender,
            EnumChatFormatting.YELLOW + "/admai search on|off|toggle  Enable web search for supported providers");
        send(
            sender,
            EnumChatFormatting.YELLOW
                + "/admai search auto|openai|openrouter|dashscope|zhipu|generic-tools  Set web search format");
        send(sender, EnumChatFormatting.YELLOW + "/admai status  Show current AI config");
    }

    private String joinArgs(String[] args, int start) {
        if (args.length <= start) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString()
            .trim();
    }

    private String maskKey(String key) {
        if (key == null || key.length() <= 8) {
            return "********";
        }
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }

    private void send(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText(message));
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
