import { randomUUID } from 'node:crypto';
import { aeOffDeckPool, getCard } from '../data/catalog.js';
import type {
  AttackPair,
  BattlePhase,
  BattleState,
  BoardUnit,
  CardDef,
  PlayerState,
  ThemeId,
  VoltageTier,
} from './types.js';
import { applyNexusDamage } from './voltage.js';

const BOARD_SIZE = 6;
const START_NEXUS = 20;
const MAX_BANK = 6;

function mulberry32(seed: number): () => number {
  let t = seed >>> 0;
  return () => {
    t += 0x6d2b79f5;
    let r = Math.imul(t ^ (t >>> 15), 1 | t);
    r ^= r + Math.imul(r ^ (r >>> 7), 61 | r);
    return ((r ^ (r >>> 14)) >>> 0) / 4294967296;
  };
}

function shuffle<T>(arr: T[], rng: () => number): T[] {
  const a = arr.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

function emptyBoard(): (BoardUnit | null)[] {
  return Array.from({ length: BOARD_SIZE }, () => null);
}

function makeUnit(def: CardDef, rngId?: string): BoardUnit {
  const isStructure = def.kind === 'structure';
  return {
    instanceId: rngId ?? randomUUID(),
    cardId: def.id,
    attack: def.attack ?? 0,
    health: def.health ?? 1,
    maxHealth: def.health ?? 1,
    armor: def.armor ?? 0,
    keywords: [...(def.keywords ?? [])],
    aspects: [...(def.aspects ?? [])],
    isStructure,
    untargetable: (def.keywords ?? []).includes('untargetable') || (def.keywords ?? []).includes('beehive'),
    hiveTurnsLeft: def.hiveCooldown,
    equipment: [],
  };
}

function createPlayer(
  id: string,
  name: string,
  isAi: boolean,
  deckIds: string[],
  themes: ThemeId[],
  voltage: VoltageTier,
  rng: () => number,
): PlayerState {
  const shuffled = shuffle(deckIds, rng);
  const hand = shuffled.slice(0, 4);
  const deck = shuffled.slice(4);
  return {
    id,
    name,
    isAi,
    nexusHp: START_NEXUS,
    maxNexusHp: START_NEXUS,
    mana: 1,
    maxMana: 1,
    bankedMana: 0,
    voltage,
    hand,
    deck,
    discard: [],
    board: emptyBoard(),
    themes,
    damageReductionPct: 0,
    reflectToNexus: false,
    singularitiesPlayed: 0,
    eternalReady: false,
    eternalActive: false,
    unitBias: themes.includes('genetics'),
  };
}

export interface MatchOptions {
  seed?: number;
  playerName: string;
  playerId: string;
  playerDeck: string[];
  playerThemes: ThemeId[];
  playerVoltage: VoltageTier;
  aiDeck: string[];
  aiThemes: ThemeId[];
  aiVoltage: VoltageTier;
  aiName?: string;
  dlbForceEvery?: number;
  /** Equipment applied to first unit played / starter slot mods */
  starterEquipment?: { attack?: number; health?: number; armor?: number };
}

export function createMatch(opts: MatchOptions): BattleState {
  const seed = opts.seed ?? (Date.now() % 1_000_000);
  const rng = mulberry32(seed);
  const p0 = createPlayer(opts.playerId, opts.playerName, false, opts.playerDeck, opts.playerThemes, opts.playerVoltage, rng);
  const p1 = createPlayer('ai', opts.aiName ?? 'PvE Opponent', true, opts.aiDeck, opts.aiThemes, opts.aiVoltage, rng);
  const dlb = opts.playerThemes.includes('dlb') || opts.aiThemes.includes('dlb');
  return {
    matchId: randomUUID(),
    seed,
    turn: 1,
    phase: 'main',
    activePlayer: 0,
    players: [p0, p1],
    attackOrder: [],
    blockPairs: [],
    swapUsedThisCombat: false,
    dlbForceEvery: opts.dlbForceEvery ?? (dlb ? 5 : 0),
    lastForcedSwapTurn: 0,
    winner: null,
    log: [`Match start seed=${seed}`],
    aePool: aeOffDeckPool(opts.playerDeck),
  };
}

function oppIndex(i: 0 | 1): 0 | 1 {
  return i === 0 ? 1 : 0;
}

function draw(p: PlayerState, n: number, state: BattleState): void {
  for (let i = 0; i < n; i++) {
    if (p.deck.length === 0) {
      state.log.push(`${p.name} deck empty`);
      return;
    }
    p.hand.push(p.deck.shift()!);
  }
}

function firstEmptySlot(board: (BoardUnit | null)[]): number {
  return board.findIndex((s) => s === null);
}

function dealDamageToUnit(unit: BoardUnit, raw: number): void {
  let dmg = raw;
  if (unit.armor > 0) {
    const absorbed = Math.min(unit.armor, dmg);
    unit.armor -= absorbed;
    dmg -= absorbed;
  }
  unit.health -= dmg;
}

function removeDead(p: PlayerState, state: BattleState): void {
  for (let i = 0; i < p.board.length; i++) {
    const u = p.board[i];
    if (u && u.health <= 0) {
      state.log.push(`${p.name} loses ${u.cardId} at slot ${i}`);
      p.discard.push(u.cardId);
      p.board[i] = null;
    }
  }
}

function spendMana(p: PlayerState, cost: number): boolean {
  const total = p.mana + p.bankedMana;
  if (total < cost) return false;
  let need = cost;
  const fromMana = Math.min(p.mana, need);
  p.mana -= fromMana;
  need -= fromMana;
  if (need > 0) p.bankedMana -= need;
  return true;
}

function addMana(p: PlayerState, amount: number): void {
  p.mana += amount;
}

function pickAeCard(state: BattleState, rng: () => number): string | null {
  if (state.aePool.length === 0) return null;
  const idx = Math.floor(rng() * state.aePool.length);
  return state.aePool[idx]!;
}

function hasOrdoAer(unit: BoardUnit): boolean {
  return unit.aspects.includes('ordo') && unit.aspects.includes('aer');
}

export type PlayerAction =
  | { type: 'play_card'; handIndex: number; targetSlot?: number; targetEnemySlot?: number }
  | { type: 'declare_attacks'; slots: number[] }
  | { type: 'declare_blocks'; pairs: AttackPair[] }
  | { type: 'swap_slots'; a: number; b: number }
  | { type: 'end_main' }
  | { type: 'pass_block' }
  | { type: 'concede' };

function ensurePhase(state: BattleState, ...ok: BattlePhase[]): void {
  if (!ok.includes(state.phase)) {
    throw new Error(`Invalid phase ${state.phase}, expected ${ok.join('|')}`);
  }
}

function applySpell(
  state: BattleState,
  actor: 0 | 1,
  def: CardDef,
  action: Extract<PlayerAction, { type: 'play_card' }>,
  rng: () => number,
): void {
  const me = state.players[actor];
  const you = state.players[oppIndex(actor)];

  switch (def.id) {
    case 'van_smite':
    case 'ae_annihilation': {
      const slot = action.targetEnemySlot ?? 0;
      const u = you.board[slot];
      if (u && !u.untargetable) {
        dealDamageToUnit(u, def.id === 'van_smite' ? 3 : 2);
      }
      break;
    }
    case 'van_heal': {
      const slot = action.targetSlot ?? 0;
      const u = me.board[slot];
      if (u) u.health = Math.min(u.maxHealth, u.health + 3);
      break;
    }
    case 'van_rally':
      for (const u of me.board) if (u && !u.isStructure) u.attack += 1;
      break;
    case 'gt_overclock':
      addMana(me, 2);
      break;
    case 'gt_wrench': {
      const slot = action.targetEnemySlot ?? 0;
      const u = you.board[slot];
      if (u?.keywords.includes('machine')) {
        you.board[slot] = null;
        you.discard.push(u.cardId);
        state.log.push('Machine dismantled');
      }
      break;
    }
    case 'th_ordo_aer': {
      const slot = action.targetSlot ?? 0;
      const u = me.board[slot];
      if (u) {
        if (!u.aspects.includes('ordo')) u.aspects.push('ordo');
        if (!u.aspects.includes('aer')) u.aspects.push('aer');
      }
      break;
    }
    case 'th_ignis': {
      const slot = action.targetEnemySlot ?? 0;
      const u = you.board[slot];
      if (u && !u.untargetable) {
        dealDamageToUnit(u, 2);
        if (!u.aspects.includes('ignis')) u.aspects.push('ignis');
      }
      break;
    }
    case 'th_ward': {
      const slot = action.targetSlot ?? 0;
      const u = me.board[slot];
      if (u) u.armor += 2;
      break;
    }
    case 'fo_plugin_speed': {
      for (const u of me.board) {
        if (u?.keywords.includes('beehive') && u.hiveTurnsLeft != null) {
          u.hiveTurnsLeft = Math.max(0, u.hiveTurnsLeft - 1);
        }
      }
      break;
    }
    case 'fo_plugin_strong':
      state.log.push('Next hive bee will mutate (applied on spawn)');
      (me as PlayerState & { _nextBeeMutate?: boolean })._nextBeeMutate = true;
      break;
    case 'fo_smoke': {
      const slot = action.targetEnemySlot ?? 0;
      const u = you.board[slot];
      if (u) u.keywords = u.keywords.filter((k) => k !== 'stealth');
      break;
    }
    case 'as_shield':
      me.damageReductionPct = Math.min(50, me.damageReductionPct + 10);
      break;
    case 'as_reflect':
      me.reflectToNexus = true;
      break;
    case 'as_attune':
    case 'ae_p2p':
    case 'ee_trans':
      draw(me, 1, state);
      break;
    case 'as_ritual':
      for (const u of me.board) {
        if (u && !u.isStructure) {
          u.attack += 1;
          u.health += 1;
          u.maxHealth += 1;
        }
      }
      break;
    case 'as_nova': {
      const dmg = applyNexusDamage(2, me.voltage, you.voltage, you.damageReductionPct);
      you.nexusHp -= dmg;
      if (you.reflectToNexus) me.nexusHp -= Math.max(1, Math.floor(dmg / 2));
      break;
    }
    case 'as_lens':
      addMana(me, 1);
      break;
    case 'av_singularity':
    case 'av_singularity_2':
    case 'av_singularity_3':
      me.singularitiesPlayed += 1;
      if (me.singularitiesPlayed >= 3) me.eternalReady = true;
      state.log.push(`Singularities ${me.singularitiesPlayed}/3`);
      break;
    case 'av_eternal':
      if (me.eternalReady || me.singularitiesPlayed >= 3) {
        me.eternalActive = true;
        state.log.push('Eternal Singularity ACTIVE — units instantly kill blockers');
      } else {
        state.log.push('Eternal Singularity fizzled — need 3 singularities');
      }
      break;
    case 'av_armor':
      me.maxNexusHp += 5;
      me.nexusHp = Math.min(me.maxNexusHp, me.nexusHp + 5);
      break;
    case 'av_catalyst':
      draw(me, 1, state);
      break;
    case 'ee_watch':
    case 'ee_relay': {
      const slot = action.targetSlot ?? 0;
      const u = me.board[slot];
      if (u?.hiveTurnsLeft != null) {
        u.hiveTurnsLeft = Math.max(0, u.hiveTurnsLeft - (def.id === 'ee_watch' ? 2 : 1));
      }
      break;
    }
    case 'ee_klein':
      addMana(me, 3);
      break;
    case 'ee_phil':
      draw(me, 1, state);
      break;
    case 'ee_catalyst':
      me.nexusHp = Math.min(me.maxNexusHp, me.nexusHp + 2);
      break;
    case 'ge_mutate': {
      const slot = action.targetSlot ?? 0;
      const u = me.board[slot];
      if (u) {
        u.attack += 1;
        u.health += 1;
        u.maxHealth += 1;
      }
      break;
    }
    case 'ge_clone': {
      const slot = action.targetSlot ?? 0;
      const src = me.board[slot];
      const empty = firstEmptySlot(me.board);
      if (src && empty >= 0) {
        const clone = { ...src, instanceId: randomUUID(), attack: 1, health: 1, maxHealth: 1, equipment: [] };
        me.board[empty] = clone;
      }
      break;
    }
    case 'ge_swarm': {
      const bee = getCard('ge_larva')!;
      for (let n = 0; n < 2; n++) {
        const empty = firstEmptySlot(me.board);
        if (empty >= 0) me.board[empty] = makeUnit(bee);
      }
      break;
    }
    case 'ge_split':
      draw(me, 2, state);
      break;
    case 'ae_craft':
    case 'ae_wireless': {
      const id = pickAeCard(state, rng);
      if (id) me.hand.push(id);
      break;
    }
    case 'ae_cell':
      draw(me, 2, state);
      break;
    case 'ae_formation': {
      const ball = getCard('ae_matter')!;
      const empty = firstEmptySlot(me.board);
      if (empty >= 0) me.board[empty] = makeUnit(ball);
      break;
    }
    case 'dlb_tantrum':
    case 'dlb_scream':
      state.activePlayer = oppIndex(state.activePlayer);
      state.phase = 'main';
      state.log.push('DLB force role swap!');
      if (def.id === 'dlb_scream') draw(me, 1, state);
      break;
    case 'dlb_ignore': {
      const dmg = applyNexusDamage(3, me.voltage, you.voltage, you.damageReductionPct);
      you.nexusHp -= dmg;
      break;
    }
    case 'dlb_mood': {
      const slots = you.board.map((u, i) => (u ? i : -1)).filter((i) => i >= 0);
      if (slots.length) {
        const slot = slots[Math.floor(rng() * slots.length)]!;
        const u = you.board[slot]!;
        if (!u.untargetable) dealDamageToUnit(u, 1);
      }
      break;
    }
    case 'dlb_nap':
      you.mana = Math.max(0, you.mana - 1);
      break;
    default:
      state.log.push(`Spell ${def.id} has no special handler`);
  }
}

function playCard(state: BattleState, actor: 0 | 1, action: Extract<PlayerAction, { type: 'play_card' }>, rng: () => number): void {
  ensurePhase(state, 'main');
  if (state.activePlayer !== actor) throw new Error('Not your turn');
  const me = state.players[actor];
  const cardId = me.hand[action.handIndex];
  if (!cardId) throw new Error('Invalid hand index');
  const def = getCard(cardId);
  if (!def) throw new Error(`Unknown card ${cardId}`);
  if (!spendMana(me, def.cost)) throw new Error('Not enough mana');

  me.hand.splice(action.handIndex, 1);
  me.discard.push(cardId);
  state.log.push(`${me.name} plays ${def.nameZh}`);

  if (def.kind === 'unit' || def.kind === 'structure') {
    const slot = action.targetSlot ?? firstEmptySlot(me.board);
    if (slot < 0 || slot >= BOARD_SIZE || me.board[slot]) throw new Error('No empty slot');
    const unit = makeUnit(def);
    me.board[slot] = unit;
    // Forestry hive occupies a slot — opponent effectively has more combat slots conceptually handled by untargetable
  } else if (def.kind === 'spell') {
    applySpell(state, actor, def, action, rng);
  }

  removeDead(state.players[0], state);
  removeDead(state.players[1], state);
  checkWinner(state);
}

function startAttackDeclare(state: BattleState): void {
  state.phase = 'attack_declare';
  state.attackOrder = [];
  state.blockPairs = [];
  state.swapUsedThisCombat = false;
}

function declareAttacks(state: BattleState, actor: 0 | 1, slots: number[]): void {
  ensurePhase(state, 'attack_declare');
  if (state.activePlayer !== actor) throw new Error('Not attacker');
  const me = state.players[actor];
  const unique = [...new Set(slots)];
  for (const s of unique) {
    const u = me.board[s];
    if (!u || u.isStructure || u.attack <= 0) throw new Error(`Slot ${s} cannot attack`);
  }
  state.attackOrder = unique;
  state.phase = 'block_declare';
  // If no attackers, skip to turn end path via resolve empty
  if (unique.length === 0) {
    state.blockPairs = [];
    resolveCombat(state);
  }
}

function canBlock(attacker: BoardUnit, blocker: BoardUnit): boolean {
  if (blocker.isStructure) return false;
  if (attacker.keywords.includes('stealth') && !blocker.keywords.includes('stealth')) return false;
  return true;
}

function declareBlocks(state: BattleState, blocker: 0 | 1, pairs: AttackPair[]): void {
  ensurePhase(state, 'block_declare');
  if (blocker !== oppIndex(state.activePlayer)) throw new Error('Not defender');
  const atk = state.players[state.activePlayer];
  const def = state.players[blocker];
  const usedBlockers = new Set<number>();
  const mapped = new Map<number, number>();
  for (const p of pairs) {
    if (!state.attackOrder.includes(p.attackerSlot)) throw new Error('Invalid attacker');
    if (p.blockerSlot >= 0) {
      if (usedBlockers.has(p.blockerSlot)) throw new Error('Blocker reused');
      const bu = def.board[p.blockerSlot];
      const au = atk.board[p.attackerSlot];
      if (!bu || !au) throw new Error('Missing unit');
      if (bu.untargetable) throw new Error('Cannot block with untargetable');
      if (!canBlock(au, bu)) throw new Error('Stealth cannot be blocked');
      usedBlockers.add(p.blockerSlot);
      mapped.set(p.attackerSlot, p.blockerSlot);
    }
  }
  state.blockPairs = state.attackOrder.map((a) => ({
    attackerSlot: a,
    blockerSlot: mapped.get(a) ?? -1,
  }));

  // Thaum swap window if any ordo+aer unit on defender
  const canSwap = def.board.some((u) => u && hasOrdoAer(u));
  if (canSwap && !state.swapUsedThisCombat) {
    state.phase = 'swap_extra';
  } else {
    resolveCombat(state);
  }
}

function swapSlots(state: BattleState, actor: 0 | 1, a: number, b: number): void {
  ensurePhase(state, 'swap_extra');
  if (actor !== oppIndex(state.activePlayer)) throw new Error('Only defender swaps');
  const p = state.players[actor];
  if (!p.board.some((u) => u && hasOrdoAer(u))) throw new Error('Need Ordo+Aer');
  const tmp = p.board[a];
  p.board[a] = p.board[b];
  p.board[b] = tmp;
  state.swapUsedThisCombat = true;
  state.log.push(`${p.name} swapped slots ${a}<->${b}`);
  // Remap block pairs if blocker slots moved
  resolveCombat(state);
}

function resolveCombat(state: BattleState): void {
  state.phase = 'resolve';
  const atkIdx = state.activePlayer;
  const defIdx = oppIndex(atkIdx);
  const atk = state.players[atkIdx];
  const def = state.players[defIdx];

  for (const pair of state.blockPairs.length ? state.blockPairs : state.attackOrder.map((s) => ({ attackerSlot: s, blockerSlot: -1 }))) {
    const attacker = atk.board[pair.attackerSlot];
    if (!attacker) continue;

    if (pair.blockerSlot < 0) {
      let dmg = applyNexusDamage(attacker.attack, atk.voltage, def.voltage, def.damageReductionPct);
      if (atk.eternalActive) dmg = Math.max(dmg, 999);
      def.nexusHp -= dmg;
      state.log.push(`${attacker.cardId} hits nexus for ${dmg}`);
      if (attacker.keywords.includes('lifesteal')) {
        atk.nexusHp = Math.min(atk.maxNexusHp, atk.nexusHp + dmg);
      }
      if (def.reflectToNexus) {
        atk.nexusHp -= Math.max(1, Math.floor(dmg * 0.5));
      }
      continue;
    }

    const blocker = def.board[pair.blockerSlot];
    if (!blocker) {
      const dmg = applyNexusDamage(attacker.attack, atk.voltage, def.voltage, def.damageReductionPct);
      def.nexusHp -= dmg;
      continue;
    }

    if (atk.eternalActive) {
      state.log.push(`Eternal kill ${blocker.cardId}`);
      blocker.health = 0;
      // attacker survives and does not take damage
    } else {
      dealDamageToUnit(blocker, attacker.attack);
      dealDamageToUnit(attacker, blocker.attack);
      if (attacker.keywords.includes('lifesteal')) {
        atk.nexusHp = Math.min(atk.maxNexusHp, atk.nexusHp + attacker.attack);
      }
      if (attacker.keywords.includes('aoe')) {
        for (const adj of [pair.blockerSlot - 1, pair.blockerSlot + 1]) {
          const adjU = def.board[adj];
          if (adjU && !adjU.untargetable) dealDamageToUnit(adjU, Math.max(1, Math.floor(attacker.attack / 2)));
        }
      }
    }
  }

  removeDead(atk, state);
  removeDead(def, state);
  checkWinner(state);
  if (state.winner != null) return;
  endTurn(state);
}

function processStructures(state: BattleState, p: PlayerState, rng: () => number): void {
  for (let i = 0; i < p.board.length; i++) {
    const u = p.board[i];
    if (!u) continue;
    const def = getCard(u.cardId);
    if (def?.manaPerTurn) addMana(p, def.manaPerTurn);

    // Astral crystal heal
    if (u.cardId === 'as_crystal') {
      p.nexusHp = Math.min(p.maxNexusHp, p.nexusHp + 1);
    }

    // Thaum node
    if (u.cardId === 'th_node') {
      const units = p.board.filter((x) => x && !x.isStructure) as BoardUnit[];
      if (units.length) {
        const t = units[Math.floor(rng() * units.length)]!;
        if (!t.aspects.includes('ordo')) t.aspects.push('ordo');
      }
    }

    // AE inscriber / controller
    if (u.cardId === 'ae_inscriber' || u.cardId === 'ae_controller') {
      const id = pickAeCard(state, rng);
      if (id && p.hand.length < 10) p.hand.push(id);
    }

    // Forestry hive
    if (u.keywords.includes('beehive') && u.hiveTurnsLeft != null) {
      u.hiveTurnsLeft -= 1;
      if (u.hiveTurnsLeft <= 0) {
        u.hiveTurnsLeft = def?.hiveCooldown ?? 3;
        const existingBee = p.board.findIndex((x) => x && x.keywords.includes('bee') && x.attack === 1 && x.health === 1);
        if (existingBee >= 0) {
          const bee = p.board[existingBee]!;
          bee.attack += 1;
          bee.health += 1;
          bee.maxHealth += 1;
          state.log.push('Bees merged into stronger bee');
        } else {
          const empty = firstEmptySlot(p.board);
          if (empty >= 0) {
            const beeDef = getCard('fo_bee')!;
            const bee = makeUnit(beeDef);
            const flagged = p as PlayerState & { _nextBeeMutate?: boolean };
            if (flagged._nextBeeMutate) {
              bee.keywords.push(rng() < 0.5 ? 'lifesteal' : 'aoe');
              flagged._nextBeeMutate = false;
            }
            p.board[empty] = bee;
            state.log.push('Hive produced a bee');
          }
        }
      }
    }
  }

  // GT capacitor banking at end of own turn handled in endTurn
}

function bankMana(p: PlayerState, state: BattleState): void {
  const hasCap = p.board.some((u) => u?.keywords.includes('capacitor'));
  if (!hasCap) {
    p.mana = 0;
    return;
  }
  p.bankedMana += p.mana;
  p.mana = 0;
  const limit = p.board.some((u) => u?.cardId === 'gt_battery') ? 4 : MAX_BANK;
  if (p.bankedMana > limit) {
    const over = p.bankedMana - limit;
    p.bankedMana = Math.floor(limit / 2);
    p.nexusHp -= over * 2;
    // dismantle a machine
    const mi = p.board.findIndex((u) => u?.keywords.includes('machine'));
    if (mi >= 0) {
      state.log.push(`${p.name} capacitor overload! Machine destroyed, self damage`);
      p.discard.push(p.board[mi]!.cardId);
      p.board[mi] = null;
    } else {
      state.log.push(`${p.name} capacitor overload! Self damage`);
    }
  }
}

function endTurn(state: BattleState): void {
  state.phase = 'turn_end';
  const actor = state.activePlayer;
  const me = state.players[actor];
  const rng = mulberry32(state.seed + state.turn * 17 + actor);

  processStructures(state, me, rng);
  bankMana(me, state);

  // DLB periodic force swap
  if (state.dlbForceEvery > 0 && state.turn - state.lastForcedSwapTurn >= state.dlbForceEvery) {
    state.lastForcedSwapTurn = state.turn;
    state.log.push('DLB schedule: forced attack role swap');
    // Keep next active as current attacker again effectively by NOT swapping below — instead flip twice? 
    // Spec: "强制攻防转换" — next turn the other player becomes attacker (normal) AND we skip so current stays?
    // Simpler: set activePlayer to current again after normal swap (double flip = same) to steal initiative
    // Interpretation: after end turn, instead of passing to opponent, pass to same player again (extra attack turn for them)
    // Actually user said: your attack turn becomes their attack. So when it would be your turn, it becomes theirs.
    // Implement: next active is opponent (normal), but set a flag... Simplest V1: after endTurn, active stays as opponent of normal — i.e. skip the swap so same player attacks again once.
    state.activePlayer = actor; // same player keeps initiative once
    state.turn += 1;
  } else {
    state.activePlayer = oppIndex(actor);
    if (state.activePlayer === 0) state.turn += 1;
  }

  const next = state.players[state.activePlayer];
  next.maxMana = Math.min(10, next.maxMana + 1);
  next.mana = next.maxMana;
  draw(next, 1, state);

  // Chaos totem reduces interval
  const totem = next.board.some((u) => u?.cardId === 'dlb_chaos') || me.board.some((u) => u?.cardId === 'dlb_chaos');
  if (totem && state.dlbForceEvery > 3) state.dlbForceEvery -= 1;

  state.attackOrder = [];
  state.blockPairs = [];
  state.phase = 'main';
  checkWinner(state);
}

function checkWinner(state: BattleState): void {
  const [a, b] = state.players;
  if (a.nexusHp <= 0 && b.nexusHp <= 0) {
    state.winner = 0;
    state.phase = 'game_over';
    return;
  }
  if (a.nexusHp <= 0) {
    state.winner = 1;
    state.phase = 'game_over';
    state.log.push('Player 1 wins');
  } else if (b.nexusHp <= 0) {
    state.winner = 0;
    state.phase = 'game_over';
    state.log.push('Player 0 wins');
  }
}

/** Simple AI: play cheapest card, then attack with all. */
export function runAiIfNeeded(state: BattleState): void {
  const guard = 20;
  for (let n = 0; n < guard; n++) {
    if (state.phase === 'game_over') return;
    const actor = state.activePlayer;
    const p = state.players[actor];
    if (!p.isAi && state.phase === 'main') return;
    if (!p.isAi && state.phase === 'attack_declare') return;
    if (p.isAi === false && state.phase === 'block_declare') return;
    if (p.isAi === false && state.phase === 'swap_extra') {
      // auto skip swap for human — wait
      return;
    }

    const rng = mulberry32(state.seed + state.turn * 31 + n);

    if (state.phase === 'main' && p.isAi) {
      // play affordable cards
      let played = false;
      for (let i = 0; i < p.hand.length; i++) {
        const def = getCard(p.hand[i]!);
        if (!def) continue;
        if (p.mana + p.bankedMana < def.cost) continue;
        try {
          if (def.kind === 'unit' || def.kind === 'structure') {
            const slot = firstEmptySlot(p.board);
            if (slot < 0) continue;
            applyAction(state, actor, { type: 'play_card', handIndex: i, targetSlot: slot });
          } else {
            applyAction(state, actor, { type: 'play_card', handIndex: i, targetSlot: 0, targetEnemySlot: 0 });
          }
          played = true;
          break;
        } catch {
          // try next
        }
      }
      if (!played) {
        applyAction(state, actor, { type: 'end_main' });
      }
      continue;
    }

    if (state.phase === 'attack_declare' && p.isAi) {
      const slots: number[] = [];
      p.board.forEach((u, i) => {
        if (u && !u.isStructure && u.attack > 0) slots.push(i);
      });
      applyAction(state, actor, { type: 'declare_attacks', slots });
      continue;
    }

    if (state.phase === 'block_declare') {
      const defender = oppIndex(state.activePlayer);
      const defP = state.players[defender];
      if (!defP.isAi) return;
      const pairs: AttackPair[] = [];
      const used = new Set<number>();
      for (const aSlot of state.attackOrder) {
        const attacker = state.players[state.activePlayer].board[aSlot];
        let block = -1;
        for (let i = 0; i < defP.board.length; i++) {
          const bu = defP.board[i];
          if (!bu || used.has(i) || bu.isStructure || bu.untargetable) continue;
          if (attacker && canBlock(attacker, bu)) {
            block = i;
            used.add(i);
            break;
          }
        }
        pairs.push({ attackerSlot: aSlot, blockerSlot: block });
      }
      applyAction(state, defender, { type: 'declare_blocks', pairs });
      continue;
    }

    if (state.phase === 'swap_extra') {
      const defender = oppIndex(state.activePlayer);
      if (state.players[defender].isAi) {
        resolveCombat(state);
      } else {
        return;
      }
    }
  }
}

export function applyAction(state: BattleState, actor: 0 | 1, action: PlayerAction): BattleState {
  if (state.phase === 'game_over') throw new Error('Game over');
  const rng = mulberry32(state.seed + state.turn * 13 + state.log.length);

  switch (action.type) {
    case 'play_card':
      playCard(state, actor, action, rng);
      break;
    case 'end_main':
      ensurePhase(state, 'main');
      if (state.activePlayer !== actor) throw new Error('Not your turn');
      startAttackDeclare(state);
      break;
    case 'declare_attacks':
      declareAttacks(state, actor, action.slots);
      break;
    case 'declare_blocks':
      declareBlocks(state, actor, action.pairs);
      break;
    case 'pass_block':
      declareBlocks(
        state,
        actor,
        state.attackOrder.map((s) => ({ attackerSlot: s, blockerSlot: -1 })),
      );
      break;
    case 'swap_slots':
      swapSlots(state, actor, action.a, action.b);
      break;
    case 'concede':
      state.winner = oppIndex(actor);
      state.phase = 'game_over';
      state.log.push(`${state.players[actor].name} conceded`);
      break;
    default:
      throw new Error('Unknown action');
  }

  runAiIfNeeded(state);
  return state;
}

export function publicState(state: BattleState, viewer: 0 | 1): BattleState {
  // Full state for V1 (PvE); hide opponent hand optionally
  const clone = structuredClone(state) as BattleState;
  const opp = oppIndex(viewer);
  clone.players[opp].deck = clone.players[opp].deck.map(() => '?');
  // keep hand hidden for AI
  if (clone.players[opp].isAi) {
    clone.players[opp].hand = clone.players[opp].hand.map(() => '?');
  }
  return clone;
}
