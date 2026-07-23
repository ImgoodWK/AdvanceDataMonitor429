import { randomUUID } from 'node:crypto';
import { getCard } from '../data/catalog.js';
import type {
  BattleState,
  BoardUnit,
  CardDef,
  PlayerState,
  SpellEffect,
  SpellTargetKind,
} from './types.js';
import { applyNexusDamage } from './voltage.js';

export const BOARD_SIZE = 6;

export function firstEmptySlot(board: (BoardUnit | null)[]): number {
  return board.findIndex((s) => s === null);
}

/** Pack occupied slots left, nulls right (LoR-style bench). */
export function compactBoard(board: (BoardUnit | null)[]): void {
  const units = board.filter((u): u is BoardUnit => u != null);
  for (let i = 0; i < board.length; i++) {
    board[i] = i < units.length ? units[i]! : null;
  }
}

export function findUnitSlot(board: (BoardUnit | null)[], instanceId: string): number {
  return board.findIndex((u) => u?.instanceId === instanceId);
}

export function dealDamageToUnit(unit: BoardUnit, raw: number): void {
  let dmg = raw;
  if (unit.armor > 0) {
    const absorbed = Math.min(unit.armor, dmg);
    unit.armor -= absorbed;
    dmg -= absorbed;
  }
  unit.health -= dmg;
}

function targetAt(board: (BoardUnit | null)[], slot: number, side: string): BoardUnit {
  if (!Number.isInteger(slot) || slot < 0 || slot >= BOARD_SIZE) {
    throw new Error(`Invalid ${side} target slot`);
  }
  const target = board[slot];
  if (!target) throw new Error(`Missing ${side} target`);
  return target;
}

export function resolveTargetKind(def: CardDef): SpellTargetKind {
  if (def.effect?.target) return def.effect.target;
  return 'none';
}

export function validateEffectTarget(
  state: BattleState,
  actor: 0 | 1,
  def: CardDef,
  action: { targetSlot?: number; targetEnemySlot?: number; targetInstanceId?: string },
  helpers: {
    oppIndex: (i: 0 | 1) => 0 | 1;
  },
): void {
  const me = state.players[actor];
  const you = state.players[helpers.oppIndex(actor)];
  const kind = resolveTargetKind(def);

  const resolveFriendly = (): BoardUnit => {
    if (action.targetInstanceId) {
      const slot = findUnitSlot(me.board, action.targetInstanceId);
      if (slot < 0) throw new Error('Missing friendly target');
      action.targetSlot = slot;
      return me.board[slot]!;
    }
    return targetAt(me.board, action.targetSlot ?? 0, 'friendly');
  };
  const resolveEnemy = (): BoardUnit => {
    if (action.targetInstanceId) {
      const slot = findUnitSlot(you.board, action.targetInstanceId);
      if (slot < 0) throw new Error('Missing enemy target');
      action.targetEnemySlot = slot;
      return you.board[slot]!;
    }
    return targetAt(you.board, action.targetEnemySlot ?? 0, 'enemy');
  };

  switch (kind) {
    case 'none':
      return;
    case 'enemy_unit': {
      const t = resolveEnemy();
      if (t.untargetable) throw new Error('Enemy target is untargetable');
      return;
    }
    case 'friendly_unit':
    case 'friendly_unit_clone': {
      const t = resolveFriendly();
      if (t.isStructure) throw new Error('Friendly target must be a unit');
      if (kind === 'friendly_unit_clone' && firstEmptySlot(me.board) < 0) {
        throw new Error('No empty slot for clone');
      }
      return;
    }
    case 'enemy_machine': {
      const t = resolveEnemy();
      if (!t.isStructure || !t.keywords.includes('machine')) {
        throw new Error('Target must be an enemy machine structure');
      }
      return;
    }
    case 'enemy_stealth': {
      const t = resolveEnemy();
      if (!t.keywords.includes('stealth')) throw new Error('Target must have stealth');
      return;
    }
    case 'friendly_cooldown': {
      const t = resolveFriendly();
      if (!t.isStructure || t.hiveTurnsLeft == null) {
        throw new Error('Target must be a structure with cooldown');
      }
      return;
    }
    default:
      return;
  }
}

