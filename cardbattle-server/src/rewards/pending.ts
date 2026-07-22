import fs from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';

export interface RewardItem {
  modid: string;
  name: string;
  meta: number;
  count: number;
  displayName?: string;
}

export interface PendingRewardEntry {
  id: string;
  createdAt: number;
  status: 'pending' | 'claimed' | 'cancelled';
  items: RewardItem[];
  source: { runId: string; stageId: string; label?: string };
}

function dataRoot(): string {
  if (process.env.CARDBATTLE_DATA_DIR?.trim()) return process.env.CARDBATTLE_DATA_DIR.trim();
  const inst = process.env.TEXTECH_INSTANCE_ROOT?.trim();
  if (inst) return path.join(inst, 'TeXTech', 'CardBattle');
  return path.resolve('data', 'runtime');
}

function rewardsFile(ownerUuid: string): string {
  const dir = path.join(dataRoot(), 'pending-rewards');
  fs.mkdirSync(dir, { recursive: true });
  return path.join(dir, `${ownerUuid}.json`);
}

export function readPending(ownerUuid: string): PendingRewardEntry[] {
  const file = rewardsFile(ownerUuid);
  if (!fs.existsSync(file)) return [];
  try {
    const data = JSON.parse(fs.readFileSync(file, 'utf8')) as { entries?: PendingRewardEntry[] };
    return data.entries ?? [];
  } catch {
    return [];
  }
}

function writePending(ownerUuid: string, entries: PendingRewardEntry[]): void {
  const file = rewardsFile(ownerUuid);
  fs.writeFileSync(file, JSON.stringify({ entries }, null, 2), 'utf8');
}

export function enqueueReward(
  ownerUuid: string,
  items: RewardItem[],
  source: PendingRewardEntry['source'],
): PendingRewardEntry {
  const entries = readPending(ownerUuid);
  const entry: PendingRewardEntry = {
    id: randomUUID(),
    createdAt: Date.now(),
    status: 'pending',
    items,
    source,
  };
  entries.push(entry);
  writePending(ownerUuid, entries);
  return entry;
}

export function listPending(ownerUuid: string): PendingRewardEntry[] {
  return readPending(ownerUuid).filter((e) => e.status === 'pending');
}

/** Stub claim for future MC bridge — marks claimed without granting items. */
export function markClaimed(ownerUuid: string, rewardId: string): PendingRewardEntry | null {
  const entries = readPending(ownerUuid);
  const hit = entries.find((e) => e.id === rewardId);
  if (!hit || hit.status !== 'pending') return null;
  hit.status = 'claimed';
  writePending(ownerUuid, entries);
  return hit;
}

export function rewardsDataRoot(): string {
  return dataRoot();
}
