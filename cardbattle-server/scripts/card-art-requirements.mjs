import fs from 'node:fs';
import path from 'node:path';
import {
  buildRequirementText,
  resolveStyleRefs,
  MAX_REFERENCE_IMAGES,
  styleBibleMarkdown,
} from './lib/card-art-style.mjs';
import {
  workspaceArtRoot,
  requirementsPath,
  legacyRequirementsPath,
  publicArtDir,
  writeJson,
  readJson,
  repoRoot,
} from './lib/card-art-common.mjs';

const catalogPath = path.join(repoRoot, 'src', 'main', 'resources', 'assets', 'textech', 'cardbattle', 'cards.json');
const refsIndexPath = path.join(workspaceArtRoot, 'refs', 'index.json');

/** Soft theme → preferred modId substrings for subject matching. */
const THEME_MOD_HINTS = {
  vanilla: ['minecraft', 'vanilla'],
  gt: ['gregtech', 'gt5', 'gtnh', 'greg'],
  thaum: ['thaum', 'forbidden'],
  forestry: ['forestry', 'binnie', 'gendustry'],
  astral: ['astral', 'witchery'],
  avaritia: ['avaritia'],
  ee: ['projecte', 'ee3', 'equivalent'],
  genetics: ['gendustry', 'genetics', 'binnie'],
  ae: ['appliedenergistics', 'ae2', 'ae2stuff'],
  dlb: ['draconic', 'dlb'],
};

function scoreEntry(entry, card) {
  const hay = `${entry.modId} ${entry.jarEntry} ${(entry.keywords || []).join(' ')}`.toLowerCase();
  let score = 0;
  const themeHints = THEME_MOD_HINTS[card.theme] || [];
  let themeHit = false;
  for (const hint of themeHints) {
    if (hay.includes(hint.toLowerCase())) {
      score += 12;
      themeHit = true;
    }
  }
  // Without a theme/mod hit, only keep strong keyword matches (avoid cross-mod noise).
  const tokens = [
    card.id,
    card.name,
    ...(card.keywords || []),
  ]
    .filter(Boolean)
    .flatMap((value) => String(value).toLowerCase().split(/[^a-z0-9]+/))
    .filter((token) => token.length >= 3);
  let keywordHits = 0;
  for (const token of tokens) {
    if (hay.includes(token)) {
      keywordHits += 1;
      score += token.length >= 5 ? 4 : 2;
    }
  }
  if (!themeHit && keywordHits === 0) return 0;
  if (!themeHit && keywordHits < 2) return 0;
  if (entry.kind === 'entity' && /unit|creature|mob|bee|golem|zombie|wisp|knight|worker/i.test(card.id)) score += 2;
  if (entry.kind === 'item' && /spell|tool|wrench|sword|battery|capacitor/i.test(card.id)) score += 2;
  if (entry.kind === 'block' && /machine|furnace|node|hive|turbine/i.test(card.id)) score += 2;
  return score;
}

function pickSubjectRefs(card, indexEntries, styleCount) {
  if (!indexEntries?.length) return [];
  const budget = Math.max(0, MAX_REFERENCE_IMAGES - styleCount);
  if (!budget) return [];
  const ranked = indexEntries
    .map((entry) => ({ entry, score: scoreEntry(entry, card) }))
    .filter((row) => row.score >= 8)
    .sort((a, b) => b.score - a.score);
  const picked = [];
  const seen = new Set();
  for (const row of ranked) {
    if (picked.length >= Math.min(3, budget)) break;
    if (seen.has(row.entry.path)) continue;
    if (!fs.existsSync(path.join(repoRoot, row.entry.path))) continue;
    seen.add(row.entry.path);
    picked.push({
      role: 'subject',
      path: row.entry.path,
      kind: row.entry.kind,
      score: row.score,
      modId: row.entry.modId,
    });
  }
  return picked;
}

const includeAll = process.argv.includes('--all');
const frozenStyleDir = path.join(workspaceArtRoot, 'style-goldens');

const cards = JSON.parse(fs.readFileSync(catalogPath, 'utf8'));
let indexEntries = [];
if (fs.existsSync(refsIndexPath)) {
  try {
    indexEntries = readJson(refsIndexPath).entries || [];
  } catch {
    indexEntries = [];
  }
}

const requirements = cards
  .filter((card) => includeAll || !fs.existsSync(path.join(publicArtDir, card.art || `${card.id}.png`)))
  .map((card) => {
    const styleRefs = resolveStyleRefs(card.theme, publicArtDir, fs, path, {
      frozenStyleDir: fs.existsSync(frozenStyleDir) ? frozenStyleDir : null,
      repoRoot,
    });
    const subjectRefs = pickSubjectRefs(card, indexEntries, styleRefs.length);
    return {
      id: card.id,
      theme: card.theme,
      runtimeFile: `cardbattle-server/public/card-art/${card.id}.png`,
      outputDir: `.workspace/card-art/generated/${card.id}`,
      aspectRatio: '1:1',
      styleRefs,
      subjectRefs,
      requirement: buildRequirementText(card),
    };
  });

fs.mkdirSync(workspaceArtRoot, { recursive: true });
fs.writeFileSync(path.join(workspaceArtRoot, 'style-bible.md'), `${styleBibleMarkdown()}\n`, 'utf8');

const payload = {
  schemaVersion: 2,
  generatedAt: new Date().toISOString(),
  style: 'voxel-cinematic-still',
  refsIndexed: indexEntries.length,
  requirements,
};
writeJson(requirementsPath, payload);
writeJson(legacyRequirementsPath, payload);
console.log(`Wrote ${requirements.length} requirements to ${requirementsPath}${includeAll ? ' (--all)' : ''}`);
console.log(`Also mirrored to ${legacyRequirementsPath} for recover compatibility`);
console.log(`Subject refs attached on ${requirements.filter((row) => row.subjectRefs.length).length} card(s)`);
if (fs.existsSync(frozenStyleDir)) {
  console.log(`Using frozen style goldens from ${path.relative(repoRoot, frozenStyleDir)}`);
}
