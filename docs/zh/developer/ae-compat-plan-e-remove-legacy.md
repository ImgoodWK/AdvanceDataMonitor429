# Plan E — 移除 Legacy AE 兼容（仅保留 GTNH 2.9.0+）

> **用途**：在 GTNH 2.9.0 成为模组**最低支持版本**后，单独开一轮 Agent 对话执行本计划，删除双路径中的 Legacy / GlodBlock 分支。  
> **不要**与 Plan A–D（已实现双路径）混淆；本计划为**不可逆**清理。  
> **执行开场白**：「继续 GTNH 2.9 AE 兼容，执行 Plan E — 移除 Legacy 支持」

**相关文档**：[ae-compat-290.md](ae-compat-290.md)（当前双路径说明）

---

## 前置条件（执行前必须满足）

- [ ] 模组 README / 发布说明已声明最低 GTNH **2.9.0 beta-1+**
- [ ] 已在 **2.9.0-beta 真实实例**完成 Native 路径回归：
  - 网络链接器字节/类型统计
  - AI：`QUERY_BYTES`、样板流体详情、`QUERY_STORAGE` 流体
  - 流体 loom cell：Workbench 分区、编织、ME 网络通知
- [ ] 已具备 dev jar 并完成 API 对照（见 `.workspace/ae2_290_api_diff.md`）：
  - `Applied-Energistics-2-Unofficial:rv3-beta-977-GTNH`
  - `AE2FluidCraft-Rework:1.5.88-gtnh`

---

## 目标架构（完成后）

```
AdvanceDataMonitor.postInit
    └── AeCompat（单路径，无 profile 分支）
            ├── AeCellStatsAdapter      → 唯一实现
            ├── AeFluidMarkerAdapter
            ├── AePatternFluidAdapter
            └── AeFluidCellConfigFactory
                    └── 调用方不变：NetworkLink / DataMonitor / Assistant / DataLoom
```

**删除**：`compat/ae/legacy/`、`GtnhEnvironmentProbe`、`GtnhVersion`、`AeCompatProfile`、`AeCompatDetectionSource`、`[compat] aeProfileOverride`、业务层全部 `com.glodblock.*` import。

**保留（建议）**：`compat/ae/` 接口 + 薄门面 `AeCompat`，便于单测；可选 Step 7 再内联。

---

## Step 1 — 升级构建依赖

**文件**：[`dependencies.gradle`](../../dependencies.gradle)

| 变更 | 从 | 到 |
|------|----|----|
| AE2FC | `1.3.7-gtnh` | `1.5.88-gtnh` |
| AE2 显式 | 无 | `Applied-Energistics-2-Unofficial:rv3-beta-977-GTNH:dev` |

```bash
gradlew compileJava
```

根据 ae2fc 1.5.x / AE2 rv3-beta-977 的 API 变更修复编译（重点：`FluidCellConfig`、`IStorageFluidCell` 包名是否迁入 `appeng.*`）。

**验收**：在尚未删 Legacy 代码前，先确认新依赖栈能编译（或与本 Step 同步删 Legacy 后一次修完）。

---

## Step 2 — 删除 Legacy 包与版本探测

### 整目录删除 `compat/ae/legacy/`（5 文件）

- `LegacyAeCellStatsAdapter.java`
- `LegacyAeFluidMarkerAdapter.java`
- `LegacyAePatternFluidAdapter.java`
- `LegacyAeFluidCellConfigFactory.java`

### 整文件删除（探测层，共 4 文件）

| 文件 | 路径 |
|------|------|
| `GtnhEnvironmentProbe.java` | `compat/ae/` |
| `GtnhVersion.java` | `compat/ae/` |
| `AeCompatProfile.java` | `compat/ae/` |
| `AeCompatDetectionSource.java` | `compat/ae/` |

---

## Step 3 — 简化 `AeCompat` 门面

**文件**：[`AeCompat.java`](../../../src/main/java/com/imgood/advancedatamonitor/compat/ae/AeCompat.java)

- 删除 `bindAdapters()`、`profile()`、`detectionSource()`、`detectionDetail()`、`isNativeFluid()`
- 删除对 `GtnhEnvironmentProbe` 的调用
- 四个 adapter 字段**静态绑定**为 Native 实现（Step 4 重命名后的类）
- `init()` 可简化为 `initialized = true` + 可选一行 `LOG.info("[ADM] AE compat: native fluid (2.9.0+)")`

[`AdvanceDataMonitor.postInit`](../../../src/main/java/com/imgood/advancedatamonitor/AdvanceDataMonitor.java) 可保留或移除 `AeCompat.init()`。

---

## Step 4 — 合并 `native_` 为默认实现

将 [`compat/ae/native_/`](../../../src/main/java/com/imgood/advancedatamonitor/compat/ae/native_/) 4 个类**上移**到 `compat/ae/` 并重命名（示例）：

