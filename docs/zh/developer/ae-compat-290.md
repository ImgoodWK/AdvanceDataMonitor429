# GTNH 2.9.0 beta-1 AE 原生流体兼容

## 概述

TeXTech 在 `compat/ae/` 包中实现 **Legacy**（2.9.0 beta-1 之前）与 **NativeFluid**（2.9.0 beta-1 及以上）双路径 AE 集成。运行时由 `AeCompat.init()`（`postInit`）探测环境并绑定适配器。

## 版本检测（优先级）

1. **配置** `[compat] aeProfileOverride`：`auto` | `legacy` | `native`
2. **整合包版本文件**：实例根目录 `.gtnh-version` 或 `config/gtnh/version.txt`（首行 semver，如 `2.9.0-beta-1`）
3. **AE2 模组版本**：`appliedenergistics2` mod 版本；`rv3-beta-900-GTNH` 及以上视为 Native 能力
4. **能力探测**：classpath 存在 `appeng.items.storage.ItemBasicFluidStorageCell`
5. **默认**：Legacy

启动日志示例：`[ADM] AE compat profile=LEGACY (source=DEFAULT_LEGACY, detail=pre-2.9.0-default)`

## 2.9.0-beta 整合包参考版本（modlist）

| 模组 | 版本 |
|------|------|
| Applied-Energistics-2-Unofficial | rv3-beta-977-GTNH |
| AE2FluidCraft-Rework | 1.5.88-gtnh |

**编译依赖**仍为 `AE2FluidCraft-Rework:1.3.7-gtnh`（与当前 GTNH 2.8 开发线一致）。2.9.0 dev jar 请放入 `libs/` 并反编译至 `.workspace/ae2_290_sources/` 做 API 对照，**勿**用 `devOnlyNonPublishable` 覆盖 compile classpath（会与 1.3.7 API 冲突）。

## API 差异摘要

| 区域 | Legacy | NativeFluid |
|------|--------|-------------|
| 流体 cell 统计 | `FluidCellInventoryHandler` / GlodBlock 优先，再 `ICellInventoryHandler` | 仅 `StorageChannel.FLUIDS` + `ICellInventory` |
| 样板流体 I/O | 反射 `getCondensedFluidInputs` 等 | 同上 + 优先正式 API（若存在） |
| Cell Workbench marker | AE2FC `ItemFluidPacket` / `Util` | NBT + AE2 util，回退 AE2FC |
| 编织元件 config | `DataLoomFluidCellConfig` | `NativeDataLoomFluidCellConfig`（探测原生 cell 类） |

## 关键类

- `AeCompat` — 门面：`cells()` / `fluidMarkers()` / `patternFluids()` / `fluidCellConfig()`
- `GtnhEnvironmentProbe` — 检测链
- `legacy/*` — GlodBlock 路径（日后 Plan E 删除）
- `native_/*` — 2.9.0+ 路径（包名 `native_` 因 `native` 为 Java 关键字）

## 调用方

- `TileEntityAdvanceNetworkLink` / `TileEntityTeXTech` — `AeCompat.cells().accumulateStorageStack`
- `AssistantServerServices.classifyCell` — `readItemCellStats` / `readFluidCellStats`
- `PatternDetailFormatter` — `AeCompat.patternFluids()`
- `AbstractDataLoomFluidCell` / `DataLoomCellUtil` — config 与 marker 适配器

## 本地测试

- **Legacy**：2.8.x 实例或 `aeProfileOverride=legacy`
- **Native**：2.9.0-beta 实例或 `aeProfileOverride=native`（需真实 2.9.0 AE2 环境验证）

## 移除 Legacy（Plan E）

当 2.9.0 成为最低支持版本后，按独立计划执行：**[ae-compat-plan-e-remove-legacy.md](ae-compat-plan-e-remove-legacy.md)**  
对话开场：「继续 GTNH 2.9 AE 兼容，执行 Plan E — 移除 Legacy 支持」
