import { describe, expect, it } from 'vitest';
import { createMatch, applyAction } from './engine.js';
import { buildDeck, generateStages, rewardChoices } from '../pve/run.js';
import { offenseMultiplier, nexusDamageMultiplier } from './voltage.js';
import { enqueueReward, listPending } from '../rewards/pending.js';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import type { BoardUnit } from './types.js';

function boardUnit(cardId: string, attack: number, health: number, maxHealth = health): BoardUnit {
  return {
    instanceId: `${cardId}-instance`,
    cardId,
    attack,
    health,
    maxHealth,
    armor: 0,
    keywords: [],
    aspects: [],
    isStructure: false,
    untargetable: false,
    equipment: [],
  };
}

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
    applyAction(state, 0, { type: 'confirm_mulligan', replaceIndices: [] });
    expect(state.players[0].hand).toHaveLength(5);
    applyAction(state, 0, { type: 'end_main' });
    expect(state.turn).toBeGreaterThanOrEqual(1);
    expect(state.players[0].hand.length).toBeGreaterThan(0);
  });

  it('passes priority after one card and ends a round after two passes', () => {
    const deck = buildDeck(['vanilla'], 'LV', 7);
    const state = createMatch({
      seed: 7,
      playerId: 'p0',
      playerName: 'P0',
      playerDeck: deck,
      playerThemes: ['vanilla'],
      playerVoltage: 'LV',
      aiDeck: buildDeck(['vanilla'], 'LV', 8),
      aiThemes: ['vanilla'],
      aiVoltage: 'LV',
    });
    applyAction(state, 0, { type: 'confirm_mulligan', replaceIndices: [] });
    state.players[1].isAi = false;
    state.players[0].mana = 10;
    state.players[0].hand = ['van_grunt'];
    applyAction(state, 0, { type: 'play_card', handIndex: 0, targetSlot: 0 });
    expect(state.activePlayer).toBe(1);
    expect(state.consecutivePasses).toBe(0);
    expect(state.players[0].discard).not.toContain('van_grunt');

    applyAction(state, 1, { type: 'pass_priority' });
    applyAction(state, 0, { type: 'pass_priority' });
    expect(state.turn).toBe(2);
    expect(state.attackTokenPlayer).toBe(1);
    expect(state.attackTokenAvailable).toBe(true);
    expect(state.players[0].spellMana).toBeGreaterThanOrEqual(0);
  });

  it('replaces selected opening cards before shuffling them back', () => {
    const deck = buildDeck(['vanilla'], 'LV', 11);
    const state = createMatch({
      seed: 11,
      playerId: 'p0',
      playerName: 'P0',
      playerDeck: deck,
      playerThemes: ['vanilla'],
      playerVoltage: 'LV',
      aiDeck: buildDeck(['vanilla'], 'LV', 12),
      aiThemes: ['vanilla'],
      aiVoltage: 'LV',
    });
    const originalHand = [...state.players[0].hand];
    const firstReplacement = state.players[0].deck[0];
    const secondReplacement = state.players[0].deck[1];

    applyAction(state, 0, { type: 'confirm_mulligan', replaceIndices: [0, 2] });

    expect(state.phase).toBe('main');
    expect(state.mulliganDone).toEqual([true, true]);
    expect(state.players[0].hand[0]).toBe(firstReplacement);
    expect(state.players[0].hand[1]).toBe(originalHand[1]);
    expect(state.players[0].hand[2]).toBe(secondReplacement);
    expect(state.players[0].hand[3]).toBe(originalHand[3]);
    expect(state.players[0].deck).toEqual(expect.arrayContaining([originalHand[0], originalHand[2]]));
  });

  it('rejects invalid mulligan indices without mutating state', () => {
    const deck = buildDeck(['vanilla'], 'LV', 13);
    const state = createMatch({
      seed: 13,
      playerId: 'p0',
      playerName: 'P0',
      playerDeck: deck,
      playerThemes: ['vanilla'],
      playerVoltage: 'LV',
      aiDeck: buildDeck(['vanilla'], 'LV', 14),
      aiThemes: ['vanilla'],
      aiVoltage: 'LV',
    });
    const before = structuredClone(state);
    expect(() =>
      applyAction(state, 0, { type: 'confirm_mulligan', replaceIndices: [0, 0] }),
    ).toThrow('Invalid mulligan index');
    expect(state).toEqual(before);
  });

  it('rejects an illegal spell target without mutating battle state', () => {
    const deck = buildDeck(['vanilla'], 'LV', 17);
    const state = createMatch({
      seed: 17,
      playerId: 'p0',
      playerName: 'P0',
      playerDeck: deck,
      playerThemes: ['vanilla'],
      playerVoltage: 'LV',
      aiDeck: buildDeck(['vanilla'], 'LV', 18),
      aiThemes: ['vanilla'],
      aiVoltage: 'LV',
    });
    applyAction(state, 0, { type: 'confirm_mulligan', replaceIndices: [] });
    state.players[1].isAi = false;
    state.players[0].hand = ['van_smite'];
    state.players[0].mana = 2;
    state.players[0].spellMana = 1;
    state.players[0].bankedMana = 3;
    const before = structuredClone(state);

    expect(() =>
      applyAction(state, 0, { type: 'play_card', handIndex: 0, targetEnemySlot: 0 }),
    ).toThrow('Missing enemy target');
    expect(state).toEqual(before);
  });

  it('burns draws beyond ten cards and loses on an empty-deck draw', () => {
    const deck = buildDeck(['vanilla'], 'LV', 19);
    const state = createMatch({
      seed: 19,
      playerId: 'p0',
      playerName: 'P0',
      playerDeck: deck,
      playerThemes: ['vanilla'],
      playerVoltage: 'LV',
      aiDeck: buildDeck(['vanilla'], 'LV', 20),
      aiThemes: ['vanilla'],
      aiVoltage: 'LV',
    });
    applyAction(state, 0, { type: 'confirm_mulligan', replaceIndices: [] });
    state.players[1].isAi = false;
    const player = state.players[0];
    player.hand = ['ge_split', ...Array<string>(9).fill('van_grunt')];
    player.deck = ['van_scout', 'van_wolf'];
    player.discard = [];
    player.mana = 10;

    applyAction(state, 0, { type: 'play_card', handIndex: 0 });
    expect(player.hand).toHaveLength(10);
    expect(player.discard).toEqual(['ge_split', 'van_wolf']);
    expect(state.log.at(-1)).toContain('burns van_wolf');

    state.activePlayer = 0;
    player.hand = ['as_attune'];
    player.deck = [];
    player.discard = [];
    player.nexusHp = 20;
    player.mana = 10;
    applyAction(state, 0, { type: 'play_card', handIndex: 0 });
    expect(state.phase).toBe('game_over');
    expect(state.winner).toBe(1);
  });

  it('resolves fast responses in LIFO order and keeps response passes out of round passes', () => {
    const state = createMatch({
      seed: 23,
      playerId: 'p0',
      playerName: 'P0',
      playerDeck: buildDeck(['vanilla'], 'LV', 23),
      playerThemes: ['vanilla'],
      playerVoltage: 'LV',
      aiDeck: buildDeck(['thaum'], 'LV', 24),
      aiThemes: ['thaum'],
      aiVoltage: 'LV',
    });
    applyAction(state, 0, { type: 'confirm_mulligan', replaceIndices: [] });
    state.players[1].isAi = false;
    state.players[0].hand = ['van_smite'];
    state.players[1].hand = ['th_ward'];
    state.players[0].mana = 10;
    state.players[1].mana = 10;
    state.players[1].board[0] = boardUnit('th_zombie', 2, 2, 4);

    applyAction(state, 0, { type: 'play_card', handIndex: 0, targetEnemySlot: 0 });
    expect(state.phase).toBe('spell_response');
    expect(state.players[0].mana).toBe(8);
    expect(state.players[0].spellMana).toBe(0);
    expect(state.spellStack.map((item) => item.cardId)).toEqual(['van_smite']);
    expect(state.players[0].discard).not.toContain('van_smite');

    applyAction(state, 1, { type: 'play_card', handIndex: 0, targetSlot: 0 });
    expect(state.spellStack.map((item) => item.cardId)).toEqual(['van_smite', 'th_ward']);
    applyAction(state, 0, { type: 'pass_priority' });
    applyAction(state, 1, { type: 'pass_priority' });

    expect(state.phase).toBe('main');
    expect(state.activePlayer).toBe(1);
    expect(state.consecutivePasses).toBe(0);
    expect(state.responsePasses).toBe(0);
    expect(state.spellStack).toEqual([]);
    expect(state.players[1].board[0]?.health).toBe(1);
    expect(state.players[1].board[0]?.armor).toBe(0);
    expect(state.players[0].discard).toContain('van_smite');
    expect(state.players[1].discard).toContain('th_ward');
  });

  it('rejects slow responses transactionally while burst spells resolve without passing priority', () => {
    const state = createMatch({
      seed: 25,
      playerId: 'p0',
      playerName: 'P0',
      playerDeck: buildDeck(['vanilla'], 'LV', 25),
      playerThemes: ['vanilla'],
      playerVoltage: 'LV',
      aiDeck: buildDeck(['vanilla'], 'LV', 26),
      aiThemes: ['vanilla'],
      aiVoltage: 'LV',
    });
    applyAction(state, 0, { type: 'confirm_mulligan', replaceIndices: [] });
    state.players[1].isAi = false;
    state.players[1].board[0] = boardUnit('van_grunt', 2, 2);
    state.players[0].hand = ['van_smite'];
    state.players[1].hand = ['van_rally'];
    state.players[0].mana = 10;
    state.players[1].mana = 10;
    applyAction(state, 0, { type: 'play_card', handIndex: 0, targetEnemySlot: 0 });
    const beforeSlow = structuredClone(state);
    expect(() => applyAction(state, 1, { type: 'play_card', handIndex: 0 })).toThrow(
      'Slow spells cannot be played in a response window',
    );
    expect(state).toEqual(beforeSlow);

    applyAction(state, 1, { type: 'pass_priority' });
    applyAction(state, 0, { type: 'pass_priority' });
    state.activePlayer = 0;
    state.players[0].hand = ['gt_overclock'];
    state.players[0].mana = 1;
    applyAction(state, 0, { type: 'play_card', handIndex: 0 });
    expect(state.phase).toBe('main');
    expect(state.activePlayer).toBe(0);
    expect(state.players[0].mana).toBe(2);
    expect(state.spellStack).toEqual([]);
  });

  it('opens a combat response window after blocks and resolves it before combat damage', () => {
    const state = createMatch({
      seed: 27,
      playerId: 'p0',
      playerName: 'P0',
      playerDeck: buildDeck(['thaum'], 'LV', 27),
      playerThemes: ['thaum'],
      playerVoltage: 'LV',
      aiDeck: buildDeck(['vanilla'], 'LV', 28),
      aiThemes: ['vanilla'],
      aiVoltage: 'LV',
    });
    applyAction(state, 0, { type: 'confirm_mulligan', replaceIndices: [] });
    state.players[1].isAi = false;
    state.players[0].board[0] = boardUnit('th_zombie', 3, 3);
    state.players[1].board[0] = boardUnit('van_grunt', 1, 3);
    state.players[0].hand = ['th_ignis'];
    state.players[0].mana = 10;
    const defenderNexusBefore = state.players[1].nexusHp;

    applyAction(state, 0, { type: 'start_attack' });
    applyAction(state, 0, { type: 'declare_attacks', slots: [0] });
    applyAction(state, 1, { type: 'declare_blocks', pairs: [{ attackerSlot: 0, blockerSlot: 0 }] });
    expect(state.phase).toBe('combat_response');
    expect(state.combatAttacker).toBe(0);
    expect(state.attackTokenAvailable).toBe(true);

    applyAction(state, 0, { type: 'play_card', handIndex: 0, targetEnemySlot: 0 });
    applyAction(state, 1, { type: 'pass_priority' });
    applyAction(state, 0, { type: 'pass_priority' });

    expect(state.phase).toBe('main');
    expect(state.combatAttacker).toBeNull();
    expect(state.attackTokenAvailable).toBe(false);
    expect(state.players[1].board[0]).toBeNull();
    expect(state.players[0].board[0]?.health).toBe(2);
    expect(state.players[1].nexusHp).toBe(defenderNexusBefore);
  });

  it('heals surviving units at round start and lets an empty declaration cancel safely', () => {
    const state = createMatch({
      seed: 31,
      playerId: 'p0',
      playerName: 'P0',
      playerDeck: buildDeck(['vanilla'], 'LV', 31),
      playerThemes: ['vanilla'],
      playerVoltage: 'LV',
      aiDeck: buildDeck(['vanilla'], 'LV', 32),
      aiThemes: ['vanilla'],
      aiVoltage: 'LV',
    });
    applyAction(state, 0, { type: 'confirm_mulligan', replaceIndices: [] });
    state.players[1].isAi = false;
    state.players[0].board[0] = boardUnit('van_grunt', 2, 1, 3);

    applyAction(state, 0, { type: 'start_attack' });
    applyAction(state, 0, { type: 'declare_attacks', slots: [] });
    expect(state.phase).toBe('main');
    expect(state.attackTokenAvailable).toBe(true);

    applyAction(state, 0, { type: 'pass_priority' });
    applyAction(state, 1, { type: 'pass_priority' });
    expect(state.turn).toBe(2);
    expect(state.players[0].board[0]?.health).toBe(3);
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

describe('path adventure', () => {
  it('builds a branching route ending in a boss skin reward', () => {
    const stages = generateStages(42, 'LV');
    expect(stages.filter((stage) => stage.column === 0)).toHaveLength(2);
    const boss = stages.find((stage) => stage.kind === 'boss');
    expect(boss?.nextStageIds).toEqual([]);
    expect(boss?.skinRewardId).toBe('overclocked_nexus');
  });

  it('exposes a stable voltage reward bridge key', () => {
    const stage = generateStages(42, 'MV').find((candidate) => candidate.kind === 'elite')!;
    const reward = rewardChoices(stage, 'MV').find((choice) => choice.id === 'voltage_cache');
    expect(reward?.rewardKey).toBe('textech.cardbattle.voltage.mv.cache');
    expect(reward?.delivery).toBe('pending_bridge');
  });
});

describe('LoR bench and attack order ids', () => {
  it('plays units without a preferred slot and declares attacks by instanceId', () => {
    const deck = buildDeck(['vanilla'], 'LV', 99);
    expect(deck.length).toBeGreaterThanOrEqual(40);
    const state = createMatch({
      seed: 99,
      playerId: 'p',
      playerName: 'P',
      playerDeck: deck,
      playerThemes: ['vanilla'],
      playerVoltage: 'LV',
      aiDeck: buildDeck(['vanilla'], 'ULV', 100),
      aiThemes: ['vanilla'],
      aiVoltage: 'ULV',
    });
    applyAction(state, 0, { type: 'confirm_mulligan', replaceIndices: [] });
    state.players[1].isAi = false;
    state.players[0].mana = 10;
    state.players[0].hand = ['van_grunt', 'van_scout'];
    applyAction(state, 0, { type: 'play_card', handIndex: 0 });
    expect(state.players[0].board[0]?.cardId).toBe('van_grunt');
    applyAction(state, 1, { type: 'pass_priority' });
    applyAction(state, 0, { type: 'play_card', handIndex: 0 });
    expect(state.players[0].board.filter(Boolean)).toHaveLength(2);
    expect(state.players[0].board[1]?.cardId).toBe('van_scout');

    applyAction(state, 1, { type: 'pass_priority' });
    applyAction(state, 0, { type: 'start_attack' });
    const ids = state.players[0].board.filter((u): u is BoardUnit => !!u && !u.isStructure).map((u) => u.instanceId);
    applyAction(state, 0, { type: 'declare_attacks', instanceIds: ids });
    expect(state.attackOrderIds).toEqual(ids);
    expect(state.phase).toBe('block_declare');
  });
});
