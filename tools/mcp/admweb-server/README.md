# textech-admweb MCP（只读）

读本地 WebAE 运行时文件；**不**执行 RCON / 游戏命令。发 token 仍用游戏内 `/admweb issue`。

## Tools

| Tool | 作用 |
|------|------|
| `read_web_tokens` | 列出 `TeXTech/WebAE/web-tokens.json`（默认脱敏） |
| `read_webae_config` | 读 `config/textech/textech.cfg` 的 `[webConsole]` |

`reveal=true` 或环境变量 `WEBAE_REVEAL_TOKENS=1` 时返回完整 token（仅本地开发）。

路径发现与 `textech-webae-api` 相同（`run/`、`WEBAE_INSTANCE_ROOT` 等）。

## 刻意不做

**RCON / 游戏内命令桥接**（`/admweb issue|login|reload`）不在本服务器实现。计划要求先只读、避免无鉴权 RCON。发 token 仍在游戏内执行；本 MCP 只读已落盘的 `web-tokens.json`。
