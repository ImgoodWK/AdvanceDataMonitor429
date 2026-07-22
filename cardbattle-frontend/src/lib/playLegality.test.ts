import { describe, expect, it } from 'vitest';
import { buildPlayAction, canPlayToSlot } from './playLegality';
import type { CardDef, PlayerState } from '../api/client';
import { BOARD_SKINS, isSkinUnlocked, resolveSkin } from './skins';

function me(partial: Partial<PlayerState> = {}): PlayerState {
  return {
    name: 'p',
    nexusHp: 20,
    maxNexusHp: 20,
    mana: 3,
    maxMana: 3,
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

  it('rejects occupied slot for units', () => {
    const occupied = me({
      board: [
        {
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
        },
        null,
        null,
        null,
        null,
        null,
      ],
    });
    const legal = canPlayToSlot({
      phase: 'main',
      me: occupied,
      handIndex: 0,
      targetSlot: 0,
      cardMap,
      side: 'player',
    });
    expect(legal.ok).toBe(false);
    expect(legal.reason).toBe('slot_full');
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
