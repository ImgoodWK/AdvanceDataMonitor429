# ADM ↔ standalone Card Battle Bridge V1

This page defines the private server-to-server integration retained by ADM. Browsers and ordinary player clients must never receive the Bridge Token.

## Configuration and safe default

ADM reads only these `[cardBattle]` settings:

- `externalApiBaseUrl`, for example `http://127.0.0.1:8787`.
- `bridgeToken`, exactly matching the standalone service's `CARDBATTLE_BRIDGE_TOKEN`.

The bridge is disabled when either value is empty. ADM does not start the service and does not fall back to writing bind codes into local files. For cross-host production deployments, use HTTPS or a protected private network and restrict access to the Card Battle port.

Every Bridge request carries:

```http
X-CardBattle-Bridge-Token: <shared-secret>
```

## Calls currently made by ADM

| Command | Request | Behavior |
|---|---|---|
| `/textech card status` | `GET /api/bridge/v1/status` | Verifies the URL, token, and V1 capability without displaying the token |
| `/textech card bind` | `POST /api/bridge/v1/bind-codes` | Sends the player's UUID and name and returns a short-lived bind code |

Bind request body:

```json
{
  "mcUuid": "player-uuid",
  "mcName": "PlayerName"
}
```

## Reward-ledger boundary

The standalone service reserves:

- `GET /api/bridge/v1/rewards?mcUuid=...`
- `POST /api/bridge/v1/rewards/:rewardId/confirm`

Confirmation requires a unique `deliveryId`; a repeated confirmation returns `alreadyConfirmed=true`. ADM currently calls neither endpoint and creates or grants no Minecraft item. Rewards therefore accumulate only in the Card Battle ledger.

Before delivery can be enabled, ADM must implement and test:

1. A server-owned item allowlist and quantity limits that reject arbitrary registry names and NBT.
2. A persistent world-level `deliveryId` ledger written before granting an item.
3. A recoverable queue for full inventories, offline players, restarts, and network timeouts.
4. Confirmation only after successful delivery, with idempotent retries and duplicate responses.
5. Administrator inspection, manual compensation, and audit logs.

Reward redemption remains disabled until every condition is satisfied.
