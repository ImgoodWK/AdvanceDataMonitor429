# WebAE OpenComputers Integration

TeXTech WebAE runs inside the Minecraft server process. OpenComputers Internet Cards support **outbound HTTP only** — they cannot listen on a port in-game. OC scripts should poll WebAE read-only REST endpoints via `internet.request`.

## Prerequisites

1. Web console enabled (`config/textech/textech.cfg` → `[webConsole] enabled=true`).
2. At least one **Advance Data Monitor** bound to the player’s AE networks.
3. An owner token from either:
   - `/textech web issue` in-game (full UUID token), or
   - `/textech web login` (6-digit code exchanged in the browser — see [User Guide](user-guide.md)).
4. Firewall allows the configured WebAE port.

## Read-only summary API

```
GET /api/oc/summary?token=<owner_token>
Authorization: Bearer <owner_token>   # either form
```

**Rate limit**: 1 request per second per owner token; `429` with `retryAfterMs` when exceeded.

**Sample response** (< 4KB):

```json
{
  "success": true,
  "storageItemCount": 842,
  "cpuBusy": true,
  "activeOrders": 2,
  "tps": 19.8
}
```

| Field | Meaning |
|-------|---------|
| `storageItemCount` | Sum of distinct item types across cached storage snapshots |
| `cpuBusy` | Any CPU currently `isBusy` |
| `activeOrders` | In-flight Web craft orders for this owner |
| `tps` | Server TPS from `ServerHealthSampler` (same as `/forge tps` Overall) |

Missing or invalid token → `401`.

## OC Lua example

Adjust `WEBAE_HOST`, `WEBAE_PORT`, and `WEBAE_TOKEN` (never commit real tokens):

```lua
-- config.lua
return {
  host = "192.168.1.10",
  port = 8080,
  token = "your-owner-token-uuid",
  interval = 2  -- API allows 1 req/s; use >= 2
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

> **Note**: `internet.request` return shape varies by pack (string body vs `(code, body)`). See the [OC Internet API docs](https://ocdoc.cil.li/api:internet) if parsing fails.

## Login code flow (no OP token issue)

Players run `/textech web login` in-game for a 6-digit code and a `?code=` URL. The browser exchanges it via:

```
POST /api/auth/exchange
Content-Type: application/json

{"code":"123456"}
```

Success returns an owner `token` equivalent to `/textech web issue`. Codes expire in **5 minutes** and are **single-use**.

## Security

- Keep tokens in local OC `config.lua` only.
- When WebAE binds `127.0.0.1`, OC must run on the same host or use a trusted tunnel.
- This API is **read-only**; use the full Web console for writes.

## See also

- [Developer Guide](developer-guide.md) — full REST list
- [User Guide](user-guide.md) — `/textech web login` and guest tokens
