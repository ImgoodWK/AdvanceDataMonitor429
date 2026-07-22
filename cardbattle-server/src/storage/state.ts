import path from 'node:path';
import type { BattleState } from '../battle/types.js';
import type { RunState } from '../pve/run.js';
import { readJsonFile, runtimeDataRoot, safeFileId, writeJsonAtomic } from './paths.js';

interface StoredRun {
  schemaVersion: 1;
  updatedAt: number;
  run: RunState;
}

interface StoredMatch {
  schemaVersion: 1;
  updatedAt: number;
  ownerUuid: string;
  match: BattleState;
}

function runFile(runId: string): string {
  return path.join(runtimeDataRoot(), 'sessions', 'runs', `${safeFileId(runId)}.json`);
}

function matchFile(matchId: string): string {
  return path.join(runtimeDataRoot(), 'sessions', 'matches', `${safeFileId(matchId)}.json`);
}

export function persistRun(run: RunState): void {
  writeJsonAtomic(runFile(run.runId), {
    schemaVersion: 1,
    updatedAt: Date.now(),
    run,
  } satisfies StoredRun);
}

export function restoreRun(runId: string): RunState | null {
  return readJsonFile<StoredRun>(runFile(runId))?.run ?? null;
}

export function persistMatch(ownerUuid: string, match: BattleState): void {
  writeJsonAtomic(matchFile(match.matchId), {
    schemaVersion: 1,
    updatedAt: Date.now(),
    ownerUuid,
    match,
  } satisfies StoredMatch);
}

export function restoreMatch(matchId: string): { ownerUuid: string; match: BattleState } | null {
  const stored = readJsonFile<StoredMatch>(matchFile(matchId));
  return stored ? { ownerUuid: stored.ownerUuid, match: stored.match } : null;
}
