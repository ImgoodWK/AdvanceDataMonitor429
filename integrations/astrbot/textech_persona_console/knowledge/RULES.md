# 改 Bot 必须遵守的规则

## 1. 权威与同步

- 服务器真实配置、数据和进程状态高于文档；代码权威在 `integrations/astrbot/`。
- 行为变更同时更新插件 README、管理台知识模板、WebAE 意图说明与运维文档。

## 2. 数据安全

- 写 JSON 前自动备份；不清空无关用户记忆。
- 密码、API Key、Token、Cookie、ClientSecret 不得写入资料站、源码、日志或回复正文。
- 管理台 API 默认脱敏；明文配置权限不得授予普通编辑账号。
- 网页消息只能选已知且允许的会话；不得暴露 UMO/完整 ID，不得绕过 `messages.send`、目标锁定或 `SEND` 确认。
- 投递确认不完整时标记 `uncertain` 且不自动重发；未经明确授权，部署测试不得向真实 QQ 提交正确确认。

## 3. 唯一职责

- Persona Lib：稳定身份、共享别名、人设、标签、任意属性和共享知识。
- Private Companion：人格执行、主动回复、关系/私密记忆、识图、生图与提示词合法化。
- `textech_intent`：WebAE/AstrBot 所有权；当前仅 AstrBot 侧已验收，真实 WebAE/Minecraft 端未定位。`web_search`：仅显式 tt 搜索。
- Console Bridge：网页人格草稿与受控消息投递；发送复用 Private Companion 确认发送链。
- SoulMap 禁用，仅保留旧数据迁移和回看，不再双写。

## 4. 部署

- AstrBot：`/opt/astrbot`
- 管理台：`/opt/textech-console`
- 改插件/路由后重启 AstrBot 并检查 6185、日志与 401/403；改管理台后重新 build 并检查 6186。
