# BetterQuesting dev fixtures (TeXTech)

GTNH 整合包任务数据快照，供本地 `runClient` / `runServer` 与 WebAE 任务书（`?page=quests`）调试。

## 来源

| 字段 | 值 |
|------|-----|
| 上游仓库 | [GT-New-Horizons-Modpack](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack) |
| 路径 | `config/betterquesting/` |
| 快照 commit | 见 `SOURCE.json` |
| BQ 模组版本 | `3.8.70-GTNH` + `GTNHLib 0.10.7`（dev；勿用 0.11.9，见 Gradle 工作流 FAQ） |

## 自动同步

`runClient` / `runServer`（含 Java 17 变体）启动前会执行 Gradle 任务 `syncDevBetterQuesting`，将本目录内容复制到 `run/config/betterquesting/`（`run/` 在 `.gitignore` 中，不提交）。

手动同步：

```powershell
.\gradlew.bat syncDevBetterQuesting
```

## 进游戏后加载任务

1. 启动 dev 客户端：`.\gradlew.bat runClient`
2. 进世界后（需 OP）执行：`/bq_admin default load`
3. 游戏内任务书或 WebAE `?page=quests` 验证

## 更新快照

从 GTNH 整合包 master 拉取最新任务文件并重写 `SOURCE.json`：

```powershell
powershell -ExecutionPolicy Bypass -File tools/dev/sync-betterquesting-from-gtnh.ps1
```

更新后请将 `dev-fixtures/betterquesting/` 与 `SOURCE.json` 一并提交 Git。

## 目录说明

| 路径 | 说明 |
|------|------|
| `DefaultQuests/` | 任务线 JSON（整合包同款） |
| `resources/` | BQ 资源包 |
| `questbook.cfg` | 任务书配置 |
| `Readme.md` | GTNH 官方任务开发说明（上游原文件） |
| `SOURCE.json` | 本仓库快照元数据（**不同步**到 `run/`） |
| `README-dev.md` | 本说明（**不同步**到 `run/`） |
