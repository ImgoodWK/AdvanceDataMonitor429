import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { readJsonFile, runtimeDataRoot, safeFileId, writeJsonAtomic } from '../storage/paths.js';

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
  source: {
    runId: string;
    stageId: string;
    label?: string;
    schemaVersion?: 2;
    rewardKey?: string;
    voltageTier?: string;
    delivery?: 'pending_bridge';
  };
}

function rewardsFile(ownerUuid: string): string {
  return path.join(runtimeDataRoot(), 'pending-rewards', `${safeFileId(ownerUuid)}.json`);
}

export function readPending(ownerUuid: string): PendingRewardEntry[] {
  return readJsonFile<{ entries?: PendingRewardEntry[] }>(rewardsFile(ownerUuid))?.entries ?? [];
}

function writePending(ownerUuid: string, entries: PendingRewardEntry[]): void {
  writeJsonAtomic(rewardsFile(ownerUuid), { schemaVersion: 2, entries });
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
  return runtimeDataRoot();
}

export function rewardDeliveryStatus(): {
  mode: 'standalone_accumulation' | 'minecraft_shared_directory';
  bridgeClaimEnabled: boolean;
  dataRoot: string;
} {
  const sharedMinecraftDirectory =
    !process.env.CARDBATTLE_DATA_DIR?.trim() && Boolean(process.env.TEXTECH_INSTANCE_ROOT?.trim());
  return {
    mode: sharedMinecraftDirectory ? 'minecraft_shared_directory' : 'standalone_accumulation',
    bridgeClaimEnabled: Boolean(process.env.CARDBATTLE_BRIDGE_TOKEN?.trim()),
    dataRoot: runtimeDataRoot(),
  };
}
