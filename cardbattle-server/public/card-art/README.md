# Card faces (`<cardId>.png`)

See `.cursor/skills/textech-card-art/SKILL.md`.

## Style

HD 1:1 **voxel cinematic still** (in-game screenshot feel): cubic volumetric forms,
hard-edged materials, centered subject, dark vignette. No text/numbers/frames.
Cost / ATK / HP stay in UI.

## Commands (`cardbattle-server/`)

```powershell
npm.cmd run art:index-refs -- --mods-dir "D:\path\to\GTNH\mods"
npm.cmd run art:requirements
npm.cmd run art:generate -- --backend diy --limit 5
npm.cmd run art:generate -- --backend meowa --limit 5 --quality standard
npm.cmd run art:ab -- --limit 4
npm.cmd run art:recover
```

- Requirements land in ignored `.workspace/card-art/art-requirements.json` (mirrored as `meowa-requirements.json`).
- DIY needs `TEXTECH_IMAGE_API_KEY` (+ optional `TEXTECH_IMAGE_BASE_URL` / `TEXTECH_IMAGE_MODEL`).
- Meowa needs `MEOWART_API_KEY`. Never commit keys; exporters never read or write them into the repo.
- Successful 1024×1024 PNGs are validated via `final_outputs.json` before promotion.
- Interrupted Meowa jobs stay in `.workspace/card-art/meowa-state.json` and are never resubmitted implicitly.
- Use `--retry-unsubmitted` only for failures that never received a job id; otherwise `art:recover`.
- Production jar may also serve copies from `assets/textech/cardbattle/card-art/`.
