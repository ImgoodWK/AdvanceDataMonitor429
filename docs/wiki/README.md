# TeXTech GitHub Wiki navigation source

> Maintenance note / 维护说明：this directory is the version-controlled source for the navigation-only GitHub Wiki. A dedicated workflow publishes these Markdown pages to the Wiki repository.

<p align="center">
  <img src="https://raw.githubusercontent.com/ImgoodWK/TeXTech-GTNH/master/docs/assets/brand/textech-social-preview-1280x640.png" alt="TeXTech social preview used by the Wiki navigation" width="70%">
</p>

## Visual asset system / 视觉资产体系

Wiki pages use repository-hosted, cache-friendly raw URLs so the cover and decorative panels render in GitHub Wiki without a relative-path dependency:

- Brand / 品牌: `docs/assets/brand/textech-hero-1920x480.png`, `textech-logo-512.png`, and `textech-social-preview-1280x640.png`.
- WebAE gallery / WebAE 画廊: `docs/assets/webae/dashboard.png`, `diagnostics.png`, `patterns.png`, `storage.png`, and `topology.png`.
- Wiki/README media pack / Wiki 与 README 素材包: `docs/assets/promo/wiki/wiki-hero-1600x420.png`, the four `feature-*.png` cards, `data-divider-1200x96.png`, and the lightweight `data-stream.gif`. See the [asset notes](https://github.com/ImgoodWK/TeXTech-GTNH/blob/master/docs/assets/promo/wiki/README.md) for dimensions, sources, and regeneration boundaries.

Images are orientation and decoration only. Alt text must remain meaningful, and a visual must never imply a live server state, player identity, secret, or a feature that the canonical docs do not describe.

The Wiki uses stable raw URLs for these files, for example:

```text
https://raw.githubusercontent.com/ImgoodWK/TeXTech-GTNH/master/docs/assets/promo/wiki/feature-monitor-640x360.png
```

## Synchronization boundary / 同步边界

`docs/` and machine-readable project data remain canonical. Wiki pages must stay short, declare that boundary at the top, and link to both Chinese and English source documents rather than copying technical content. When a public behavior or URL changes, update the canonical docs first, then adjust the Wiki signpost and its paired-language wording.

Published pages / 已发布页面：`Home`, `Getting Started`, `Features`, `WebAE`, `AI and Voice`, `Development`, `Project History`, `Security and Provenance`, and `_Sidebar`.

<details>
  <summary>Editing checklist / 编辑检查清单</summary>

  - Keep English and Chinese headings, cards, and CTA order aligned.
  - Keep the navigation-only boundary statement near the top of every page.
  - Prefer HTML alignment, tables, details blocks, and separators; do not add CSS that GitHub will strip.
  - Preserve canonical links and readable image alt text.
  - Run the documentation checker and a lightweight raw-asset URL check before publishing.
</details>
