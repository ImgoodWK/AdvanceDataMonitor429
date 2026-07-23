import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { workspaceArtRoot, loadRequirements, writeJson } from './lib/card-art-common.mjs';

/**
 * Small-sample A/B: same requirements + refs through diy and meowa without promoting.
 *
 *   npm run art:ab -- --ids van_scout,gt_wrench --limit 2
 *
 * Outputs under .workspace/card-art/ab/<timestamp>/ and a scorecard template JSON.
 */

const scriptsDir = path.dirname(fileURLToPath(import.meta.url));

function parseArgs(argv) {
  const options = {
    ids: null,
    limit: 4,
    quality: 'standard',
    backends: ['diy', 'meowa'],
    dryRun: false,
  };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--ids') options.ids = argv[++i];
    else if (arg === '--limit') options.limit = Number(argv[++i]);
    else if (arg === '--quality') options.quality = argv[++i];
    else if (arg === '--backends') options.backends = argv[++i].split(',').map((value) => value.trim());
    else if (arg === '--dry-run') options.dryRun = true;
    else throw new Error(`Unknown argument: ${arg}`);
  }
  return options;
}

function runGenerate(backend, abRoot, options) {
  return new Promise((resolve, reject) => {
    const args = [
      path.join(scriptsDir, 'generate-card-art.mjs'),
      '--backend',
      backend,
      '--no-promote',
      '--force',
      '--limit',
      String(options.limit),
      '--quality',
      options.quality,
      '--output-root',
      abRoot,
    ];
    if (options.dryRun) args.push('--dry-run');
    if (options.ids) {
      args.push('--ids', options.ids);
    }
    const child = spawn(process.env.NODE || 'node', args, {
      cwd: path.join(scriptsDir, '..'),
      env: process.env,
      stdio: 'inherit',
      windowsHide: true,
    });
    child.on('error', reject);
    child.on('close', (code) => resolve(code ?? 1));
  });
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const requirements = loadRequirements().requirements;
  const selected = requirements
    .filter((row) => !options.ids || options.ids.split(',').map((id) => id.trim()).includes(row.id))
    .slice(0, options.limit);
  if (!selected.length) {
    throw new Error('No requirements matched; run npm run art:requirements first (or pass --ids of missing cards)');
  }

  const stamp = new Date().toISOString().replace(/[:.]/g, '-');
  const abRoot = path.join(workspaceArtRoot, 'ab', stamp);
  fs.mkdirSync(abRoot, { recursive: true });

  const scorecard = {
    schemaVersion: 1,
    createdAt: new Date().toISOString(),
    quality: options.quality,
    cards: selected.map((row) => ({
      id: row.id,
      theme: row.theme,
      styleRefs: row.styleRefs,
      subjectRefs: row.subjectRefs,
      scores: {
        diy: { voxelReadability: null, themeIdentity: null, styleConsistency: null, noTextArtifacts: null, centeredComposition: null, notes: '' },
        meowa: { voxelReadability: null, themeIdentity: null, styleConsistency: null, noTextArtifacts: null, centeredComposition: null, notes: '' },
      },
      decision: null,
    })),
    decisionRules: [
      'If diy meets all score dimensions (>=4/5 average) → mass-produce with diy',
      'If diy is clearly worse → mass-produce with meowa (accept ~+16% cost)',
      'If only a few cards fail → patch those cards with --backend meowa',
    ],
  };
  writeJson(path.join(abRoot, 'scorecard.json'), scorecard);

  const exitCodes = {};
  for (const backend of options.backends) {
    console.log(`\n=== A/B backend=${backend} ===`);
    exitCodes[backend] = await runGenerate(backend, abRoot, options);
  }

  writeJson(path.join(abRoot, 'run-meta.json'), {
    exitCodes,
    abRoot: path.relative(path.join(scriptsDir, '..', '..'), abRoot).replace(/\\/g, '/'),
    cardIds: selected.map((row) => row.id),
  });
  console.log(`\nA/B outputs: ${abRoot}`);
  console.log('Fill scorecard.json then update .cursor/skills/textech-card-art/SKILL.md default backend.');
  if (Object.values(exitCodes).some((code) => code !== 0)) process.exitCode = 1;
}

main().catch((error) => {
  console.error(error.stack || error.message);
  process.exitCode = 1;
});
