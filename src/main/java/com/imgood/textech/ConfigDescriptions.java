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
            "sparkEnabled",
            "Enable the WebAE Spark profiler page and API when the optional Spark mod is installed.",
            "安装可选 Spark 模组时，是否启用 WebAE Spark 性能分析页面和接口。");
        put(
            "webConsole",
            "sparkMaxHistory",
            "Maximum number of Spark run records retained in TeXTech/WebAE/spark-history.json.",
            "保存在 TeXTech/WebAE/spark-history.json 中的 Spark 运行记录数量上限。");
        put(
            "webConsole",
            "sparkDefaultDurationSeconds",
            "Default Spark profiler duration requested from the WebAE page, in seconds.",
            "WebAE 页面发起 Spark 分析时的默认时长（秒）。");
        put(
            "webConsole",
            "sparkMaxDurationSeconds",
            "Hard maximum duration for a Spark profiler run started through WebAE, in seconds.",
            "通过 WebAE 发起的 Spark 分析允许的最大时长（秒）。");
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
            "Minimum interval (ms) between fuzzy recipe searches per owner via /api/recipes/search?q=. Default 1000.",
            "每位 owner 模糊配方搜索 /api/recipes/search?q= 的最小间隔（毫秒）。默认 1000。");
        put(
            "webConsole",
            "recipeKeepMemoryAfterUpload",
            "Keep the full recipe cache in server memory after upload/save. Default false — clear heap and let browsers sync chunks; server reloads lazily for craft-tree / API.",
            "上传并写盘后是否保留服务端内存中的全量配方。默认 false（清堆，由浏览器分块同步；材料树/兜底 API 按需懒加载）。");
        put(
            "webConsole",
            "recipeSyncChunkSize",
            "Recipes per browser-sync chunk file and GET /api/recipes/sync/chunk. Default 400.",
            "浏览器同步每个分块文件 / GET /api/recipes/sync/chunk 的配方条数。默认 400。");
        put(
            "webConsole",
            "nesqlRepositoryPath",
            "NESQL exporter repository root for /admweb icons import-nesql. Empty = <instance>/TeXTech/WebAE (same folder as client recipe export).",
            "NESQL 导出仓库根目录，用于 /admweb icons import-nesql。空 = 实例根目录下 TeXTech/WebAE（与客户端配方导出目录相同）。");
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
            "iconDirectRenderEnabled",
            "When true, missing icons may be rendered synchronously by an online MC client on HTTP 404 (blocks up to iconDirectRenderTimeoutMs). Default false. Lazy async capture is separate (iconLazyCaptureEnabled, also default false).",
            "为 true 时，HTTP 404 缺图可由在线 MC 客户端同步渲染（最多阻塞 iconDirectRenderTimeoutMs）。默认 false。异步懒加载为独立开关 iconLazyCaptureEnabled（亦默认 false）。");
        put(
            "webConsole",
            "iconDirectRenderTimeoutMs",
            "Max wait (ms) for a direct icon render before falling back to async lazy queue. Default 3000.",
            "直渲图标最大等待毫秒数，超时后回退异步懒加载队列。默认 3000。");
        put(
            "webConsole",
            "iconDirectRenderPerTick",
            "Direct icon renders processed per client tick. Default 4, range 1-32.",
            "客户端每 tick 处理的直渲图标数。默认 4，范围 1-32。");
        put(
            "webConsole",
            "powerSampleWindowSeconds",
            "Sliding window duration in seconds for power/steam rate calculation. Longer windows give smoother rates. Default 60.",
            "电力/蒸汽速率计算的滑动窗口时长（秒）。窗口越长速率越平滑。默认 60 秒。");
        put(
            "webConsole",
            "metricSampleIntervalMs",
            "Sampling interval in milliseconds for network-wide scalar metrics (item/fluid/CPU/GT counts) used by dashboard trend charts. Valid values: 1000-60000. Default 30000.",
            "网络级标量指标（物品/流体/CPU/GT 计数）的采样间隔（毫秒），用于仪表盘趋势图。有效值：1000-60000。默认 30000。");
        put(
            "webConsole",
            "metricSampleWindowSeconds",
            "Rolling window duration in seconds for network metric history. Longer windows retain more trend history at the cost of memory. Valid values: 60-3600. Default 300.",
            "网络指标历史的滚动窗口时长（秒）。窗口越长保留的趋势历史越多，但内存占用更高。有效值：60-3600。默认 300。");
        put(
            "webConsole",
            "dashboardMaxTracksPerWidget",
            "Maximum pins/series allowed on a single dashboard widget. Valid values: 1-50. Default 10.",
            "单个仪表盘组件允许的钉选/序列上限。有效值：1-50。默认 10。");
        put(
            "webConsole",
            "dashboardMaxTracksGlobal",
            "Maximum active item/fluid/entity tracks per player across all dashboard widgets. Valid values: 1-256. Default 16.",
            "同一玩家在所有仪表盘组件上同时活跃的物品/流体/实体跟踪总数上限。有效值：1-256。默认 16。");
        put(
            "webConsole",
            "dashboardMaxItemTracks",
            "Maximum per-item amount history tracks per (player, network). Valid values: 1-64. Default 8.",
            "每个（玩家, 网络）的物品存量历史跟踪上限。有效值：1-64。默认 8。");
        put(
            "webConsole",
            "dashboardMaxFluidTracks",
            "Maximum per-fluid amount history tracks per (player, network). Valid values: 1-64. Default 16.",
            "每个（玩家, 网络）的流体存量历史跟踪上限。有效值：1-64。默认 16。");
        put(
            "webConsole",
            "dashboardMaxEntityTracks",
            "Maximum CPU/GT entity history tracks per (player, network). Valid values: 1-64. Default 16.",
            "每个（玩家, 网络）的 CPU/GT 实体历史跟踪上限。有效值：1-64。默认 16。");
        put(
            "webConsole",
            "gtDefaultScanRadius",
            "Default GT machine scan radius for Data Imprint Tool batch scanning. Valid values: 1-256. Default 16.",
            "数据映录器批量扫描 GT 机器的默认半径。有效值：1-256。默认 16。");
        put(
            "webConsole",
            "refreshIntervalMs",
            "Unified refresh interval in milliseconds for server snapshot collection and frontend polling. Lower values give fresher data but cost more main-thread time. Valid values: 1000-60000. Default 10000.",
            "服务端快照采集与前端轮询的统一刷新间隔（毫秒）。值越小数据越新鲜但占主线程时间越多。有效值：1000-60000。默认 10000。");
        put(
            "webConsole",
            "gtRefreshIntervalMs",
            "GT machine snapshot collection interval in milliseconds. GT machines change slowly so this can be larger than refreshIntervalMs. Valid values: 1000-60000. Default 30000.",
            "GT 机器快照采集间隔（毫秒）。GT 机器状态变化较慢，可大于 refreshIntervalMs。有效值：1000-60000。默认 30000。");
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
            "adminGrantDays",
            "Admin grant token lifetime in days. 0 means never expire. Valid values: 0-3650. Default 90.",
            "管理员提权 Token 有效期（天）。0 表示永不过期。有效值：0-3650。默认 90。");
        put(
            "webConsole",
            "iconCacheEnabled",
            "Enable the item/fluid icon cache system so the web console can render item icons. Default true.",
            "启用物品/流体图标缓存系统，使网页控制台能够渲染物品图标。默认开启。");
        put(
            "webConsole",
            "iconUploadEnabled",
            "Allow clients to upload rendered item icons to the server via explicit /admweb icons upload (or import). Does not enable HTTP 404 lazy capture. Default true.",
            "允许客户端通过显式 /admweb icons upload（或 import）上传渲染好的物品图标。不开启 HTTP 404 懒加载补渲。默认开启。");
        put(
            "webConsole",
            "iconLazyCaptureEnabled",
            "When true, missing icons on GET /api/icon enqueue IconMissingQueue for client render+upload after chat consent. Default false — prefer /admweb icons upload|local.",
            "为 true 时，GET /api/icon 缺图会入队 IconMissingQueue，在聊天同意后由客户端渲染并上传。默认 false — 请用 /admweb icons upload|local。");
        put(
            "webConsole",
            "iconLazyPreferOpOnly",
            "When iconLazyCaptureEnabled is true, only offer consent to OP players as icon providers. Default true.",
            "当 iconLazyCaptureEnabled 开启时，仅向 OP 玩家发起图标提供方同意请求。默认 true。");
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
            "TTL in milliseconds for per-network pattern browse cache. Default 120000.",
            "按网络缓存样板 browse 结果的 TTL（毫秒）。默认 120000。");
        put(
            "webConsole",
            "topologyEnabled",
            "Enable GET /api/network/topology for simulated AE network topology graphs. Default true.",
            "启用 GET /api/network/topology 模拟 AE 网络拓扑图 API。默认开启。");
        put(
            "webConsole",
            "topologyCacheTtlMs",
            "Cooldown in milliseconds for manual topology snapshot capture (logical/spatial). Default 10000 (10 s).",
            "手动拓扑快照采集冷却时间（毫秒，逻辑/空间视图）。默认 10000（10 秒）。");
        put(
            "webConsole",
            "topologySnapshotPersist",
            "Persist topology snapshots to TeXTech/WebAE/topology/ across server restarts. Default true.",
            "将拓扑快照持久化到 TeXTech/WebAE/topology/，服务端重启后仍可读取。默认开启。");
        put(
            "webConsole",
            "topologySimulatedEnabled",
            "DEPRECATED: Enable topology cable-simulation render mode and GET /api/ae2/cable-texture. Default false.",
            "已弃用：启用拓扑「线缆模拟」渲染模式与 GET /api/ae2/cable-texture。默认关闭。");
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
            "Deprecated: legacy single tile size (migrated to medium tier when not 128). Default 128.",
            "已废弃：旧版单一瓦片像素边长（非 128 时迁移为 medium 档）。默认 128。");
        put(
            "webConsole",
            "worldMapMaxQualityTier",
            "Highest world map quality tier allowed on this server: low, medium, high, ultra. Default medium.",
            "服务端允许的世界地图最高清晰度档位：low / medium / high / ultra。默认 medium。");
        put(
            "webConsole",
            "worldMapDefaultQualityTier",
            "Default world map quality tier when WebAE has no user preference. Default medium.",
            "WebAE 未设置用户偏好时的默认世界地图清晰度档位。默认 medium。");
        put(
            "webConsole",
            "worldMapBoundsPaddingChunks",
            "Extra chunk padding around AE device occupied chunks for world map (Chebyshev). Default 1.",
            "世界地图 AE 设备所在 chunk 外扩邻接 chunk 数（切比雪夫距离，含对角）。默认 1。");
        put(
            "webConsole",
            "worldMapTileBudgetPerTick",
            "Max chunk tiles rendered per server tick for world map (Phase B). Default 1.",
            "世界地图每 tick 最多渲染的 chunk 瓦片数（Phase B）。默认 1。");
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
            "worldMapObliqueEnabled",
            "Master switch for oblique/isometric world map views. When false, only flat is available regardless of worldMapViewsEnabled. Default false.",
            "斜视角/等距世界地图总开关。关闭时仅保留俯视，无视 worldMapViewsEnabled 中的 oblique 项。默认关闭。");
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
            "webConsole",
            "worldMapRenderEngine",
            "Flat terrain tile engine: legacy (average color painter) or uv (texture UV + biome/lighting). Default uv.",
            "俯视地形瓦片引擎：legacy（平均色 painter）或 uv（纹理 UV + 生物群系/光照）。默认 uv。");
        put(
            "webConsole",
            "worldMapObliqueEngine",
            "Oblique terrain tile engine: legacy (column painter) or ray (per-pixel ray trace). Default ray.",
            "斜视角地形瓦片引擎：legacy（柱体 painter）或 ray（逐像素光线追踪）。默认 ray。");
        put(
            "webConsole",
            "worldMapChunkPadding",
            "Chunk padding around each tile snapshot for cross-boundary block lookups (1 = 3x3). Default 1.",
            "每个瓦片快照向四周扩展的 chunk 数，用于跨边界方块查询（1 = 3×3）。默认 1。");
        put(
            "webConsole",
            "worldMapTextureCacheMax",
            "LRU cap for server-side block face textures loaded by the UV renderer. Default 2048.",
            "UV 渲染器从 JAR 加载的方块面纹理 LRU 缓存上限。默认 2048。");
        put(
            "webConsole",
            "worldMapRayBudgetPerTick",
            "Per-tick chunk render budget when oblique ray engine is used. Default 1.",
            "斜视角 ray 引擎每 tick 渲染的 chunk 预算。默认 1。");
        put(
            "webConsole",
            "worldMapRenderThreads",
            "Background render thread count for world map tiles. 0 = auto (CPU cores / 2, minimum 1). Higher values allow faster pre-rendering but increase CPU load.",
            "世界地图瓦片后台渲染线程数。0=自动（CPU 核心数 / 2，至少 1）。增大可加快预渲染但增加 CPU 负载。");
        put(
            "webConsole",
            "worldMapMaxRayDepth",
            "Max transparent block layers blended per oblique ray pixel. Default 6.",
            "斜视角光线每个像素最多叠层的透明方块数。默认 6。");
        put(
            "webConsole",
            "worldMapLowTierObliqueEngine",
            "Oblique engine for low/medium quality tiers: legacy or ray. Default ray (uses ray tracing for all tiers).",
            "低/中档清晰度斜视角引擎：legacy 或 ray。默认 ray（全档位使用光线追踪）。");
        put(
            "webConsole",
            "worldMapZoomLevels",
            "World map zoom pyramid depth (z0 native chunk tiles + parent merges). Default 1 (z0 only; viewport scales).",
            "世界地图 zoom 金字塔层数（z0 原生 chunk + 父级合并）。默认 1（仅 z0，缩放由 viewport 处理）。");
        put(
            "webConsole",
            "worldMapZoomBudgetPerTick",
            "Max parent zoom tile syntheses per server tick. Default 4.",
            "每 tick 最多合成的父级 zoom 瓦片数。默认 4。");
        put(
            "webConsole",
            "worldMapBlockPatchesEnabled",
            "Enable JSON + built-in block patch models for oblique ray hits (stairs, slabs, GT). Default true.",
            "启用 JSON + 内置方块 patch 模型用于斜视角光线命中（楼梯、半砖、GT 等）。默认开启。");
        put(
            "webConsole",
            "worldMapAeQualityBoost",
            "Bump terrain tile quality one tier for chunks containing AE devices (within max tier). Default false.",
            "对含 AE 设备的 chunk 地形瓦片提升一档清晰度（不超过服务端上限）。默认关闭。");
        put(
            "webConsole",
            "worldMapAeOverlayQualityTier",
            "Quality tier for AE overlay tiles (low/medium/high/ultra), independent from terrain quality. Default medium.",
            "AE 叠加层瓦片清晰度（low/medium/high/ultra），与地形 quality 解耦。默认 medium。");
        put(
            "webConsole",
            "worldMapServerAtlasEnabled",
            "Bake server-side block face textures into a single atlas grid to reduce image object count. Default true.",
            "将服务端方块面纹理烘焙进单张 atlas 网格，减少 Image 对象数量。默认开启。");
        put(
            "webConsole",
            "worldMapServerAtlasPx",
            "Server-side world map texture atlas edge length in pixels (multiple of 16). Default 2048.",
            "服务端世界地图纹理 atlas 边长（像素，须为 16 的倍数）。默认 2048。");
        put(
            "webConsole",
            "worldMapTerrainSource",
            "World map terrain tile source: auto (auto-detect), dynmap (force GWM/GTNH-Web-Map pre-rendered tiles), or self (self-rendered). Default auto.",
            "世界地图地形瓦片来源：auto（自动检测）、dynmap（强制使用 GWM/GTNH-Web-Map 预渲染瓦片）或 self（自研引擎渲染）。默认 auto。");
        put(
            "webConsole",
            "worldMapDynmapTileRoot",
            "Local Dynmap tile root directory. Empty = auto-detect dynmap/web/tiles/ under instance root.",
            "本地 Dynmap 瓦片根目录。留空则自动探测实例根目录下的 dynmap/web/tiles/。");
        put(
            "webConsole",
            "worldMapSnapshotMode",
            "World map snapshot mode: client_only (JourneyMap/GL client capture) or legacy (server render).",
            "世界地图快照模式：client_only（客户端 JourneyMap/GL 采集）或 legacy（服务端渲染）。");
        put(
            "webConsole",
            "worldMapJourneyMapEnabled",
            "When true, prefer JourneyMap local tile cache for terrain snapshots when JM is installed.",
            "为 true 时，若已安装 JourneyMap，地形快照优先读取 JM 本地瓦片缓存。");
        put(
            "webConsole",
            "worldMapJourneyMapDataRoot",
            "Override JourneyMap data root (journeymap/data). Empty = auto-detect under instance root.",
            "覆盖 JourneyMap 数据根目录（journeymap/data）。留空则自动探测。");
        put(
            "webConsole",
            "worldMapConsentRadiusChunks",
            "Chunk radius to find online players near an AE network for snapshot upload consent.",
            "查找 AE 网络附近在线玩家以请求快照上传同意的区块半径。");
        put(
            "webConsole",
            "worldMapConsentTimeoutSec",
            "Seconds to wait for a player to accept a snapshot upload request.",
            "等待玩家接受快照上传请求的秒数。");
        put(
            "webConsole",
            "worldMapSnapshotCooldownMs",
            "Cooldown in milliseconds between manual world map snapshot requests (client capture/upload; minimal server load). Default 10000 (10 s).",
            "手动世界地图快照请求冷却时间（毫秒；采集在客户端，服务端负载很小）。默认 10000（10 秒）。");
        put(
            "webConsole",
            "worldMapOwnerSkipConsent",
            "When true, owner /admweb worldmap upload near the network skips consent prompt.",
            "为 true 时，网络主人在附近执行 /admweb worldmap upload 可跳过同意提示。");
        put(
            "webConsole",
            "worldMapClientFallbackQuality",
            "GL fallback quality tier when JourneyMap is unavailable (low recommended).",
            "无 JourneyMap 时的 GL 兜底质量档位（建议 low）。");
        put(
            "webConsole",
            "worldMapClientDownloadBudgetPerTick",
            "MC client tiles downloaded from server per tick for local map-cache sync.",
            "MC 客户端每 tick 从服务端下载到本地 map-cache 的瓦片数。");
        put(
            "webConsole",
            "worldMapBrowserCacheEnabled",
            "Enable browser IndexedDB cache for world map snapshot tiles.",
            "启用浏览器 IndexedDB 缓存世界地图快照瓦片。");
        put(
            "webConsole",
            "worldMapLegacyServerRender",
            "Enable legacy server-side world map tile rendering (ignored when snapshotMode=client_only).",
            "启用旧版服务端世界地图瓦片渲染（snapshotMode=client_only 时忽略）。");
        put(
            "webConsole",
            "worldMapSnapshotSourcePriority",
            "Comma-separated per-chunk snapshot terrain priority: dynmap, journeymap, client_gl.",
            "逗号分隔的快照地形逐块采集优先级：dynmap、journeymap、client_gl。");
        put(
            "webConsole",
            "worldMapDynmapCaptureEnabled",
            "Allow Dynmap as a snapshot terrain capture source (local tiles or HTTP fetch on client).",
            "允许 Dynmap 作为快照地形采集源（客户端本地瓦片或 HTTP 拉取）。");
        put(
            "webConsole",
            "worldMapJourneyMapCaptureEnabled",
            "Allow JourneyMap filesystem cache as a snapshot terrain capture source.",
            "允许 JourneyMap 本地缓存作为快照地形采集源。");
        put(
            "webConsole",
            "worldMapClientGlCaptureEnabled",
            "Allow client GL RenderBlocks as the final snapshot terrain fallback.",
            "允许客户端 GL RenderBlocks 作为快照地形最终兜底。");
        put(
            "webConsole",
            "worldMapSpDirectServe",
            "Integrated single-player: serve missing snapshot tiles via direct FS/GL read.",
            "集成单人服：快照缺失时通过直读本地数据/GL 提供瓦片。");
        put(
            "webConsole",
            "worldMapSpDirectCacheTtlSec",
            "SP direct tile in-memory cache TTL seconds.",
            "单人直读瓦片内存缓存 TTL（秒）。");
        put(
            "webConsole",
            "worldMapDynmapClientFetchUrl",
            "Override Dynmap HTTP base URL for client capture; empty uses dynmapBaseUrl.",
            "客户端 Dynmap HTTP 拉取根 URL；留空则使用 dynmapBaseUrl。");
        put(
            "webConsole",
            "worldMapAeCableWidthBlocks",
            "AE overlay cable line width in blocks (0.125–1.0, default 0.25).",
            "AE 透视层线缆线宽（方块，0.125–1.0，默认 0.25）。");
        put(
            "webConsole",
            "worldMapAePartWidthBlocks",
            "AE overlay attachment line width in blocks; 0 = same as cable width.",
            "AE 透视层附件线宽（方块）；0 表示与线缆相同。");
        put(
            "webConsole",
            "worldMapClientCaptureMode",
            "Client GL capture policy: off, ultra_only (legacy), or when_online (prefer client RenderBlocks for all tiers when player is online in dim). Default when_online.",
            "客户端 GL 截图策略：off、ultra_only（仅 ultra 档）或 when_online（玩家在线且同维度时所有档位优先客户端 RenderBlocks）。默认 when_online。");
        put(
            "webConsole",
            "worldMapClientCaptureRadius",
            "Proactive flat terrain capture radius in chunks around the player (0 = disabled). Default 2.",
            "玩家周围主动捕获 flat 地形瓦片的 chunk 半径（0 = 关闭）。默认 2。");
        put(
            "webConsole",
            "worldMapClientCaptureBudgetPerTick",
            "Max proactive terrain captures per client tick. Default 1.",
            "每客户端 tick 主动捕获地形瓦片数上限。默认 1。");
        put(
            "webConsole",
            "worldMapProgressiveFallback",
            "When true, serve lower cached tier or Dynmap crop PNG with X-WorldMap-Tile-Status: upgrading while target tier renders. Default true.",
            "开启时，目标档位渲染中先返回低清缓存或 Dynmap 裁剪瓦片（响应头 upgrading）。默认开启。");
        put(
            "webConsole",
            "worldMapClientHdTimeoutMs",
            "Milliseconds to wait for client GL upload before server-side fallback render. Default 5000.",
            "等待客户端 GL 上传的超时毫秒数，超时后回退服务端渲染。默认 5000。");
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
        put(
            "debug",
            "webaePerf",
            "Enable periodic WebAE performance profiler summaries to logs/textech/webae-perf.log (slow tick/HTTP still logged regardless). Default false.",
            "启用 WebAE 性能诊断周期性摘要，写入 logs/textech/webae-perf.log（慢 tick/HTTP 仍会无条件记录）。默认关闭。");
        put(
            "webConsole",
            "questEnabled",
            "Enable WebAE BetterQuesting quest book page and read APIs. Default true.",
            "启用 WebAE BetterQuesting 任务书页面与只读 API。默认开启。");
        put(
            "webConsole",
            "questSubmitEnabled",
            "Allow Web quest item/fluid submission from AE network. Default true.",
            "允许从 AE 网络在 Web 端提交任务物品/流体。默认开启。");
        put(
            "webConsole",
            "questClaimEnabled",
            "Allow WebAE to claim pure item/choice quest rewards into the selected AE network. Mixed or non-item rewards stay in-game only. Default true.",
            "允许 WebAE 将纯物品/多选一任务奖励领取到所选 AE 网络。混合或非物品奖励仍需游戏内领取。默认开启。");
        put(
            "webConsole",
            "questChainSubmitEnabled",
            "Allow one-click chain submit that walks prerequisites in order (craft/submit). Default true.",
            "允许一键链式提交：按拓扑顺序完成前置任务（可合成后提交）。默认开启。");
        put(
            "webConsole",
            "questSubmitMaxStacks",
            "Max distinct item stacks per quest submit action. Default 64.",
            "单次任务提交的最大物品种类数。默认 64。");
        put(
            "webConsole",
            "questCraftWaitTimeoutMs",
            "Craft-then-submit orchestration timeout in milliseconds. Default 120000.",
            "合成后提交编排的超时时间（毫秒）。默认 120000。");
        put(
            "webConsole",
            "questEscrowEnabled",
            "Enable AE virtual escrow: lock items/fluids before quest submit/detect, release on Retrieval success or failure. Default true.",
            "启用 AE 虚拟 escrow：提交/持有检测前锁定物品/流体，Retrieval 成功或失败后退回。默认开启。");
        put(
            "webConsole",
            "questEscrowTimeoutMs",
            "Quest escrow session timeout in milliseconds before auto-returning locked stacks to AE. Default 120000.",
            "任务 escrow 会话超时（毫秒），超时后自动将锁定物料退回 AE。默认 120000。");
        put(
            "webConsole",
            "questFluidAllContainersOption",
            "Allow WebAE quest UI checkbox to count all fluid containers (buckets/cans) toward fluid/cell equivalence. When false, only GT/IC2 cells are used. Default false.",
            "允许 WebAE 任务书勾选将桶/罐等全部流体容器计入流体/单元等价。关闭时仅 GT/IC2 流体单元。默认关闭。");
        put(
            "webConsole",
            "questCacheTtlSec",
            "Quest definition cache TTL in seconds. Default 300.",
            "任务定义缓存 TTL（秒）。默认 300。");
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
