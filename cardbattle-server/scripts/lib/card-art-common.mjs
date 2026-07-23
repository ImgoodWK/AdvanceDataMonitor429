import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

export const serverRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
export const repoRoot = path.resolve(serverRoot, '..');
export const workspaceArtRoot = path.join(repoRoot, '.workspace', 'card-art');
export const requirementsPath = path.join(workspaceArtRoot, 'art-requirements.json');
/** Legacy alias kept for recover-card-art.py */
export const legacyRequirementsPath = path.join(workspaceArtRoot, 'meowa-requirements.json');
export const publicArtDir = path.join(serverRoot, 'public', 'card-art');

export function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

export function writeJson(filePath, value) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  const temporary = `${filePath}.tmp`;
  fs.writeFileSync(temporary, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
  fs.renameSync(temporary, filePath);
}

export function statePathFor(backend) {
  return path.join(workspaceArtRoot, backend === 'meowa' ? 'meowa-state.json' : `${backend}-state.json`);
}

export function runsRootFor(backend) {
  return path.join(workspaceArtRoot, backend === 'meowa' ? 'meowa-runs' : `${backend}-runs`);
}

export function loadState(backend) {
  const statePath = statePathFor(backend);
  if (!fs.existsSync(statePath)) return { schemaVersion: 1, backend, cards: {} };
  const state = readJson(statePath);
  state.cards ??= {};
  state.backend = backend;
  return state;
}

export function writeState(backend, state) {
  writeJson(statePathFor(backend), state);
}

export function findManifests(root) {
  if (!fs.existsSync(root)) return [];
  const manifests = [];
  const pending = [root];
  while (pending.length) {
    const current = pending.pop();
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const entryPath = path.join(current, entry.name);
      if (entry.isDirectory()) pending.push(entryPath);
      else if (entry.isFile() && entry.name === 'final_outputs.json') manifests.push(entryPath);
    }
  }
  return manifests;
}

export function readPngSize(filePath) {
  const header = Buffer.alloc(24);
  const fd = fs.openSync(filePath, 'r');
  try {
    if (fs.readSync(fd, header, 0, header.length, 0) !== header.length) throw new Error('truncated PNG');
  } finally {
    fs.closeSync(fd);
  }
  if (!header.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]))) {
    throw new Error('file does not have a PNG signature');
  }
  return { width: header.readUInt32BE(16), height: header.readUInt32BE(20) };
}

export function writeFinalOutputsManifest(outputRoot, pngFileName, jobId) {
  const manifest = {
    status: 'success',
    job_id: jobId,
    backend: 'diy',
    outputs: [
      {
        type: 'media',
        mime_type: 'image/png',
        path: pngFileName,
      },
    ],
  };
  writeJson(path.join(outputRoot, 'final_outputs.json'), manifest);
  return manifest;
}

/**
 * Normalize provider outputs to the 1024×1024 card-art contract in-place.
 * Non-square images are center-cropped to the largest inscribed square first.
 * Uses Windows System.Drawing when available; otherwise requires an exact match.
 */
export function ensureCardArtSize1024(pngPath) {
  const size = readPngSize(pngPath);
  if (size.width === 1024 && size.height === 1024) return size;
  if (size.width < 512 || size.height < 512) {
    throw new Error(`expected PNG ≥512 on both axes, received ${size.width}x${size.height}`);
  }
  if (process.platform !== 'win32') {
    throw new Error(`expected 1024x1024 PNG, received ${size.width}x${size.height}`);
  }
  const temporary = `${pngPath}.resize-tmp.png`;
  const script = [
    'Add-Type -AssemblyName System.Drawing',
    `$src = ${JSON.stringify(pngPath)}`,
    `$dst = ${JSON.stringify(temporary)}`,
    '$img = [System.Drawing.Image]::FromFile($src)',
    'try {',
    '  $side = [Math]::Min($img.Width, $img.Height)',
    '  $sx = [int](($img.Width - $side) / 2)',
    '  $sy = [int](($img.Height - $side) / 2)',
    '  $srcRect = New-Object System.Drawing.Rectangle $sx, $sy, $side, $side',
    '  $bmp = New-Object System.Drawing.Bitmap 1024,1024',
    '  $g = [System.Drawing.Graphics]::FromImage($bmp)',
    '  $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic',
    '  $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality',
    '  $destRect = New-Object System.Drawing.Rectangle 0, 0, 1024, 1024',
    '  $g.DrawImage($img, $destRect, $srcRect, [System.Drawing.GraphicsUnit]::Pixel)',
    '  $bmp.Save($dst, [System.Drawing.Imaging.ImageFormat]::Png)',
    '  $g.Dispose(); $bmp.Dispose()',
    '} finally { $img.Dispose() }',
  ].join('; ');
  const result = spawnSync('powershell.exe', ['-NoProfile', '-Command', script], {
    encoding: 'utf8',
    windowsHide: true,
  });
  if (result.status !== 0 || !fs.existsSync(temporary)) {
    throw new Error(
      `failed to normalize ${size.width}x${size.height} → 1024x1024: ${result.stderr || result.stdout || 'unknown'}`,
    );
  }
  fs.renameSync(temporary, pngPath);
  return readPngSize(pngPath);
}

