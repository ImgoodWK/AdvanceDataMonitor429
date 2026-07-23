import fs from 'node:fs';
import path from 'node:path';
import {
  repoRoot,
  loadRequirements,
  loadState,
  writeState,
  runsRootFor,
  resolveReferencePaths,
  installFromRun,
  nextOutputRoot,
  existingCompletedRun,
  publicArtDir,
} from './lib/card-art-common.mjs';
import { runDiyImage2, diyConfigured } from './backends/gpt-image2.mjs';
import { runMeowaImage2, meowaCli } from './backends/meowa.mjs';

const defaultReference = path.join(publicArtDir, 'gt_worker.png');

function parseArgs(argv) {
  const options = {
    backend: 'diy',
    concurrency: 1,
    limit: 5,
    quality: 'standard',
    reference: null,
    ids: null,
    dryRun: false,
    retryUnsubmitted: false,
    promote: true,
    force: false,
    outputRootOverride: null,
  };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--backend') options.backend = argv[++i];
    else if (arg === '--concurrency') options.concurrency = Number(argv[++i]);
    else if (arg === '--limit') options.limit = Number(argv[++i]);
    else if (arg === '--quality') options.quality = argv[++i];
    else if (arg === '--reference') options.reference = path.resolve(argv[++i]);
    else if (arg === '--ids') options.ids = new Set(argv[++i].split(',').map((id) => id.trim()).filter(Boolean));
    else if (arg === '--dry-run') options.dryRun = true;
    else if (arg === '--retry-unsubmitted') options.retryUnsubmitted = true;
    else if (arg === '--no-promote') options.promote = false;
    else if (arg === '--force') options.force = true;
    else if (arg === '--output-root') options.outputRootOverride = path.resolve(argv[++i]);
    else throw new Error(`Unknown argument: ${arg}`);
  }
  if (!['diy', 'meowa'].includes(options.backend)) {
    throw new Error('--backend must be diy or meowa');
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

async function generateOne(requirement, options, state) {
  const runtimePath = path.join(repoRoot, requirement.runtimeFile);
  if (!options.force && options.promote && fs.existsSync(runtimePath)) {
    return { id: requirement.id, status: 'existing' };
  }

  const runsRoot = options.outputRootOverride
    ? path.join(options.outputRootOverride, options.backend)
    : runsRootFor(options.backend);

  if (!options.force && !options.outputRootOverride) {
    const completedRun = existingCompletedRun(runsRoot, requirement.id);
    if (completedRun) {
      const installed = installFromRun(requirement, completedRun, { promote: options.promote });
      const record = {
        status: options.promote ? 'installed' : 'generated',
        backend: options.backend,
        jobId: installed.jobId,
        outputRoot: path.relative(repoRoot, completedRun),
        runtimeFile: requirement.runtimeFile,
        width: installed.width,
        height: installed.height,
        finishedAt: new Date().toISOString(),
      };
      state.cards[requirement.id] = record;
      writeState(options.backend, state);
      console.log(`[${requirement.id}] installed existing completed ${options.backend} output`);
      return { id: requirement.id, status: record.status };
    }
  }

  const previous = state.cards[requirement.id];
  const mayRetry = options.retryUnsubmitted && previous?.status === 'failed' && !previous.jobId;
  if (
    !options.force
    && !mayRetry
    && previous
    && ['generating', 'submitted', 'failed', 'interrupted', 'failed-validation'].includes(previous.status)
  ) {
    console.error(
      `[${requirement.id}] skipped ${previous.status} task${previous.jobId ? ` ${previous.jobId}` : ''}; inspect before retrying`,
    );
    return { id: requirement.id, status: 'blocked' };
  }

  const outputRoot = options.outputRootOverride
    ? path.join(options.outputRootOverride, options.backend, requirement.id)
    : nextOutputRoot(runsRoot, requirement.id);
  fs.mkdirSync(outputRoot, { recursive: true });

  const record = {
    status: 'generating',
    backend: options.backend,
    outputRoot: path.relative(repoRoot, outputRoot),
    runtimeFile: requirement.runtimeFile,
    startedAt: new Date().toISOString(),
  };
  state.cards[requirement.id] = record;
  writeState(options.backend, state);

  const referencePaths = resolveReferencePaths(requirement, {
    reference: options.reference,
    defaultReference: defaultReference,
  });

  if (options.dryRun) {
    record.status = 'dry-run';
    record.referenceCount = referencePaths.length;
    writeState(options.backend, state);
    console.log(
      `[${requirement.id}] would generate via ${options.backend} → ${path.relative(repoRoot, outputRoot)} (refs=${referencePaths.length})`,
    );
    return { id: requirement.id, status: 'dry-run' };
  }

  try {
    if (options.backend === 'diy') {
      const result = await runDiyImage2({
        prompt: requirement.requirement,
        referencePaths,
        outputRoot,
        quality: options.quality,
        onProgress: (msg) => console.log(`[${requirement.id}] ${msg}`),
      });
      record.jobId = result.jobId;
      record.status = 'submitted';
      writeState(options.backend, state);
    } else {
      const result = await runMeowaImage2({
        prompt: requirement.requirement,
        referencePaths,
        outputRoot,
        quality: options.quality,
        cardId: requirement.id,
        onLine: (line) => {
          const important =
            line.startsWith('[INFO] submitted')
            || line.includes('status=success')
            || line.startsWith('[WARN]')
            || /(?:error|exception|insufficient|credit)/i.test(line);
          if (important) console.log(`[${requirement.id}] ${line}`);
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
            writeState(options.backend, state);
          }
        },
      });
      if (result.creditError) record.creditError = true;
      if (!result.ok) {
        record.status = result.jobId ? 'interrupted' : 'failed';
        record.exitCode = result.exitCode;
        record.error = result.error?.message;
        record.finishedAt = new Date().toISOString();
        writeState(options.backend, state);
        return {
          id: requirement.id,
          status: record.status,
          creditError: record.creditError === true,
          submitted: Boolean(record.jobId),
        };
      }
      if (result.jobId) record.jobId = result.jobId;
    }

    const installed = installFromRun(requirement, outputRoot, { promote: options.promote });
    if (!installed) throw new Error('successful command did not produce exactly one final_outputs.json');
    Object.assign(record, {
      status: options.promote ? 'installed' : 'generated',
      jobId: installed.jobId ?? record.jobId,
      finalJobId: installed.jobId ?? record.finalJobId,
      width: installed.width,
      height: installed.height,
      finishedAt: new Date().toISOString(),
    });
    writeState(options.backend, state);
    console.log(
      `[${requirement.id}] ${record.status} ${options.promote ? path.relative(repoRoot, runtimePath) : path.relative(repoRoot, outputRoot)}`,
    );
    return { id: requirement.id, status: record.status };
  } catch (error) {
    record.status = record.jobId ? 'interrupted' : 'failed';
    if (error.message?.includes('1024x1024') || error.message?.includes('final_outputs')) {
      record.status = 'failed-validation';
    }
    record.error = error.message;
    record.finishedAt = new Date().toISOString();
    writeState(options.backend, state);
    console.error(`[${requirement.id}] ${record.status}: ${error.message}`);
    return {
      id: requirement.id,
      status: record.status,
      error,
      creditError: /insufficient|credit|balance/i.test(error.message),
      submitted: Boolean(record.jobId),
    };
  }
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.backend === 'meowa' && !fs.existsSync(meowaCli)) {
    throw new Error(`Meowa CLI missing: ${meowaCli}`);
  }
  if (options.backend === 'diy' && !options.dryRun && !diyConfigured()) {
    throw new Error('TEXTECH_IMAGE_API_KEY is not set for --backend diy');
  }
  if (!fs.existsSync(defaultReference)) {
    throw new Error(`Default style reference missing: ${defaultReference}`);
  }

  const requirements = loadRequirements().requirements;
  const state = loadState(options.backend);
  const selected = requirements
    .filter((requirement) => !options.ids || options.ids.has(requirement.id))
    .filter((requirement) => {
      if (options.force || !options.promote) return true;
      return !fs.existsSync(path.join(repoRoot, requirement.runtimeFile));
    })
    .slice(0, options.limit);

  if (!selected.length) {
    console.log('No pending card art matched this batch.');
    return;
  }
  console.log(
    `Generating ${selected.length} card(s), backend=${options.backend}, concurrency=${options.concurrency}, quality=${options.quality}, promote=${options.promote}`,
  );

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
        console.error('Stopping batch because the backend reported insufficient credits/balance.');
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

  const counts = results.reduce((groups, result) => {
    groups[result.status] = (groups[result.status] ?? 0) + 1;
    return groups;
  }, {});
  console.log(`Batch result: ${Object.entries(counts).map(([status, count]) => `${status}=${count}`).join(', ')}`);
  if (queue.length) console.log(`Deferred by safety stop: ${queue.length}`);
  if (results.some((result) => ['failed', 'interrupted', 'failed-validation'].includes(result.status))) {
    process.exitCode = 1;
  }
}

main().catch((error) => {
  console.error(error.stack || error.message);
  process.exitCode = 1;
});
