# cardbattle-frontend agent guidance

Root `AGENTS.md` applies. This package builds into `src/main/resources/assets/textech/cardbattle/`.

## Stack

- React 18 + Vite 5 + TypeScript
- **Framer Motion** — UI transitions, hand fan, hover lift
- **GSAP** — combat FX timeline (impact rings, dust, slot flash)
- **@use-gesture/react** — drag-to-play

## Conventions

- Design tokens: `src/lib/themeTokens.ts`
- Board skins: `src/lib/skins.ts` + `src/styles/skins.css`
- Drag legality: `src/lib/playLegality.ts` (keep pure; covered by Vitest)
- Screens: `LoginScreen` / `LobbyScreen` / `BattleScreen`; keep `App.tsx` as state shell only
- Card art URL: `/card-art/${def.art || def.id + '.png'}` — never bake cost/ATK/HP into art

## Commands

```powershell
npm.cmd test -- --run
npm.cmd exec tsc -- --noEmit
npm.cmd run build
```

Do not commit `TEXTECH_IMAGE_API_KEY` or `MEOWART_API_KEY`. Card generation uses `.cursor/skills/textech-card-art/`.
