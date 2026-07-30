# GTNH 2.9.0-beta-2 AE Native-Fluid Integration

## Overview

TeXTech 2.0 supports GTNH `2.9.0-beta-2+` only. `AeCompat.init()` binds the `NATIVE_FLUID` adapters during `postInit`. Sources under `compat/ae/legacy/` remain as internal mixed-format and migration fallbacks, but the runtime no longer selects a Legacy profile and those classes do not imply support for old packs.

## Environment probe

`GtnhEnvironmentProbe` can retain config, pack-version, or AE2-version details for diagnostics, but every branch selects NativeFluid:

1. `[compat] aeProfileOverride=native`: explicitly selects NativeFluid.
2. `aeProfileOverride=legacy`: ignored, with detail `legacy-override-ignored:<value>`.
3. `.gtnh-version` / `config/gtnh/version.txt`: records the version and selects NativeFluid.
4. AE2 mod version: records the version and selects NativeFluid.
5. No metadata: selects NativeFluid with detail `2.9.0-beta-2-native-default`.

Normal log example: `[ADM] AE compat profile=NATIVE_FLUID (source=GTNH_VERSION_FILE, detail=2.9.0-beta-2)`.

## 2.0 compile baseline

`dependencies.gradle` pins the GTNH 2.9.0-beta-2 stack. Current key versions are:

| Mod | Compile version |
|-----|-----------------|
| Applied-Energistics-2-Unofficial | `rv3-beta-1000-GTNH` |
| AE2FluidCraft-Rework | `1.5.95-gtnh` |
| GT5-Unofficial | `5.09.54.20` |

Do not replace the compile classpath with old AE2/AE2FC dev JARs, and do not interpret partial class loading as compatibility with an old GTNH pack.

## Adapter responsibilities

| Area | TeXTech 2.0 implementation |
|------|-----------------------------|
| Fluid-cell statistics | Native adapter using `StorageChannel.FLUIDS` + `ICellInventory` |
| Pattern fluid I/O | Prefer official AE2 APIs; use internal reflection only for mixed formats |
| Cell Workbench markers | Native NBT / AE2 utilities with historical-marker reads |
| Loom fluid configuration | `NativeDataLoomFluidCellConfig` |

## Key classes and call sites

- `AeCompat`: facade for `cells()`, `fluidMarkers()`, `patternFluids()`, and `fluidCellConfig()`.
- `GtnhEnvironmentProbe`: NativeFluid diagnostic source and old-override compatibility reader.
- `native_/*`: active TeXTech 2.0 adapters.
- `legacy/*`: mixed-format/migration helpers reused by native adapters, pending source cleanup.
- `TileEntityAdvanceNetworkLink`, `TileEntityAdvanceDataMonitor`, `AssistantServerServices`, `PatternDetailFormatter`, and `AbstractDataLoomFluidCell` are primary callers.

## Verification

- Test byte/type statistics, fluid patterns, Workbench partitioning, and AI infinite-cell detection on a GTNH `2.9.0-beta-2+` instance.
- Use `aeProfileOverride=legacy` only to verify ignored-override logging; it cannot be used for an old-pack regression.
- Source cleanup is tracked by [ae-compat-plan-e-remove-legacy.md](ae-compat-plan-e-remove-legacy.md); that plan does not change the support boundary already active in 2.0.
