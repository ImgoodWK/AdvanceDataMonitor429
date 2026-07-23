import { randomBytes } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/** Minimal .env loader (no dependency). Does not override existing env. */
function loadDotEnv(): void {
  const envPath = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '.env');
  if (!fs.existsSync(envPath)) return;
  for (const line of fs.readFileSync(envPath, 'utf8').split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eq = trimmed.indexOf('=');
    if (eq <= 0) continue;
    const key = trimmed.slice(0, eq).trim();
    let val = trimmed.slice(eq + 1).trim();
    if (
      (val.startsWith('"') && val.endsWith('"')) ||
      (val.startsWith("'") && val.endsWith("'"))
    ) {
      val = val.slice(1, -1);
    }
    if (process.env[key] === undefined) process.env[key] = val;
  }
}

function randomPassword(): string {
  return randomBytes(9).toString('base64url');
}

function upsertEnv(file: string, key: string, value: string): void {
  let text = fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : '';
  const line = `${key}=${value}`;
  const re = new RegExp(`^${key}=.*$`, 'm');
  if (re.test(text)) text = text.replace(re, line);
  else text = `${text.trimEnd()}\n${line}\n`;
  fs.writeFileSync(file, text, 'utf8');
}

loadDotEnv();

const { bootstrapSuperadmin, hasAnyUser } = await import('../src/auth/accounts.js');

const envPath = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '.env');
const username = process.env.CARDBATTLE_BOOTSTRAP_ADMIN_USER?.trim() || 'cardadmin';
let password = process.env.CARDBATTLE_BOOTSTRAP_ADMIN_PASSWORD?.trim();
if (!password) {
  password = randomPassword();
  upsertEnv(envPath, 'CARDBATTLE_BOOTSTRAP_ADMIN_USER', username);
  upsertEnv(envPath, 'CARDBATTLE_BOOTSTRAP_ADMIN_PASSWORD', password);
}

if (hasAnyUser()) {
  console.log(`[bootstrap] users already exist; username hint=${username}`);
  process.exit(0);
}

const user = bootstrapSuperadmin(username, password, 'Card Admin');
console.log('[bootstrap] created superadmin');
console.log(`  username: ${user.username}`);
console.log(`  password: ${password}`);
console.log(`  saved to: ${envPath} (gitignored)`);
console.log('  请登录后立即在「个人设置」中修改密码。');
