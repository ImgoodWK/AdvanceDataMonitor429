# 奖励桥接契约（V2 stub）

卡牌服写入（不读 GTNH 物品 API）：

`{CARDBATTLE_DATA_DIR|TEXTECH_INSTANCE_ROOT/TeXTech/CardBattle}/pending-rewards/{ownerUuid}.json`

文件使用安全化 owner ID、临时文件和原子替换写入。未配置 MC 服务端或单人存档目录时，`CARDBATTLE_DATA_DIR` 就是独立奖励账本；每次战后选择会继续追加，重启服务不会清空。

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
      "source": {
        "runId": "...",
        "stageId": "stage_3a",
        "label": "MV 电压奖励缓存（待游戏内桥接）",
        "schemaVersion": 2,
        "rewardKey": "textech.cardbattle.voltage.mv.cache",
        "voltageTier": "MV",
        "delivery": "pending_bridge"
      }
    }
  ]
}
```

## HTTP

- `GET /api/rewards/pending` — 当前玩家列出 pending；大厅的「后端奖励仓库」直接展示这些条目
- `POST /api/rewards/:id/mark-claimed` — 仅供可信 MC 桥在**实际发放后**调用；除玩家 Bearer 外还必须提供 `X-CardBattle-Bridge-Token`
- 未配置 `CARDBATTLE_BRIDGE_TOKEN` 时，mark-claimed 返回 `503 bridge_not_configured`；令牌不匹配返回 403，网页不能自行吞掉奖励
- `GET /api/health` 的 `rewardDelivery` 返回 `standalone_accumulation` / `minecraft_shared_directory` 和桥接是否启用

`rewardKey` 是未来桥接的稳定主键；不要解析 `label` 或 `displayName` 决定奖励。每个 GTNH 电压档位都预留独立的 `textech.cardbattle.voltage.<tier>.cache` 键。当前 `items` 仍是兼容 V1 的泵占位映射，正式发放前必须在模组侧校验注册表名称与 meta。

卡组加卡、冒险能力和棋盘皮肤立即写入本次 `RunState`，不会进入物品 pending 队列；领奖响应的 `unlockedSkinIds` 由前端保存到本机皮肤库存。

## 模组桥接侧

- `/textech card claim` 或上线钩子：读队列 → 校验 `rewardKey` → 给 `EntityPlayer` 发 `ItemStack` → 带私密桥令牌 mark claimed
- 可选：耗电、电压回写卡牌服

对战帧路径与 MC tick **无同步**；仅文件/HTTP 队列交互。没有任何 MC 配置时，卡牌后端仍是完整可运行产品，奖励保持 pending 累计。
