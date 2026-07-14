# BetterQuesting dev fixtures (TeXTech)

GTNH 整合包任务数据快照，供本地 `runClient` / `runServer` 与 WebAE 任务书（`?page=quests`）调试。

## 来源

| 字段 | 值 |
|------|-----|
| 上游仓库 | [GT-New-Horizons-Modpack](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack) |
| 路径 | `config/betterquesting/` |
| 快照 commit | 见 `SOURCE.json` |
| BQ 模组版本 | `3.8.72-GTNH` + `GTNHLib 0.11.24` + `ModularUI2 2.3.79` + `GT5 5.09.54.20`（GTNH 2.9.0-beta-2） |

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
| `DefaultQuests/` | 任务线 JSON（整合包同款 + 本仓库自定义章） |
| `resources/` | BQ 资源包 |
| `questbook.cfg` | 任务书配置 |
| `Readme.md` | GTNH 官方任务开发说明（上游原文件） |
| `SOURCE.json` | 本仓库快照元数据（**不同步**到 `run/`） |
| `README-dev.md` | 本说明（**不同步**到 `run/`） |

## WebAE Test Lab（本仓库自定义章）

首 Tab **WebAE Test Lab**（`QuestLinesOrder.txt` 第一行）覆盖 WebAE 任务书 DETECT / SUBMIT（物品 + 流体）、直接/选择奖励、勾选框与仅游戏内合成（`IN_GAME_ONLY`）对照。

| 存放位置 | `dev-fixtures/betterquesting/DefaultQuests/`（`Quests/` + `QuestLines/` + `QuestLinesOrder.txt`） |
|----------|--------------------------------------------------------------------------------------------------|
| 是否进 Git | **是** — 新电脑 `git clone` / `pull` 后即有；经 `syncDevBetterQuesting` 复制到 `run/config/betterquesting/` |
| 是否进模组 jar | **否** — 不在 `src/main/resources`；打包给客户端/整合包不会带上本章 |
| UUID 区间 | `questLineIDHigh=0x54455854`（`TEXT`），`questIDLow` 从 `0x57454201` 起 |

加载步骤与上文相同：`syncDevBetterQuesting`（或 `runClient`）→ 进世界 OP → `/bq_admin default load` → 游戏内任务书首 Tab / WebAE `?page=quests`。

从上游 `sync-betterquesting-from-gtnh.ps1` 刷新快照会覆盖 `DefaultQuests/`：请**保留**本仓 `WebAETestLab-*` 目录，并保证 `QuestLinesOrder.txt` **第一行**仍是 `WebAE Test Lab`（可用 Git 对比后手工重合并）。

消耗型物品任务写法为 `bq_standard:retrieval` + `consume:1`；WebAE 将该组合标为 **SUBMIT**（勿再用 DETECT/`completeRetrieval` 误完成）。
