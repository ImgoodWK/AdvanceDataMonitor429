/**
 * Voxel / in-game cinematic still contract for TeXTech card faces.
 * Do not name Minecraft, Steve, or copyrighted skins in prompts.
 */

export const STYLE_SUFFIX = [
  'HD cinematic still that looks like a captured in-game screenshot',
  'cubic volumetric forms and discrete hard-edged material facets',
  'readable modular machine or creature silhouette',
  'soft in-engine lighting, subtle ambient occlusion, dark vignette',
  'subject fills the central 70 percent of the frame',
  'no HUD, no inventory UI, no hotbar, no crosshair, no debug overlay',
  'no text, no letters, no numbers, no logos, no watermark, no card frame',
  'style reference controls lighting, material language, and composition only, never subject identity',
  'subject reference controls identity and material motifs only, never inventory-icon layout',
].join(', ');

/** Theme palette hints aligned with cardbattle-frontend themeTokens (materials/light only). */
export const THEME_MATERIAL_HINTS = {
  vanilla: 'earthy greens, oak and stone blocks, soft daylight',
  gt: 'industrial steel and copper pipes, amber machine glow, cool blue panel lights',
  thaum: 'arcane purple and deep crimson, glowing runes as light sources only without readable glyphs',
  forestry: 'honey amber, wood grain, leaf greens, apiary warmth',
  astral: 'starlight cyan and pale blue crystal luminescence',
  avaritia: 'near-white metal, cosmic void blacks, crystalline singularity sheen',
  ee: 'alchemy orange, dark matter charcoal, furnace ember glow',
  genetics: 'bio-lab greens, glass tanks, organic circuitry',
  ae: 'fluix teal, dense cable lattices, clean ME machine housings',
  dlb: 'danger red accents, scorched metal, harsh hazard lighting',
};

/**
 * Per-theme style golden under cardbattle-server/public/card-art/.
 * Themes without dedicated goldens fall back to a close neighbor.
 */
export const THEME_STYLE_GOLDENS = {
  vanilla: 'van_knight.png',
  gt: 'gt_worker.png',
  thaum: 'th_cultist.png',
  forestry: 'fo_keeper.png',
  astral: 'as_acolyte.png',
  avaritia: 'av_sword.png',
  ee: 'ee_rm.png',
  genetics: 'gt_worker.png',
  ae: 'gt_lv_machine.png',
  dlb: 'th_boss.png',
};

export const MAX_REFERENCE_IMAGES = 8;

export function styleBibleMarkdown() {
  return `# TeXTech card-art style bible (voxel cinematic still)

## Look
- HD 1:1 portrait that reads like an **in-game cinematic screenshot**, not a low-res pixel sprite sheet.
- Cubic volumetric forms, discrete hard-edged material facets, modular machines/creatures.
- Soft in-engine lighting, readable silhouette, centered subject (~70% frame), dark vignette.

## Prompt language (allowed)
- cubic volumetric forms, discrete material facets, modular housings, soft in-engine lighting
- readable silhouette, dark vignette, cinematic still, no HUD

## Prompt language (forbidden)
- Do **not** name Minecraft, Steve, Alex, Notch, or specific copyrighted skins.
- Do **not** bake cost / ATK / HP / keywords / card frames into the image.
- Do **not** ask for inventory icons, hotbar, debug F3 overlay, or watermark text.

## Reference roles
- **styleRefs**: approved goldens — lighting, material language, composition only.
- **subjectRefs**: mod textures / entity cues — identity and motifs only; never copy UI icon layout.

## Theme goldens
${Object.entries(THEME_STYLE_GOLDENS)
  .map(([theme, file]) => `- \`${theme}\` → \`cardbattle-server/public/card-art/${file}\``)
  .join('\n')}

## Theme material hints
${Object.entries(THEME_MATERIAL_HINTS)
  .map(([theme, hint]) => `- **${theme}**: ${hint}`)
  .join('\n')}

## Backends
- Default exploration: DIY OpenAI-compatible GPT Image 2 (\`TEXTECH_IMAGE_*\` env).
- Quality fallback: Meowa \`image-2-run\` (\`MEOWART_API_KEY\`).
- Meowa remains for non-card work: pixel sprites, bg removal, maps, animation.
`;
}

export function buildRequirementText(card) {
  const theme = card.theme || 'vanilla';
  const materials = THEME_MATERIAL_HINTS[theme] || THEME_MATERIAL_HINTS.vanilla;
  return [
    'GTNH-modpack-inspired HD voxel cinematic still for a trading-card portrait',
    `theme=${theme}`,
    `subject=${card.nameZh || card.name}`,
    `card type=${card.kind}`,
    card.keywords?.length ? `visual motifs=${card.keywords.join(',')}` : null,
    `material and light cues=${materials}`,
    'single centered subject, readable silhouette, cubic volumetric forms, dark vignette',
    'no text, no letters, no numbers, no logos, no watermark, no card frame, no HUD',
  ]
    .filter(Boolean)
    .join(', ');
}

export function resolveStyleRefs(theme, publicArtDir, fs, path, options = {}) {
  const file = THEME_STYLE_GOLDENS[theme] || THEME_STYLE_GOLDENS.vanilla;
  const frozenDir = options.frozenStyleDir;
  if (frozenDir) {
    const frozen = path.join(frozenDir, file);
    if (fs.existsSync(frozen)) {
      return [{ role: 'style', path: `.workspace/card-art/style-goldens/${file}` }];
    }
    const gtFile = THEME_STYLE_GOLDENS.gt;
    const frozenGt = path.join(frozenDir, gtFile);
    if (fs.existsSync(frozenGt)) {
      return [{ role: 'style', path: `.workspace/card-art/style-goldens/${gtFile}` }];
    }
  }
  const absolute = path.join(publicArtDir, file);
  if (!fs.existsSync(absolute)) {
    const fallback = path.join(publicArtDir, THEME_STYLE_GOLDENS.gt);
    if (fs.existsSync(fallback)) {
      return [{ role: 'style', path: `cardbattle-server/public/card-art/${THEME_STYLE_GOLDENS.gt}` }];
    }
    return [];
  }
  return [{ role: 'style', path: `cardbattle-server/public/card-art/${file}` }];
}
