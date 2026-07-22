import type { CardDef, PlayerState } from '../api/client';

export type DropSide = 'player' | 'enemy';

export interface PlayLegality {
  ok: boolean;
  reason?: string;
}

const ENEMY_UNIT_TARGET_SPELLS = new Set(['van_smite', 'ae_annihilation', 'th_ignis']);
const FRIENDLY_UNIT_TARGET_SPELLS = new Set(['van_heal', 'th_ordo_aer', 'th_ward', 'ge_mutate', 'ge_clone']);

function targetAt(player: PlayerState | undefined, slot: number) {
  if (!Number.isInteger(slot) || slot < 0 || slot > 5) return null;
  return player?.board[slot] ?? null;
}

export function isPlayWindow(phase: string): boolean {
  return phase === 'main' || phase === 'spell_response' || phase === 'combat_response';
}

/** Pure legality checks for drag-play / click fallback. */
export function canPlayToSlot(args: {
  phase: string;
  me: PlayerState;
  opponent?: PlayerState;
  handIndex: number;
  targetSlot: number;
  cardMap: Map<string, CardDef>;
  side: DropSide;
}): PlayLegality {
  if (!isPlayWindow(args.phase)) return { ok: false, reason: 'not_play_window' };
  const cardId = args.me.hand[args.handIndex];
  if (!cardId || cardId === '?') return { ok: false, reason: 'no_card' };
  const def = args.cardMap.get(cardId);
  if (!def) return { ok: false, reason: 'unknown_card' };

  const response = args.phase === 'spell_response' || args.phase === 'combat_response';
  if (response && def.kind !== 'spell') return { ok: false, reason: 'response_spell_only' };
  if (response && (def.spellSpeed ?? 'slow') === 'slow') {
    return { ok: false, reason: 'slow_response' };
  }

  const cost = def.cost ?? 0;
  const manaAvail =
    args.me.mana + (args.me.bankedMana ?? 0) + (def.kind === 'spell' ? (args.me.spellMana ?? 0) : 0);
  if (cost > manaAvail) return { ok: false, reason: 'mana' };

  if (def.kind === 'spell') {
    if (!Number.isInteger(args.targetSlot) || args.targetSlot < 0 || args.targetSlot > 5) {
      return { ok: false, reason: 'slot_range' };
    }
    if (ENEMY_UNIT_TARGET_SPELLS.has(def.id)) {
      if (args.side !== 'enemy') return { ok: false, reason: 'need_enemy_target' };
      const target = targetAt(args.opponent, args.targetSlot);
      if (!target) return { ok: false, reason: 'missing_target' };
      if (target.untargetable) return { ok: false, reason: 'untargetable' };
      return { ok: true };
    }
    if (FRIENDLY_UNIT_TARGET_SPELLS.has(def.id)) {
      if (args.side !== 'player') return { ok: false, reason: 'need_friendly_target' };
      const target = targetAt(args.me, args.targetSlot);
      if (!target || target.isStructure) return { ok: false, reason: 'missing_unit_target' };
      if (def.id === 'ge_clone' && args.me.board.every((unit) => unit != null)) {
        return { ok: false, reason: 'slot_full' };
      }
      return { ok: true };
    }
    if (def.id === 'gt_wrench') {
      if (args.side !== 'enemy') return { ok: false, reason: 'need_enemy_target' };
      const target = targetAt(args.opponent, args.targetSlot);
      if (!target?.isStructure || !target.keywords.includes('machine')) {
        return { ok: false, reason: 'need_machine_target' };
      }
      return { ok: true };
    }
    if (def.id === 'fo_smoke') {
      if (args.side !== 'enemy') return { ok: false, reason: 'need_enemy_target' };
      const target = targetAt(args.opponent, args.targetSlot);
      return target?.keywords.includes('stealth')
        ? { ok: true }
        : { ok: false, reason: 'need_stealth_target' };
    }
    if (def.id === 'ee_watch') {
      if (args.side !== 'player') return { ok: false, reason: 'need_friendly_target' };
      const target = targetAt(args.me, args.targetSlot);
      return target?.isStructure && target.hiveTurnsLeft != null
        ? { ok: true }
        : { ok: false, reason: 'need_cooldown_target' };
    }
    // Untargeted spells may be dropped on either board as a click/drag fallback.
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
