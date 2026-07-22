# TeXTech 卡牌对战

与 WebAE 同属模组内容；**内嵌于模组 jar**（NanoHTTPD），**玩家/服主不需要安装 Node.js**。  
进入存档（含单人）自动启动；退出存档自动停止。

## 玩家用法（装模组即可）

1. 安装 TeXTech（默认 `[cardBattle] enabled=true`）。
2. 进入单人档或服务器世界。
3. 浏览器打开 `http://127.0.0.1:8787/`（日志也会打印）。
4. 页面会自动用本机 Token `local` 登录；也可改粘贴 WebAE Token。

游戏内：`/textech card status`。

## 对战体验（精品化 UI）

- **像素扁平 GTNH** 统一 UI 壳；棋盘可换皮肤（默认「格雷工厂」，胜场解锁「神秘工坊」「星辉观象台」）
- **拖拽出牌**：主阶段将手牌拖到场面空槽；法术可拖向敌方；保留点击出牌 fallback
- **全程动画**：阶段横幅、Nexus 震屏/伤害数字、出牌冲击、胜负遮罩（Framer Motion + GSAP）
- 细节见 [ui-design.md](./ui-design.md)

## 配置 `[cardBattle]`

| 键 | 默认 | 说明 |
|----|------|------|
| enabled | true | 进档自动启动 |
| port | 8787 | HTTP 端口 |
| bindAddress | 127.0.0.1 | 仅本机；局域网需改 `0.0.0.0` 并自行做好防火墙 |
| devToken | local | 本机旁路登录；清空则强制 WebAE Token |

## 开发者

- Java：`com.imgood.textech.cardbattle.*`
- 前端：`cardbattle-frontend/` → `npm run build` → `assets/textech/cardbattle/`
- 卡表：`assets/textech/cardbattle/cards.json`
- 卡面：`cardbattle-server/public/card-art/`（开发）与 jar `assets/textech/cardbattle/card-art/`
- 仓库内 `cardbattle-server/` 仅为早期 Node 原型，**运行时不使用**

规则 / 奖励 / UI：[rules.md](./rules.md) · [rewards-bridge.md](./rewards-bridge.md) · [ui-design.md](./ui-design.md)
