# Card Battle UI Contract

The shell uses a cinematic GTNH industrial direction: dark metal, theme glow, a rounded arena, and layered glass panels. Wide battle screens use three columns—decision/stack, arena/hand, and card dossier—while narrow screens collapse to one column without shrinking the board below a readable width. Card cost, attack, health, armor, keywords, and Slow/Fast/Burst badges remain live UI layers.

## Interaction

- The opening screen toggles each of four cards between keep and replace, displays the selected count, submits `confirm_mulligan`, and keeps the board locked until both players confirm.
- The HUD always shows action priority, attack-token ownership, regular mana, spell mana, and GT capacitor storage separately.
- Hand play is enabled only during the local player's priority window. Main windows accept normal plays; response windows accept fast and burst spells only.
- Units and structures drop onto the **bench** (auto-seat / optional prefer slot). Spell targeting follows `effect.target`. Drop resolution uses `elementFromPoint` on `[data-drop]`.
- Drag and click legality highlights enforce speed timing plus targeted-spell constraints before submission; the server repeats the same authoritative validation.
- Hovering, keyboard-focusing, or selecting a card updates a pinnable dossier with authoritative `rulesZh`, timing, targeting, keyword definitions, aspects, and live attack/health/armor/cooldown values.
- An optional Battle Coach tip line summarizes legal actions (`localStorage.textech_cb_coach`, default on).
- `Start attack` is enabled only for the attack-token owner; `Pass priority` ends the round only after a consecutive opposing pass.
- The contextual action bar renders only decisions legal in the current phase. Attack declaration drags bench units onto the battlefield row (LTR = order via `instanceIds`); empty confirmation cancels and preserves the token. Defense drags bench blockers onto attackers and keeps ghost blocks visible.
- `spell_response` and `combat_response` render a dedicated stack panel with the top item first, caster and speed on every layer, and an explicit empty-stack combat state. Pass labels distinguish giving up a response from confirming spell-stack or combat resolution.
- Combat response appears after blocks and optional Thaumcraft repositioning. A `Skip mystic swap` action prevents a defender from becoming stuck when repositioning is available but unwanted; mystic swap picks two bench slots.
- The adventure map is column based and visually distinguishes available, completed, and disconnected nodes.

## Animation and implementation

| Library | Responsibility |
|---|---|
| Framer Motion | Phase banners, card mounting, hand motion, and selection lift. |
| `@use-gesture/react` | Pointer drag-to-play. |
| GSAP | Per-card attack lunges, strike flashes, damage values, recoil, impact rings, dust, and board flashes. |

Combat resolution snapshots each attacking card, then animates the ordered attackers toward their blocker or the opposing Nexus one by one. Each strike produces a slash flash and attack value before recoil/fade. Nexus damage also uses a floating number, screen shake, and red vignette. Reduced-motion users receive the readable state transition without the lunge timeline.

## Skins

Board skins are `gt_factory` (default), `thaum_workshop` (2 lifetime wins), `astral_observatory` (5 lifetime wins), and `overclocked_nexus` (boss reward). Dynamic card values remain UI layers and must not be baked into art.

Skin unlocks are cosmetic and do not affect authoritative match state. Victory migration remains local for the two legacy skins; boss rewards use the explicit `unlockedSkinIds` response.

## Card art

Card portraits use an HD 1:1 **voxel cinematic still** contract (in-game screenshot feel: cubic volumetric forms, hard-edged materials, centered subject, dark vignette)—not low-res pixel sprites. Cost / ATK / HP / keywords remain UI layers. Runtime serves `/card-art/<cardId>.png` (standalone) or the jar asset directory (embedded). Missing PNGs must fall back to a theme placeholder.

Generation: `.cursor/skills/textech-card-art/`. Default exploration backend is DIY GPT Image 2 (`TEXTECH_IMAGE_*`); Meowa `image-2-run` (`MEOWART_API_KEY`) is the quality fallback. Optional mod texture indexing (`npm run art:index-refs`) feeds `styleRefs` / `subjectRefs` via `art:requirements`. Compare backends with `npm run art:ab` before locking the mass-produce default.

Theme palettes and phase labels live in `src/lib/themeTokens.ts`; skin definitions and unlock policy live in `src/lib/skins.ts`.

The Java server and TypeScript mirror expose the same battle fields consumed by this UI. New visual states must remain readable without animation, and drag interactions must keep their click fallback for embedded-browser compatibility.
