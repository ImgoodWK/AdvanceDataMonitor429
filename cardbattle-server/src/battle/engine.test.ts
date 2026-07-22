import { describe, expect, it } from 'vitest';
import { createMatch, applyAction } from './engine.js';
import { buildDeck } from '../pve/run.js';
import { offenseMultiplier, nexusDamageMultiplier } from './voltage.js';
import { enqueueReward, listPending } from '../rewards/pending.js';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';

describe('voltage', () => {
  it('punishes under-level offense', () => {
    expect(offenseMultiplier('LV', 'HV')).toBeLessThan(1);
    expect(offenseMultiplier('HV', 'LV')).toBe(1);
  });
  it('reduces nexus damage by own voltage', () => {
    expect(nexusDamageMultiplier('IV')).toBeLessThan(nexusDamageMultiplier('ULV'));
  });
});

describe('battle engine', () => {
  it('plays a full short match without throwing', () => {
    const deck = buildDeck(['vanilla'], 'LV', 42);
    const state = createMatch({
      seed: 42,
      playerId: 'p',
      playerName: 'P',
      playerDeck: deck,
      playerThemes: ['vanilla'],
      playerVoltage: 'LV',
      aiDeck: buildDeck(['vanilla'], 'ULV', 43),
      aiThemes: ['vanilla'],
      aiVoltage: 'ULV',
    });
    applyAction(state, 0, { type: 'end_main' });
    expect(state.turn).toBeGreaterThanOrEqual(1);
    expect(state.players[0].hand.length).toBeGreaterThan(0);
  });
});

describe('pending rewards', () => {
  it('writes queue file', () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'cb-'));
    process.env.CARDBATTLE_DATA_DIR = dir;
    const entry = enqueueReward(
      'test-owner',
      [{ modid: 'gregtech', name: 'pump', meta: 0, count: 16 }],
      { runId: 'r1', stageId: 's1' },
    );
    expect(entry.id).toBeTruthy();
    expect(listPending('test-owner').length).toBe(1);
  });
});
