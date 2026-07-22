# TeXTech 卡牌对战 UI 设计

精品化前端（`cardbattle-frontend/`）的视觉与交互约定。规则逻辑见 [rules.md](./rules.md)。

## 视觉方向

- **UI 壳**：统一像素扁平 GTNH 风格（深色金属底、2px 描边、像素阴影）
- **棋盘皮肤**：LoR 式可解锁战场背景（`src/lib/skins.ts`）
  - `gt_factory` 格雷工厂（默认）
  - `thaum_workshop` 神秘工坊（累计胜 2）
  - `astral_observatory` 星辉观象台（累计胜 5）
- **卡面**：按 10 主题各自风格；费用/攻血/关键词由 UI 叠加，不烘焙进图

## 技术栈

| 库 | 用途 |
|----|------|
| Framer Motion | 组件挂载、手牌扇形、选中抬起、布局过渡 |
| `@use-gesture/react` | 指针拖拽出牌 |
| GSAP | 战斗结算时序（冲击环、像素尘、槽位闪白） |

## 交互

1. **拖拽出牌**（主阶段）：手牌按下 → 拖到己方空槽 / 法术可拖敌方 → 合法金绿高亮 / 非法红边 → 松手发 `play_card`
2. **点击 fallback**：先点手牌再点槽位或「出到槽 N」按钮
3. **攻击声明**：点击己方非结构单位勾选，再确认攻击

## 动画清单

| 事件 | 表现 |
|------|------|
| 阶段切换 | 顶部 `PhaseBanner` 滑入 |
| Nexus 掉血 | 浮动伤害数字 + 震屏 + 红色 vignette |
| Nexus 回血 | 绿色浮动数字 |
| 场面变化 | GSAP 冲击环 + 像素尘 |
| 单位离场 | 槽位亮度闪白 |
| 胜负 | 全屏遮罩 + 奖励选择 |

## 卡面资产

- 运行时：`cardbattle-server/public/card-art/<id>.png`（Node 原型）与模组 jar `assets/textech/cardbattle/card-art/`（生产）
- 生成：`.cursor/skills/textech-card-art/` + Meowa `image-2-run`
- 试点：gt + thaum 共 20 张已设 `CardDef.art`

## 设计令牌

见 `cardbattle-frontend/src/lib/themeTokens.ts`（10 主题色板）与 `src/styles/{app,themes,skins}.css`。
