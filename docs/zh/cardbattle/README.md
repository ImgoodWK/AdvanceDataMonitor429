# 独立卡牌服务联动

卡牌战斗的前端、Node/TypeScript 后端、游戏规则、资产、账号和运行数据已经从 ADM 模组仓库分离。ADM 不再内嵌卡牌 HTTP 服务，不在进档时启动卡牌进程，也不再把 SPA 或卡面打入模组 jar。

卡牌游戏的实现、资产与设计文档以独立仓库 [TeXTech: Overclocked Arcana](https://github.com/ImgoodWK/TeXTech-Overclocked-Arcana) 为权威来源；ADM 只维护 Minecraft 联动适配器。

## ADM 保留的能力

- `[cardBattle] externalApiBaseUrl`：独立卡牌服务地址。
- `[cardBattle] bridgeToken`：与独立服务 `CARDBATTLE_BRIDGE_TOKEN` 一致的私密共享密钥。
- `/textech card status`：通过 `GET /api/bridge/v1/status` 检查桥接服务。
- `/textech card bind`：为当前玩家调用 `POST /api/bridge/v1/bind-codes` 生成绑定码。

两个配置值任一为空时，桥接保持关闭。旧的 `enabled`、`port`、`bindAddress`、`devToken`、`serverDir`、`frontendDir` 和 `nodePath` 不再读取；旧 cfg 中残留这些键不会启动任何服务。

## 暂未启用

卡牌服务已经可以保存待领取奖励账本，但 ADM 尚未拉取或发放物品。物品白名单、背包满处理、世界侧幂等账本和管理员审计未完成前，不得开启自动兑换。完整边界见[奖励桥](rewards-bridge.md)。
