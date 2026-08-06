# TeXTech Gradle Workflow

> Audience: Developers · Build / migration / porting · Last synced: 2026-08

This document merges GTNH ExampleMod template notes, build-system migration, mod porting workflow, and build FAQ.

---

## Table of Contents

- [1. Project Build Overview](#1-project-build-overview)
  - [1.1 Common Commands](#11-common-commands)
  - [1.2 Key Files](#12-key-files)
  - [1.3 ExampleMod Template Features](#13-examplemod-template-features)
- [2. Build System Migration](#2-build-system-migration)
  - [2.1 General Migration Steps](#21-general-migration-steps)
  - [2.2 Mixin Configuration Migration](#22-mixin-configuration-migration)
- [3. Mod Porting Guide](#3-mod-porting-guide)
  - [3.1 Repository and Build Setup](#31-repository-and-build-setup)
  - [3.2 Slim Fork and Dependencies](#32-slim-fork-and-dependencies)
  - [3.3 Porting Preparation](#33-porting-preparation)
  - [3.4 Porting Code](#34-porting-code)
- [4. Build FAQ](#4-build-faq)
- [5. Advanced Extensions](#5-advanced-extensions)

---

## 1. Project Build Overview

TeXTech is based on the [GTNH ExampleMod 1.7.10](https://github.com/GTNewHorizons/ExampleMod1.7.10) skeleton; most behavior comes from the `com.gtnewhorizons.gtnhconvention` plugin.

### 1.1 Common Commands

```powershell
.\gradlew.bat spotlessCheck     # Check Java / Gradle formatting
.\gradlew.bat test               # Run Java tests
.\gradlew.bat build              # Compile, test, and create all release artifacts
.\gradlew.bat runClient          # Launch dev client
.\gradlew.bat runServer          # Launch dev server
.\gradlew.bat voiceJar           # Build only the optional voice companion
.\gradlew.bat webaeZip           # Package the committed WebAE static bundle
.\gradlew.bat -Ptextech.useChinaMirrors=true build # Explicit mirror opt-in
```

On Unix-like shells use `./gradlew`.

`build/libs/` contains four release-facing artifacts when built from a canonical Tag:

| Artifact | Purpose |
|----------|---------|
| `textech-<tag>.jar` | Required core mod; excludes WebAE pages and the large Vosk model |
| `textech-<tag>-voice.jar` | Optional client-side offline voice model (`textechvoice`) |
| `textech-<tag>-webae.zip` | Optional server UI; extract at the instance root |
| `textech-<tag>-sources.jar` | Developer source reference; never install in player `mods/` |

For `v3.0.0-rc.3`, `<tag>` is literally `v3.0.0-rc.3`.

### 1.2 Key Files

| File | Purpose |
|------|---------|
| `settings.gradle.kts` | Plugin repositories, official-source defaults, and the explicit mirror switch |
| `build.gradle.kts` | Applies GTNH convention plugin; **avoid arbitrary edits** — replace template version on upgrade |
| `gradle.properties` | modId, version, MC/Forge/MCP versions, Jabel, Mixin, Access Transformer, etc. |
| `dependencies.gradle` | Mod dependencies (GT5, AE2FC, Vosk/JNA/PinIn shadow, etc.) |
| `repositories.gradle` | Extra Maven repositories |
| `libs/` | Local dev jars (Chisel, Galacticraft, IC2NuclearControl, etc.) |
| `jitpack.yml` | Jitpack CI config |
| `.github/workflows/` | Repository-owned CI, CodeQL, Tag Release, Wiki sync, and provenance monitor |

### 1.3 ExampleMod Template Features

- Upgradable: replace `build.gradle` with a new template version
- Optional API artifact, version replacement, dependency shadowing
- Mixin and Access Transformer support
- Scala support (`src/main/scala/`)
- Git Tags for version numbers; TeXTech uses repository-owned CI and Tag Release workflows

**Creating a new mod from scratch** (upstream ExampleMod reference only):

1. Extract [project starter](https://github.com/GTNewHorizons/ExampleMod1.7.10/releases/download/master-packages/starter.zip)
2. Handle LICENSE, initialize Git
3. Edit `gradle.properties`, package names, and class names
4. Run `./gradlew build`

TeXTech does not publish ExampleMod `starter.zip` or `migration.zip` packages from this repository and does not create `latest-packages` or `v*-packages` Tags. Those archives are not player release assets.

---

## 2. Build System Migration

Applies to typical Forge mods (no special core plugin / shadow / AT / ASM). Contributions welcome if steps are missing.

### 2.1 General Migration Steps

1. Copy and replace repo files from [migration.zip](https://github.com/GTNewHorizons/ExampleMod1.7.10/releases/download/master-packages/migration.zip) (**except `build.gradle`**)
2. Copy original `build.gradle(.kts)` `repositories` into `repositories.gradle`
3. Copy original `dependencies` into `dependencies.gradle`
4. Replace original file with template `build.gradle`; move custom tasks to `addon.gradle` (auto-integrated when present)
5. Adapt `gradle.properties`
6. Ensure `src/main/resources/mcmod.info` contains `${modId}`, `${modName}`, `${modVersion}`, `${minecraftVersion}`
7. Re-import in IDE (IntelliJ: clean caches and restart recommended)
8. Run `./gradlew clean setupDecompWorkspace`

### 2.2 Mixin Configuration Migration

See [example-mixins branch](https://github.com/GTNewHorizons/ExampleMod1.7.10/tree/example-mixins):

1. Extract mixin package and plugin config from `mixins.yourModId.json` into `gradle.properties`
2. Implement MixinPlugin per example
3. Delete `mixins.mymodid.json`

---

## 3. Mod Porting Guide

### 3.1 Repository and Build Setup

1. Read original mod README/Wiki for special build config
2. Fork original repo to preserve commit history
3. Run [§2 Build System Migration](#2-build-system-migration) on the fork

### 3.2 Slim Fork and Dependencies

Avoid hard dependency on specific jars in `libs/`; prefer Maven:

1. Check if original project publishes to Maven / Jitpack
2. If not and license allows: fork → add `jitpack.yml` + CI → tag → get coordinates from [jitpack.io](https://jitpack.io)
3. Single jar: see [Jitpack single-file publish](https://gist.github.com/jitpack-io/f928a858aa5da08ad9d9662f982da983)

If the mod depends on other mods, port the dependency chain first.

### 3.3 Porting Preparation

Build and classify errors:

- **Missing references**: renamed/removed classes/methods/fields → adjust calls
- **Build errors**: missing external libs → add `dependencies.gradle` entries

Fix all build-level errors before code porting.

### 3.4 Porting Code

Suggested order:

1. Fix moved/renamed: remove bad imports, let IDE auto-import equivalents
2. Stub code that cannot be fixed quickly (track with TODO)
3. Build and try to run
4. Fix crashers first
5. Fix features small to large
6. Drop unmaintainable features and document in issues
7. Regression test; fix porting bugs

---

## 4. Build FAQ

### Select an mcp conf dir for the deobfuscator

An MCP deobfuscator config dialog may appear:

![](http://i.imgur.com/gzBMLrr.png)

**Solution**: point to the Forge unpacked conf directory:

- Linux/macOS: `~/.gradle/caches/minecraft/net/minecraftforge/forge/1.7.10-10.13.4.1614-1.7.10/unpacked/conf`
- Windows: `%USERPROFILE%/.gradle/caches/minecraft/net/minecraftforge/forge/1.7.10-10.13.4.1614-1.7.10/unpacked/conf`

If still unresolved, open a GitHub issue.

### Could not find CodeChickenLib / plugin or dependency resolution

Normal local builds and CI use Gradle Plugin Portal, Maven Central, GTNH Nexus, and the project's explicitly declared official repositories. Domestic mirrors are never the implicit sole source. `mavenLocal()` is available only outside CI and is ordered last, so tagged builds cannot depend on local publications.

Developers who need domestic mirrors must opt in explicitly:

```powershell
.\gradlew.bat -Ptextech.useChinaMirrors=true build
```

The property defaults to `false`, including in CI and Release workflows. Opting in adds Aliyun and Tencent repositories while retaining the official sources as fallbacks. If a mirror returns a 5xx response, remove the property to return to the reproducible official-source path.

Resolution failures can also be caused by **stale module metadata**:

**One-time fix** (from project root):

```powershell
.\gradlew.bat --refresh-dependencies compileJava
```

If that is not enough, delete the local cache entry and rebuild:

```powershell
Remove-Item -Recurse -Force "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\com.github.GTNewHorizons\CodeChickenLib" -ErrorAction SilentlyContinue
.\gradlew.bat compileJava
```

Do not make a mirror the only CI source to work around one dependency. If an official GTNH coordinate is genuinely unavailable, first verify the pinned version in `dependencies.gradle` against GTNH Nexus.

### GregTech / ModularUI2 / AE2 (GTNH 2.9.0-beta-2)

This repo is **aligned to GTNH pack 2.9.0-beta-2** (no older GT5 5.09.51 / MUI2 2.2 / GTNHLib 0.10 stack):

| Component | Version |
|-----------|---------|
| GT5-Unofficial | `5.09.54.20` |
| Applied-Energistics-2 | `rv3-beta-1000-GTNH` |
| AE2FluidCraft-Rework | `1.5.95-gtnh` |
| ModularUI2 | `2.3.79-1.7.10` |
| GTNHLib | `0.11.24` |
| BetterQuesting | `3.8.72-GTNH` |
| NewHorizonsCoreMod | `2.9.5` |
| StructureLib | `1.4.42` |
| NotEnoughItems | `2.8.111-GTNH` |

`addon.gradle` forces those coordinates.

**AE2 optional API stubs**: the published AE2 jar's `TileChest` bytecode implements optional Mekanism / RotaryCraft interfaces. TeXTech builds `build/ae2-optional-api-stubs.jar` from `tools/ae2-optional-stubs/` as `compileOnly`. **CoFH RF** comes from transitive Curse CoFH Core at runtime (GT++ cryotheum needs `Mods.COFHCore`). `runClient*` / `runServer*` run `syncDevCofhMappingsConfig` to set `run/config/CodeChickenLib.cfg` `mappingDir` under `GRADLE_USER_HOME`, so CoFH's coremod works in RFG deobf runs. Do not globally exclude CoFH Core.

### BetterQuesting / WebAE quest book debugging

- **Mod deps**: `BetterQuesting 3.8.72-GTNH` + `GTNHLib 0.11.24` (title notifications need `TitleAPI.setEffectTier`).
- **Common crash**: `NoSuchMethodError: TitleAPI.setEffectTier` → stale gtnhlib at runtime; clear `run/mods` and `--refresh-dependencies`.
- **Quest data**: GTNH pack quest snapshot lives in `dev-fixtures/betterquesting/` (see `SOURCE.json`). `runClient` / `runServer` depend on `syncDevBetterQuesting`.
- **Manual sync**: `.\gradlew.bat syncDevBetterQuesting`
- **In-game load**: as OP run `/bq_admin default load`; verify WebAE `?page=quests` or the in-game quest book.
- **Refresh snapshot**: `powershell -ExecutionPolicy Bypass -File tools/dev/sync-betterquesting-from-gtnh.ps1`, then commit `dev-fixtures/betterquesting/`.

### NBTEdit `NoSuchFieldError: field_71412_D`

`run/mods/ForgeNBTEdit-universal-1.0.0.test.jar` is legacy In-game NBTEdit and breaks on Java 17 / lwjgl3ify (stale SRG field access for `Minecraft.mcDataDir`). It is **not** a Gradle dependency—usually left over from a manual drop into `run/mods`.

Delete it and rerun:

```powershell
Remove-Item -Force "run\mods\ForgeNBTEdit-universal-1.0.0.test.jar" -ErrorAction SilentlyContinue
.\gradlew.bat runClient17
```

Use this mod's binder NBT viewer (`GuiNbtViewer`) or NEI instead.

---

## 5. Advanced Extensions

### Access Transformers

Define AT config files in `gradle.properties`. See [example-access-transformers branch](https://github.com/GTNewHorizons/ExampleMod1.7.10/tree/example-access-transformers).

**Warning**: AT may make decompiled dependency sources unusable; IntelliJ cannot search classes without sources.

### Mixins

Modify vanilla/mod behavior at runtime without editing source. Enable in `gradle.properties` to auto-generate mixin config. See [Hodgepodge](https://github.com/GTNewHorizons/Hodgepodge/) and [Angelica](https://github.com/GTNewHorizons/Angelica/pull/8).

### addon.gradle

For custom Gradle tasks, add `addon.gradle[.kts]` (early integration) or `addon.late.gradle[.kts]` (late properties available). Use `addon.local.gradle[.kts]` for local tweaks not committed to Git.

---

*Template authors: SinTh0r4s, TheElan, basdxz · GTNewHorizons ExampleMod1.7.10*
