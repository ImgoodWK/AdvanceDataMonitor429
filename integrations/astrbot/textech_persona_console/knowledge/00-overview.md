# TeXTech Bot 总览

服务器真实配置、数据与进程状态是运行权威；本目录是可部署的无密钥运维知识模板。

## 服务

| 服务 | 端口 | 说明 |
|------|------|------|
| AstrBot 面板 | `6185` | 官方插件、平台与 Provider 管理 |
| TeXTech 管理台 | `6186` | 人设、记忆、统一 Bot 行为、受控网页消息、权限、审计、备份回滚与运维 |
| AstrBot 目录 | `/opt/astrbot` | Docker Compose；数据在 `data/` |
| 管理台目录 | `/opt/textech-console` | 独立 Docker Compose |

## 权威数据

- 共享身份、人设与任意属性：`plugin_data/astrbot_plugin_persona_lib/personas.json`
- 用户陪伴状态和私密记忆：`plugin_data/astrbot_plugin_private_companion/companions.json`
- 网页草稿/投递任务：`plugin_data/astrbot_plugin_console_bridge/queue.json`（内部含 UMO，API 只返回派生 key 和脱敏 hint）
- 管理台账号、角色、Token 哈希和脱敏写操作审计：`/opt/textech-console/data/console.db`

任何真实密码、API Key、Token、Cookie 都不得写入资料站或 Git；本机运维凭据只放 `secrets/local.env`。
