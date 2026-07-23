import { describe, expect, it } from 'vitest';
import {
  buildPlayAction,
  canPlayDrop,
  canPlayToBench,
  canPlayToSlot,
  needsEnemyTarget,
  spellTargetKind,
} from './playLegality';
import type { BoardUnit, CardDef, PlayerState } from '../api/client';
import { BOARD_SKINS, isSkinUnlocked, resolveSkin } from './skins';

function me(partial: Partial<PlayerState> = {}): PlayerState {
  return {
    name: 'p',
    nexusHp: 20,
    maxNexusHp: 20,
    mana: 3,
    maxMana: 3,
    spellMana: 0,
    bankedMana: 0,
    voltage: 'LV',
    hand: ['gt_worker'],
    board: [null, null, null, null, null, null],
    damageReductionPct: 0,
    reflectToNexus: false,
    singularitiesPlayed: 0,
    eternalActive: false,
    ...partial,
  };
}

function unit(partial: Partial<BoardUnit> = {}): BoardUnit {
  return {
    instanceId: 'x',
    cardId: 'gt_worker',
    attack: 2,
    health: 3,
    maxHealth: 3,
    armor: 0,
    keywords: [],
    aspects: [],
    isStructure: false,
    untargetable: false,
    ...partial,
  };
}

const cardMap = new Map<string, CardDef>([
  [
    'gt_worker',
    {
      id: 'gt_worker',
      name: 'GT Worker',
      nameZh: 'GT 工人',
      theme: 'gt',
      kind: 'unit',
      cost: 2,
    },
  ],
  [
    'gt_overclock',
    {
      id: 'gt_overclock',
      name: 'Overclock',
      nameZh: '超频',
      theme: 'gt',
      kind: 'spell',
      cost: 1,
      spellSpeed: 'burst',
    },
  ],
  [
    'van_smite',
    {
      id: 'van_smite',
      name: 'Smite',
      nameZh: '惩戒',
      theme: 'vanilla',
      kind: 'spell',
      cost: 2,
      spellSpeed: 'fast',
      effect: { id: 'damage', target: 'enemy_unit', amount: 3 },
    },
  ],
  [
    'van_rally',
    {
      id: 'van_rally',
      name: 'Rally',
      nameZh: '集结',
      theme: 'vanilla',
      kind: 'spell',
      cost: 3,
      spellSpeed: 'slow',
    },
  ],
]);

