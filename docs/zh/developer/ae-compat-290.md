# GTNH 2.9.0-beta-2 AE 原生流体集成

## 概述

TeXTech 2.0 只支持 GTNH `2.9.0-beta-2+`，`AeCompat.init()` 在 `postInit` 绑定 `NATIVE_FLUID` 适配器。`compat/ae/legacy/` 源码仍为混合数据格式和后续迁移提供内部回退，但运行时不再选择 Legacy profile，也不代表支持旧整合包。

## 环境探针

`GtnhEnvironmentProbe` 可记录配置、整合包版本文件或 AE2 模组版本作为诊断来源，但所有分支最终都选择 NativeFluid：

1. `[compat] aeProfileOverride=native`：显式 NativeFluid。
2. `aeProfileOverride=legacy`：请求被忽略，详情为 `legacy-override-ignored:<值>`。
3. `.gtnh-version` / `config/gtnh/version.txt`：记录版本字符串并选择 NativeFluid。
4. AE2 模组版本：记录版本字符串并选择 NativeFluid。
5. 无版本信息：选择 NativeFluid，详情 `2.9.0-beta-2-native-default`。

正常日志示例：`[ADM] AE compat profile=NATIVE_FLUID (source=GTNH_VERSION_FILE, detail=2.9.0-beta-2)`。

## 2.0 编译基线

`dependencies.gradle` 固定 GTNH 2.9.0-beta-2 栈，当前关键版本为：

| 模组 | 编译版本 |
|------|----------|
| Applied-Energistics-2-Unofficial | `rv3-beta-1000-GTNH` |
| AE2FluidCraft-Rework | `1.5.95-gtnh` |
| GT5-Unofficial | `5.09.54.20` |

不要用旧 AE2/AE2FC dev JAR 覆盖编译 classpath，也不要把能加载个别类视为旧 GTNH 版本兼容。

## 适配器职责

| 区域 | TeXTech 2.0 实现 |
|------|------------------|
| 流体 cell 统计 | `StorageChannel.FLUIDS` + `ICellInventory` 的 Native 适配器 |
| 样板流体 I/O | 优先 AE2 正式 API；对混合格式使用内部反射回退 |
| Cell Workbench marker | Native NBT / AE2 util，兼容读取历史标记 |
| 编织元件 config | `NativeDataLoomFluidCellConfig` |

## 关键类与调用方

- `AeCompat`：`cells()` / `fluidMarkers()` / `patternFluids()` / `fluidCellConfig()` 门面。
- `GtnhEnvironmentProbe`：NativeFluid 诊断来源与旧 override 兼容读取。
- `native_/*`：TeXTech 2.0 的实际适配器。
- `legacy/*`：仅供 Native 适配器复用的混合格式/迁移辅助，待后续源码清理。
- `TileEntityAdvanceNetworkLink`、`TileEntityAdvanceDataMonitor`、`AssistantServerServices`、`PatternDetailFormatter`、`AbstractDataLoomFluidCell` 是主要调用方。

## 验证

- 在 GTNH `2.9.0-beta-2+` 实例验证存储字节/类型、流体样板、Workbench 分区和 AI 无限元件识别。
- `aeProfileOverride=legacy` 仅用于确认忽略行为与诊断日志，不能用于旧实例回归。
- 源码清理计划见 [ae-compat-plan-e-remove-legacy.md](ae-compat-plan-e-remove-legacy.md)；该计划不改变已经生效的 2.0 支持范围。
