# Reward Bridge Contract V2

Voltage rewards are queued under:

`{CARDBATTLE_DATA_DIR|TEXTECH_INSTANCE_ROOT/TeXTech/CardBattle}/pending-rewards/{ownerUuid}.json`

Owner IDs are filename-sanitized and the ledger is written through a temporary file plus atomic replacement. Without an MC server or single-player instance path, `CARDBATTLE_DATA_DIR` remains the complete standalone reward ledger and entries continue accumulating across restarts.

The V2 `source` object adds these stable fields:

```json
{
  "schemaVersion": 2,
  "rewardKey": "textech.cardbattle.voltage.mv.cache",
  "voltageTier": "MV",
  "delivery": "pending_bridge",
  "runId": "...",
  "stageId": "stage_3a",
  "label": "display text only"
}
```

A complete queue entry retains the V1-compatible envelope:

```json
{
  "id": "uuid",
  "createdAt": 0,
  "status": "pending",
  "items": [
    { "modid": "gregtech", "name": "gt.metaitem.01", "meta": 32612, "count": 32, "displayName": "MV Pump x32" }
  ],
  "source": {
    "schemaVersion": 2,
    "rewardKey": "textech.cardbattle.voltage.mv.cache",
    "voltageTier": "MV",
    "delivery": "pending_bridge",
    "runId": "...",
    "stageId": "stage_3a"
  }
}
```

Future Minecraft delivery must dispatch by `rewardKey`, never by parsing `label` or `displayName`. Every voltage tier reserves `textech.cardbattle.voltage.<tier>.cache`. The current item array is a V1-compatible pump placeholder and must be validated against the GTNH registry before real grants are enabled.

Deck cards, run powers, and board skins update `RunState` directly and do not enter the item queue. Claim responses return `unlockedSkinIds` for the frontend cosmetic inventory.

## HTTP

- `GET /api/rewards/pending` lists pending entries for the authenticated owner; the lobby renders this list as the backend reward vault.
- `POST /api/rewards/:id/mark-claimed` is for a trusted MC bridge only, after external delivery. It requires both owner Bearer authentication and `X-CardBattle-Bridge-Token`.
- With no `CARDBATTLE_BRIDGE_TOKEN`, mark-claimed returns `503 bridge_not_configured`; a mismatch returns 403. The browser cannot consume a reward.
- `GET /api/health` reports `standalone_accumulation` or `minecraft_shared_directory` plus bridge availability in `rewardDelivery`.

## Future Minecraft bridge

An in-game claim command or login hook should read the queue, validate each `rewardKey` against a server-owned registry, construct the `ItemStack`, grant it to the owner, and only then mark the entry claimed with the private bridge token. Match frames never synchronize with the Minecraft tick; the bridge remains file/HTTP based. With no MC configuration, the standalone game remains fully playable and rewards stay pending.