describe('playLegality', () => {
  it('allows unit play to empty own slot in main', () => {
    const legal = canPlayToSlot({
      phase: 'main',
      me: me(),
      handIndex: 0,
      targetSlot: 2,
      cardMap,
      side: 'player',
    });
    expect(legal.ok).toBe(true);
  });

  it('allows bench zone drop when any slot is empty', () => {
    const legal = canPlayToBench({
      phase: 'main',
      me: me(),
      handIndex: 0,
      cardMap,
    });
    expect(legal.ok).toBe(true);
    const drop = canPlayDrop({
      phase: 'main',
      me: me(),
      handIndex: 0,
      target: { kind: 'bench', side: 'player' },
      cardMap,
    });
    expect(drop.ok).toBe(true);
  });

  it('uses effect.target for spell targeting', () => {
    expect(spellTargetKind(cardMap.get('van_smite'))).toBe('enemy_unit');
    expect(needsEnemyTarget(cardMap.get('van_smite'))).toBe(true);
  });

  it('rejects when mana is insufficient', () => {
    const legal = canPlayToSlot({
      phase: 'main',
      me: me({ mana: 0, bankedMana: 0 }),
      handIndex: 0,
      targetSlot: 0,
      cardMap,
      side: 'player',
    });
    expect(legal.ok).toBe(false);
    expect(legal.reason).toBe('mana');
  });

  it('counts spell reserve for spells but not units', () => {
    const spell = canPlayToSlot({
      phase: 'main',
      me: me({ hand: ['gt_overclock'], mana: 0, spellMana: 1 }),
      handIndex: 0,
      targetSlot: 0,
      cardMap,
      side: 'enemy',
    });
    const unitPlay = canPlayToSlot({
      phase: 'main',
      me: me({ mana: 0, spellMana: 3 }),
      handIndex: 0,
      targetSlot: 0,
      cardMap,
      side: 'player',
    });
    expect(spell.ok).toBe(true);
    expect(unitPlay.reason).toBe('mana');
  });

  it('rejects occupied slot for units but bench still ok', () => {
    const occupied = me({
      board: [unit(), null, null, null, null, null],
    });
    const slot = canPlayToSlot({
      phase: 'main',
      me: occupied,
      handIndex: 0,
      targetSlot: 0,
      cardMap,
      side: 'player',
    });
    const bench = canPlayToBench({
      phase: 'main',
      me: occupied,
      handIndex: 0,
      cardMap,
    });
    expect(slot.ok).toBe(false);
    expect(slot.reason).toBe('slot_full');
    expect(bench.ok).toBe(true);
  });

  it('requires a legal enemy unit for targeted damage spells', () => {
    const emptyTarget = canPlayToSlot({
      phase: 'main',
      me: me({ hand: ['van_smite'] }),
      opponent: me(),
      handIndex: 0,
      targetSlot: 0,
      cardMap,
      side: 'enemy',
    });
    const legalTarget = canPlayToSlot({
      phase: 'main',
      me: me({ hand: ['van_smite'] }),
      opponent: me({ board: [unit(), null, null, null, null, null] }),
      handIndex: 0,
      targetSlot: 0,
      cardMap,
      side: 'enemy',
    });
    expect(emptyTarget.reason).toBe('missing_target');
    expect(legalTarget.ok).toBe(true);
  });

  it('builds spell action with enemy target', () => {
    const action = buildPlayAction({
      handIndex: 0,
      targetSlot: 3,
      side: 'enemy',
      kind: 'spell',
    });
    expect(action).toEqual({
      type: 'play_card',
      handIndex: 0,
      targetSlot: 0,
      targetEnemySlot: 3,
    });
  });

  it('builds unit action without targetSlot for bench zone', () => {
    const action = buildPlayAction({
      handIndex: 0,
      side: 'player',
      kind: 'unit',
    });
    expect(action).toEqual({
      type: 'play_card',
      handIndex: 0,
    });
  });

  it('allows fast and burst responses but rejects slow spells and units', () => {
    const opponent = me({ board: [unit(), null, null, null, null, null] });
    const fast = canPlayToSlot({
      phase: 'combat_response',
      me: me({ hand: ['van_smite'] }),
      opponent,
      handIndex: 0,
      targetSlot: 0,
      cardMap,
      side: 'enemy',
    });
    const burst = canPlayToSlot({
      phase: 'spell_response',
      me: me({ hand: ['gt_overclock'] }),
      opponent,
      handIndex: 0,
      targetSlot: 0,
      cardMap,
      side: 'enemy',
    });
    const slow = canPlayToSlot({
      phase: 'spell_response',
      me: me({ hand: ['van_rally'] }),
      opponent,
      handIndex: 0,
      targetSlot: 0,
      cardMap,
      side: 'enemy',
    });
    const unitPlay = canPlayToSlot({
      phase: 'combat_response',
      me: me(),
      opponent,
      handIndex: 0,
      targetSlot: 0,
      cardMap,
      side: 'player',
    });
    expect(fast.ok).toBe(true);
    expect(burst.ok).toBe(true);
    expect(slow.reason).toBe('slow_response');
    expect(unitPlay.reason).toBe('response_spell_only');
  });
});

describe('skins', () => {
  it('unlocks default factory skin at 0 victories', () => {
    expect(isSkinUnlocked(BOARD_SKINS[0], 0)).toBe(true);
    expect(isSkinUnlocked(BOARD_SKINS[1], 0)).toBe(false);
    expect(isSkinUnlocked(BOARD_SKINS[1], 2)).toBe(true);
  });

  it('resolves known and unknown ids', () => {
    expect(resolveSkin('thaum_workshop').nameZh).toBe('神秘工坊');
    expect(resolveSkin('nope').id).toBe('gt_factory');
  });
});
