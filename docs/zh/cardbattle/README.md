# TeXTech 卡牌对战

卡牌对战现在有两个互不依赖的运行入口：

- **独立部署（推荐）**：`cardbattle-server/` 是 Node/TypeScript 权威后端，可直接部署到普通服务器或 Docker，完全不需要 Minecraft、Forge 或 MC tick。
- **模组内嵌**：Java 镜像仍可随 TeXTech jar 在单人存档/服务器世界内启动，方便本地整合。

独立后端会把冒险、对局和待桥接奖励写入 `CARDBATTLE_DATA_DIR`；浏览器保存当前 `runId` / `matchId`，刷新页面或重启后端后均可继续。

## 独立部署

```powershell
cd cardbattle-frontend
npm.cmd ci
npm.cmd run build:standalone
cd ../cardbattle-server
npm.cmd ci
npm.cmd run build
npm.cmd start
```

生产环境可在仓库根目录执行 `docker build -f cardbattle-server/Dockerfile -t textech-cardbattle .`，或使用 `cardbattle-server/docker-compose.example.yml`。默认地址为 `http://127.0.0.1:8787/`；容器内默认监听 `0.0.0.0`。

## 模组内玩家用法

1. 安装 TeXTech（默认 `[cardBattle] enabled=true`）。
2. 进入单人档或服务器世界。
3. 浏览器打开 `http://127.0.0.1:8787/`（日志也会打印）。
4. 页面会自动用本机 Token `local` 登录；也可改粘贴 WebAE Token。

游戏内：`/textech card status`。

## 对战体验（精品化 UI）

- **开局调度**：四张起手牌可任意选择替换；先从牌库补牌，再把换下的牌洗回，避免立即抽回
- **首轮正常抽牌**：双方确认调度后各再抽 1 张，首轮主行动以 5 张手牌开始
- **抽牌边界**：手牌最多 10 张，超出会爆牌进弃牌堆；空牌库再次抽牌立即落败
- **交替行动权**：单位、结构、慢速/快速法术会交出行动权；爆发法术立即生效并保留行动权；主行动双方连续放弃才结束轮次
- **三档法术速度**：慢速只能在主行动空栈发起，快速可加入主行动或战斗响应栈，爆发不入栈
- **法术栈与攻击响应**：公开显示栈顶和施法者，双方连续放弃响应后按后进先出结算；格挡完成后先开放法术响应，再结算战斗
- **攻击标记轮换**：每轮只有标记持有者可发起一次攻击，下一轮默认换边
- **法术法力**：每轮最多储存 3 点未使用法力，只能用于法术；GT 电容库存是独立的模组机制
- **结算边界**：普通法力优先支付，随后使用法术法力与 GT 储能；存活单位在新轮次恢复至最大生命；已声明的格挡者离场后保留幽灵格挡
- **像素扁平 GTNH** 统一 UI 壳；棋盘可按胜场或 PvE 首领奖励解锁
- **拖拽出牌**：主阶段将手牌拖到场面空槽；响应窗口只接受快速/爆发法术；保留点击出牌 fallback
- **完整预览**：悬停、键盘聚焦或点选卡牌可查看服务端权威的精确效果、速度、目标限制、关键词和场上实时数值
- **逐卡攻击动画**：攻击单位按队列逐张冲向格挡者或 Nexus，播放斩击闪光、伤害数字、回弹与死亡反馈（Framer Motion + GSAP）
- **分支 PvE 路线**：普通战斗 → 分支 → 精英 → 终局首领；战后选择加卡、冒险能力或电压奖励缓存
- 细节见 [ui-design.md](./ui-design.md)

## 独立后端环境变量

| 变量 | 默认 | 说明 |
|---|---|---|
| `CARDBATTLE_HOST` | `127.0.0.1` | HTTP 监听地址；容器使用 `0.0.0.0` |
| `CARDBATTLE_PORT` | `8787` | HTTP 端口 |
| `CARDBATTLE_DATA_DIR` | `./data/runtime` | 冒险、对局和奖励账本持久化目录 |
| `CARDBATTLE_FRONTEND_DIR` | 自动探测 | 独立 SPA 构建目录 |
| `CARDBATTLE_DEV_TOKEN` | 无 | 独立账号令牌；生产必须设置强随机值 |
| `CARDBATTLE_CORS_ORIGINS` | 空 | 允许的浏览器 Origin，多个值用逗号分隔 |
| `CARDBATTLE_BRIDGE_TOKEN` | 空 | 可信 MC 发奖桥私密令牌；未配置时奖励只累计 |

## 模组内配置 `[cardBattle]`

| 键 | 默认 | 说明 |
|----|------|------|
| enabled | true | 进档自动启动 |
| port | 8787 | HTTP 端口 |
| bindAddress | 127.0.0.1 | 仅本机；局域网需改 `0.0.0.0` 并自行做好防火墙 |
| devToken | local | 本机旁路登录；清空则强制 WebAE Token |

## 开发者

- 独立权威后端：`cardbattle-server/`
- 可选 Java 镜像：`com.imgood.textech.cardbattle.*`
- 前端：`cardbattle-frontend/`；`build:standalone` 输出独立 SPA，`build` 输出 jar 资源
- 权威卡表：`cardbattle-server/src/data/catalog.ts`；`npm.cmd run catalog:export` 同步 jar `cards.json`
- 卡面：`cardbattle-server/public/card-art/`（开发）与 jar `assets/textech/cardbattle/card-art/`
独立 Node 引擎与可选 Java 镜像必须保持同一 HTTP/状态/奖励契约。交互与规则借鉴交替行动权、攻击标记、法术响应栈和路线冒险等通用结构；角色、卡牌文本、美术与品牌素材均使用 TeXTech 自有设计。

规则 / 奖励 / UI：[rules.md](./rules.md) · [rewards-bridge.md](./rewards-bridge.md) · [ui-design.md](./ui-design.md)
