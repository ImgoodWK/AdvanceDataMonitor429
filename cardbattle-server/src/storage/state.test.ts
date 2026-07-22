import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { describe, expect, it } from 'vitest';
import { createMatch } from '../battle/engine.js';
import { buildDeck, generateStages, type RunState } from '../pve/run.js';
import { persistMatch, persistRun, restoreMatch, restoreRun } from './state.js';

describe('standalone state storage', () => {
  it('restores runs and authoritative matches from disk', () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'cb-state-'));
    process.env.CARDBATTLE_DATA_DIR = dir;
    const run: RunState = {
      runId: 'run-persist',
      ownerUuid: 'owner-persist',
      seed: 42,
      voltage: 'LV',
      themes: ['vanilla'],
      deck: buildDeck(['vanilla'], 'LV', 42),
      equipment: { attack: 0, health: 0, armor: 0 },
      stageIndex: 0,
      stages: generateStages(42, 'LV'),
      availableStageIds: ['stage_1a'],
      completedStageIds: [],
      currentStageId: null,
      powers: [],
      unlockedSkinIds: ['gt_factory'],
      pendingChoice: null,
      completed: false,
      victories: 0,
    };
    const match = createMatch({
      seed: 42,
      playerId: run.ownerUuid,
      playerName: 'Persistent player',
      playerDeck: run.deck,
      playerThemes: run.themes,
      playerVoltage: run.voltage,
      aiDeck: buildDeck(['vanilla'], 'LV', 43),
      aiThemes: ['vanilla'],
      aiVoltage: 'LV',
    });

    persistRun(run);
    persistMatch(run.ownerUuid, match);

    expect(restoreRun(run.runId)?.ownerUuid).toBe(run.ownerUuid);
    expect(restoreMatch(match.matchId)?.match.seed).toBe(42);
  });
});
