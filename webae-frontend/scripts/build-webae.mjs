import { build } from 'vite';
import { fileURLToPath } from 'node:url';
import { existsSync } from 'node:fs';
import {
  mkdir,
  mkdtemp,
  readFile,
  rename,
  rm,
  stat,
} from 'node:fs/promises';
import { dirname, isAbsolute, join, relative, resolve } from 'node:path';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const frontendDir = resolve(scriptDir, '..');
const repoRoot = resolve(frontendDir, '..');
const outputDir = resolve(repoRoot, 'src/main/resources/assets/textech/webae');
const stagingRoot = resolve(repoRoot, '.workspace/webae-build');

async function pathExists(path) {
  return existsSync(path);
}

async function isFile(path) {
  try {
    return (await stat(path)).isFile();
  } catch {
    return false;
  }
}

function resolveBundleReference(stagingDir, reference) {
  const pathOnly = reference.split(/[?#]/, 1)[0];
  if (!pathOnly || /^(?:[a-z][a-z\d+.-]*:|\/\/)/i.test(pathOnly)) {
    throw new Error(`WebAE index references a non-local JavaScript asset: ${reference}`);
  }

  const target = resolve(stagingDir, pathOnly.replace(/^[/\\]+/, ''));
  const relativeTarget = relative(stagingDir, target);
  if (!relativeTarget || relativeTarget.startsWith('..') || isAbsolute(relativeTarget)) {
    throw new Error(`WebAE index references JavaScript outside its bundle: ${reference}`);
  }
  return target;
}

async function validateBundle(stagingDir) {
  const indexPath = join(stagingDir, 'index.html');
  if (!(await isFile(indexPath))) {
    throw new Error(`Vite completed without a usable WebAE bundle in ${stagingDir}`);
  }

  const indexHtml = await readFile(indexPath, 'utf8');
  const javascriptRefs = Array.from(
    indexHtml.matchAll(/\b(?:src|href)\s*=\s*(["'])([^"']+?\.js(?:[?#][^"']*)?)\1/gi),
    (match) => match[2],
  );
  if (javascriptRefs.length === 0) {
    throw new Error(`WebAE index does not reference any JavaScript assets in ${stagingDir}`);
  }

  for (const reference of javascriptRefs) {
    const target = resolveBundleReference(stagingDir, reference);
    if (!(await isFile(target))) {
      throw new Error(`WebAE index references a missing JavaScript asset: ${reference}`);
    }
  }
}

async function promote(stagingDir) {
  await mkdir(dirname(outputDir), { recursive: true });
  const backupDir = join(stagingRoot, `previous-${Date.now()}-${process.pid}`);
  let previousMoved = false;
  let installed = false;

  try {
    if (await pathExists(outputDir)) {
      await rename(outputDir, backupDir);
      previousMoved = true;
    }

    try {
      await rename(stagingDir, outputDir);
      installed = true;
    } catch (error) {
      if (previousMoved && !(await pathExists(outputDir)) && await pathExists(backupDir)) {
        await rename(backupDir, outputDir);
        previousMoved = false;
      }
      throw error;
    }

    if (previousMoved) {
      try {
        await rm(backupDir, { recursive: true, force: true });
      } catch (error) {
        // The new bundle is already installed. A transient Windows file lock
        // on the backup must not report a failed promotion or trigger a false
        // "previous bundle untouched" message.
        console.warn(`Promoted WebAE bundle but could not remove backup ${backupDir}:`, error);
      }
    }
  } finally {
    if (!installed && await pathExists(stagingDir)) {
      await rm(stagingDir, { recursive: true, force: true });
    }
  }
}

await mkdir(stagingRoot, { recursive: true });
const stagingDir = await mkdtemp(join(stagingRoot, 'bundle-'));

try {
  await build({
    root: frontendDir,
    configFile: resolve(frontendDir, 'vite.config.ts'),
    build: {
      outDir: stagingDir,
      emptyOutDir: true,
    },
  });
  await validateBundle(stagingDir);
  if (process.env.WEBAE_BUILD_VALIDATE_ONLY === '1') {
    await rm(stagingDir, { recursive: true, force: true });
    console.log(`Validated WebAE bundle in ${stagingDir}; production assets were not changed.`);
  } else {
    await promote(stagingDir);
    console.log(`Promoted WebAE bundle from ${stagingDir} to ${outputDir}`);
  }
} catch (error) {
  await rm(stagingDir, { recursive: true, force: true });
  console.error('WebAE bundle was not promoted; the previous bundle was left untouched.');
  throw error;
}
