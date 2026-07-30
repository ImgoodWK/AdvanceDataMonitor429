# ADM ↔ 独立卡牌服务 Bridge V1

本页描述 ADM 模组侧保留的私密服务端联动。浏览器和普通玩家客户端不得直接持有 Bridge Token。

## 配置与默认状态

ADM 的 `[cardBattle]` 只读取：

- `externalApiBaseUrl`：例如 `http://127.0.0.1:8787`。
- `bridgeToken`：必须与独立服务的 `CARDBATTLE_BRIDGE_TOKEN` 完全一致。

任一值为空即为关闭；ADM 不自动启动卡牌服务，也不回退到本机文件发码。生产环境跨主机连接时应使用 HTTPS 或受保护的内网，并限制卡牌服务端口的来源地址。

所有 Bridge 请求携带：

```http
X-CardBattle-Bridge-Token: <shared-secret>
```

## ADM 当前调用

| 命令 | 请求 | 行为 |
|---|---|---|
| `/textech card status` | `GET /api/bridge/v1/status` | 验证地址、令牌和 V1 能力；不显示令牌 |
| `/textech card bind` | `POST /api/bridge/v1/bind-codes` | 发送玩家 UUID 与名称，返回短期绑定码 |

绑定请求体：

```json
{
  "mcUuid": "player-uuid",
  "mcName": "PlayerName"
}
```

## 奖励账本边界

独立服务预留了：

- `GET /api/bridge/v1/rewards?mcUuid=...`
- `POST /api/bridge/v1/rewards/:rewardId/confirm`

确认接口要求唯一 `deliveryId`，重复确认会返回 `alreadyConfirmed=true`。但 ADM 当前不会调用这两个接口，也不会生成或发放 Minecraft 物品；因此现阶段只有卡牌侧累计，不存在自动兑换。

启用发放前必须在 ADM 侧补齐并测试：

1. 服务端固定物品白名单与数量上限，拒绝任意注册名/NBT。
2. 先写世界级持久化 `deliveryId` 防重账本，再向玩家发放。
3. 背包满、玩家离线、重启和网络超时的可恢复队列。
4. 发放成功后才确认；重复响应和重试必须幂等。
5. 管理员查询、人工补偿和审计日志。

这些条件未全部满足前，奖励兑换保持关闭。
