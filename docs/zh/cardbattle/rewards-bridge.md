# 奖励桥接契约（V1 stub）

卡牌服写入（不读 GTNH 物品 API）：

`{CARDBATTLE_DATA_DIR|TEXTECH_INSTANCE_ROOT/TeXTech/CardBattle}/pending-rewards/{ownerUuid}.json`

```json
{
  "entries": [
    {
      "id": "uuid",
      "createdAt": 0,
      "status": "pending",
      "items": [
        { "modid": "gregtech", "name": "gt.metaitem.01", "meta": 32611, "count": 64, "displayName": "LV Pump x64" }
      ],
      "source": { "runId": "...", "stageId": "stage_1", "label": "对标奖励" }
    }
  ]
}
```

## HTTP

- `GET /api/rewards/pending` — 列出 pending
- `POST /api/rewards/:id/mark-claimed` — **仅标记 claimed**，不发物品

## 日后模组侧（未实现）

- `/textech card claim` 或上线钩子：读队列 → 给 `EntityPlayer` 发 `ItemStack` → mark claimed
- 可选：耗电、电压回写卡牌服

对战帧路径与 MC tick **无同步**；仅文件/HTTP 队列交互。
