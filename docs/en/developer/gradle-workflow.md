# TeXTech Gradle Workflow

> Audience: Developers · Build / migration / porting · Last synced: 2026-07

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
.\gradlew.bat build              # Compile and package
.\gradlew.bat runClient          # Launch dev client
.\gradlew.bat runServer          # Launch dev server
.\gradlew.bat test               # Run tests
.\gradlew.bat clean setupDecompWorkspace   # Rebuild decompiled workspace
```

On Unix-like shells use `./gradlew`.

### 1.2 Key Files

| File | Purpose |
|------|---------|
| `build.gradle.kts` | Applies GTNH convention plugin; **avoid arbitrary edits** — replace template version on upgrade |
| `gradle.properties` | modId, version, MC/Forge/MCP versions, Jabel, Mixin, Access Transformer, etc. |
| `dependencies.gradle` | Mod dependencies (GT5, AE2FC, Vosk/JNA/PinIn shadow, etc.) |
| `repositories.gradle` | Extra Maven repositories |
| `libs/` | Local dev jars (Chisel, Galacticraft, IC2NuclearControl, etc.) |
| `jitpack.yml` | Jitpack CI config |
| `.github/workflows/` | GitHub CI (build, release) |

### 1.3 ExampleMod Template Features

- Upgradable: replace `build.gradle` with a new template version
- Optional API artifact, version replacement, dependency shadowing
- Mixin and Access Transformer support
- Scala support (`src/main/scala/`)
- Git Tags for version numbers
- Jitpack / GitHub CI auto-release

**Creating a new mod from scratch** (reference flow):

1. Extract [project starter](https://github.com/GTNewHorizons/ExampleMod1.7.10/releases/download/master-packages/starter.zip)
2. Handle LICENSE, initialize Git
3. Edit `gradle.properties`, package names, and class names
4. Run `./gradlew build`

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
