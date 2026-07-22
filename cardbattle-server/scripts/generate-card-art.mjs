import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import readline from 'node:readline';
import { fileURLToPath } from 'node:url';

const serverRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const repoRoot = path.resolve(serverRoot, '..');
const requirementsPath = path.join(repoRoot, '.workspace', 'card-art', 'meowa-requirements.json');
const runsRoot = path.join(repoRoot, '.workspace', 'card-art', 'meowa-runs');
const statePath = path.join(repoRoot, '.workspace', 'card-art', 'meowa-state.json');
const meowaCli = path.join(repoRoot, '.agents', 'skills', 'game-assets', 'meowart_api.py');
const defaultReference = path.join(serverRoot, 'public', 'card-art', 'gt_worker.png');

const STYLE_SUFFIX = [
  'premium painterly game illustration',
  'dramatic industrial-fantasy lighting',
  'detailed materials',
  'subject fills the central 70 percent of the composition',
  'the reference controls rendering quality, lighting, and centered composition only, never subject identity',
].join(', ');

function parseArgs(argv) {
  const options = {
    concurrency: 1,
    limit: 5,
    quality: 'standard',
    reference: defaultReference,
    ids: null,
    dryRun: false,
    retryUnsubmitted: false,
  };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--concurrency') options.concurrency = Number(argv[++i]);
    else if (arg === '--limit') options.limit = Number(argv[++i]);
    else if (arg === '--quality') options.quality = argv[++i];
    else if (arg === '--reference') options.reference = path.resolve(serverRoot, argv[++i]);
    else if (arg === '--ids') options.ids = new Set(argv[++i].split(',').map((id) => id.trim()).filter(Boolean));
    else if (arg === '--dry-run') options.dryRun = true;
    else if (arg === '--retry-unsubmitted') options.retryUnsubmitted = true;
    else throw new Error(`Unknown argument: ${arg}`);
  }
  if (!Number.isInteger(options.concurrency) || options.concurrency < 1 || options.concurrency > 4) {
    throw new Error('--concurrency must be an integer from 1 to 4');
  }
  if (!Number.isInteger(options.limit) || options.limit < 1) throw new Error('--limit must be a positive integer');
  if (!['standard', 'detailed', 'ultimate'].includes(options.quality)) {
    throw new Error('--quality must be standard, detailed, or ultimate');
  }
  return options;
}

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function writeState(state) {
  fs.mkdirSync(path.dirname(statePath), { recursive: true });
  const tempPath = `${statePath}.tmp`;
  fs.writeFileSync(tempPath, `${JSON.stringify(state, null, 2)}\n`, 'utf8');
  fs.renameSync(tempPath, statePath);
}

function loadState() {
  if (!fs.existsSync(statePath)) return { schemaVersion: 1, cards: {} };
  const state = readJson(statePath);
  state.cards ??= {};
  return state;
}

