# 运维

## 安全组

- `6185` AstrBot 面板
- `6186` TeXTech 管理台（建议仅管理员公网 IP）

## 常用命令

```bash
docker ps | grep -E 'astrbot|textech'
docker logs -f astrbot
docker logs -f textech-console
cd /opt/astrbot && docker compose restart
cd /opt/textech-console && docker compose up -d --build
```

## 管理台环境变量

- `CONSOLE_BOOTSTRAP_PASSWORD`：首次创建 admin 密码
- `SESSION_SECRET`：Cookie 签名密钥
- `ASTRBOT_DATA`：默认 `/astrbot-data`（挂载主机 data）

## 审计与备份回滚

- `GET /api/audit`：按账号、动作、资源路径或结果查询；审计只存元数据，不存请求正文、密码、Key 或 Token。
- `GET /api/backups`：列出 `data/backups/console_*` 中可回滚 JSON 的路径和大小，不返回文件内容。
- `POST /api/backups/restore`：需要 `backups.restore`，请求里的 `confirm` 必须与快照名一致；先校验全部 JSON 和路径，再备份当前目标并原子替换。
- 回滚配置后通常需要重启 AstrBot；先在管理台确认文件与审计结果，再使用受控重启按钮。


## 网页消息中心

- `GET /api/messages/targets` / `jobs`：需要 `messages.view`；目标只含派生 key、类型、显示名和脱敏 hint。
- `POST /api/messages/draft`：需要 `messages.compose`，仅创建 LLM 人格草稿任务，不发送。
- `POST /api/messages/send`：需要 `messages.send`，且 `confirm_target == target_key`、`confirm_phrase == "SEND"` 才入队。
- 紧急停发：把 `astrbot_plugin_console_bridge_config.json` 的 `send_enabled` 设为 `false` 后重启 AstrBot；草稿仍可生成。
- 部署回归只创建无害草稿并用错误确认验证 400/无 send job；未经明确授权，不得提交正确 `SEND` 做真实 QQ 测试。

## 改 bot 后同步资料

1. 完成补丁 / 重启
2. 调用 `POST /api/knowledge/changelog` 或管理台「追加变更日志」
3. 如有规则变更，编辑 `RULES.md` / 对应文档并保存
4. 本地执行 `node scripts/sync_knowledge.js`
