# textech-webae-api MCP

Bearer 调用本地 WebAE REST（默认 `http://127.0.0.1:8090`）。

## Tools

| Tool | API |
|------|-----|
| `webae_health` | `GET /api/server/health` |
| `webae_diagnostics` | `GET /api/server/diagnostics` |
| `webae_networks` | `GET /api/networks` |
| `webae_storage` | `GET /api/storage?network=` |
| `webae_recipes_search` | `GET /api/recipes/search?q=` |
| `webae_config` | 读 `textech.cfg` `[webConsole]`（无 HTTP） |

## Env

| 变量 | 作用 |
|------|------|
| `WEBAE_TOKEN` | Bearer token（优先） |
| `WEBAE_BASE_URL` | 覆盖 base URL |
| `WEBAE_INSTANCE_ROOT` / `TEXTECH_INSTANCE_ROOT` | 实例根（找 `TeXTech/WebAE/web-tokens.json` 与 cfg） |
| `WEBAE_TOKENS_FILE` / `WEBAE_CFG_FILE` | 直接指定文件路径 |

无 `WEBAE_TOKEN` 时，尝试读取 `run/TeXTech/WebAE/web-tokens.json` 中首个 owner token。

## 前置

游戏/服务端已启动且 `[webConsole] enabled=true`；用 `/admweb issue` 发过 token。
