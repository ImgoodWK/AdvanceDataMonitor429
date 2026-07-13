---
name: textech-webae-full-build
description: >-
  Runs the full TeXTech WebAE delivery checklist: npm run build, verify
  assets/textech/webae/ bundle hash, perf-diagnostics gate for new /api routes,
  and remind game restart + Ctrl+F5. Use when editing webae-frontend/,
  webae/ Java API, WebAE pages, embedded web console, or when the user mentions
  WebAE build, frontend not updating in-game, or npm run build.
---

# TeXTech WebAE Full Build

端到端交付清单。规则兜底见 `.cursor/rules/webae-frontend-build.mdc`、`webae-perf-diagnostics.mdc`；本 Skill 负责执行与验收。

## 何时自动走本 Skill

- 本轮修改了 `webae-frontend/**` 或 `src/.../webae/**`（含 API / router / handler）
- 用户提到：WebAE、网页控制台、前端没生效、要 build 前端
- 宣称「WebAE 已修好 / 可验证」之前 **必须** 走完本清单

用户明确说「先别 build / 我自己 build」时跳过构建，但在总结中写明未 build。

## 清单

```
WebAE Delivery:
- [ ] 1. 前端改动 → npm run build
- [ ] 2. 核对 index.html 中 js/index-*.js 哈希已更新
- [ ] 3. 若新增/改动 /api/* → perf 门禁
- [ ] 4. 总结写明已 build + 提醒重启游戏 + Ctrl+F5
```

### 1. 构建

```powershell
Set-Location webae-frontend; npm run build
```

依赖缺失时先 `npm install`，再 build。失败则修好后重跑，勿留下过期产物。

网络/权限按 `.cursor/rules/agent-execution-permissions.mdc`。

### 2. 核对产物

检查 `src/main/resources/assets/textech/webae/index.html` 引用的 `js/index-*.js` 哈希是否相对改动前已变。

**禁止**手改该目录打包文件；以前端源码 + `npm run build` 为准。

### 3. 新 API 的 perf 门禁

若本轮新增或改动了 WebAE HTTP 路由，按 `webae-perf-diagnostics.mdc`：

1. 挂在 `WebApiRouter`（或手动 `recordHttp`）
2. 主线程采集 → `recordCollect`
3. 新 tick 阶段 → `endPhase`
4. 中英开发者手册端点表已更新（若适用）

仅改纯前端、无新 `/api/*` 时可跳过本步。

### 4. 完成定义

- `npm run build` 成功
- `index.html` 的 JS 哈希已更新（有前端改动时）
- 新 API 已接 perf（若适用）
- 向用户总结：**已 `npm run build`**；提醒**重启游戏**后浏览器 **Ctrl+F5**

## 不必构建

- 本轮未改 `webae-frontend/`（例如只改 `webae/` Java、文档、规则）且用户未要求 build
- 用户明确自行 build