export function declaredPngFromManifest(manifestPath) {
  const manifest = readJson(manifestPath);
  if (manifest.status !== 'success') throw new Error(`manifest status is ${manifest.status}`);
  const outputs = manifest.outputs?.filter((output) => output.type === 'media' && output.mime_type === 'image/png') ?? [];
  if (outputs.length !== 1) throw new Error(`expected one declared PNG, found ${outputs.length}`);

  const declaredPath = outputs[0].path;
  const colocated = path.join(path.dirname(manifestPath), path.basename(declaredPath));
  const candidates = [
    colocated,
    path.resolve(serverRoot, declaredPath),
    path.isAbsolute(declaredPath) ? declaredPath : null,
  ].filter(Boolean);
  const finalPath = candidates.find((candidate) => fs.existsSync(candidate));
  if (!finalPath) throw new Error(`declared PNG is missing: ${declaredPath}`);
  if (path.dirname(finalPath) !== path.dirname(manifestPath)) {
    throw new Error('declared PNG is outside its sanitized task output directory');
  }
  const size = ensureCardArtSize1024(finalPath);
  if (size.width !== 1024 || size.height !== 1024) {
    throw new Error(`expected 1024x1024 PNG, received ${size.width}x${size.height}`);
  }
  return { finalPath, ...size, jobId: manifest.job_id };
}

export function installFromRun(requirement, outputRoot, { promote = true } = {}) {
  const manifests = findManifests(outputRoot);
  if (manifests.length !== 1) return null;
  const output = declaredPngFromManifest(manifests[0]);
  if (!promote) {
    return { ...output, runtimePath: null, manifestPath: manifests[0] };
  }
  const runtimePath = path.join(repoRoot, requirement.runtimeFile);
  fs.mkdirSync(path.dirname(runtimePath), { recursive: true });
  fs.copyFileSync(output.finalPath, runtimePath);
  return { ...output, runtimePath, manifestPath: manifests[0] };
}

export function nextOutputRoot(runsRoot, cardId) {
  for (let attempt = 1; ; attempt += 1) {
    const candidate = path.join(runsRoot, `${cardId}-v${attempt}`);
    if (!fs.existsSync(candidate)) return candidate;
  }
}

export function existingCompletedRun(runsRoot, cardId) {
  if (!fs.existsSync(runsRoot)) return null;
  const candidates = fs
    .readdirSync(runsRoot, { withFileTypes: true })
    .filter((entry) => entry.isDirectory() && entry.name.startsWith(`${cardId}-v`))
    .map((entry) => path.join(runsRoot, entry.name))
    .sort();
  return (
    candidates.find((candidate) => {
      const manifests = findManifests(candidate);
      if (manifests.length !== 1) return false;
      try {
        declaredPngFromManifest(manifests[0]);
        return true;
      } catch {
        return false;
      }
    }) ?? null
  );
}

export function resolveReferencePaths(requirement, options, repoRootPath = repoRoot) {
  const collected = [];
  const pushUnique = (absolute) => {
    if (!absolute || !fs.existsSync(absolute)) return;
    if (collected.includes(absolute)) return;
    collected.push(absolute);
  };

  if (options.reference) pushUnique(path.resolve(options.reference));

  for (const ref of requirement.styleRefs ?? []) {
    pushUnique(path.join(repoRootPath, ref.path));
  }
  for (const ref of requirement.subjectRefs ?? []) {
    pushUnique(path.join(repoRootPath, ref.path));
  }

  if (!collected.length && options.defaultReference) {
    pushUnique(options.defaultReference);
  }
  return collected.slice(0, 8);
}

export function loadRequirements() {
  const pathToUse = fs.existsSync(requirementsPath)
    ? requirementsPath
    : fs.existsSync(legacyRequirementsPath)
      ? legacyRequirementsPath
      : null;
  if (!pathToUse) {
    throw new Error(`Requirements file missing. Run npm run art:requirements first (${requirementsPath})`);
  }
  return readJson(pathToUse);
}
