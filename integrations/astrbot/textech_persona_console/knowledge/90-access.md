# 访问信息与凭据边界

## 服务地址

| 服务 | 地址 |
|------|------|
| TeXTech 管理台 | `http://<server>:6186` |
| AstrBot 面板 | `http://<server>:6185` |

## 凭据规则

- 本文档和资料站不保存用户名对应的真实密码、API Token、Session Secret 或 Provider Key。
- 本机 Agent 从 `TeXTech-qqbot/secrets/local.env` 取 SSH 等运维凭据，不在聊天中索要或回显。
- 管理台账号存在 `/opt/textech-console/data/console.db`；忘记密码时走受控重置，不把密码回写资料站。
- API Token 只在生成时交付一次，数据库只存哈希；调用方把 Token 放自己的安全凭据存储。
- `audit.view` 和 `backups.view` 默认授予只读角色；`backups.restore` 默认仅管理员拥有，也可由自定义角色显式配置。
- 网页消息权限拆为 `messages.view` / `messages.compose` / `messages.send`；预设 editor 可查看/生成草稿，只有 admin 可发送。
- 审计日志不记录请求正文或凭据；备份 API 只返回相对路径与大小，不提供配置内容下载。
- 回滚必须输入完整快照名确认，并会为当前线上文件创建新的安全快照。
- 消息目标 API 不返回原始 UMO 或完整 ID；确认投递必须匹配目标 key 并输入 `SEND`，`uncertain` 状态不得按失败任务自动重发。
- 6185/6186 应由安全组限制管理员来源；生产推荐 TLS 反向代理。
