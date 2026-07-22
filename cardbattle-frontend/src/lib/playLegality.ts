import type { CardDef, PlayerState } from '../api/client';

export type DropSide = 'player' | 'enemy';

export interface PlayLegality {
  ok: boolean;
  reason?: string;
}

/** Pure legality checks for drag-play / click fallback. */
export function canPlayToSlot(args: {
  phase: string;
  me: PlayerState;
  handIndex: number;
  targetSlot: number;
  cardMap: Map<string, CardDef>;
  side: DropSide;
}): PlayLegality {
  if (args.phase !== 'main') return { ok: false, reason: 'not_main' };
  const cardId = args.me.hand[args.handIndex];
  if (!cardId || cardId === '?') return { ok: false, reason: 'no_card' };
  const def = args.cardMap.get(cardId);
  if (!def) return { ok: false, reason: 'unknown_card' };

  const cost = def.cost ?? 0;
  const manaAvail = args.me.mana + (args.me.bankedMana ?? 0);
  if (cost > manaAvail) return { ok: false, reason: 'mana' };

  if (def.kind === 'spell') {
    if (args.side === 'enemy') return { ok: true };
    // Spells may also be cast via default target without a free board slot.
    return { ok: true };
  }

  if (args.side !== 'player') return { ok: false, reason: 'need_own_slot' };
  if (args.targetSlot < 0 || args.targetSlot > 5) return { ok: false, reason: 'slot_range' };
  if (args.me.board[args.targetSlot] != null) return { ok: false, reason: 'slot_full' };
  return { ok: true };
}

export function buildPlayAction(args: {
  handIndex: number;
  targetSlot: number;
  side: DropSide;
  kind?: string;
}) {
  if (args.kind === 'spell' || args.side === 'enemy') {
    return {
      type: 'play_card' as const,
      handIndex: args.handIndex,
      targetSlot: args.side === 'player' ? args.targetSlot : 0,
      targetEnemySlot: args.side === 'enemy' ? args.targetSlot : 0,
    };
  }
  return {
    type: 'play_card' as const,
    handIndex: args.handIndex,
    targetSlot: args.targetSlot,
  };
}
