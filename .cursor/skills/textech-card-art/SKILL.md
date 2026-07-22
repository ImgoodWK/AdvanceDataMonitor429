---
name: textech-card-art
description: Generate and import TeXTech card-battle art via Meowa game-assets. Use when creating card faces, promoting PNGs into cardbattle-server/public/card-art, or batching CardDef art fields. Never commit MEOWART_API_KEY.
---

# TeXTech Card Art (Meowa)

## Prerequisites

1. Install Meowa skill: `npx skills add https://github.com/Meowa-AI/meowa-skills --skill game-assets`
2. Set **user** env only: `MEOWART_API_KEY` (never paste into chat/repo). Rotate if exposed.
3. Before generate: run `meowart_api.py skill-doc --task "HD card portrait 1:1 transparent optional"`.

## Asset contract

- Runtime: static PNG under `cardbattle-server/public/card-art/<cardId>.png`
- Prefer HD 1:1 square; dynamic layers (cost/ATK/HP/keywords) are UI, not baked into art
- Working outputs: `D:\dev-agent-cache\tmp\card-art\` or `.workspace/card-art/` then promote

## Workflow

1. Read card rows from `cardbattle-server/src/data/catalog.ts` (`id`, `nameZh`, `theme`, `keywords`)
2. Batch with Meowa `image-2-run` / `nano-banana-run` (guide current names); one requirement per card
3. Prompt pattern: `GTNH-inspired trading card portrait, theme=<theme>, subject=<nameZh>, no text, no numbers, centered character, dark vignette`
4. Copy finals to `cardbattle-server/public/card-art/<id>.png`
5. Optionally set `art: "<id>.png"` on `CardDef` (frontend also falls back to `/card-art/<id>.png` if you name files by id — today UI uses `def.art` only; prefer setting `art` field)

## Safety

- Do not call Meowa from the game server at runtime
- Do not log or commit API keys
- Reject writing into `src/main/resources` unless explicitly requested later for jar packaging
