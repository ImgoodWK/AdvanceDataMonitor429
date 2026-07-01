package com.imgood.textech;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bilingual (English / 中文) descriptions for all {@link Config} options.
 * Used in the .cfg file comments and the in-game manual config reference pages.
 */
public final class ConfigDescriptions {

    private static final String SEP = " / ";

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        put(
            "debug",
            "general",
            "Enable general mod debug traces (for example NBT JSON parser diagnostics).",
            "启用模组通用调试跟踪（例如 NBT JSON 解析诊断）。");
        put(
            "debug",
            "guiNetworkLink",
            "Show debug overlays on the AE network link GUI (for example button ID count).",
            "在 AE 网络链接 GUI 上显示调试叠加层（例如按钮 ID 计数）。");
        put(
            "debug",
            "monitorTestMode",
            "Refresh data monitor chart bindings with random values every tick and sync to clients.",
            "每 tick 用随机值刷新数据监视器图表绑定并同步到客户端（仅调试）。");

        put(
            "ai",
            "apiBaseUrl",
            "OpenAI-compatible chat API base URL. DeepSeek default is https://api.deepseek.com",
            "兼容 OpenAI 的聊天 API 基础地址。DeepSeek 默认为 https://api.deepseek.com");
        put(
            "ai",
            "apiKey",
            "Client-local API key (stored in config/advancedatamonitor/ai-client-local.cfg, never on dedicated server). DEEPSEEK_API_KEY env var also works.",
            "客户端本地 API 密钥（保存在 config/advancedatamonitor/ai-client-local.cfg，专用服不会读取）。也可使用 DEEPSEEK_API_KEY 环境变量。");
        put("ai", "model", "Chat model name, for example deepseek-chat.", "聊天模型名称，例如 deepseek-chat。");
        put("ai", "networkEnabled", "Allow AI chat to send network requests.", "是否允许 AI 聊天发送网络请求。");
        put(
            "ai",
            "webSearchEnabled",
            "Allow the AI chat request to ask supported providers for web search.",
            "是否允许 AI 聊天请求联网搜索（需服务商支持）。");
        put(
            "ai",
            "webSearchMode",
            "Web search request format: auto, openai, openrouter, dashscope, zhipu, generic-tools, or off.",
            "联网搜索请求格式：auto、openai、openrouter、dashscope、zhipu、generic-tools 或 off。");
        put("ai", "debugLogging", "Write sanitized AI request diagnostics to the mod log.", "将脱敏后的 AI 请求诊断信息写入模组日志。");
        put(
            "ai",
            "streamingEnabled",
            "Use streaming chat responses when the provider supports it.",
            "当服务商支持时使用流式聊天响应。");
        put(
            "ai",
            "privacyConfirmed",
            "Whether the player has confirmed that AI chat sends prompts to the configured provider.",
            "玩家是否已确认 AI 聊天会将提示词发送至所配置的服务商。");
        put("ai", "recentModels", "Comma-separated recently used AI model names.", "最近使用的 AI 模型名称，逗号分隔。");
        put("ai", "timeoutSeconds", "HTTP timeout in seconds.", "HTTP 超时时间（秒）。");
        put("ai", "maxTokens", "Maximum tokens returned by the model.", "模型返回的最大 Token 数。");
        put("ai", "temperature", "Sampling temperature.", "采样温度。");

        put("voice", "enabled", "Enable the voice assistant hotkey and STT flow.", "启用语音助手快捷键和语音转文字流程。");
        put(
            "voice",
            "privacyConfirmed",
            "Whether the player has confirmed the configured voice recognition mode.",
            "玩家是否已确认所配置的语音识别模式。");
        put("voice", "sttMode", "Speech-to-text mode: embedded-vosk or http.", "语音转文字模式：embedded-vosk 或 http。");
        put(
            "voice",
            "sttBaseUrl",
            "OpenAI-compatible STT API base URL. Used only when sttMode=http. Empty uses ai.apiBaseUrl.",
            "兼容 OpenAI 的 STT API 基础地址。仅在 sttMode=http 时使用。留空则使用 ai.apiBaseUrl。");
        put(
            "voice",
            "sttApiKey",
            "API key for HTTP STT. Empty uses VOICE_STT_API_KEY or ai.apiKey. Not needed for embedded-vosk.",
            "HTTP 语音转文字 API 密钥。留空则使用 VOICE_STT_API_KEY 或 ai.apiKey。embedded-vosk 模式不需要。");
        put(
            "voice",
            "sttModel",
            "Speech-to-text model. embedded-vosk default is zh-small; http default is whisper-1.",
            "语音转文字模型。embedded-vosk 默认为 zh-small；http 默认为 whisper-1。");
        put("voice", "sttTimeoutSeconds", "STT timeout in seconds.", "语音转文字超时时间（秒）。");

