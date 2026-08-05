# GTNH Version Compatibility

> **Applies to**: TeXTech **v2.0.0** and **v3.0.0-rc.1**
> **Last updated**: 2026-08-05
> 中文: [GTNH版本兼容说明.md](../../zh/developer/GTNH版本兼容说明.md)

This page is for pack authors, server admins, and players. Source code and pinned build dependencies are authoritative: the TeXTech 2 stable line and 3.0 RC line target GTNH `2.9.0-beta-2+` and do not support older pack lines.

## 1. Support matrix

| GTNH pack | TeXTech 2 stable / 3.0 RC status | Notes |
|-----------|---------------------|-------|
| **2.9.0-beta-2 and compatible later versions** | ✅ Supported | Uses the AE2 native-fluid (`NATIVE_FLUID`) path. |
| **2.9.0-beta-1 and earlier** | ❌ Unsupported | Dependencies and runtime behavior are no longer tested against old packs. GTNH 2.8.x users should remain on a TeXTech 1.0.x build made for that environment or upgrade the pack first. |

`dependencies.gradle` pins the GTNH 2.9.0-beta-2 AE2, AE2FC, GT5, and related development stack. Partial class loading on an older pack does not make that environment supported.

## 2. AE runtime path

`GtnhEnvironmentProbe` always selects `NATIVE_FLUID` in the current supported release lines. A normal startup log resembles:

```text
[ADM] AE compat profile=NATIVE_FLUID (source=GTNH_VERSION_FILE, detail=2.9.0-beta-2)
```

If no version metadata is available, the fallback detail is `2.9.0-beta-2-native-default`; it still does not select the legacy path.

`[compat] aeProfileOverride` remains temporarily readable for existing configurations:

| Value | Current behavior |
|-------|-----------------------|
| `auto` / empty | Select NativeFluid. |
| `native` | Explicitly select NativeFluid. |
| `legacy` | Ignore the request and select NativeFluid; the log detail contains `legacy-override-ignored`. |

Classes still present under `compat/ae/legacy/` may be used by native adapters for mixed data formats, NBT markers, or migration fallbacks. Their presence does not imply runtime support for GTNH 2.8.x or old AE2/AE2FC combinations.

## 3. Player and pack-author impact

- Stable installations should pair GTNH `2.9.0-beta-2+` with matching `v2.0.0` assets; RC testers must use the complete matching `v3.0.0-rc.1` asset set.
- Upgrade the GTNH pack before moving from TeXTech 1.0.x; `aeProfileOverride=legacy` cannot restore 2.8.x support.
- The core JAR, optional voice JAR, and optional WebAE ZIP must come from the same TeXTech Release.
- The WebAE ZIP only contains browser UI files. Extract it at the instance root so `TeXTech/WebAE/ui/index.html` exists; it does not change GTNH/AE2 compatibility.

## 4. Developer notes

[ae-compat-290.md](ae-compat-290.md) retains technical background for the compatibility layer. [ae-compat-plan-e-remove-legacy.md](ae-compat-plan-e-remove-legacy.md) is now an internal cleanup plan for remaining legacy classes, tests, and names—not a pending promise to support old packs. Any future support change must follow `dependencies.gradle`, the environment probe, and the tested runtime matrix before this page is updated.

## 5. Version-independent known limits

- Some items still use procedural placeholder art; see [temporary-textures.md](temporary-textures.md).
- Some block TESRs may still display placeholder models; this is unrelated to the AE compatibility path.
- Items without built-in recipes still require NEI, creative mode, or pack scripts; see the player guide.
