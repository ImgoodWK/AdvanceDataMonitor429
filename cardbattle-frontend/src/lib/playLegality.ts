import type { CardDef, PlayerState } from '../api/client';

export type DropSide = 'player' | 'enemy';

export type SpellTargetKind =
  | 'enemy_unit'
  | 'friendly_unit'
  | 'friendly_unit_clone'
  | 'enemy_machine'
  | 'enemy_stealth'
  | 'friendly_cooldown'
  | 'none';

export interface PlayLegality {
  ok: boolean;
  reason?: string;
}

export interface DropTarget {
  kind: 'bench' | 'battlefield' | 'unit' | 'blocker';
  side?: DropSide;
  slot?: number;
  attackerInstanceId?: string;
}

const LEGACY_ENEMY_UNIT = new Set(['van_smite', 'ae_annihilation', 'th_ignis']);
const LEGACY_FRIENDLY_UNIT = new Set(['van_heal', 'th_ordo_aer', 'th_ward', 'ge_mutate']);
const LEGACY_FRIENDLY_CLONE = new Set(['ge_clone']);

function targetAt(player: PlayerState | undefined, slot: number) {
  if (!Number.isInteger(slot) || slot < 0 || slot > 5) return null;
  return player?.board[slot] ?? null;
}

export function firstEmptyBenchSlot(board: (PlayerState['board'][number])[]): number {
  for (let i = 0; i < board.length; i++) {
    if (board[i] == null) return i;
  }
  return -1;
}

export function parseDataDrop(value: string | null | undefined): DropTarget | null {
  if (!value) return null;
  const parts = value.split(':');
  if (parts[0] === 'bench' && (parts[1] === 'player' || parts[1] === 'enemy')) {
    return { kind: 'bench', side: parts[1] };
  }
  if (parts[0] === 'battlefield' && (parts[1] === 'player' || parts[1] === 'enemy')) {
    const slot = parts[2] != null ? Number(parts[2]) : undefined;
    return {
      kind: 'battlefield',
      side: parts[1],
      slot: slot != null && Number.isInteger(slot) ? slot : undefined,
    };
  }
  if (parts[0] === 'unit' && (parts[1] === 'player' || parts[1] === 'enemy') && parts[2] != null) {
    const slot = Number(parts[2]);
    if (!Number.isInteger(slot)) return null;
    return { kind: 'unit', side: parts[1], slot };
  }
  if (parts[0] === 'blocker' && parts[1]) {
    return { kind: 'blocker', attackerInstanceId: parts.slice(1).join(':') };
  }
  return null;
}

export function parseDropFromElement(el: Element | null): DropTarget | null {
  if (!el) return null;
  const host = el.closest('[data-drop]');
  return parseDataDrop(host?.getAttribute('data-drop'));
}

export function spellTargetKind(def: CardDef | undefined): SpellTargetKind {
  if (!def) return 'none';
  if (def.effect?.target) return def.effect.target as SpellTargetKind;
  if (LEGACY_ENEMY_UNIT.has(def.id)) return 'enemy_unit';
  if (LEGACY_FRIENDLY_CLONE.has(def.id)) return 'friendly_unit_clone';
  if (LEGACY_FRIENDLY_UNIT.has(def.id)) return 'friendly_unit';
  if (def.id === 'gt_wrench') return 'enemy_machine';
  if (def.id === 'fo_smoke') return 'enemy_stealth';
  if (def.id === 'ee_watch') return 'friendly_cooldown';
  return 'none';
}

export function needsEnemyTarget(def: CardDef | undefined): boolean {
  const kind = spellTargetKind(def);
  return (
    kind === 'enemy_unit' ||
    kind === 'enemy_machine' ||
    kind === 'enemy_stealth'
  );
}

export function needsFriendlyTarget(def: CardDef | undefined): boolean {
  const kind = spellTargetKind(def);
  return (
    kind === 'friendly_unit' ||
    kind === 'friendly_unit_clone' ||
    kind === 'friendly_cooldown'
  );
}

export function isPlayWindow(phase: string): boolean {
  return phase === 'main' || phase === 'spell_response' || phase === 'combat_response';
}

function basePlayChecks(args: {
  phase: string;
  me: PlayerState;
  handIndex: number;
  cardMap: Map<string, CardDef>;
}): { ok: true; def: CardDef } | { ok: false; reason: string } {
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

  return { ok: true, def };
}

function validateSpellTarget(args: {
  def: CardDef;
  me: PlayerState;
  opponent?: PlayerState;
  targetSlot: number;
  side: DropSide;
}): PlayLegality {
  const kind = spellTargetKind(args.def);

  if (kind === 'none') return { ok: true };

  if (kind === 'enemy_unit' || kind === 'enemy_machine' || kind === 'enemy_stealth') {
    if (args.side !== 'enemy') return { ok: false, reason: 'need_enemy_target' };
    const target = targetAt(args.opponent, args.targetSlot);
    if (!target) return { ok: false, reason: 'missing_target' };
    if (target.untargetable) return { ok: false, reason: 'untargetable' };
    if (kind === 'enemy_machine') {
      return target.isStructure && target.keywords.includes('machine')
        ? { ok: true }
        : { ok: false, reason: 'need_machine_target' };
    }
    if (kind === 'enemy_stealth') {
      return target.keywords.includes('stealth')
        ? { ok: true }
        : { ok: false, reason: 'need_stealth_target' };
    }
    return { ok: true };
  }

  if (kind === 'friendly_unit' || kind === 'friendly_unit_clone') {
    if (args.side !== 'player') return { ok: false, reason: 'need_friendly_target' };
    const target = targetAt(args.me, args.targetSlot);
    if (!target || target.isStructure) return { ok: false, reason: 'missing_unit_target' };
    if (kind === 'friendly_unit_clone' && firstEmptyBenchSlot(args.me.board) < 0) {
      return { ok: false, reason: 'slot_full' };
    }
    return { ok: true };
  }

  if (kind === 'friendly_cooldown') {
    if (args.side !== 'player') return { ok: false, reason: 'need_friendly_target' };
    const target = targetAt(args.me, args.targetSlot);
    return target?.isStructure && target.hiveTurnsLeft != null
      ? { ok: true }
      : { ok: false, reason: 'need_cooldown_target' };
  }

  return { ok: true };
}

