# Plan E — Remove Legacy AE Compatibility (GTNH 2.9.0+ Only)

> **Purpose**: After GTNH 2.9.0 becomes the mod's **minimum supported version**, run this plan in a dedicated Agent session to delete Legacy / GlodBlock branches from the dual-path setup.  
> **Do not** confuse with Plan A–D (dual-path already implemented); this plan is **irreversible** cleanup.  
> **Opening prompt**: "Continue GTNH 2.9 AE compat — execute Plan E — remove Legacy support"

**Related doc**: [ae-compat-290.md](ae-compat-290.md) (current dual-path description)

---

## Prerequisites (must be met before execution)

- [ ] Mod README / release notes declare minimum GTNH **2.9.0 beta-1+**
- [ ] Native path regression completed on a **real 2.9.0-beta instance**:
  - Network linker byte/type stats
  - AI: `QUERY_BYTES`, pattern fluid details, `QUERY_STORAGE` fluids
  - Fluid loom cell: Workbench partition, weaving, ME network notification
- [ ] Dev jars available and API diff done (see `.workspace/ae2_290_api_diff.md`):
  - `Applied-Energistics-2-Unofficial:rv3-beta-977-GTNH`
  - `AE2FluidCraft-Rework:1.5.88-gtnh`

---

## Target Architecture (after completion)

```
TeXTech.postInit
    └── AeCompat (single path, no profile branch)
            ├── AeCellStatsAdapter      → sole implementation
            ├── AeFluidMarkerAdapter
            ├── AePatternFluidAdapter
            └── AeFluidCellConfigFactory
                    └── callers unchanged: NetworkLink / DataMonitor / Assistant / DataLoom
```

**Delete**: `compat/ae/legacy/`, `GtnhEnvironmentProbe`, `GtnhVersion`, `AeCompatProfile`, `AeCompatDetectionSource`, `[compat] aeProfileOverride`, all business-layer `com.glodblock.*` imports.

**Keep (recommended)**: `compat/ae/` interfaces + thin facade `AeCompat` for unit tests; optional Step 7 inlines later.

---

## Step 1 — Upgrade Build Dependencies

**File**: [`dependencies.gradle`](../../dependencies.gradle)

| Change | From | To |
|--------|------|-----|
| AE2FC | `1.3.7-gtnh` | `1.5.88-gtnh` |
| AE2 explicit | none | `Applied-Energistics-2-Unofficial:rv3-beta-977-GTNH:dev` |

```bash
gradlew compileJava
```

Fix compile errors from ae2fc 1.5.x / AE2 rv3-beta-977 API changes (focus: whether `FluidCellConfig`, `IStorageFluidCell` packages moved into `appeng.*`).

**Acceptance**: confirm new dependency stack compiles before or while deleting Legacy in the same pass.

---

## Step 2 — Delete Legacy Package and Version Probing

### Delete entire `compat/ae/legacy/` directory (5 files)

- `LegacyAeCellStatsAdapter.java`
- `LegacyAeFluidMarkerAdapter.java`
- `LegacyAePatternFluidAdapter.java`
- `LegacyAeFluidCellConfigFactory.java`

### Delete probe layer files (4 files)

| File | Path |
|------|------|
| `GtnhEnvironmentProbe.java` | `compat/ae/` |
| `GtnhVersion.java` | `compat/ae/` |
| `AeCompatProfile.java` | `compat/ae/` |
| `AeCompatDetectionSource.java` | `compat/ae/` |

---

## Step 3 — Simplify `AeCompat` Facade

**File**: [`AeCompat.java`](../../../src/main/java/com/imgood/textech/compat/ae/AeCompat.java)

- Remove `bindAdapters()`, `profile()`, `detectionSource()`, `detectionDetail()`, `isNativeFluid()`
- Remove calls to `GtnhEnvironmentProbe`
- Statically bind four adapter fields to Native implementations (classes renamed in Step 4)
- `init()` may simplify to `initialized = true` + optional `LOG.info("[ADM] AE compat: native fluid (2.9.0+)")`

[`TeXTech.postInit`](../../../src/main/java/com/imgood/textech/TeXTech.java) may keep or remove `AeCompat.init()`.

---

## Step 4 — Merge `native_` as Default Implementation

Move four classes from [`compat/ae/native_/`](../../../src/main/java/com/imgood/textech/compat/ae/native_/) **up** to `compat/ae/` and rename (example):

