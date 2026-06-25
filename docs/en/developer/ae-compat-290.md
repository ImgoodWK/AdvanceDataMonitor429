# GTNH 2.9.0 beta-1 AE Native Fluid Compatibility

## Overview

AdvanceDataMonitor uses dual AE integration paths under `compat/ae/`: **Legacy** (before 2.9.0 beta-1) and **NativeFluid** (2.9.0 beta-1+). `AeCompat.init()` runs in `postInit`, probes the environment, and binds adapters.

## Detection priority

1. Config `[compat] aeProfileOverride`: `auto` | `legacy` | `native`
2. Pack version file: `.gtnh-version` or `config/gtnh/version.txt` at instance root (first line semver, e.g. `2.9.0-beta-1`)
3. AE2 mod version: `appliedenergistics2`; `rv3-beta-900-GTNH` and above treated as native-capable
4. Capability probe: `appeng.items.storage.ItemBasicFluidStorageCell` on classpath
5. Default: Legacy

Log example: `[ADM] AE compat profile=LEGACY (source=DEFAULT_LEGACY, detail=pre-2.9.0-default)`

## GTNH 2.9.0-beta reference versions (modlist)

| Mod | Version |
|-----|---------|
| Applied-Energistics-2-Unofficial | rv3-beta-977-GTNH |
| AE2FluidCraft-Rework | 1.5.88-gtnh |

**Compile dependency** remains `AE2FluidCraft-Rework:1.3.7-gtnh`. Place 2.9.0 dev jars in `libs/` and decompile to `.workspace/ae2_290_sources/` for API diff — do not add 2.9.0 jars as `devOnlyNonPublishable` on the compile classpath (conflicts with 1.3.7).

## API differences (summary)

| Area | Legacy | NativeFluid |
|------|--------|-------------|
| Fluid cell stats | GlodBlock `FluidCellInventoryHandler` first, then `ICellInventoryHandler` | `StorageChannel.FLUIDS` + `ICellInventory` only |
| Pattern fluid I/O | Reflection on `getCondensedFluidInputs` etc. | Same + prefer official API when present |
| Workbench markers | AE2FC `ItemFluidPacket` / `Util` | NBT + AE2 util, AE2FC fallback |
| Loom fluid config | `DataLoomFluidCellConfig` | `NativeDataLoomFluidCellConfig` when native cell class exists |

## Key classes

- `AeCompat` — facade: `cells()`, `fluidMarkers()`, `patternFluids()`, `fluidCellConfig()`
- `GtnhEnvironmentProbe` — detection chain
- `legacy/*` — GlodBlock path (remove in future Plan E)
- `native_/*` — 2.9.0+ path (`native_` because `native` is a Java keyword)

## Call sites

- `TileEntityAdvanceNetworkLink` / `TileEntityAdvanceDataMonitor` — `AeCompat.cells().accumulateStorageStack`
- `AssistantServerServices.classifyCell` — `readItemCellStats` / `readFluidCellStats`
- `PatternDetailFormatter` — `AeCompat.patternFluids()`
- `AbstractDataLoomFluidCell` / `DataLoomCellUtil` — config and marker adapters

## Local testing

- **Legacy**: 2.8.x instance or `aeProfileOverride=legacy`
- **Native**: 2.9.0-beta instance or `aeProfileOverride=native` (requires real 2.9.0 AE2 stack)

## Removing Legacy (Plan E)

When 2.9.0 is the minimum supported pack, follow: **[ae-compat-plan-e-remove-legacy.md](ae-compat-plan-e-remove-legacy.md)**  
Session opener: "Continue GTNH 2.9 AE compat — execute Plan E, remove Legacy support"
