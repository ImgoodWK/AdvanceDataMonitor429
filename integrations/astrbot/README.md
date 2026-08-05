# TeXTech / WebAE ↔ AstrBot 集成

这里是 QQ Bot 自研集成与管理台的源码权威：

- `astrbot_plugin_textech_intent`：WebAE / AstrBot 共用 Bot 时的所有权裁决；AstrBot 默认显式前缀 `tt`，1.2.0 会为下游能力保留原始/路由后文本与显式前缀元数据。普通 HTTP(S) 链接、QQ Share/JSON/XML 卡片和明确 Forward/Node 转发会预留给链接总结，即使卡片标题含 WebAE 关键词；精确 `webae ...` 与配置命令前缀（如 `/tps ...`）仍归 WebAE，非链接路由不变
- `astrbot_plugin_web_search`：预搜索注入；默认仅“`tt` + 明确搜索意图”执行
- `astrbot_plugin_link_summary`：从 Plain/Share/QQ 卡片/Reply/Node/合并转发等用户可见结构中提取第一条安全公网链接；普通网页统一执行 DOM 噪音过滤、重复正文去重和有界总输入，知乎回答使用无凭据的精确公开正文适配。1.0.11 将知乎精确公开 JSON GET 限定为最多三次，并在两次重试前依次使用 0.35 秒、1.0 秒的短暂递增退避；1.0.10 的普通网页/知乎群会话 provider 选择、调用或空响应失败时按对象去重尝试默认 provider 的行为保持不变，但不枚举任意 provider，也不把该额外切换用于 B 站或合并转发。1.0.9 仍只把通过 LLM 提炼与防照抄质量检查的结果作为网页摘要，全部 provider 候选失败、空响应或两次近似照抄时发送中性兜底，绝不回显正文摘录。QQ Official 合并转发会优先读取平台实际暴露的嵌套正文；1.0.8 中明确纯图片转发不会再被外层 QQ 导航卡片抢成普通网页，并复用 Private Companion 现有 `PLUGIN_VISION_PROVIDER_ID` 转发识图链（图片准备、GIF 抽帧、缓存、失败冷却和预算），不新增模型凭据。识图成功后普通摘要 provider 失败仍返回有界视觉摘要；共享视觉不可用时保留当前会话 provider 兼容路径。普通媒体消息不触发。B 站视频附带经本地安全下载的封面字节、标题、简介、播放/发布时间等元数据和匿名化热评概览。对已识别链接和明确合并转发无论正常摘要还是安全兜底都确定回复，优先级 99 仍低于 `textech_intent` 的 100；普通消息、未知结构、显式 WebAE 路由和进入插件前已停止的事件不变
- `astrbot_plugin_persona_lib`：稳定 QQ 身份、共享别名、人设、标签、任意属性、私密记忆与共享知识
- `astrbot_plugin_private_companion_overlay`：让生图按共享别名跨用户读取 Persona Lib 全部预设属性，让显式 `tt` 携带/引用图片时稳定进入“参考图 + 提示词”的 `edit` 链路，并旁路持久化成功图片的最终提示词元数据
- `astrbot_plugin_console_bridge`：1.2.0 Persona Console 受控人格草稿和确认投递桥；草稿按 AstrBot fallback 有界回退，真实投递只接收已知会话并复用 Private Companion 单次发送链
- `textech_persona_console`：2.4 独立管理台，含 Persona Studio 网页人格问答、Persona / Memory / Bot 行为、Bot 图片提示词库与账号级收藏（可视缩略图懒加载、原图按需及一年期私有缓存）、网页消息中心、RBAC、脱敏写审计与 JSON 快照回滚；原资料站已迁移到独立 `D:\gtnhcode\TeXTech` 文档中枢
- `astrbot_dashboard_overlay`：为线上 AstrBot Dashboard 幂等注入 TeXTech 深青绿/青色/荧光黄主题，并从总入口 URL fragment 接收短期原生 JWT；写入 Dashboard 自有浏览器存储后立即清理 fragment，不修改 AstrBot 后端或业务数据

部署插件到 `/opt/astrbot/data/plugins/`，管理台部署到 `/opt/textech-console/`。WebAE 的 `webaeIntentKeywords` 和两边显式前缀必须与 AstrBot 插件配置保持一致。当前已知服务器只验证了 AstrBot 侧；真实 WebAE/Minecraft 实例及其 `TeXTech/WebAE/qq-bot.json` 尚未定位，联合路由示例只是契约，不是生产验收结果。任何 `.env`、密码、API Key、Token、Cookie 或数据库不得复制回仓库。

生产入口为 AstrBot `https://textech.top:8445/`、Persona Console `https://textech.top:8444/`。从总入口点击时，AstrBot 使用短期 Dashboard JWT，Persona 调用 `POST /api/auth/portal` 并切换到现有管理员安全 Cookie；两者都不读取或下发管理员密码。6186 仅监听 loopback，并通过共享 `api-atlas_default` 网络由 Caddy 反代。