| 现类 | 建议新名 |
|------|----------|
| `NativeAeCellStatsAdapter` | `AeCellStatsAdapterImpl` |
| `NativeAeFluidMarkerAdapter` | `AeFluidMarkerAdapterImpl` |
| `NativeAePatternFluidAdapter` | `AePatternFluidAdapterImpl` |
| `NativeAeFluidCellConfigFactory` | `AeFluidCellConfigFactoryImpl` |

删除 `compat/ae/native_/` 目录。

### 必须内联的 Legacy 回退（当前 Native 仍引用 Legacy）

| 位置 | 处理 |
|------|------|
| `NativeAeCellStatsAdapter` → `LegacyAeCellStatsAdapter.isInfiniteCell` | 移入 `AeCellStatsAdapterImpl` 或新建 `AeInfiniteCellUtil` |
| `NativeAeFluidMarkerAdapter` → `LegacyAeFluidMarkerAdapter.*` | NBT 解析 + AE2 util 内联到 `AeFluidMarkerAdapterImpl` |
| `NativeAePatternFluidAdapter` → `LegacyAePatternFluidAdapter.appendFluidValue` | 移入 `AePatternFluidAdapterImpl` 或 `AePatternFluidUtil` |
| `NativeAeFluidCellConfigFactory` → `LegacyAeFluidCellConfigFactory` | 删除回退，仅 `NativeDataLoomFluidCellConfig` 或合并后的 config |

---

## Step 5 — 编织元件：移除 GlodBlock 业务耦合

| 文件 | 操作 |
|------|------|
| `IDataLoomFluidCell.java` | `extends IStorageFluidCell` 改为 AE2 2.9.0 原生接口（反编译确认 FQCN） |
| `AbstractDataLoomFluidCell.java` | 更新 `isStorageCell()` 注释；确认 2.9.0 行为 |
| `DataLoomFluidCellConfig.java` / `NativeDataLoomFluidCellConfig.java` | 合并为单一 config 类，继承 AE2 2.9.0 分区基类 |
| `DataLoomCellCapacity.java` | 核对 mB/byte、types divisor |

**勿删**（与 AE Legacy 无关）：

- `TileEntityAdvanceStorageLink.LEGACY_MARKED_ITEMS_TAG` — NBT 存档迁移
- `AiClientPreferences` 的 `legacyKey` — 客户端配置迁移

---

## Step 6 — 配置与文档

| 文件 | 操作 |
|------|------|
| `Config.java` | 删除 `compatAeProfileOverride` |
| `config/ConfigCompatLoader.java` | **删除文件** |
| `Config.java` `synchronizeConfiguration` | 移除 `ConfigCompatLoader.load` |
| `ConfigDescriptions.java` | 删除 `[compat]` 条目 |
| `docs/zh/developer/ae-compat-290.md` | 改写为「仅 2.9.0+」或归档 |
| `docs/en/developer/ae-compat-290.md` | 同上 |
| `docs/zh/ai-assistant/开发指南.md` | 删除 Legacy/GlodBlock 双路径描述 |
| `docs/en/ai-assistant/development-guide.md` | 同上 |
| `.cursor/rules/project-structure.mdc` | 更新 `compat/` 计数；删 `legacy/`、`native_/` |

---

## Step 7（可选）— 完全去掉 compat 分层

若不再需要 mock/切换：

- 删除 `AeCompat.java` 与四个 interface
- 业务类直接 `import ...AeCellStatsAdapterImpl.INSTANCE`

**建议**：Plan E 先保留薄门面；内联留作后续小 PR。

---

## 验收清单

- [ ] `gradlew compileJava` 成功
- [ ] `grep -r "com.glodblock" src/` 为零
- [ ] `grep -r "legacy/Legacy" src/` 为零（ae compat legacy）
- [ ] 无 `[compat]` 配置节
- [ ] 2.9.0-beta 游戏内功能回归通过
- [ ] 更新 `project-structure.mdc` 与 ae-compat 文档

---

## 工作量与对话拆分

| 步骤 | 规模 |
|------|------|
| Step 1 依赖 | 中 |
| Step 2–4 删 Legacy + 合并 native | 中 |
| Step 5 编织元件 | 中偏大 |
| Step 6 文档 | 小 |
| **合计** | **1 个 Agent 对话** |

---

## Todo 清单（执行时勾选）

1. **pe-deps** — 升级 `dependencies.gradle` 并 compile 通过  
2. **pe-delete-legacy** — 删 `legacy/` + 探测类，简化 `AeCompat`  
3. **pe-merge-native** — 合并 `native_`，去除 Legacy 回退  
4. **pe-dataloom** — GlodBlock → AE2 2.9.0 原生 API  
5. **pe-docs** — 配置、文档、Cursor 规则同步  
