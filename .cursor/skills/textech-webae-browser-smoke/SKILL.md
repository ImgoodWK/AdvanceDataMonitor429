---
name: textech-webae-browser-smoke
description: >-
  Runs a TeXTech WebAE browser smoke checklist with cursor-ide-browser against
  Vite :5174 or embedded :8090. Use when the user @-mentions this skill or asks
  for WebAE UI smoke test, visual verification, or browser acceptance.
disable-model-invocation: true
---

# TeXTech WebAE Browser Smoke

按需 `@textech-webae-browser-smoke`。使用内置 **`cursor-ide-browser`** MCP；不替代 `npm test`。

## 前置

- MC 服务端 `[webConsole] enabled=true`（默认 `127.0.0.1:8090`）
- 有效 Token：游戏内 `/admweb issue`，或 6 位登录码
- 推荐双终端：
  1. `gradlew runServer`（或整合包服务端）
  2. `cd webae-frontend && npm run dev`（`:5174`，API 代理到 `:8090`）

也可用已 build 的嵌入页：`http://127.0.0.1:8090/?token=...`（需游戏已加载最新产物）。

## 清单

```
Browser Smoke:
- [ ] 1. browser_tabs list → 决定新建或复用 tab
- [ ] 2. browser_navigate 到目标 URL（带 token 或走登录）
- [ ] 3. browser_lock → snapshot → 关键路径
- [ ] 4. 关键截图（关键页）
- [ ] 5. browser_lock unlock
```

### 推荐 URL

- Dev：`http://localhost:5174?token=<TOKEN>`
- Prod 嵌入：`http://127.0.0.1:8090?token=<TOKEN>`

### 建议点击路径

1. 登录成功（或 token 直入）
2. 侧栏：Dashboard → Storage（或 Recipes）→ Settings → **性能诊断 / Diagnostics**
3. 公开只读：确认页面无白屏、控制台无致命错误（能看到的范围内）

### 工作流约束（browser MCP）

- 已有 tab：先 `browser_lock` 再交互
- 新会话：`browser_navigate` → `browser_lock` → 交互 → `unlock`
- 失败 4 次或卡住：停止并报告观察结果，勿死循环
- 登录/权限阻塞：交给用户手动处理

## 能测 / 不能测

| 能测 | 不能测（勿强行） |
|------|------------------|
| 登录 UI、侧栏导航、表单、Diagnostics | `/admweb *` 命令 |
| 公开 `GET` 驱动的只读页 | 配方/图标客户端上传、地图 consent |
| 截图视觉回归 | AE 下单 / 样板注入实写（除非用户明确要求且环境安全） |

## 完成后

向用户报告：URL、通过的页面、失败点、截图结论。若本轮改过前端源码，确认已走 `@textech-webae-full-build`（或已 `npm run build`）。
