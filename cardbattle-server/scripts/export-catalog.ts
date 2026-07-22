import fs from 'node:fs';
import path from 'node:path';
import { CARD_CATALOG } from '../src/data/catalog.js';

const output = path.resolve(
  process.cwd(),
  '..',
  'src',
  'main',
  'resources',
  'assets',
  'textech',
  'cardbattle',
  'cards.json',
);

fs.mkdirSync(path.dirname(output), { recursive: true });
fs.writeFileSync(output, `${JSON.stringify(CARD_CATALOG, null, 2)}\n`, 'utf8');
console.log(`Exported ${CARD_CATALOG.length} cards to ${output}`);
