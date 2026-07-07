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
            "debug",
            "connectorProfile",
            "Log AE connector refresh counts and average durations every 10 seconds (network / crafting / storage links).",
            "每 10 秒记录 AE 连接器（网络/合成/存储链接器）刷新次数与平均耗时，便于 Spark 对照。");
        put(
            "debug",
            "uiFrameworkBlock",
            "Register the UI framework debug block (creative) and its component showcase GUI.",
            "注册 UI 框架调试方块（创造模式）及组件展示 GUI。");
        put(
            "debug",
            "webae",
            "Log WebAE diagnostics (NEI recipe collector handler/recipe counts, icon rendering, etc.).",
            "记录 WebAE 调试诊断（NEI 配方采集器 handler/配方计数、图标渲染等）。");

        put(
            "ai",
            "apiBaseUrl",
            "OpenAI-compatible chat API base URL. DeepSeek default is https://api.deepseek.com",
            "兼容 OpenAI 的聊天 API 基础地址。DeepSeek 默认为 https://api.deepseek.com");
        put(
            "ai",
            "apiKey",
            "Client-local API key (stored in config/textech/ai-client-local.cfg, never on dedicated server). DEEPSEEK_API_KEY env var also works.",
            "客户端本地 API 密钥（保存在 config/textech/ai-client-local.cfg，专用服不会读取）。也可使用 DEEPSEEK_API_KEY 环境变量。");
        put("ai", "model", "Chat model name, for example deepseek-chat.", "聊天模型名称，例如 deepseek-chat。");
        put("ai", "networkEnabled", "Allow AI chat to send network requests.", "是否允许 AI 聊天发送网络请求。");
        put(
            "ai",
            "webSearchEnabled",
            "Enable built-in web search before sending the prompt to the LLM.",
            "是否在调用 LLM 前启用内置联网搜索。");
        put(
            "ai",
            "webSearchMode",
            "Built-in search engine: auto, tavily_keyless, duckduckgo, tavily, brave, serper, searxng, or off.",
            "内置搜索引擎：auto、tavily_keyless、duckduckgo、tavily、brave、serper、searxng 或 off。");
        put(
            "ai",
            "searchApiKey",
            "API key for Tavily, Brave, or Serper search engines (client-local only).",
            "Tavily / Brave / Serper 搜索引擎 API Key（仅客户端本地）。");
        put(
            "ai",
            "searchBaseUrl",
            "Base URL for a self-hosted SearXNG instance, for example https://searx.example.com.",
            "自建 SearXNG 实例的基础 URL，例如 https://searx.example.com。");
        put("ai", "searchMaxResults", "Maximum web search results to inject (1-10).", "注入 LLM 的搜索结果数量上限（1-10）。");
        put(
            "ai",
            "searchFallback",
            "When one search engine fails, automatically try the next engine in the chain.",
            "某个搜索引擎失败时，是否自动尝试链上的下一个引擎。");
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
                + "Only when true: writes to logs/textech/data-loom-debug.log.",
            "数据编织元件服务端追踪日志（索引、跳过原因、能量、产出等）。" + "仅在为 true 时写入 logs/textech/data-loom-debug.log。");
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

        put(
            "webConsole",
            "enabled",
            "Enable the WebAE HTTP console for remote AE2 monitoring and control. Default false.",
            "启用 WebAE HTTP 控制台，用于远程 AE2 监控和控制。默认关闭。");
        put("webConsole", "port", "HTTP server port for WebAE console. Default 8090.", "WebAE 控制台的 HTTP 服务端口。默认 8090。");
        put(
            "webConsole",
            "bindAddress",
            "Bind address for WebAE HTTP server. Default 127.0.0.1 (local only). Set to 0.0.0.0 to expose to LAN. WARNING: Exposing to LAN without additional firewall may allow unauthorized access.",
            "WebAE HTTP 服务绑定地址。默认 127.0.0.1（仅本机）。设为 0.0.0.0 可开放局域网访问。警告：开放局域网未经额外防火墙保护可能存在安全风险。");
        put(
            "webConsole",
            "snapshotIntervalSeconds",
            "Interval in seconds for automatic background storage snapshots. Set to 0 to disable periodic snapshots. Default 30.",
            "后台自动存储快照的间隔秒数。设为 0 禁用定期快照。默认 30 秒。");
        put(
            "webConsole",
            "recipeUploadEnabled",
            "Allow OPs to upload NEI recipes to the server recipe cache via /admweb recipes upload command. Phase 2 removed the in-game keybind. Default true.",
            "允许 OP 通过 /admweb recipes upload 命令上传 NEI 配方到服务器配方缓存。Phase 2 起移除了游戏内快捷键。默认开启。");
        put(
            "webConsole",
            "recipeCacheMode",
            "Recipe cache eviction mode: lru (evict oldest when maxRecipeCacheMB exceeded) or full (no LRU eviction; GTNH recommended). Default full.",
            "配方缓存淘汰模式：lru（超过 maxRecipeCacheMB 时 LRU 淘汰）或 full（不 LRU 淘汰，GTNH 推荐）。默认 full。");
        put(
            "webConsole",
            "maxRecipeCacheMB",
            "Approximate maximum memory (MB) for the server-side recipe cache. In lru mode recipes are evicted when exceeded; in full mode only logs a warning. Default 256.",
            "服务端配方缓存的最大近似内存（MB）。lru 模式下超过时淘汰；full 模式仅告警。默认 256。");
        put(
            "webConsole",
            "recipeUploadBatchesPerTick",
            "Recipe upload JSON batches sent per client tick. Default 3, range 1-32.",
            "每 tick 上传的配方 JSON 批次数。默认 3，范围 1-32。");
        put(
            "webConsole",
            "recipeSearchMinIntervalMs",
            "Minimum interval (ms) between fuzzy recipe searches per owner via /api/recipes/search?q=. Default 300.",
            "每位 owner 模糊配方搜索 /api/recipes/search?q= 的最小间隔（毫秒）。默认 300。");
        put(
            "webConsole",
            "nesqlRepositoryPath",
            "NESQL exporter repository root for /admweb icons import-nesql. Empty = <instance>/TeXTechWebAE (same folder as client recipe export).",
            "NESQL 导出仓库根目录，用于 /admweb icons import-nesql。空 = 实例根目录下 TeXTechWebAE（与客户端配方导出目录相同）。");
        put(
            "webConsole",
            "neiDeepScanItemsPerTick",
            "NEI item-driven deep scan items per client tick (/admweb recipes upload deep). 0 = disabled (default).",
            "NEI 物品驱动深度扫描每 tick 物品数（/admweb recipes upload deep）。0 = 禁用（默认）。");
        put(
            "webConsole",
            "iconMissingDispatchPerTick",
            "IconMissingQueue lazy-load requests dispatched per server tick. Default 8, range 1-64.",
            "IconMissingQueue 懒加载图标请求每 tick 派发数。默认 8，范围 1-64。");
        put(
            "webConsole",
            "powerSampleWindowSeconds",
            "Sliding window duration in seconds for power/steam rate calculation. Longer windows give smoother rates. Default 60.",
            "电力/蒸汽速率计算的滑动窗口时长（秒）。窗口越长速率越平滑。默认 60 秒。");
        put(
            "webConsole",
            "metricSampleIntervalMs",
            "Sampling interval in milliseconds for network-wide scalar metrics (item/fluid/CPU/GT counts) used by dashboard trend charts. Valid values: 1000-60000. Default 10000.",
            "网络级标量指标（物品/流体/CPU/GT 计数）的采样间隔（毫秒），用于仪表盘趋势图。有效值：1000-60000。默认 10000。");
        put(
            "webConsole",
            "metricSampleWindowSeconds",
            "Rolling window duration in seconds for network metric history. Longer windows retain more trend history at the cost of memory. Valid values: 60-3600. Default 300.",
            "网络指标历史的滚动窗口时长（秒）。窗口越长保留的趋势历史越多，但内存占用更高。有效值：60-3600。默认 300。");
        put(
            "webConsole",
            "gtDefaultScanRadius",
            "Default GT machine scan radius for Data Imprint Tool batch scanning. Valid values: 1-256. Default 16.",
            "数据映录器批量扫描 GT 机器的默认半径。有效值：1-256。默认 16。");
        put(
            "webConsole",
            "refreshIntervalMs",
            "Unified refresh interval in milliseconds for server snapshot collection and frontend polling. Lower values give fresher data but cost more main-thread time. Valid values: 1000-60000. Default 1000.",
            "服务端快照采集与前端轮询的统一刷新间隔（毫秒）。值越小数据越新鲜但占主线程时间越多。有效值：1000-60000。默认 1000。");
        put(
            "webConsole",
            "gtRefreshIntervalMs",
            "GT machine snapshot collection interval in milliseconds. GT machines change slowly so this can be larger than refreshIntervalMs. Valid values: 1000-60000. Default 10000.",
            "GT 机器快照采集间隔（毫秒）。GT 机器状态变化较慢，可大于 refreshIntervalMs。有效值：1000-60000。默认 10000。");
        put(
            "webConsole",
            "maxNetworksDisplayed",
            "Maximum number of AE2 networks the web console can display simultaneously (multi-select). Valid values: 1-20. Default 5.",
            "网页控制台可同时显示的 AE2 网络数量上限（多选）。有效值：1-20。默认 5。");
        put(
            "webConsole",
            "tokenLifetimeHours",
            "Web auth token lifetime in hours. 0 means never expire. When >0, tokens are rejected after issuedAt + TTL. Valid values: 0-8760. Default 0.",
            "Web 认证 Token 有效期（小时）。0 表示永不过期；>0 时按签发时间 + TTL 校验。有效值：0-8760。默认 0。");
        put(
            "webConsole",
            "iconCacheEnabled",
            "Enable the item/fluid icon cache system so the web console can render item icons. Default true.",
            "启用物品/流体图标缓存系统，使网页控制台能够渲染物品图标。默认开启。");
        put(
            "webConsole",
            "iconUploadEnabled",
            "Allow clients to upload rendered item icons to the server via /admweb icons upload command. Phase 2 removed the in-game keybind. Default true.",
            "允许客户端通过 /admweb icons upload 命令上传渲染好的物品图标到服务器。Phase 2 起移除了游戏内快捷键。默认开启。");
        put(
            "webConsole",
            "iconPackEnabled",
            "Allow the web console to switch between and upload icon texture packs. Default true.",
            "允许网页控制台切换与上传图标材质包。默认开启。");
        put(
            "webConsole",
            "iconRenderPerTick",
            "Icons rendered per client tick for a single-mode /admweb icons upload. Default 64, range 8-512.",
            "单 mode 图标导出时每 tick 渲染数量。默认 64，范围 8-512。");
        put(
            "webConsole",
            "iconRenderPerTickAll",
            "Icons rendered per tick when /admweb icons upload uses mode all (more conservative). Default 32.",
            "upload all 时每 tick 渲染数量（更保守）。默认 32。");
        put(
            "webConsole",
            "iconUploadChunksPerTick",
            "Icon upload JSON chunks sent per client tick. Default 4, range 1-32.",
            "每 tick 上传的图标 JSON 分片数。默认 4，范围 1-32。");
        put(
            "webConsole",
            "iconProgressChatIntervalMs",
            "Minimum interval (ms) between in-game chat progress messages during icon export. Default 3000.",
            "图标导出时游戏内聊天进度提示的最小间隔（毫秒）。默认 3000。");
        put(
            "webConsole",
            "patternBrowsePageSize",
            "Default page size for GET /api/patterns/browse (Grid + Interface merged). Default 80, range 20-200.",
            "GET /api/patterns/browse 分页默认条数（Grid + Interface 合并）。默认 80，范围 20-200。");
        put(
            "webConsole",
            "patternBrowseMaxTotal",
            "Maximum total patterns returned by browse API before truncation. Default 20000.",
            "browse API 返回的最大样板总数（超出则截断）。默认 20000。");
        put(
            "webConsole",
            "patternCacheTtlMs",
            "TTL in milliseconds for per-network pattern browse cache. Default 30000.",
            "按网络缓存样板 browse 结果的 TTL（毫秒）。默认 30000。");
        put(
            "webConsole",
            "topologyEnabled",
            "Enable GET /api/network/topology for simulated AE network topology graphs. Default true.",
            "启用 GET /api/network/topology 模拟 AE 网络拓扑图 API。默认开启。");
        put(
            "webConsole",
            "topologyCacheTtlMs",
            "TTL in milliseconds for manual topology snapshot cooldown (logical/spatial). Default 1800000 (30 min).",
            "手动拓扑快照冷却时间（毫秒，逻辑/空间视图）。默认 1800000（30 分钟）。");
        put(
            "webConsole",
            "topologySnapshotPersist",
            "Persist topology snapshots to config/textech/web-topology/ across server restarts. Default true.",
            "将拓扑快照持久化到 config/textech/web-topology/，服务端重启后仍可读取。默认开启。");
        put(
            "webConsole",
            "dynmapBaseUrl",
            "Optional Dynmap base URL (e.g. http://host:8123) for WebAE player location deep links. Empty disables the button.",
            "可选 Dynmap 基础 URL（如 http://host:8123），供 WebAE 玩家坐标外链使用。留空则隐藏按钮。");
        put(
            "webConsole",
            "worldMapEnabled",
            "Enable GET /api/worldmap/* world map overlay API (requires topologyEnabled). Default true.",
            "启用 GET /api/worldmap/* 世界地图覆盖层 API（需 topologyEnabled）。默认开启。");
        put(
            "webConsole",
            "worldMapTilePx",
            "Pixel edge length per chunk tile for world map terrain rendering. Default 128.",
            "世界地图每个 chunk 瓦片的像素边长。默认 128。");
        put(
            "webConsole",
            "worldMapBoundsPaddingChunks",
            "Extra chunk padding around AE device occupied chunks for world map (Chebyshev). Default 1.",
            "世界地图 AE 设备所在 chunk 外扩邻接 chunk 数（切比雪夫距离，含对角）。默认 1。");
        put(
            "webConsole",
            "worldMapTileBudgetPerTick",
            "Max chunk tiles rendered per server tick for world map (Phase B). Default 2.",
            "世界地图每 tick 最多渲染的 chunk 瓦片数（Phase B）。默认 2。");
        put(
            "webConsole",
            "worldMapMaxChunks",
            "Max chunk tiles per dimension for world map bounds clamp. Default 512.",
            "世界地图单维度最大 chunk 瓦片数（超出则 clamp）。默认 512。");
        put(
            "webConsole",
            "worldMapRequireNetworkScope",
            "Require network query param and allowed-chunk scope check on GET /api/worldmap/tiles. Default true.",
            "GET /api/worldmap/tiles 须带 network 参数并校验 AE 网络允许 chunk 范围。默认开启。");
        put(
            "webConsole",
            "worldMapViewsEnabled",
            "Comma-separated enabled world map tile views (flat, oblique, or oblique_se/oblique_sw/oblique_ne/oblique_nw). Default flat + all oblique directions.",
            "启用的世界地图瓦片视角，逗号分隔（flat、oblique 或 oblique_se/oblique_sw/oblique_ne/oblique_nw）。默认俯视 + 全部斜视角。");
        put(
            "webConsole",
            "worldMapClientHdEnabled",
            "Allow online owner/authorized client to upload HD world map tiles over server software renders. Default true.",
            "允许在线主人/授权客户端上传 HD 世界地图瓦片覆盖服务端软件渲染。默认开启。");
        put(
            "webConsole",
            "worldMapClientHdBudgetPerTick",
            "Max HD world map chunk tiles rendered per client tick (Phase 4). Default 1.",
            "客户端每 tick 最多渲染的 HD 世界地图 chunk 瓦片数（Phase 4）。默认 1。");
        put(
            "webConsole",
            "worldMapAeOverlayEnabled",
            "Enable AE overlay tile layer (devices + cables + parts) on world map. Default true.",
            "启用世界地图 AE 透视瓦片层（设备 + 线缆 + 零件）。默认开启。");
        put(
            "webConsole",
            "worldMapAeOverlayIncludeCables",
            "Include AE cables in AE overlay scope and tile rendering. Default true.",
            "AE 透视层范围与瓦片渲染包含线缆。默认开启。");
        put(
            "debug",
            "webaeIcons",
            "Enable verbose WebAE icon rendering/upload logging to logs/textech/webae-icons.log. Default false.",
            "启用 WebAE 图标渲染/上传详细日志，写入 logs/textech/webae-icons.log。默认关闭。");
        put(
            "debug",
            "webaeChat",
            "Enable verbose WebAE chat collection/send logging to logs/textech/webae-chat.log. Default false.",
            "启用 WebAE 聊天收集/发送详细日志，写入 logs/textech/webae-chat.log。默认关闭。");
        put(
            "debug",
            "webaeDashboard",
            "Enable verbose WebAE snapshot/dashboard collection logging to logs/textech/webae-dashboard.log. Default false.",
            "启用 WebAE 快照/仪表盘采集详细日志，写入 logs/textech/webae-dashboard.log。默认关闭。");
        put(
            "debug",
            "webaeSynthesis",
            "Enable verbose WebAE synthesis/order logging to logs/textech/webae-synthesis.log. Default false.",
            "启用 WebAE 合成/下单详细日志，写入 logs/textech/webae-synthesis.log。默认关闭。");
        put(
            "debug",
            "webaePatterns",
            "Enable verbose WebAE pattern list/encode/inject logging to logs/textech/webae-patterns.log. Default false.",
            "启用 WebAE 样板列表/编码/注入详细日志，写入 logs/textech/webae-patterns.log。默认关闭。");
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
