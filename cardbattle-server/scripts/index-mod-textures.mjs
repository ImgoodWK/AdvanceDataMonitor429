import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { workspaceArtRoot, writeJson, repoRoot } from './lib/card-art-common.mjs';

/**
 * Index mod jar textures for card-art subject references.
 * Outputs (gitignored):
 *   .workspace/card-art/refs/index.json
 *   .workspace/card-art/refs/files/<modId>/...
 *
 * Usage:
 *   node scripts/index-mod-textures.mjs
 *   node scripts/index-mod-textures.mjs --mods-dir D:/GTNH/mods
 */

function parseArgs(argv) {
  const options = {
    modsDirs: [path.join(repoRoot, 'libs')],
    limitJars: 50,
    maxFilesPerJar: 80,
  };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--mods-dir') options.modsDirs.push(path.resolve(argv[++i]));
    else if (arg === '--limit-jars') options.limitJars = Number(argv[++i]);
    else if (arg === '--max-files-per-jar') options.maxFilesPerJar = Number(argv[++i]);
    else throw new Error(`Unknown argument: ${arg}`);
  }
  return options;
}

function listJars(dirs) {
  const jars = [];
  for (const dir of dirs) {
    if (!fs.existsSync(dir)) continue;
    for (const name of fs.readdirSync(dir)) {
      if (!name.toLowerCase().endsWith('.jar')) continue;
      jars.push(path.join(dir, name));
    }
  }
  return jars;
}

function guessKind(entryPath) {
  const lower = entryPath.toLowerCase().replace(/\\/g, '/');
  // Skip UI chrome — useless as cinematic subject refs.
  if (lower.includes('/textures/gui') || lower.includes('/textures/guis') || lower.includes('/gui/')) {
    return null;
  }
  if (lower.includes('/textures/font') || lower.includes('/textures/misc/')) return null;
  if (lower.includes('/textures/items/') || lower.includes('/textures/item/')) return 'item';
  if (lower.includes('/textures/blocks/') || lower.includes('/textures/block/')) return 'block';
  if (lower.includes('/textures/entity/') || lower.includes('/textures/entities/')) return 'entity';
  if (lower.includes('/textures/models/')) return 'model';
  // Do not index generic /textures/ catch-all (often UI atlases).
  return null;
}

function keywordsFromPath(entryPath) {
  const base = path.basename(entryPath, path.extname(entryPath)).toLowerCase();
  const parts = entryPath
    .toLowerCase()
    .replace(/\\/g, '/')
    .split('/')
    .flatMap((segment) => segment.split(/[^a-z0-9]+/))
    .filter((token) => token.length >= 3);
  return [...new Set([base, ...parts])].slice(0, 24);
}

function listJarEntries(jarPath) {
  const tar = spawnSync('tar', ['-tf', jarPath], { encoding: 'utf8', windowsHide: true, maxBuffer: 32 * 1024 * 1024 });
  if (tar.status === 0) {
    return tar.stdout.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
  }
  const jar = spawnSync('jar', ['tf', jarPath], { encoding: 'utf8', windowsHide: true, maxBuffer: 32 * 1024 * 1024 });
  if (jar.status === 0) {
    return jar.stdout.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
  }
  throw new Error((tar.stderr || jar.stderr || 'unable to list jar').toString().slice(0, 200));
}

function extractEntry(jarPath, entry, outRoot) {
  const safeRel = entry.replace(/^\/+/, '').replace(/\.\./g, '_');
  const dest = path.join(outRoot, safeRel);
  fs.mkdirSync(path.dirname(dest), { recursive: true });

  const tar = spawnSync('tar', ['-xf', jarPath, '-C', outRoot, entry], {
    encoding: 'utf8',
    windowsHide: true,
  });
  if (tar.status === 0 && fs.existsSync(dest)) return dest;

  const jar = spawnSync('jar', ['xf', jarPath, entry], { cwd: outRoot, encoding: 'utf8', windowsHide: true });
  if (jar.status === 0 && fs.existsSync(dest)) return dest;
  return null;
}

function modIdFromJar(jarPath) {
  const base = path.basename(jarPath, '.jar').toLowerCase();
  return base.replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'mod';
}

function extractMatchingPngs(jarPath, outRoot, maxFiles) {
  const listing = listJarEntries(jarPath);
  const candidates = listing.filter((line) => line.toLowerCase().endsWith('.png') && guessKind(line)).slice(0, maxFiles);
  const extracted = [];
  for (const entry of candidates) {
    const found = extractEntry(jarPath, entry, outRoot);
    if (!found) continue;
    extracted.push({
      jarEntry: entry,
      path: path.relative(repoRoot, found).replace(/\\/g, '/'),
      kind: guessKind(entry),
      keywords: keywordsFromPath(entry),
    });
  }
  return extracted;
}

function main() {
  const options = parseArgs(process.argv.slice(2));
  const jars = listJars(options.modsDirs).slice(0, options.limitJars);
  const refsRoot = path.join(workspaceArtRoot, 'refs');
  const filesRoot = path.join(refsRoot, 'files');
  fs.mkdirSync(filesRoot, { recursive: true });

  const index = {
    schemaVersion: 1,
    generatedAt: new Date().toISOString(),
    modsDirs: options.modsDirs.map((dir) => path.relative(repoRoot, dir).replace(/\\/g, '/') || dir),
    jarsScanned: jars.length,
    entries: [],
    errors: [],
  };

  for (const jarPath of jars) {
    const modId = modIdFromJar(jarPath);
    const outRoot = path.join(filesRoot, modId);
    fs.mkdirSync(outRoot, { recursive: true });
    try {
      const entries = extractMatchingPngs(jarPath, outRoot, options.maxFilesPerJar);
      for (const entry of entries) {
        index.entries.push({
          modId,
          jar: path.relative(repoRoot, jarPath).replace(/\\/g, '/'),
          ...entry,
        });
      }
      console.log(`[${modId}] indexed ${entries.length} texture(s) from ${path.basename(jarPath)}`);
    } catch (error) {
      index.errors.push({ jar: jarPath, error: error.message.slice(0, 200) });
      console.warn(`[${modId}] skipped: ${error.message}`);
    }
  }

  writeJson(path.join(refsRoot, 'index.json'), index);
  console.log(`Wrote ${index.entries.length} entries to ${path.join(refsRoot, 'index.json')}`);
  if (index.errors.length) console.log(`Jar list/extract errors: ${index.errors.length}`);
  if (!index.entries.length) {
    console.log(
      'No textures indexed. Pass --mods-dir pointing at a GTNH mods folder with item/block/entity PNGs.',
    );
  }
}

main();
