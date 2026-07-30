# GTNH 版本兼容说明

> **适用版本**：TeXTech **v2.0.0**  
> **最后更新**：2026-07-30  
> English: [gtnh-version-compatibility.md](../../en/developer/gtnh-version-compatibility.md)

本文面向整合包作者、服管与玩家。源码与构建依赖是支持范围的权威来源；TeXTech 2.0 的开发和发布基线为 GTNH `2.9.0-beta-2`，不再提供旧整合包兼容。

## 1. 支持范围

| GTNH 整合包 | TeXTech 2.0 状态 | 说明 |
|-------------|------------------|------|
| **2.9.0-beta-2 及以上兼容版本** | ✅ 支持 | 使用 AE2 原生流体 API（NativeFluid）路径。 |
| **2.9.0-beta-1 及更早版本** | ❌ 不支持 | 依赖版本和运行路径均不再按旧整合包测试；2.8.x 用户请继续使用适配旧环境的 TeXTech 1.0.x，或先升级整合包。 |

`dependencies.gradle` 固定了 GTNH 2.9.0-beta-2 的 AE2、AE2FC、GT5 等开发依赖；在更旧整合包中仅能成功加载部分类并不代表受支持。

## 2. AE 运行路径

`GtnhEnvironmentProbe` 在 TeXTech 2.0 始终选择 `NATIVE_FLUID`。正常启动日志类似：

```text
[ADM] AE compat profile=NATIVE_FLUID (source=GTNH_VERSION_FILE, detail=2.9.0-beta-2)
```

找不到版本信息时，默认详情为 `2.9.0-beta-2-native-default`，仍不会退回旧路径。

配置文件中 `[compat] aeProfileOverride` 暂时保留以兼容已有配置：

| 值 | TeXTech 2.0 行为 |
|----|------------------|
| `auto` / 空值 | 选择 NativeFluid。 |
| `native` | 显式选择 NativeFluid。 |
| `legacy` | 被忽略并选择 NativeFluid；日志详情包含 `legacy-override-ignored`。 |

源码中的 `compat/ae/legacy/` 类仍可能被原生适配器用于混合数据格式、NBT 标记或迁移回退，这不构成对 GTNH 2.8.x 或旧 AE2/AE2FC 组合的运行支持。

## 3. 对玩家与整合包作者的影响

- 新安装应使用 GTNH `2.9.0-beta-2+` 与 TeXTech `v2.0.0` 的匹配附件。
- 从 1.0.x 升级前，先升级 GTNH 整合包；不要尝试用 `aeProfileOverride=legacy` 恢复 2.8.x 支持。
- 主 JAR、可选语音 JAR、可选 WebAE ZIP 必须来自同一 TeXTech Release。
- WebAE ZIP 只含浏览器 UI，解压到实例根目录后应存在 `TeXTech/WebAE/ui/index.html`；它不会改变 GTNH/AE2 兼容范围。

## 4. 开发者说明

[ae-compat-290.md](ae-compat-290.md) 保留兼容层的技术背景；[ae-compat-plan-e-remove-legacy.md](ae-compat-plan-e-remove-legacy.md) 现在仅是后续清理未使用旧类、测试和命名的内部计划，不是尚待兑现的旧整合包兼容承诺。任何支持范围变更都应先以 `dependencies.gradle`、环境探针和实际测试矩阵为准，再同步本页。

## 5. 与版本无关的已知限制

- 部分物品仍使用程序化临时贴图，见[临时材质清单](临时材质清单.md)。
- 部分方块 TESR 仍可能显示占位模型；这与 AE 兼容路径无关。
- 未提供内置配方的物品仍需 NEI、创造模式或整合包脚本获取，详见玩家手册。