        put("assistant", "maxOrderAmount", "Maximum amount accepted for one voice crafting order.", "单次语音合成下单允许的最大数量。");
        put(
            "assistant",
            "maxWithdrawAmount",
            "Maximum amount accepted for one AE2 storage withdraw into player inventory.",
            "单次从 AE2 存储取出到玩家背包允许的最大数量。");
        put(
            "assistant",
            "craftJobTimeoutSeconds",
            "Maximum seconds to wait for an AE2 crafting calculation before cancelling it.",
            "等待 AE2 合成计算完成的最长秒数，超时则取消。");
        put(
            "assistant",
            "maxConcurrentCraftJobs",
            "Maximum concurrent assistant AE2 crafting calculations per player.",
            "每位玩家同时进行的最大助手 AE2 合成计算数。");
        put(
            "assistant",
            "queryCandidateBatchSize",
            "Number of AE2 query candidates sent per network packet batch.",
            "每次 AE2 查询候选项网络包分批发送的条数。");
        put(
            "assistant",
            "maxQueryCandidates",
            "Maximum AE2 query candidates returned for one assistant lookup.",
            "单次助手 AE2 查询最多返回的候选项条数。");
        put(
            "assistant",
            "linkSearchRadius",
            "Block radius around the player when searching for AE2 link blocks (crafting/storage/network).",
            "助手搜索 AE2 链接方块（合成/存储/网络）时以玩家为中心的水平搜索半径（格）。");

        put(
            "plannerHudLimits",
            "minMaxDisplay",
            "Minimum allowed planner HUD displayed entry count.",
            "计划器 HUD 显示条目数允许的最小值。");
        put(
            "plannerHudLimits",
            "maxMaxDisplay",
            "Maximum allowed planner HUD displayed entry count.",
            "计划器 HUD 显示条目数允许的最大值。");
        put(
            "plannerHudLimits",
            "minPosX",
            "Minimum allowed planner HUD horizontal position ratio.",
            "计划器 HUD 水平位置比例允许的最小值。");
        put(
            "plannerHudLimits",
            "maxPosX",
            "Maximum allowed planner HUD horizontal position ratio.",
            "计划器 HUD 水平位置比例允许的最大值。");
        put(
            "plannerHudLimits",
            "minPosY",
            "Minimum allowed planner HUD vertical position ratio.",
            "计划器 HUD 垂直位置比例允许的最小值。");
        put(
            "plannerHudLimits",
            "maxPosY",
            "Maximum allowed planner HUD vertical position ratio.",
            "计划器 HUD 垂直位置比例允许的最大值。");
        put("plannerHudLimits", "minScale", "Minimum allowed planner HUD scale.", "计划器 HUD 缩放允许的最小值。");
        put("plannerHudLimits", "maxScale", "Maximum allowed planner HUD scale.", "计划器 HUD 缩放允许的最大值。");
        put("plannerHudLimits", "minWidth", "Minimum allowed planner HUD text width.", "计划器 HUD 文本宽度允许的最小值。");
        put("plannerHudLimits", "maxWidth", "Maximum allowed planner HUD text width.", "计划器 HUD 文本宽度允许的最大值。");

        put(
            "dataDustLoomCell",
            "itemRatePerSecond",
            "Base item generation rate per second for the Data Dust Loom Cell (items/s).",
            "数据织尘元件每秒基础物品生成速率（个/秒）。");
        put(
            "dataFormLoomCell",
            "itemRatePerSecond",
            "Base item generation rate per second for the Data Form Loom Cell (items/s).",
            "数据织形元件每秒基础物品生成速率（个/秒）。");
        put(
            "dataFlowCell",
            "fluidRatePerSecond",
            "Base fluid generation rate per second for the Data Flow Cell (mB/s).",
            "数据涌流元件每秒基础流体生成速率（mB/秒）。");
        put(
            "dataSourceLoomCell",
            "essentiaRatePerSecond",
            "Base essentia generation rate per second for the Data Source Loom Cell (mB/s, aspect fluids).",
            "数据织源元件每秒基础源质生成速率（mB/秒，aspect 流体）。");
        put(
            "dataLoomCell",
            "syncIntervalSeconds",
            "Generation sync interval in seconds for all Data Loom cells (performance batching).",
            "所有数据编织元件的生成同步间隔（秒），用于性能批处理。");
        put(
            "dataLoomCell",
            "debugLogging",
            "Server-side trace log for Data Loom weaving (indexing, skip reasons, energy, output). "
                + "Only when true: writes to logs/advancedatamonitor/data-loom-debug.log.",
            "数据编织元件服务端追踪日志（索引、跳过原因、能量、产出等）。" + "仅在为 true 时写入 logs/advancedatamonitor/data-loom-debug.log。");
        put(
            "dataLoomCell",
            "energyDrainPerTick",
            "Fixed AE drain per game tick while a Data Loom cell weaves (default 999999 AE/t).",
            "数据编织元件编织时固定耗能（默认 999999 AE/t）。");
        put(
            "dataLoomCell",
            "weaveAmplifierRateMultiplier",
            "Output multiplier per Weave Amplifier card (default 4x). Cards stack multiplicatively.",
            "每张编织增幅卡的产出倍率（默认 4×）。多张卡片倍率相乘。");
        put(
            "dataLoomCell",
            "superWeaveAmplifierRateMultiplier",
            "Output multiplier per Super Weave Amplifier card (default 16x). Cards stack multiplicatively.",
            "每张超级编织增幅卡的产出倍率（默认 16×）。多张卡片倍率相乘。");