function findManifests(root) {
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

function readPngSize(filePath) {
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

function declaredPngFromManifest(manifestPath) {
  const manifest = readJson(manifestPath);
  if (manifest.status !== 'success') throw new Error(`manifest status is ${manifest.status}`);
  const outputs = manifest.outputs?.filter((output) => output.type === 'media' && output.mime_type === 'image/png') ?? [];
  if (outputs.length !== 1) throw new Error(`expected one declared PNG, found ${outputs.length}`);

  const declaredPath = outputs[0].path;
  const candidates = [
    path.resolve(serverRoot, declaredPath),
    path.join(path.dirname(manifestPath), path.basename(declaredPath)),
  ];
  const finalPath = candidates.find((candidate) => fs.existsSync(candidate));
  if (!finalPath) throw new Error(`declared PNG is missing: ${declaredPath}`);
  if (path.dirname(finalPath) !== path.dirname(manifestPath)) {
    throw new Error('declared PNG is outside its sanitized task output directory');
  }
  const size = readPngSize(finalPath);
  if (size.width !== 1024 || size.height !== 1024) {
    throw new Error(`expected 1024x1024 PNG, received ${size.width}x${size.height}`);
  }
  return { finalPath, ...size, jobId: manifest.job_id };
}

function installFromRun(requirement, outputRoot) {
  const manifests = findManifests(outputRoot);
  if (manifests.length !== 1) return null;
  const output = declaredPngFromManifest(manifests[0]);
  const runtimePath = path.join(repoRoot, requirement.runtimeFile);
  fs.mkdirSync(path.dirname(runtimePath), { recursive: true });
  fs.copyFileSync(output.finalPath, runtimePath);
  return { ...output, runtimePath, manifestPath: manifests[0] };
}

function nextOutputRoot(cardId) {
  for (let attempt = 1; ; attempt += 1) {
    const candidate = path.join(runsRoot, `${cardId}-v${attempt}`);
    if (!fs.existsSync(candidate)) return candidate;
  }
}

function existingCompletedRun(cardId) {
  if (!fs.existsSync(runsRoot)) return null;
  const candidates = fs.readdirSync(runsRoot, { withFileTypes: true })
    .filter((entry) => entry.isDirectory() && entry.name.startsWith(`${cardId}-v`))
    .map((entry) => path.join(runsRoot, entry.name))
    .sort();
  return candidates.find((candidate) => {
    const manifests = findManifests(candidate);
    if (manifests.length !== 1) return false;
    try {
      declaredPngFromManifest(manifests[0]);
      return true;
    } catch {
      return false;
    }
  }) ?? null;
}

function emitLines(stream, cardId, state, record) {
  const reader = readline.createInterface({ input: stream });
  reader.on('line', (line) => {
    const important = line.startsWith('[INFO] submitted')
      || line.includes('status=success')
      || line.startsWith('[WARN]')
      || /(?:error|exception|insufficient|credit)/i.test(line);
    if (important) console.log(`[${cardId}] ${line}`);
    if (/(?:insufficient|not enough).*(?:credit|balance)|(?:credit|balance).*(?:insufficient|not enough)/i.test(line)) {
      record.creditError = true;
    }
    const submitted = line.match(/api_job_id=([A-Za-z0-9_-]+)/);
    const completed = line.match(/"job_id"\s*:\s*"([A-Za-z0-9_-]+)"/);
    const jobId = completed?.[1] ?? submitted?.[1];
    if (jobId && record.jobId !== jobId) {
      record.jobId = jobId;
      if (completed?.[1]) record.finalJobId = completed[1];
      else if (submitted?.[1]) record.submissionJobId = submitted[1];
      record.status = 'submitted';
      writeState(state);
    }
  });
}

async function generateOne(requirement, options, state) {
  const runtimePath = path.join(repoRoot, requirement.runtimeFile);
  if (fs.existsSync(runtimePath)) return { id: requirement.id, status: 'existing' };

  const completedRun = existingCompletedRun(requirement.id);
  if (completedRun) {
    const installed = installFromRun(requirement, completedRun);
    const record = {
      status: 'installed',
      jobId: installed.jobId,
      outputRoot: path.relative(repoRoot, completedRun),
      runtimeFile: requirement.runtimeFile,
      width: installed.width,
      height: installed.height,
      finishedAt: new Date().toISOString(),
    };
    state.cards[requirement.id] = record;
    writeState(state);
    console.log(`[${requirement.id}] installed existing completed output`);
    return { id: requirement.id, status: 'installed' };
  }

  const previous = state.cards[requirement.id];
  const mayRetry = options.retryUnsubmitted && previous?.status === 'failed' && !previous.jobId;
  if (!mayRetry && previous && ['generating', 'submitted', 'failed', 'interrupted', 'failed-validation'].includes(previous.status)) {
    console.error(`[${requirement.id}] skipped ${previous.status} task${previous.jobId ? ` ${previous.jobId}` : ''}; inspect before retrying`);
    return { id: requirement.id, status: 'blocked' };
  }

  const outputRoot = nextOutputRoot(requirement.id);
  const record = {
    status: 'generating',
    outputRoot: path.relative(repoRoot, outputRoot),
    runtimeFile: requirement.runtimeFile,
    startedAt: new Date().toISOString(),
  };
  state.cards[requirement.id] = record;
  writeState(state);

  const prompt = `${requirement.requirement}, ${STYLE_SUFFIX}`;
  const args = [
    meowaCli,
    'image-2-run',
    '--prompt', prompt,
    '--reference-image', options.reference,
    '--resolution', '1K',
    '--aspect-ratio', '1:1',
    '--quality', options.quality,
    '--output-dir', outputRoot,
  ];
  if (options.dryRun) {
    record.status = 'dry-run';
    writeState(state);
    console.log(`[${requirement.id}] would generate ${path.relative(repoRoot, outputRoot)}`);
    return { id: requirement.id, status: 'dry-run' };
  }

  return await new Promise((resolve) => {
    const child = spawn(process.env.PYTHON || 'python', args, {
      cwd: serverRoot,
      env: process.env,
      stdio: ['ignore', 'pipe', 'pipe'],
      windowsHide: true,
    });
    emitLines(child.stdout, requirement.id, state, record);
    emitLines(child.stderr, requirement.id, state, record);
    child.on('error', (error) => {
      record.status = 'failed';
      record.error = error.message;
      record.finishedAt = new Date().toISOString();
      writeState(state);
      resolve({ id: requirement.id, status: 'failed', error });
    });
    child.on('close', (exitCode) => {
      if (exitCode !== 0) {
        record.status = record.jobId ? 'interrupted' : 'failed';
        record.exitCode = exitCode;
        record.finishedAt = new Date().toISOString();
        writeState(state);
        resolve({ id: requirement.id, status: record.status, creditError: record.creditError === true, submitted: Boolean(record.jobId) });
        return;
      }
      try {
        const installed = installFromRun(requirement, outputRoot);
        if (!installed) throw new Error('successful command did not produce exactly one final_outputs.json');
        Object.assign(record, {
          status: 'installed',
          jobId: installed.jobId ?? record.jobId,
          finalJobId: installed.jobId ?? record.finalJobId,
          width: installed.width,
          height: installed.height,
          finishedAt: new Date().toISOString(),
        });
        writeState(state);
        console.log(`[${requirement.id}] installed ${path.relative(repoRoot, runtimePath)}`);
        resolve({ id: requirement.id, status: 'installed' });
      } catch (error) {
        record.status = 'failed-validation';
        record.error = error.message;
        record.finishedAt = new Date().toISOString();
        writeState(state);
        console.error(`[${requirement.id}] validation failed: ${error.message}`);
        resolve({ id: requirement.id, status: 'failed-validation', error });
      }
    });
  });
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  for (const requiredPath of [requirementsPath, meowaCli, options.reference]) {
    if (!fs.existsSync(requiredPath)) throw new Error(`Required file is missing: ${requiredPath}`);
  }
  const requirements = readJson(requirementsPath).requirements;
  const state = loadState();
  const selected = requirements
    .filter((requirement) => !options.ids || options.ids.has(requirement.id))
    .filter((requirement) => !fs.existsSync(path.join(repoRoot, requirement.runtimeFile)))
    .slice(0, options.limit);

  if (!selected.length) {
    console.log('No pending card art matched this batch.');
    return;
  }
  console.log(`Generating or installing ${selected.length} card(s), concurrency=${options.concurrency}, quality=${options.quality}`);
  const queue = [...selected];
  const results = [];
  let stopRequested = false;
  let consecutiveUnsubmittedFailures = 0;
  const workers = Array.from({ length: Math.min(options.concurrency, queue.length) }, async () => {
    while (queue.length && !stopRequested) {
      const requirement = queue.shift();
      const result = await generateOne(requirement, options, state);
      results.push(result);
      if (result.creditError) {
        stopRequested = true;
        console.error('Stopping batch because Meowa reported insufficient credits.');
      } else if (result.status === 'failed' && !result.submitted) {
        consecutiveUnsubmittedFailures += 1;
        if (consecutiveUnsubmittedFailures >= 3) {
          stopRequested = true;
          console.error('Stopping batch after three consecutive failures before submission.');
        }
      } else if (result.status !== 'blocked') {
        consecutiveUnsubmittedFailures = 0;
      }
    }
  });
  await Promise.all(workers);
  const counts = Object.groupBy
    ? Object.groupBy(results, (result) => result.status)
    : results.reduce((groups, result) => ({ ...groups, [result.status]: [...(groups[result.status] ?? []), result] }), {});
  console.log(`Batch result: ${Object.entries(counts).map(([status, items]) => `${status}=${items.length}`).join(', ')}`);
  if (queue.length) console.log(`Deferred by safety stop: ${queue.length}`);
  if (results.some((result) => ['failed', 'interrupted', 'failed-validation'].includes(result.status))) process.exitCode = 1;
}

main().catch((error) => {
  console.error(error.stack || error.message);
  process.exitCode = 1;
});
