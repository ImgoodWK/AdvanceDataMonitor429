# 数据路径与字段

主机路径前缀：`/opt/astrbot/data`（容器内 `/AstrBot/data`）。

## Persona Lib

```text
plugin_data/astrbot_plugin_persona_lib/personas.json
```

形状：`{ "<stable_subject_id>": { kind, scope, subject_id, names, tags, attributes, appearance, personality, content, extra, ... } }`。

## Private Companion

```text
plugin_data/astrbot_plugin_private_companion/companions.json
```

形状：`{ "users": { "<user_id>": { ..., "companion_memory": { "items": [] } } } }`。

## Console Bridge 队列

```text
plugin_data/astrbot_plugin_console_bridge/queue.json
```

队列是 Console 与 AstrBot 的内部共享文件，使用跨进程锁和原子替换。任务内可含原始 UMO 和正文，因此不得下载或公开；`/api/messages/*` 只向有权限账号返回派生 `target_key`、脱敏 ID hint 与任务公开字段。
## 配置

- `cmd_config.json`
- `config/astrbot_plugin_textech_intent_config.json`
- `config/astrbot_plugin_web_search_config.json`
- `config/astrbot_plugin_persona_lib_config.json`
- `config/astrbot_plugin_private_companion_config.json`
- `config/astrbot_plugin_console_bridge_config.json`

## 备份与管理台

- JSON 写前备份：`data/backups/console_<timestamp>[_NN]/`；同秒写入也使用唯一目录，页面可逐文件/整快照回滚
- 控制台 DB：`/opt/textech-console/data/console.db`（账号、角色、Token 哈希、最多 20000 条脱敏写操作审计）
- 资料站：`/opt/textech-console/data/knowledge/`
