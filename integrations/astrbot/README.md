# TeXTech / WebAE ↔ AstrBot 集成

这里是 QQ Bot 自研集成与管理台的源码权威：

- `astrbot_plugin_textech_intent`：WebAE / AstrBot 共用 Bot 时的所有权裁决；AstrBot 默认显式前缀 `tt`
- `astrbot_plugin_web_search`：预搜索注入；默认仅“`tt` + 明确搜索意图”执行
- `astrbot_plugin_persona_lib`：稳定 QQ 身份、共享别名、人设、标签、任意属性、私密记忆与共享知识
- `astrbot_plugin_private_companion_overlay`：让生图按共享别名跨用户读取 Persona Lib 全部预设属性
- `astrbot_plugin_console_bridge`：1.2.0 Persona Console 受控人格草稿和确认投递桥；草稿按 AstrBot fallback 有界回退，真实投递只接收已知会话并复用 Private Companion 单次发送链
- `textech_persona_console`：2.3 独立管理台，含 Persona Studio 网页人格问答、Persona / Memory / Bot 行为、网页消息中心、RBAC、脱敏写审计与 JSON 快照回滚

部署插件到 `/opt/astrbot/data/plugins/`，管理台部署到 `/opt/textech-console/`。WebAE 的 `webaeIntentKeywords` 和两边显式前缀必须与 AstrBot 插件配置保持一致。当前已知服务器只验证了 AstrBot 侧；真实 WebAE/Minecraft 实例及其 `TeXTech/WebAE/qq-bot.json` 尚未定位，联合路由示例只是契约，不是生产验收结果。任何 `.env`、密码、API Key、Token、Cookie 或数据库不得复制回仓库。