| Current class | Suggested new name |
|---------------|-------------------|
| `NativeAeCellStatsAdapter` | `AeCellStatsAdapterImpl` |
| `NativeAeFluidMarkerAdapter` | `AeFluidMarkerAdapterImpl` |
| `NativeAePatternFluidAdapter` | `AePatternFluidAdapterImpl` |
| `NativeAeFluidCellConfigFactory` | `AeFluidCellConfigFactoryImpl` |

Delete `compat/ae/native_/` directory.

### Legacy fallbacks that must be inlined (Native still references Legacy today)

| Location | Action |
|----------|--------|
| `NativeAeCellStatsAdapter` → `LegacyAeCellStatsAdapter.isInfiniteCell` | Move into `AeCellStatsAdapterImpl` or new `AeInfiniteCellUtil` |
| `NativeAeFluidMarkerAdapter` → `LegacyAeFluidMarkerAdapter.*` | Inline NBT parsing + AE2 util into `AeFluidMarkerAdapterImpl` |
| `NativeAePatternFluidAdapter` → `LegacyAePatternFluidAdapter.appendFluidValue` | Move into `AePatternFluidAdapterImpl` or `AePatternFluidUtil` |
| `NativeAeFluidCellConfigFactory` → `LegacyAeFluidCellConfigFactory` | Remove fallback; keep only merged config class |

---

## Step 5 — Data Loom Cells: Remove GlodBlock Business Coupling

| File | Action |
|------|--------|
| `IDataLoomFluidCell.java` | Change `extends IStorageFluidCell` to AE2 2.9.0 native interface (decompile to confirm FQCN) |
| `AbstractDataLoomFluidCell.java` | Update `isStorageCell()` comments; confirm 2.9.0 behavior |
| `DataLoomFluidCellConfig.java` / `NativeDataLoomFluidCellConfig.java` | Merge into single config class extending AE2 2.9.0 partition base |
| `DataLoomCellCapacity.java` | Verify mB/byte, types divisor |

**Do not delete** (unrelated to AE Legacy):

- `TileEntityAdvanceStorageLink.LEGACY_MARKED_ITEMS_TAG` — save migration NBT
- `AiClientPreferences` `legacyKey` — client config migration

---

## Step 6 — Config and Documentation

| File | Action |
|------|--------|
| `Config.java` | Delete `compatAeProfileOverride` |
| `config/ConfigCompatLoader.java` | **Delete file** |
| `Config.java` `synchronizeConfiguration` | Remove `ConfigCompatLoader.load` |
| `ConfigDescriptions.java` | Delete `[compat]` entries |
| `docs/zh/developer/ae-compat-290.md` | Rewrite as "2.9.0+ only" or archive |
| `docs/en/developer/ae-compat-290.md` | Same |
| `docs/zh/ai-assistant/开发指南.md` | Remove Legacy/GlodBlock dual-path description |
| `docs/en/ai-assistant/development-guide.md` | Same |
| `.cursor/rules/project-structure.mdc` | Update `compat/` count; remove `legacy/` / `native_/` |

---

## Step 7 (Optional) — Remove compat Layer Entirely

If mock/switch no longer needed:

- Delete `AeCompat.java` and four interfaces
- Business classes import `...AeCellStatsAdapterImpl.INSTANCE` directly

**Recommendation**: keep thin facade in Plan E; inline as a follow-up small PR.

---

## Acceptance Checklist

- [ ] `gradlew compileJava` succeeds
- [ ] `grep -r "com.glodblock" src/` returns zero
- [ ] `grep -r "legacy/Legacy" src/` returns zero (ae compat legacy)
- [ ] No `[compat]` config section
- [ ] In-game regression on 2.9.0-beta passes
- [ ] Update `project-structure.mdc` and ae-compat docs

---

## Effort and Session Split

| Step | Size |
|------|------|
| Step 1 dependencies | Medium |
| Step 2–4 delete Legacy + merge native | Medium |
| Step 5 data loom cells | Medium–large |
| Step 6 docs | Small |
| **Total** | **1 Agent session** |

---

## Todo Checklist (check during execution)

1. **pe-deps** — Upgrade `dependencies.gradle` and compile  
2. **pe-delete-legacy** — Delete `legacy/` + probe classes, simplify `AeCompat`  
3. **pe-merge-native** — Merge `native_`, remove Legacy fallbacks  
4. **pe-dataloom** — GlodBlock → AE2 2.9.0 native API  
5. **pe-docs** — Config, docs, Cursor rules sync  
