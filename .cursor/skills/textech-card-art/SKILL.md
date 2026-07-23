---
name: textech-card-art
description: Generate and import TeXTech card-battle art via DIY GPT Image 2 or Meowa. Use when creating card faces, indexing mod texture refs, A/B backends, or promoting PNGs into cardbattle-server/public/card-art. Never commit API keys.
---

# TeXTech Card Art (DIY + Meowa)

## Style contract (voxel cinematic still)

- HD 1:1 portrait that looks like an **in-game cinematic screenshot**, not a low-res pixel sprite.
- Cubic volumetric forms, discrete hard-edged material facets, readable modular machines/creatures.
- Soft in-engine lighting, centered subject (~70%), dark vignette.
- **Never** name Minecraft / Steve / copyrighted skins in prompts.
- Cost / ATK / HP / keywords / frames are UI layers — never bake into art.
- Canonical helpers: `cardbattle-server/scripts/lib/card-art-style.mjs`
- Generated copy: `.workspace/card-art/style-bible.md` (from `art:requirements`)

## Prerequisites

### DIY backend (default for cost exploration)

1. Set **user** env only:
   - `TEXTECH_IMAGE_API_KEY` (required)
   - `TEXTECH_IMAGE_BASE_URL` (default `https://api.openai.com/v1`)
   - `TEXTECH_IMAGE_MODEL` (default `gpt-image-2`)
   - `TEXTECH_IMAGE_SIZE` (default `1024x1024`)
   - optional `TEXTECH_IMAGE_QUALITY`
2. Never paste keys into chat/repo/logs.

### Meowa backend (quality fallback + specialty)

1. Install Meowa skill if needed: `npx skills add https://github.com/Meowa-AI/meowa-skills --skill game-assets`
2. Set **user** env only: `MEOWART_API_KEY`
3. Optional: `meowart_api.py skill-doc --task "HD voxel cinematic card portrait 1:1"`

## Asset contract

- Runtime: `cardbattle-server/public/card-art/<cardId>.png`
- Prefer HD 1:1 1024×1024; validate via `final_outputs.json` before promote
- Working outputs: `.workspace/card-art/` (gitignored)

## Workflow

```powershell
cd cardbattle-server

# Optional: index mod textures from libs + GTNH mods dir
npm.cmd run art:index-refs -- --mods-dir "D:\path\to\GTNH\mods"

# Build requirements (voxel prompts + styleRefs + subjectRefs)
# Missing-only by default; use --all to rebuild every card (full regen).
npm.cmd run art:requirements
npm.cmd run art:requirements -- --all

# DIY batch (default backend)
npm.cmd run art:generate -- --backend diy --limit 5
# Full overwrite of existing public art:
npm.cmd run art:generate -- --backend diy --force --limit 100 --concurrency 2

# Meowa fallback batch
npm.cmd run art:generate -- --backend meowa --limit 5 --quality standard

# A/B same cards, no promote — fills .workspace/card-art/ab/<stamp>/
npm.cmd run art:ab -- --ids van_scout,gt_wrench --limit 2
```

1. Catalog source: `src/main/resources/assets/textech/cardbattle/cards.json`
2. Style goldens: prefer frozen copies in `.workspace/card-art/style-goldens/` when present (safe full regen); else per-theme files in `public/card-art/` (see `THEME_STYLE_GOLDENS`)
3. Subject refs: fuzzy match from `.workspace/card-art/refs/index.json` (max 8 refs total)
4. Providers that return square ≠1024 are resized in-place to 1024×1024 before promote (Windows)
5. Optionally set `art: "<id>.png"` on card defs after promote

## Backend roles

| Need | Use |
|---|---|
| Cheap HD card faces | `--backend diy` |
| DIY quality fails A/B | `--backend meowa` (~+16% cost acceptable) |
| Per-card rescue | `--backend meowa --ids <id> --force` or recover Meowa jobs |
| Pixel sprites / bg remove / maps / animation | Meowa `game-assets` specialty commands — not DIY |

## A/B decision protocol

1. Pick 6–8 missing cards across themes (or `--ids`).
2. Run `npm run art:ab` (writes scorecard template under `.workspace/card-art/ab/`).
3. Score each backend 1–5: voxel readability, theme identity, style consistency, no text artifacts, centered composition.
4. Decision:
   - DIY average ≥4 → default mass-produce **diy**
   - DIY clearly worse → default **meowa**
   - Only a few failures → keep diy, patch with meowa
5. Record the chosen default in this skill under **Default backend** after the run.

### Default backend

`diy` (exploration default until an A/B scorecard says otherwise).

## Safety

- Do not call DIY or Meowa from the game / cardbattle runtime servers
- Do not log or commit API keys (`TEXTECH_IMAGE_*`, `MEOWART_API_KEY`)
- Do not promote into `src/main/resources` unless explicitly requested for jar packaging
- Interrupted Meowa jobs: `npm run art:recover` (never implicit resubmit)