/** Unit/structure: any empty bench slot is enough. */
export function canPlayToBench(args: {
  phase: string;
  me: PlayerState;
  handIndex: number;
  cardMap: Map<string, CardDef>;
}): PlayLegality {
  const base = basePlayChecks(args);
  if (!base.ok) return base;
  if (base.def.kind !== 'unit' && base.def.kind !== 'structure') {
    return { ok: false, reason: 'need_bench_target' };
  }
  if (firstEmptyBenchSlot(args.me.board) < 0) return { ok: false, reason: 'slot_full' };
  return { ok: true };
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
  const base = basePlayChecks(args);
  if (!base.ok) return base;
  const def = base.def;

  if (def.kind === 'spell') {
    if (!Number.isInteger(args.targetSlot) || args.targetSlot < 0 || args.targetSlot > 5) {
      return { ok: false, reason: 'slot_range' };
    }
    return validateSpellTarget({
      def,
      me: args.me,
      opponent: args.opponent,
      targetSlot: args.targetSlot,
      side: args.side,
    });
  }

  if (args.side !== 'player') return { ok: false, reason: 'need_own_slot' };
  if (args.targetSlot < 0 || args.targetSlot > 5) return { ok: false, reason: 'slot_range' };
  if (args.me.board[args.targetSlot] != null) {
    if (firstEmptyBenchSlot(args.me.board) >= 0) {
      return { ok: false, reason: 'slot_full' };
    }
    return { ok: false, reason: 'slot_full' };
  }
  return { ok: true };
}

export function canPlayDrop(args: {
  phase: string;
  me: PlayerState;
  opponent?: PlayerState;
  handIndex: number;
  target: DropTarget;
  cardMap: Map<string, CardDef>;
}): PlayLegality {
  const cardId = args.me.hand[args.handIndex];
  const def = cardId && cardId !== '?' ? args.cardMap.get(cardId) : undefined;
  if (!def) return { ok: false, reason: 'unknown_card' };

  if (args.target.kind === 'bench' && args.target.side === 'player') {
    if (def.kind === 'unit' || def.kind === 'structure') {
      return canPlayToBench(args);
    }
    if (def.kind === 'spell' && spellTargetKind(def) === 'none') {
      const base = basePlayChecks(args);
      return base.ok ? { ok: true } : base;
    }
    return { ok: false, reason: 'need_unit_target' };
  }

  if (args.target.kind === 'unit' && args.target.side != null && args.target.slot != null) {
    return canPlayToSlot({
      phase: args.phase,
      me: args.me,
      opponent: args.opponent,
      handIndex: args.handIndex,
      targetSlot: args.target.slot,
      cardMap: args.cardMap,
      side: args.target.side,
    });
  }

  if (
    args.target.kind === 'battlefield' &&
    def.kind === 'spell' &&
    spellTargetKind(def) === 'none'
  ) {
    const base = basePlayChecks(args);
    return base.ok ? { ok: true } : base;
  }

  return { ok: false, reason: 'invalid_drop' };
}

export function buildPlayAction(args: {
  handIndex: number;
  targetSlot?: number;
  side: DropSide;
  kind?: string;
}) {
  if (args.kind === 'spell' || args.side === 'enemy') {
    const slot = args.targetSlot ?? 0;
    return {
      type: 'play_card' as const,
      handIndex: args.handIndex,
      targetSlot: args.side === 'player' ? slot : 0,
      targetEnemySlot: args.side === 'enemy' ? slot : 0,
    };
  }
  const action: { type: 'play_card'; handIndex: number; targetSlot?: number } = {
    type: 'play_card',
    handIndex: args.handIndex,
  };
  if (args.targetSlot != null && Number.isInteger(args.targetSlot)) {
    action.targetSlot = args.targetSlot;
  }
  return action;
}

export function resolvePlayFromDrop(args: {
  handIndex: number;
  target: DropTarget;
  me: PlayerState;
  kind?: string;
}): unknown | null {
  if (args.target.kind === 'bench' && args.target.side === 'player') {
    return buildPlayAction({ handIndex: args.handIndex, side: 'player', kind: args.kind });
  }
  if (args.target.kind === 'unit' && args.target.side != null && args.target.slot != null) {
    return buildPlayAction({
      handIndex: args.handIndex,
      targetSlot: args.target.slot,
      side: args.target.side,
      kind: args.kind,
    });
  }
  if (args.target.kind === 'battlefield' || args.target.kind === 'bench') {
    const prefer = firstEmptyBenchSlot(args.me.board);
    if (args.kind !== 'spell' && prefer >= 0) {
      return buildPlayAction({
        handIndex: args.handIndex,
        targetSlot: prefer,
        side: 'player',
        kind: args.kind,
      });
    }
    if (args.kind === 'spell') {
      return buildPlayAction({
        handIndex: args.handIndex,
        targetSlot: 0,
        side: args.target.side === 'enemy' ? 'enemy' : 'player',
        kind: 'spell',
      });
    }
  }
  return null;
}
