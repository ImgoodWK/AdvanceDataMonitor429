# TeXTech brand assets

> 中文说明见下半部分。 These are the approved public assets for TeXTech / 铽丝科技. Drafts, rejected variants, contact sheets, and generation metadata are intentionally excluded from the repository.

## Published files

| File | Size | Purpose |
|---|---:|---|
| `textech-logo-512.png` | 512×512 RGBA | Transparent primary mark for documentation and high-resolution listings |
| `../../../src/main/resources/assets/textech/textures/promo/textech-icon-256.png` | 256×256 RGBA | In-mod icon referenced by `mcmod.info` |
| `textech-hero-1920x480.png` | 1920×480 RGB | README and project header |
| `textech-social-preview-1280x640.png` | 1280×640 RGB | GitHub Social Preview |

The visual direction is B6, “monitor + woven ring”: the monitor represents AE2 observation and WebAE; the continuous ring represents binary threads weaving data back into matter. The mark contains no generated lettering. All `TeXTech`, `铽丝科技`, and slogan text in the banner and social image was composed deterministically after generation.

## Palette and typography

- Deep Void `#0E1A30`
- Panel Navy `#122040`
- Core Cyan `#00FFFF`
- Sidebar Ice `#C8E0FF`
- Restrained blue-violet accent `#705CFF`
- English display type: Bahnschrift, rasterized only; the font file is not distributed.
- Chinese display type: Microsoft YaHei, rasterized only; the font file is not distributed.

## Generation record

- Date: 2026-08-05
- Art direction and selection: ImgoodWK with Codex assistance
- Graphic generation: OpenAI ImageGen built into Codex, using the locally reviewed B6 refinement sheet as a visual reference
- Post-processing: local chroma-key removal with a soft matte and despill; deterministic resize, layout, background pattern, text, and export with Pillow
- Review: source dimensions and hashes were checked first; only thumbnails with a maximum edge of 512 px were inspected. Transparent corners, alpha bounds, and 512/256/64/32 px readability were checked locally.

Final graphic-generation prompt:

```text
Use case: logo-brand
Asset type: final square logo mark for the TeXTech Minecraft GTNH mod
Input image: the supplied four-panel B6 refinement sheet. Use only the top-right "ring + monitor" concept as the design reference; do not reproduce the sheet, labels, or other variants.
Primary request: refine the futuristic front-view data monitor with a woven continuous-loop ring centered inside the screen. The woven ring represents binary threads weaving data into reality; the monitor represents AE2 monitoring and WebAE.
Style/medium: original clean vector-like logo mark, flat geometric shapes, crisp hard edges, minimal, scalable, no photorealism, no 3D mockup.
Composition/framing: one centered icon, square 1:1, generous 12% padding, monitor silhouette large and balanced, woven ring clearly readable at 32px, no corner emblems, no surrounding scene.
Color palette: Deep Void #0E1A30 for the monitor body and inner negative-space accents, Core Cyan #00FFFF for primary lines, Sidebar Ice #C8E0FF for restrained highlights, one very small blue-violet accent only if necessary.
Scene/backdrop: perfectly flat solid #00FF00 chroma-key background for local removal.
Constraints: NO TEXT, NO LETTERS, NO NUMBERS, NO LABELS, NO WATERMARK. Keep only one monitor and one woven ring. Background must be exactly one uniform #00FF00 color with no shadow, gradient, texture, reflection, floor plane, glow spill, or lighting variation. Do not use #00FF00 inside the icon. Strong silhouette, controlled stroke widths, no tiny chart grid, no decorative micro-detail, no outer halo, crisp antialiased boundary suitable for transparent cutout.
```

The green generation background was an explicit, temporary chroma key and is not part of the brand palette or any published image.

## 中文说明

本目录只保留四份正式公开资产：512×512 透明 Logo、256×256 游戏/模组图标、1920×480 README Hero 和 1280×640 GitHub Social Preview。完整候选集、失败变体、接触表和生成任务元数据均为本地创作过程文件，不进入公开仓库。

正式方向采用 B6“监视器＋编织环”：监视器对应 AE2 数据观察与 WebAE，连续编织环对应“用二进制之线把数据重新编织为现实”。模型只生成无文字图形；`TeXTech`、`铽丝科技` 与中英文 Slogan 均使用确定性字体在本地排版，避免 AI 拼写错误。

除第三方名称及另有说明的内容外，这些 TeXTech 项目资产随仓库按 MIT License 发布。生成过程未加入第三方 Logo、隐藏追踪、遥测或不可见指令。
