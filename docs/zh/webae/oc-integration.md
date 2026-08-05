# WebAE 与 OpenComputers 集成

TeXTech WebAE 内嵌于 Minecraft 服务端进程，OpenComputers 的 Internet Card **只能出站 HTTP**，不能在游戏内监听端口。因此 OC 脚本应通过 `internet.request` 轮询 WebAE 的只读 REST API。

## 前置条件

1. 服务端已启用 Web 控制台（`config/textech/textech.cfg` → `[webConsole] enabled=true`）。
2. 玩家至少绑定一块 **高级数据监视器**（Advance Data Monitor）。
3. 获取 owner Token 之一：
   - 游戏内 `/textech web issue`（完整 UUID Token），或
   - `/textech web login`（6 位登录码 → 浏览器兑换为 Token，见 [用户手册](用户手册.md)）。
4. 防火墙放行 Web 控制台端口（默认见配置 `port`）。

## 只读摘要 API

```
GET /api/oc/summary?token=<owner_token>
Authorization: Bearer <owner_token>   # 二选一
```

**限流**：每个 owner Token **1 次/秒**；超出返回 `429`，body 含 `retryAfterMs`。

**响应示例**（< 4KB，适合 OC Lua 解析）：

```json
{
  "success": true,
  "storageItemCount": 842,
  "cpuBusy": true,
  "activeOrders": 2,
  "tps": 19.8
}
```

| 字段 | 含义 |
|------|------|
| `storageItemCount` | 各网络缓存快照中物品种类数之和（非 AE 总堆叠） |
| `cpuBusy` | 任一 CPU 当前 `isBusy` |
| `activeOrders` | 该 owner 进行中的 Web 下单数 |
| `tps` | 服务端 TPS（`ServerHealthSampler`，与 `/forge tps` Overall 同口径） |

无 Token 或无效 Token → `401`。

## OC Lua 示例

将 `WEBAE_HOST`、`WEBAE_PORT`、`WEBAE_TOKEN` 改为你的环境（Token 勿提交到公开仓库）：

```lua
-- config.lua（与 OC 电脑同目录）
return {
  host = "192.168.1.10",
  port = 8080,
  token = "your-owner-token-uuid",
  interval = 2  -- 秒；API 限流 1/s，建议 >= 2
}
```

```lua
-- webae_summary.lua
local cfg = require("config")
local internet = require("internet")

local url = string.format(
  "http://%s:%d/api/oc/summary?token=%s",
  cfg.host, cfg.port, cfg.token
)

while true do
  local ok, resp = pcall(function()
    return internet.request(url)
  end)
  if ok and resp then
    local count = resp:match('"storageItemCount":(%d+)')
    local busy = resp:match('"cpuBusy":(%a+)')
    local orders = resp:match('"activeOrders":(%d+)')
    local tps = resp:match('"tps":([%d%.]+)')
    print(string.format(
      "AE items=%s cpuBusy=%s orders=%s tps=%s",
      count or "?", busy or "?", orders or "?", tps or "?"
    ))
  else
    print("WebAE request failed")
  end
  os.sleep(cfg.interval or 2)
end
```

> **说明**：Internet Card 的 `internet.request` 在不同 GTNH 版本上返回值可能是 body 字符串或 `(code, body)` 元组；若你的 pack 行为不同，请按 [OC Wiki Internet API](https://ocdoc.cil.li/api:internet) 调整解析。

## 登录码流程（无 OP 发 Token）

普通玩家可在游戏内执行 `/textech web login`，获得 6 位数字码与带 `?code=` 的 URL。在浏览器打开后自动兑换为 owner Token，**无需 OP 执行 `/textech web issue`**。

```
POST /api/auth/exchange
Content-Type: application/json

{"code":"123456"}
```

成功返回 `token` 字段，与 `/textech web issue` 签发的 owner Token 等价。码 **5 分钟 TTL、单次使用**。

## 安全建议

- 仅将 `token` 保存在 OC 电脑本地或受控 `config.lua` 中。
- 生产环境 WebAE 绑定 `127.0.0.1` 时，OC 需与 MC 同机或通过受信隧道访问。
- 本 API **只读**；下单、样板编辑等写操作请使用完整 Web 控制台或其他自动化路径。

## 相关文档

- [开发者手册](开发者手册.md) — 完整 REST 列表
- [用户手册](用户手册.md) — `/textech web login` 与访客 Token
