# GTNH 版本兼容说明

> **适用版本**：TeXTech **v1.0.0**（初次公开发布）  
> **最后更新**：2026-06-25  
> English: [gtnh-version-compatibility.md](../../en/developer/gtnh-version-compatibility.md)

本文面向**整合包作者、服管与玩家**，说明本模组在 GTNH 不同整合包版本上的支持范围、当前双路径兼容策略，以及**下一版本将移除旧版支持**的计划。

技术实现细节见 [ae-compat-290.md](ae-compat-290.md)；执行移除时的开发者清单见 [ae-compat-plan-e-remove-legacy.md](ae-compat-plan-e-remove-legacy.md)。

---

## 1. v1.0.0 支持范围

| GTNH 整合包 | AE2 / AE2FC 典型版本 | 支持状态 | 说明 |
|-------------|----------------------|----------|------|
| **2.8.x**（含 2.8.4） | AE2 rv3-beta-900 之前 · AE2FC **1.3.7-gtnh** | ✅ 支持 | 走 **Legacy** 路径（GlodBlock / AE2FC 反射集成） |
| **2.9.0-beta-1 及以上** | AE2 rv3-beta-977-GTNH · AE2FC **1.5.88-gtnh** | ✅ 支持 | 走 **NativeFluid** 路径（AE2 原生流体 API） |
| 2.9.0-beta-1 **以下** | — | ⚠️ **下一版本起不再支持** | 见 §3 |

模组在启动时通过 `AeCompat.init()` 自动探测环境并选择路径，一般**无需手动配置**。启动日志示例：

```
[ADM] AE compat profile=LEGACY (source=DEFAULT_LEGACY, detail=pre-2.9.0-default)
[ADM] AE compat profile=NATIVE (source=GTNH_VERSION_FILE, detail=2.9.0-beta-1)
```

### 手动覆盖（高级）

配置文件 `config/textech/textech.cfg` 中 `[compat]` 节：

| 配置项 | 可选值 | 说明 |
|--------|--------|------|
| `aeProfileOverride` | `auto`（默认） | 按检测链自动选择 |
| | `legacy` | 强制 Legacy 路径（调试 2.8.x 行为） |
| | `native` | 强制 Native 路径（需 2.9.0+ AE2 环境） |

---

## 2. 双路径兼容涵盖的功能

以下功能在 **2.8.x 与 2.9.0-beta+** 上均可用，但底层实现不同：

| 功能 | Legacy（2.8.x） | Native（2.9.0+） |
|------|-----------------|-------------------|
| 网络链接器 — 存储字节/类型统计 | GlodBlock 流体 handler 反射 | `StorageChannel.FLUIDS` + `ICellInventory` |
| AI 助手 — `QUERY_BYTES`、无限元件识别 | Legacy cell stats 适配器 | Native cell stats 适配器 |
| AI 助手 — 样板流体详情 | AE2FC 反射 API | 优先 AE2 正式 API |
| 数据编织元件 — 流体标记与 Workbench 分区 | `DataLoomFluidCellConfig` | `NativeDataLoomFluidCellConfig` |
| 监视器 / 存储链接器 — 流体堆栈统计 | 同上 | 同上 |

**不受 AE 版本影响的模组功能**（与 compat 无关）：挂索系统、高级计划器、语音助手、超能砂糖桔、星空剑、监视器图表/UI 等。

---

## 3. 下一版本计划：移除 2.9.0-beta-1 以下支持

### 声明

- **v1.0.0**：仍支持 GTNH **2.8.x** 与 **2.9.0-beta-1+** 双路径。
- **v1.1.0（或下一个 minor 版本，以 Release Notes 为准）**：**最低要求 GTNH 2.9.0-beta-1**；将删除 Legacy / GlodBlock 分支及 `[compat]` 配置节。

### 移除原因

1. GTNH 2.9.0 主线已稳定推进，AE2 原生流体 API 成为标准。
2. 双路径增加维护成本（反射、GlodBlock 耦合、测试矩阵翻倍）。
3. 编译依赖将统一升级至 AE2FC 1.5.88-gtnh / AE2 rv3-beta-977-GTNH，无法再与 2.8 开发线共用同一 classpath。

### 对玩家的影响

| 你使用的整合包 | v1.0.0 | 下一版本（计划） |
|--------------|--------|------------------|
| GTNH 2.8.x | ✅ 可用 | ❌ 请升级整合包至 2.9.0-beta-1+ |
| GTNH 2.9.0-beta-1+ | ✅ 可用 | ✅ 继续支持 |

### 迁移建议

- **仍在 2.8.x 的玩家**：可继续使用 **v1.0.0**；计划升级整合包前无需额外操作。
- **整合包作者**：若包体仍基于 2.8.x，请在发布说明中标注「TeXTech ≥1.1.0 需 GTNH 2.9.0-beta-1+」；或锁定模组版本为 `1.0.x`。
- **开发者**：移除工作按 [ae-compat-plan-e-remove-legacy.md](ae-compat-plan-e-remove-legacy.md) 执行；对话开场白：「继续 GTNH 2.9 AE 兼容，执行 Plan E — 移除 Legacy 支持」。

---

## 4. v1.0.0 已知限制（与版本无关）

初次发布有意不完整，与 GTNH 版本兼容无关：

- **配方**：大量物品/方块尚未添加 GTNH 合成配方，需 NEI / 创造模式获取。
- **材质**：部分物品使用程序化临时贴图，见 [临时材质清单.md](临时材质清单.md)。
- **渲染**：合成链接器、存储链接器等方块 TESR 仍可能使用占位立方体（`USE_PLACEHOLDER_CUBE`），待 OBJ 模型接入。

---

## 5. 相关文档

| 文档 | 用途 |
|------|------|
| [ae-compat-290.md](ae-compat-290.md) | 双路径技术细节、检测链、关键类 |
| [ae-compat-plan-e-remove-legacy.md](ae-compat-plan-e-remove-legacy.md) | Plan E：删除 Legacy 的逐步清单 |
| [临时材质清单.md](临时材质清单.md) | 占位材质审计 |
| [技术文档.md](技术文档.md) | 模组整体架构 |
