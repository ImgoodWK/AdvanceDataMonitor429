# Persona Console Bridge 1.2.0

## Persona Studio web preview

Bridge 1.2.0 accepts a draft only when the synthetic target is exactly `preview:local`, its kind is `preview`, and its UMO is empty. It generates a direct web answer with the selected main-Bot or shared Persona Lib persona. Preview jobs are never valid send jobs.

## Draft provider fallback

Draft generation reuses AstrBot `provider_settings.fallback_chat_models` in configured order. Each provider keeps the bounded `draft_timeout_seconds`, the whole chain is capped by `draft_total_timeout_seconds`, and `draft_max_provider_attempts` limits provider count. This retry is draft-only: confirmed QQ delivery remains single-attempt and becomes `uncertain` after an interrupted send.

Persona Console 2.3 与 AstrBot 之间的受控人格草稿和消息投递桥。插件只消费共享数据卷中的文件队列，不开放额外 HTTP 端口，也不保存新的平台密钥。

## 数据流

1. Console 只列出 Private Companion 已知且当前允许的私聊/群聊目标，并用派生 key 和脱敏 ID 提示展示。
2. 编辑或管理员提交草稿要求；本插件使用当前默认聊天 provider、主 Bot 人格、Private Companion 回复风格和 Persona Lib 共享人设生成草稿。
3. 草稿返回 Console 后必须由人审核或编辑。
4. 只有拥有 `messages.send` 的管理员同时确认目标并输入固定短语 `SEND`，Console 才会写入发送任务。
5. 本插件通过 Private Companion 的确认发送链投递，使装饰钩子、QQ 官方平台适配与平台历史落库保持一致。

队列路径：

```text
/AstrBot/data/plugin_data/astrbot_plugin_console_bridge/queue.json
```

Console 容器对应路径为 `/astrbot-data/plugin_data/astrbot_plugin_console_bridge/queue.json`。

## 安全边界

- 不接受任意 UMO、QQ 号或群号，只能选 Private Companion 当前已知会话。
- 群目标会再次执行 Private Companion whitelist/blacklist 规则。
- API 不返回原始 UMO 或完整目标 ID。
- 草稿和发送正文含疑似 Key、Token、密码或 Bearer 凭据时拒绝入队/出队。
- 每目标默认冷却 30 秒；全局默认 600 秒内最多 5 条确认投递。
- `sending` 状态失联或已进入确认投递后出现异常时标记 `uncertain`，不会自动重发。
- 日志只记录任务 ID 前缀、状态和投递路径，不记录消息正文或原始目标 ID。

## 配置

配置由 `_conf_schema.json` 管理。紧急停止网页投递时设置：

```json
{
  "send_enabled": false
}
```

关闭后草稿生成仍可使用。完全停止队列轮询则设置 `enabled=false` 并重启 AstrBot。

## 部署验证

- 日志出现 `[console_bridge] started`，且无持续异常。
- Console `/api/messages/targets` 未认证返回 401，授权用户只看到名称、类型、脱敏 hint 与派生 target key。
- 先只创建无害草稿并等待 `draft_ready`；部署回归不得使用正确的 `SEND` 确认触发真实 QQ 消息。
