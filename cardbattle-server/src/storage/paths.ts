import fs from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';

export function runtimeDataRoot(): string {
  const standalone = process.env.CARDBATTLE_DATA_DIR?.trim();
  if (standalone) return path.resolve(standalone);
  const instance = process.env.TEXTECH_INSTANCE_ROOT?.trim();
  if (instance) return path.join(path.resolve(instance), 'TeXTech', 'CardBattle');
  return path.resolve('data', 'runtime');
}

export function safeFileId(value: string): string {
  const safe = value.replace(/[^a-zA-Z0-9._-]/g, '_');
  if (!safe || safe === '.' || safe === '..') throw new Error('Invalid storage id');
  return safe;
}

export function readJsonFile<T>(file: string): T | null {
  if (!fs.existsSync(file)) return null;
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8')) as T;
  } catch (error) {
    console.warn(`[storage] failed to read ${file}`, error);
    return null;
  }
}

export function writeJsonAtomic(file: string, value: unknown): void {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const temp = `${file}.${process.pid}.${randomUUID()}.tmp`;
  fs.writeFileSync(temp, JSON.stringify(value, null, 2), 'utf8');
  fs.renameSync(temp, file);
}