        put(
            "superOrange",
            "droneEnabled",
            "Spawn a companion drone when the player carries a Super Orange.",
            "玩家携带超能砂糖桔时生成伴生无人机。");
        put(
            "superOrange",
            "headEffectsEnabled",
            "Render halo and rename nameplate above Super Orange carriers. Other players always see them; your own are hidden in first person.",
            "在超能砂糖桔携带者头顶渲染光环与改名铭牌。他人始终可见；第一人称下隐藏自身效果。");
        put(
            "superOrange",
            "dropMultiplierEnabled",
            "Multiply block drops for players carrying a Super Orange.",
            "为携带超能砂糖桔的玩家倍增方块掉落。");
        put(
            "superOrange",
            "dropMultiplier",
            "Default per-item drop multiplier for new Super Orange stacks (GUI can override up to dropMultiplierMax).",
            "新超能砂糖桔默认掉落倍率（GUI 可设置，上限见 dropMultiplierMax）。");
        put(
            "superOrange",
            "dropMultiplierMax",
            "Maximum per-item drop multiplier configurable in the Super Orange GUI.",
            "超能砂糖桔 GUI 可设置的最大掉落倍率。");
        put(
            "superOrange",
            "projectileImmunityEnabled",
            "Grant complete projectile damage immunity while carrying a Super Orange.",
            "携带超能砂糖桔时获得完全投射物伤害免疫。");
        put(
            "superOrange",
            "droneAttackRange",
            "Range in blocks within which the Super Orange drone scans for hostile mobs.",
            "超能砂糖桔无人机扫描敌对生物的范围（格）。");
        put(
            "superOrange",
            "droneAttackDamage",
            "True damage dealt per attack hit by the Super Orange drone (bypasses armor).",
            "超能砂糖桔无人机每次攻击造成的真实伤害（无视护甲）。");
        put(
            "superOrange",
            "droneAttacksPerSecond",
            "How many times per second the Super Orange drone damages its target.",
            "超能砂糖桔无人机每秒对目标造成伤害的次数。");
        put(
            "superOrange",
            "droneMaxClones",
            "Maximum number of clone drones the Super Orange can split into to attack multiple targets.",
            "超能砂糖桔可分裂出的最大克隆无人机数量，用于攻击多个目标。");
        put(
            "superOrange",
            "droneFollowHeight",
            "Height offset above the player where the companion drone hovers.",
            "伴生无人机在玩家上方悬浮的高度偏移。");

        put(
            "matterBallDecompressor",
            "itemsPerSecond",
            "Base item decompression rate (items per second) before AE acceleration cards.",
            "物质球解压器基础解压速率（每秒物品数，未计 AE 加速卡）。");

        put(
            "grapple",
            "hintRange",
            "Range in blocks to show grapple anchor proximity HUD while holding the hook.",
            "手持挂索器时显示挂索节点接近提示 HUD 的范围（格）。");
        put(
            "grapple",
            "interactRange",
            "Maximum distance in blocks to attach to a grapple anchor.",
            "挂接到挂索节点的最大距离（格）。");
        put(
            "grapple",
            "scanChunkRadius",
            "Chunk radius used when listing nearby grapple anchors while attached.",
            "已挂接时列出附近挂索节点所使用的区块半径。");
        put(
            "grapple",
            "maxTravelChunkRadius",
            "Maximum travel distance in chunk radius between grapple anchors.",
            "挂索节点间最大移动距离（区块半径）。");
        put("grapple", "moveSpeed", "Grapple travel speed in blocks per server tick.", "挂索移动速度（格/服务端 tick）。");
        put(
            "grapple",
            "snapRadiusPx",
            "Crosshair magnetic snap radius in screen pixels for selecting grapple nodes.",
            "选择挂索节点时准星磁吸半径（屏幕像素）。");
        put(
            "grapple",
            "travelSnapDegrees",
            "View cone in degrees for selecting a travel target while attached (approximate aim).",
            "已挂接时选择移动目标的视锥角度（近似瞄准）。");
        put(
            "grapple",
            "attachSnapDegrees",
            "View cone in degrees for selecting a node to attach while nearby.",
            "在附近选择挂接节点时的视锥角度。");
        put(
            "grapple",
            "maxTravelQueueSize",
            "Maximum queued grapple travel hops while already sliding.",
            "已在滑移中时允许排队的最大挂索移动跳数。");
    }

    private ConfigDescriptions() {}

    public static String bilingual(String english, String chinese) {
        return english + SEP + chinese;
    }

    public static String get(String category, String key) {
        String description = DESCRIPTIONS.get(category + "." + key);
        if (description == null) {
            AdvanceDataMonitor.LOG.warn("Missing config description for {}.{}", category, key);
            return category + "." + key;
        }
        return description;
    }

    private static void put(String category, String key, String english, String chinese) {
        DESCRIPTIONS.put(category + "." + key, bilingual(english, chinese));
    }
}
