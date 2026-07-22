import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const serverRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const repoRoot = path.resolve(serverRoot, '..');
const catalogPath = path.join(repoRoot, 'src', 'main', 'resources', 'assets', 'textech', 'cardbattle', 'cards.json');
const publicArt = path.join(serverRoot, 'public', 'card-art');
const outputRoot = path.join(repoRoot, '.workspace', 'card-art');
const manifestPath = path.join(outputRoot, 'meowa-requirements.json');

const cards = JSON.parse(fs.readFileSync(catalogPath, 'utf8'));
const requirements = cards
  .filter((card) => !fs.existsSync(path.join(publicArt, card.art || `${card.id}.png`)))
  .map((card) => ({
    id: card.id,
    theme: card.theme,
    runtimeFile: `cardbattle-server/public/card-art/${card.id}.png`,
    outputDir: `.workspace/card-art/generated/${card.id}`,
    aspectRatio: '1:1',
    requirement: [
      'GTNH-inspired HD trading card portrait',
      `theme=${card.theme}`,
      `subject=${card.nameZh || card.name}`,
      `card type=${card.kind}`,
      card.keywords?.length ? `visual motifs=${card.keywords.join(',')}` : null,
      'single centered subject, readable silhouette, dark vignette, cohesive mod-themed materials',
      'no text, no letters, no numbers, no logos, no watermark, no card frame',
    ]
      .filter(Boolean)
      .join(', '),
  }));

fs.mkdirSync(outputRoot, { recursive: true });
fs.writeFileSync(
  manifestPath,
  `${JSON.stringify({ schemaVersion: 1, generatedAt: new Date().toISOString(), requirements }, null, 2)}\n`,
  'utf8',
);
console.log(`Wrote ${requirements.length} requirements to ${manifestPath}`);
