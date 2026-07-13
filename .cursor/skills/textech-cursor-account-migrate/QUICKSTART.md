# Cursor 账号迁移 — 一页纸

换号后在本仓库聊天里发：

```text
@textech-cursor-account-migrate 我已换到新 Cursor 账号，按清单帮我恢复
```

## 你自己只需做的

1. **push 本仓库**（确保 `.cursor/` 与 `tools/mcp/` 在远程）  
2. 新账号登录 Cursor，**打开同一本地文件夹**  
3. **Reload MCP**  
4. 若要用 Automation / PR 评论：新账号接好 **GitHub**  
5. 对 Agent 说「下一条」重开 3 条 Automation（一次保存一条）

## 不用搬的

- `.cursor/mcp.json`、Skill、规则、CI → 跟 git  
- 游戏 Token / `run/` 数据 → 跟本机实例，不是 Cursor 账号  