export function applySpellEffect(
  state: BattleState,
  actor: 0 | 1,
  def: CardDef,
  action: { targetSlot?: number; targetEnemySlot?: number },
  rng: () => number,
  helpers: {
    oppIndex: (i: 0 | 1) => 0 | 1;
    draw: (p: PlayerState, n: number, s: BattleState) => void;
    addToHand: (p: PlayerState, id: string, s: BattleState) => void;
    addMana: (p: PlayerState, n: number) => void;
    pickAeCard: (s: BattleState, rng: () => number) => string | null;
    makeUnit: (def: CardDef, id?: string) => BoardUnit;
  },
): boolean {
  const effect = def.effect;
  if (!effect) return false;

  const me = state.players[actor];
  const you = state.players[helpers.oppIndex(actor)];
  const friendSlot = action.targetSlot ?? 0;
  const enemySlot = action.targetEnemySlot ?? 0;
  const friend = me.board[friendSlot] ?? null;
  const enemy = you.board[enemySlot] ?? null;
  const amount = effect.amount ?? 0;
  const amount2 = effect.amount2 ?? 0;

  switch (effect.id) {
    case 'damage_unit':
      if (enemy && !enemy.untargetable) dealDamageToUnit(enemy, amount);
      break;
    case 'heal_unit':
      if (friend) friend.health = Math.min(friend.maxHealth, friend.health + amount);
      break;
    case 'buff_unit':
      if (friend && !friend.isStructure) {
        friend.attack += amount;
        friend.health += amount2;
        friend.maxHealth += amount2;
        for (const kw of effect.keywordsAdd ?? []) {
          if (!friend.keywords.includes(kw)) friend.keywords.push(kw);
        }
      }
      break;
    case 'buff_all':
      for (const u of me.board) {
        if (u && !u.isStructure) {
          u.attack += amount;
          if (amount2) {
            u.health += amount2;
            u.maxHealth += amount2;
          }
        }
      }
      break;
    case 'armor_unit':
      if (friend) friend.armor += amount;
      break;
    case 'draw':
      helpers.draw(me, Math.max(1, amount || 1), state);
      break;
    case 'gain_mana':
      helpers.addMana(me, amount);
      break;
    case 'nexus_damage': {
      const dmg = applyNexusDamage(amount, me.voltage, you.voltage, you.damageReductionPct);
      you.nexusHp -= dmg;
      if (you.reflectToNexus) me.nexusHp -= Math.max(1, Math.floor(dmg / 2));
      break;
    }
    case 'nexus_heal':
      me.nexusHp = Math.min(me.maxNexusHp, me.nexusHp + amount);
      break;
    case 'nexus_max_heal':
      me.maxNexusHp += amount;
      me.nexusHp = Math.min(me.maxNexusHp, me.nexusHp + amount);
      break;
    case 'summon_token':
    case 'summon_tokens': {
      const tokenId = effect.tokenCardId ?? 'ge_larva';
      const count = effect.tokenCount ?? (effect.id === 'summon_token' ? 1 : 2);
      const tokenDef = getCard(tokenId);
      if (tokenDef) {
        for (let n = 0; n < count; n++) {
          compactBoard(me.board);
          const empty = firstEmptySlot(me.board);
          if (empty >= 0) me.board[empty] = helpers.makeUnit(tokenDef);
        }
      }
      break;
    }
    case 'strip_stealth':
      if (enemy) enemy.keywords = enemy.keywords.filter((k) => k !== 'stealth');
      break;
    case 'destroy_machine':
      if (enemy?.keywords.includes('machine')) {
        you.board[enemySlot] = null;
        you.discard.push(enemy.cardId);
        compactBoard(you.board);
        state.log.push('Machine dismantled');
      }
      break;
    case 'hive_cooldown': {
      const reduce = Math.max(1, amount || 1);
      if (effect.target === 'friendly_cooldown' && friend?.hiveTurnsLeft != null) {
        friend.hiveTurnsLeft = Math.max(0, friend.hiveTurnsLeft - reduce);
      } else {
        for (const u of me.board) {
          if (u?.keywords.includes('beehive') && u.hiveTurnsLeft != null) {
            u.hiveTurnsLeft = Math.max(0, u.hiveTurnsLeft - reduce);
          }
        }
      }
      break;
    }
    case 'add_aspects':
      if (friend) {
        for (const a of effect.aspects ?? []) {
          if (!friend.aspects.includes(a)) friend.aspects.push(a);
        }
      }
      break;
    case 'damage_and_aspect':
      if (enemy && !enemy.untargetable) {
        dealDamageToUnit(enemy, amount);
        for (const a of effect.aspects ?? []) {
          if (!enemy.aspects.includes(a)) enemy.aspects.push(a);
        }
      }
      break;
    case 'singularity':
      me.singularitiesPlayed += 1;
      if (me.singularitiesPlayed >= 3) me.eternalReady = true;
      state.log.push(`Singularities ${me.singularitiesPlayed}/3`);
      break;
    case 'eternal':
      if (me.eternalReady || me.singularitiesPlayed >= 3) {
        me.eternalActive = true;
        state.log.push('Eternal Singularity ACTIVE — units instantly kill blockers');
      } else {
        state.log.push('Eternal Singularity fizzled — need 3 singularities');
      }
      break;
    case 'ae_generate': {
      const id = helpers.pickAeCard(state, rng);
      if (id) helpers.addToHand(me, id, state);
      break;
    }
    case 'steal_attack_token':
      state.attackTokenPlayer = actor;
      state.attackTokenAvailable = true;
      state.log.push('DLB steals the attack token!');
      if (amount > 0) helpers.draw(me, amount, state);
      break;
    case 'enemy_lose_mana':
      you.mana = Math.max(0, you.mana - Math.max(1, amount || 1));
      break;
    case 'random_enemy_damage': {
      const slots = you.board.map((u, i) => (u && !u.untargetable ? i : -1)).filter((i) => i >= 0);
      if (slots.length) {
        const slot = slots[Math.floor(rng() * slots.length)]!;
        dealDamageToUnit(you.board[slot]!, Math.max(1, amount || 1));
      }
      break;
    }
    case 'damage_reduction':
      me.damageReductionPct = Math.min(50, me.damageReductionPct + (amount || 10));
      break;
    case 'reflect':
      me.reflectToNexus = true;
      break;
    case 'clone_unit': {
      const src = friend;
      compactBoard(me.board);
      const empty = firstEmptySlot(me.board);
      if (src && empty >= 0) {
        me.board[empty] = {
          ...src,
          instanceId: randomUUID(),
          attack: 1,
          health: 1,
          maxHealth: 1,
          keywords: [...src.keywords],
          aspects: [...src.aspects],
          equipment: [],
        };
      }
      break;
    }
    case 'reduce_dlb_interval':
      if (state.dlbForceEvery > 3) state.dlbForceEvery -= Math.max(1, amount || 1);
      break;
    default:
      return false;
  }
  return true;
}

/** Legacy special: next hive bee mutates (fo_plugin_strong). */
export function applyNextBeeMutate(me: PlayerState): void {
  (me as PlayerState & { _nextBeeMutate?: boolean })._nextBeeMutate = true;
}
