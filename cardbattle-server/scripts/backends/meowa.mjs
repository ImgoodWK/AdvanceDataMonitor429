import { spawn } from 'node:child_process';
import path from 'node:path';
import readline from 'node:readline';
import { repoRoot } from '../lib/card-art-common.mjs';
import { STYLE_SUFFIX } from '../lib/card-art-style.mjs';

export const meowaCli = path.join(repoRoot, '.agents', 'skills', 'game-assets', 'meowart_api.py');

/**
 * @param {{
 *   prompt: string,
 *   referencePaths: string[],
 *   outputRoot: string,
 *   quality: string,
 *   cardId: string,
 *   onLine?: (line: string) => void,
 * }} args
 */
export function runMeowaImage2(args) {
  const { prompt, referencePaths, outputRoot, quality, onLine } = args;
  const fullPrompt = `${prompt}, ${STYLE_SUFFIX}`;
  const cliArgs = [
    meowaCli,
    'image-2-run',
    '--prompt',
    fullPrompt,
    '--resolution',
    '1K',
    '--aspect-ratio',
    '1:1',
    '--quality',
    quality,
    '--output-dir',
    outputRoot,
  ];
  for (const ref of referencePaths.slice(0, 8)) {
    cliArgs.push('--reference-image', ref);
  }

  return new Promise((resolve) => {
    const pythonBin = process.env.PYTHON
      || (process.platform === 'win32' ? 'py' : 'python');
    const spawnArgs = process.platform === 'win32' && pythonBin === 'py'
      ? ['-3', ...cliArgs]
      : cliArgs;
    const child = spawn(pythonBin, spawnArgs, {
      cwd: path.join(repoRoot, 'cardbattle-server'),
      env: process.env,
      stdio: ['ignore', 'pipe', 'pipe'],
      windowsHide: true,
      shell: false,
    });
    const meta = { jobId: null, creditError: false, submissionJobId: null, finalJobId: null };

    const attach = (stream) => {
      const reader = readline.createInterface({ input: stream });
      reader.on('line', (line) => {
        onLine?.(line);
        if (/(?:insufficient|not enough).*(?:credit|balance)|(?:credit|balance).*(?:insufficient|not enough)/i.test(line)) {
          meta.creditError = true;
        }
        const submitted = line.match(/api_job_id=([A-Za-z0-9_-]+)/);
        const completed = line.match(/"job_id"\s*:\s*"([A-Za-z0-9_-]+)"/);
        const jobId = completed?.[1] ?? submitted?.[1];
        if (jobId) {
          meta.jobId = jobId;
          if (completed?.[1]) meta.finalJobId = completed[1];
          else if (submitted?.[1]) meta.submissionJobId = submitted[1];
        }
      });
    };
    attach(child.stdout);
    attach(child.stderr);

    child.on('error', (error) => {
      resolve({ ok: false, error, ...meta });
    });
    child.on('close', (exitCode) => {
      resolve({ ok: exitCode === 0, exitCode, ...meta });
    });
  });
}
