# 插件与自建桥接

## Persona Lib（唯一共享身份 / 人设源）

- 插件：`data/plugins/astrbot_plugin_persona_lib/`
- 数据：`data/plugin_data/astrbot_plugin_persona_lib/personas.json`
- 身份：记录 key / `subject_id` 绑定 QQ 稳定 ID；`names` 是全群共享别名
- 人设：`appearance`、`personality`、`tags`、任意 `attributes`、`content`、`extra`
- 私密 memory 由 `owner_id` 隔离；共享 persona 可供任何群成员查询和引用

### PersonaOp 持久模板

- `collect_instruction` 必须包含 `target_id`、`tags`、`attributes`；运行时兼容旧模板，但生产持久配置应刷新为当前 Persona Lib Schema 默认值，避免双模板注入。

## Private Companion（人格执行 / 主动聊 / 识图 / 生图 / 私密记忆）

- 插件：`data/plugins/astrbot_plugin_private_companion/`
- 用户状态：`data/plugin_data/astrbot_plugin_private_companion/companions.json`
- 配置：`data/config/astrbot_plugin_private_companion_config.json`
- 自动回复、生图工具、识图和提示词合法化由它统一承担；AstrBot 自带随机主动回复关闭，避免双回
- 生图适配器读取整个 Persona Lib，按提示词中的共享别名命中目标人设与任意属性

## Console Bridge（网页人格草稿 / 受控投递）

- 插件：`data/plugins/astrbot_plugin_console_bridge/`，版本 1.2.0
- 队列：`data/plugin_data/astrbot_plugin_console_bridge/queue.json`
- 草稿使用当前默认 provider，并合并主 Bot 人格、Private Companion 回复风格和 Persona Lib 共享人设
- 目标只能来自 Private Companion 当前已知且允许的会话；API 不暴露原始 UMO/完整 ID
- 投递仅走 Private Companion 确认发送链；失败确认或进程失联标记 `uncertain`，不自动重试

## 路由与联网

- `astrbot_plugin_textech_intent`：`tt`（含 `tt生图`）强制 AstrBot；WebAE 关键词静默让出
- 当前已知服务器没有 WebAE/Minecraft 实例或 `TeXTech/WebAE/qq-bot.json`；静默让出不代表已有 WebAE 接手，联合路由尚未生产验收
- `astrbot_plugin_web_search`：默认只处理“`tt` + 明确搜索意图”，自动插话与普通闲聊不联网
- `astrbot_plugin_soulmap`：已移出活动目录，仅保留禁用备份用于迁移/回看，不再作为共享人设写入源

## 记忆（其它插件，可选）

管理台可只读扫描其它记忆插件；可编辑的长期私密记忆以 Private Companion `companion_memory.items[]` 为准。
