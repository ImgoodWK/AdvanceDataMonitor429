import { randomUUID } from 'node:crypto';
import { aeOffDeckPool, getCard } from '../data/catalog.js';
import type {
  AttackPair,
  BattlePhase,
  BattleState,
  BoardUnit,
  CardDef,
  PlayerState,
  SpellSpeed,
  SpellStackItem,
  ThemeId,
  VoltageTier,
} from './types.js';
import { applyNexusDamage } from './voltage.js';

const BOARD_SIZE = 6;
const START_NEXUS = 20;
const MAX_BANK = 6;
const MAX_HAND = 10;

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
    spellMana: 0,
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
    phase: 'mulligan',
    activePlayer: 0,
    attackTokenPlayer: 0,
    attackTokenAvailable: true,
    combatAttacker: null,
    consecutivePasses: 0,
    responsePasses: 0,
    responseOriginPlayer: null,
    spellStack: [],
    nextStackId: 1,
    mulliganDone: [false, false],
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

function addToHand(p: PlayerState, cardId: string, state: BattleState): void {
  if (p.hand.length >= MAX_HAND) {
    p.discard.push(cardId);
    state.log.push(`${p.name} burns ${cardId} (hand full)`);
    return;
  }
  p.hand.push(cardId);
}

function draw(p: PlayerState, n: number, state: BattleState): void {
  for (let i = 0; i < n; i++) {
    if (p.deck.length === 0) {
      state.log.push(`${p.name} loses: attempted to draw from an empty deck`);
      p.nexusHp = 0;
      checkWinner(state);
      return;
    }
    addToHand(p, p.deck.shift()!, state);
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

function spendMana(p: PlayerState, cost: number, spell: boolean): boolean {
  const total = p.mana + p.bankedMana + (spell ? p.spellMana : 0);
  if (total < cost) return false;
  let need = cost;
  const fromMana = Math.min(p.mana, need);
  p.mana -= fromMana;
  need -= fromMana;
  if (spell) {
    const fromSpell = Math.min(p.spellMana, need);
    p.spellMana -= fromSpell;
    need -= fromSpell;
  }
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
  | { type: 'confirm_mulligan'; replaceIndices: number[] }
  | { type: 'play_card'; handIndex: number; targetSlot?: number; targetEnemySlot?: number }
  | { type: 'pass_priority' }
  | { type: 'start_attack' }
  | { type: 'declare_attacks'; slots: number[] }
  | { type: 'declare_blocks'; pairs: AttackPair[] }
  | { type: 'swap_slots'; a: number; b: number }
  | { type: 'pass_swap' }
  | { type: 'end_main' }
  | { type: 'pass_block' }
  | { type: 'concede' };

function ensurePhase(state: BattleState, ...ok: BattlePhase[]): void {
  if (!ok.includes(state.phase)) {
    throw new Error(`Invalid phase ${state.phase}, expected ${ok.join('|')}`);
  }
}

function spellSpeed(def: CardDef): SpellSpeed {
  return def.spellSpeed ?? 'slow';
}

function isResponsePhase(phase: BattlePhase): phase is 'spell_response' | 'combat_response' {
  return phase === 'spell_response' || phase === 'combat_response';
}

function confirmMulligan(
  state: BattleState,
  actor: 0 | 1,
  replaceIndices: number[] | undefined,
  rng: () => number,
): void {
  ensurePhase(state, 'mulligan');
  if (state.mulliganDone[actor]) throw new Error('Mulligan already confirmed');
  const player = state.players[actor];
  if (replaceIndices != null && !Array.isArray(replaceIndices)) throw new Error('Invalid mulligan index');
  const requested = replaceIndices ?? [];
  const unique = new Set<number>();
  for (const index of requested) {
    if (!Number.isInteger(index) || index < 0 || index >= player.hand.length || unique.has(index)) {
      throw new Error('Invalid mulligan index');
    }
    unique.add(index);
  }
  const indices = [...unique].sort((a, b) => a - b);
  if (indices.length > player.deck.length) throw new Error('Not enough cards to replace mulligan');

  const returned = indices.map((index) => player.hand[index]!);
  for (const index of indices) player.hand[index] = player.deck.shift()!;
  player.deck.push(...returned);
  player.deck = shuffle(player.deck, rng);
  state.mulliganDone[actor] = true;
  state.log.push(`${player.name} confirms mulligan (${indices.length})`);

  if (state.mulliganDone[0] && state.mulliganDone[1]) {
    // The opening hand is four cards; round one starts with the normal draw.
    draw(state.players[0], 1, state);
    draw(state.players[1], 1, state);
    if (state.winner != null) return;
    state.phase = 'main';
    state.activePlayer = state.attackTokenPlayer;
    state.log.push('Mulligan complete');
  }
}

const ENEMY_UNIT_TARGET_SPELLS = new Set(['van_smite', 'ae_annihilation', 'th_ignis']);
const FRIENDLY_UNIT_TARGET_SPELLS = new Set(['van_heal', 'th_ordo_aer', 'th_ward', 'ge_mutate', 'ge_clone']);

function targetAt(board: (BoardUnit | null)[], slot: number, side: 'friendly' | 'enemy'): BoardUnit {
  if (!Number.isInteger(slot) || slot < 0 || slot >= BOARD_SIZE) {
    throw new Error(`Invalid ${side} target slot`);
  }
  const target = board[slot];
  if (!target) throw new Error(`Missing ${side} target`);
  return target;
}

function validateSpellTarget(
  state: BattleState,
  actor: 0 | 1,
  def: CardDef,
  action: Extract<PlayerAction, { type: 'play_card' }>,
): void {
  const me = state.players[actor];
  const you = state.players[oppIndex(actor)];

  if (ENEMY_UNIT_TARGET_SPELLS.has(def.id)) {
    const target = targetAt(you.board, action.targetEnemySlot ?? 0, 'enemy');
    if (target.untargetable) throw new Error('Enemy target is untargetable');
    return;
  }
  if (FRIENDLY_UNIT_TARGET_SPELLS.has(def.id)) {
    const target = targetAt(me.board, action.targetSlot ?? 0, 'friendly');
    if (target.isStructure) throw new Error('Friendly target must be a unit');
    if (def.id === 'ge_clone' && firstEmptySlot(me.board) < 0) {
      throw new Error('No empty slot for clone');
    }
    return;
  }
  if (def.id === 'gt_wrench') {
    const target = targetAt(you.board, action.targetEnemySlot ?? 0, 'enemy');
    if (!target.isStructure || !target.keywords.includes('machine')) {
      throw new Error('Target must be an enemy machine structure');
    }
    return;
  }
  if (def.id === 'fo_smoke') {
    const target = targetAt(you.board, action.targetEnemySlot ?? 0, 'enemy');
    if (!target.keywords.includes('stealth')) throw new Error('Target must have stealth');
    return;
  }
  if (def.id === 'ee_watch') {
    const target = targetAt(me.board, action.targetSlot ?? 0, 'friendly');
    if (!target.isStructure || target.hiveTurnsLeft == null) {
      throw new Error('Target must be a structure with cooldown');
    }
    return;
  }
  if (def.id === 'av_eternal' && !me.eternalReady && me.singularitiesPlayed < 3) {
    throw new Error('Eternal Singularity requires 3 singularities');
  }
}

function validatePlayCard(
  state: BattleState,
  actor: 0 | 1,
  def: CardDef,
  action: Extract<PlayerAction, { type: 'play_card' }>,
): number | null {
  const me = state.players[actor];
  const available = me.mana + me.bankedMana + (def.kind === 'spell' ? me.spellMana : 0);
  if (available < def.cost) throw new Error('Not enough mana');

  if (def.kind === 'unit' || def.kind === 'structure') {
    const slot = action.targetSlot ?? firstEmptySlot(me.board);
    if (!Number.isInteger(slot) || slot < 0 || slot >= BOARD_SIZE || me.board[slot]) {
      throw new Error('No empty slot');
    }
    return slot;
  }
  if (def.kind === 'spell') validateSpellTarget(state, actor, def, action);
  return null;
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
        const clone = {
          ...src,
          instanceId: randomUUID(),
          attack: 1,
          health: 1,
          maxHealth: 1,
          keywords: [...src.keywords],
          aspects: [...src.aspects],
          equipment: [],
        };
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
      if (id) addToHand(me, id, state);
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
      state.attackTokenPlayer = actor;
      state.attackTokenAvailable = true;
      state.log.push('DLB steals the attack token!');
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
  ensurePhase(state, 'main', 'spell_response', 'combat_response');
  if (state.activePlayer !== actor) throw new Error('Not your turn');
  const me = state.players[actor];
  const cardId = me.hand[action.handIndex];
  if (!cardId) throw new Error('Invalid hand index');
  const def = getCard(cardId);
  if (!def) throw new Error(`Unknown card ${cardId}`);
  const response = isResponsePhase(state.phase);
  if (response && def.kind !== 'spell') throw new Error('Only spells may be played in a response window');
  const speed = def.kind === 'spell' ? spellSpeed(def) : null;
  if (response && speed === 'slow') throw new Error('Slow spells cannot be played in a response window');
  const unitSlot = validatePlayCard(state, actor, def, action);
  if (!spendMana(me, def.cost, def.kind === 'spell')) throw new Error('Not enough mana');

  me.hand.splice(action.handIndex, 1);
  state.log.push(`${me.name} plays ${def.nameZh}`);

  if (def.kind === 'unit' || def.kind === 'structure') {
    const unit = makeUnit(def);
    me.board[unitSlot!] = unit;
    // Forestry hive occupies a slot — opponent effectively has more combat slots conceptually handled by untargetable
  } else if (def.kind === 'spell' && speed === 'burst') {
    me.discard.push(cardId);
    applySpell(state, actor, def, action, rng);
  } else if (def.kind === 'spell') {
    const item: SpellStackItem = {
      stackId: state.nextStackId++,
      caster: actor,
      cardId,
      speed: speed as 'slow' | 'fast',
      ...(action.targetSlot != null ? { targetSlot: action.targetSlot } : {}),
      ...(action.targetEnemySlot != null ? { targetEnemySlot: action.targetEnemySlot } : {}),
    };
    state.spellStack.push(item);
    if (!response) {
      state.phase = 'spell_response';
      state.responseOriginPlayer = actor;
    }
    state.responsePasses = 0;
    state.consecutivePasses = 0;
    state.activePlayer = oppIndex(actor);
    state.log.push(`${def.nameZh} enters the spell stack`);
    return;
  }

  removeDead(state.players[0], state);
  removeDead(state.players[1], state);
  checkWinner(state);
  if (state.winner == null) {
    state.consecutivePasses = 0;
    if (response) state.responsePasses = 0;
    if (speed !== 'burst') state.activePlayer = oppIndex(actor);
  }
}

function actionForStackItem(item: SpellStackItem): Extract<PlayerAction, { type: 'play_card' }> {
  return {
    type: 'play_card',
    handIndex: -1,
    ...(item.targetSlot != null ? { targetSlot: item.targetSlot } : {}),
    ...(item.targetEnemySlot != null ? { targetEnemySlot: item.targetEnemySlot } : {}),
  };
}

function discardUnresolvedStack(state: BattleState): void {
  while (state.spellStack.length > 0) {
    const item = state.spellStack.pop()!;
    state.players[item.caster].discard.push(item.cardId);
    state.log.push(`${item.cardId} is cancelled because the match ended`);
  }
}

function resolveSpellStack(state: BattleState): void {
  const responsePhase = state.phase;
  if (!isResponsePhase(responsePhase)) throw new Error('No response window');
  const origin = state.responseOriginPlayer;
  state.phase = 'resolve';

  while (state.spellStack.length > 0) {
    const item = state.spellStack.pop()!;
    const caster = state.players[item.caster];
    const def = getCard(item.cardId);
    const action = actionForStackItem(item);
    let fizzled = def == null;
    if (def) {
      try {
        validateSpellTarget(state, item.caster, def, action);
      } catch {
        fizzled = true;
      }
    }
    if (def && !fizzled) {
      applySpell(state, item.caster, def, action, mulberry32(state.seed + state.turn * 41 + item.stackId));
      state.log.push(`${def.nameZh} resolves from the spell stack`);
    } else {
      state.log.push(`${item.cardId} fizzles because its target is no longer legal`);
    }
    caster.discard.push(item.cardId);
    removeDead(state.players[0], state);
    removeDead(state.players[1], state);
    checkWinner(state);
    if (state.winner != null) {
      discardUnresolvedStack(state);
      state.responsePasses = 0;
      state.responseOriginPlayer = null;
      return;
    }
  }

  state.responsePasses = 0;
  state.responseOriginPlayer = null;
  if (responsePhase === 'combat_response') {
    resolveCombat(state);
  } else {
    state.phase = 'main';
    state.activePlayer = origin == null ? state.activePlayer : oppIndex(origin);
  }
}

function openCombatResponse(state: BattleState): void {
  if (state.combatAttacker == null) throw new Error('No combat attacker');
  state.phase = 'combat_response';
  state.activePlayer = state.combatAttacker;
  state.responsePasses = 0;
  state.responseOriginPlayer = null;
  state.spellStack = [];
  state.log.push('Combat response window opens');
}

function startAttackDeclare(state: BattleState, actor: 0 | 1): void {
  ensurePhase(state, 'main');
  if (state.activePlayer !== actor) throw new Error('Not your priority');
  if (!state.attackTokenAvailable || state.attackTokenPlayer !== actor) {
    throw new Error('No attack token');
  }
  state.phase = 'attack_declare';
  state.combatAttacker = actor;
  state.consecutivePasses = 0;
  state.attackOrder = [];
  state.blockPairs = [];
  state.swapUsedThisCombat = false;
}

function declareAttacks(state: BattleState, actor: 0 | 1, slots: number[]): void {
  ensurePhase(state, 'attack_declare');
  if (state.combatAttacker !== actor) throw new Error('Not attacker');
  const me = state.players[actor];
  const unique = [...new Set(slots)];
  if (unique.length === 0) {
    state.phase = 'main';
    state.combatAttacker = null;
    state.attackOrder = [];
    state.blockPairs = [];
    state.log.push(`${me.name} cancels the attack declaration`);
    return;
  }
  for (const s of unique) {
    const u = me.board[s];
    if (!u || u.isStructure || u.attack <= 0) throw new Error(`Slot ${s} cannot attack`);
  }
  state.attackOrder = unique;
  state.phase = 'block_declare';
  state.activePlayer = oppIndex(actor);
}

function canBlock(attacker: BoardUnit, blocker: BoardUnit): boolean {
  if (blocker.isStructure) return false;
  if (attacker.keywords.includes('stealth') && !blocker.keywords.includes('stealth')) return false;
  return true;
}

function declareBlocks(state: BattleState, blocker: 0 | 1, pairs: AttackPair[]): void {
  ensurePhase(state, 'block_declare');
  const attackerIndex = state.combatAttacker;
  if (attackerIndex == null || blocker !== oppIndex(attackerIndex)) throw new Error('Not defender');
  const atk = state.players[attackerIndex];
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
    openCombatResponse(state);
  }
}

function swapSlots(state: BattleState, actor: 0 | 1, a: number, b: number): void {
  ensurePhase(state, 'swap_extra');
  if (state.combatAttacker == null || actor !== oppIndex(state.combatAttacker)) throw new Error('Only defender swaps');
  const p = state.players[actor];
  if (!p.board.some((u) => u && hasOrdoAer(u))) throw new Error('Need Ordo+Aer');
  const tmp = p.board[a];
  p.board[a] = p.board[b];
  p.board[b] = tmp;
  state.swapUsedThisCombat = true;
  state.log.push(`${p.name} swapped slots ${a}<->${b}`);
  // Remap block pairs if blocker slots moved
  openCombatResponse(state);
}

function passSwap(state: BattleState, actor: 0 | 1): void {
  ensurePhase(state, 'swap_extra');
  if (state.combatAttacker == null || actor !== oppIndex(state.combatAttacker)) {
    throw new Error('Only defender may skip the swap');
  }
  state.log.push(`${state.players[actor].name} skips the mystic swap`);
  openCombatResponse(state);
}

function resolveCombat(state: BattleState): void {
  state.phase = 'resolve';
  const atkIdx = state.combatAttacker;
  if (atkIdx == null) throw new Error('No combat attacker');
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
      // A removed blocker leaves a ghost block. Without an Overwhelm-like
      // keyword this attacker no longer has a Nexus target this combat.
      state.log.push(`${attacker.cardId} remains blocked after its blocker left combat`);
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
  state.attackTokenAvailable = false;
  state.combatAttacker = null;
  state.attackOrder = [];
  state.blockPairs = [];
  state.consecutivePasses = 0;
  state.responsePasses = 0;
  state.responseOriginPlayer = null;
  state.spellStack = [];
  state.activePlayer = defIdx;
  state.phase = 'main';
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
      if (id) addToHand(p, id, state);
    }

    if (u.cardId === 'ee_relay') {
      const cooldownTarget = p.board.find(
        (candidate) => candidate?.keywords.includes('beehive') && (candidate.hiveTurnsLeft ?? 0) > 0,
      );
      if (cooldownTarget?.hiveTurnsLeft != null) {
        cooldownTarget.hiveTurnsLeft = Math.max(0, cooldownTarget.hiveTurnsLeft - 1);
      }
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

function bankRoundMana(p: PlayerState, state: BattleState): void {
  const spellReserve = Math.min(3 - p.spellMana, p.mana);
  p.spellMana += spellReserve;
  p.mana -= spellReserve;
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

function endRound(state: BattleState): void {
  state.phase = 'turn_end';
  for (const playerIndex of [0, 1] as const) {
    const player = state.players[playerIndex];
    processStructures(state, player, mulberry32(state.seed + state.turn * 17 + playerIndex));
    bankRoundMana(player, state);
  }

  let nextToken = oppIndex(state.attackTokenPlayer);
  if (state.dlbForceEvery > 0 && state.turn - state.lastForcedSwapTurn >= state.dlbForceEvery) {
    state.lastForcedSwapTurn = state.turn;
    nextToken = state.attackTokenPlayer;
    state.log.push('DLB schedule: previous attacker keeps the token');
  }
  state.turn += 1;
  state.attackTokenPlayer = nextToken;
  state.attackTokenAvailable = true;
  state.activePlayer = nextToken;
  state.combatAttacker = null;
  state.consecutivePasses = 0;
  state.responsePasses = 0;
  state.responseOriginPlayer = null;
  state.spellStack = [];
  for (const player of state.players) {
    player.maxMana = Math.min(10, player.maxMana + 1);
    player.mana = player.maxMana;
    for (const unit of player.board) {
      if (unit) unit.health = unit.maxHealth;
    }
    draw(player, 1, state);
    if (state.winner != null) return;
  }

  const totem = state.players.some((p) => p.board.some((u) => u?.cardId === 'dlb_chaos'));
  if (totem && state.dlbForceEvery > 3) state.dlbForceEvery -= 1;

  state.attackOrder = [];
  state.blockPairs = [];
  state.phase = 'main';
  checkWinner(state);
}

function passPriority(state: BattleState, actor: 0 | 1): void {
  ensurePhase(state, 'main', 'spell_response', 'combat_response');
  if (state.activePlayer !== actor) throw new Error('Not your priority');
  if (isResponsePhase(state.phase)) {
    state.responsePasses += 1;
    state.log.push(`${state.players[actor].name} passes response priority`);
    if (state.responsePasses >= 2) {
      resolveSpellStack(state);
    } else {
      state.activePlayer = oppIndex(actor);
    }
    return;
  }
  state.consecutivePasses += 1;
  state.log.push(`${state.players[actor].name} passes priority`);
  if (state.consecutivePasses >= 2) {
    endRound(state);
    return;
  }
  state.activePlayer = oppIndex(actor);
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

/** Priority-aware PvE AI: one action per priority window. */
export function runAiIfNeeded(state: BattleState): void {
  for (let n = 0; n < 40; n++) {
    if (state.phase === 'game_over') return;
    const rng = mulberry32(state.seed + state.turn * 31 + n);

    if (state.phase === 'mulligan') {
      const actor = state.players.findIndex((player, index) => player.isAi && !state.mulliganDone[index]);
      if (actor < 0) return;
      const replaceIndices = state.players[actor]!.hand
        .map((id, index) => ({ def: getCard(id), index }))
        .filter(({ def }) => (def?.cost ?? 0) >= 5)
        .map(({ index }) => index);
      confirmMulligan(state, actor as 0 | 1, replaceIndices, rng);
      continue;
    }

    if (state.phase === 'attack_declare') {
      const actor = state.combatAttacker;
      if (actor == null || !state.players[actor].isAi) return;
      const p = state.players[actor];
      const slots: number[] = [];
      p.board.forEach((u, i) => {
        if (u && !u.isStructure && u.attack > 0) slots.push(i);
      });
      declareAttacks(state, actor, slots);
      continue;
    }

    if (state.phase === 'block_declare') {
      if (state.combatAttacker == null) return;
      const defender = oppIndex(state.combatAttacker);
      const defP = state.players[defender];
      if (!defP.isAi) return;
      const pairs: AttackPair[] = [];
      const used = new Set<number>();
      for (const aSlot of state.attackOrder) {
        const attacker = state.players[state.combatAttacker].board[aSlot];
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
      declareBlocks(state, defender, pairs);
      continue;
    }

    if (state.phase === 'swap_extra') {
      if (state.combatAttacker == null) return;
      const defender = oppIndex(state.combatAttacker);
      if (state.players[defender].isAi) {
        passSwap(state, defender);
      } else {
        return;
      }
      continue;
    }

    if (state.phase === 'spell_response' || state.phase === 'combat_response') {
      const actor = state.activePlayer;
      const p = state.players[actor];
      if (!p.isAi) return;
      let played = false;
      for (let i = 0; i < p.hand.length; i++) {
        const def = getCard(p.hand[i]!);
        if (!def || def.kind !== 'spell' || spellSpeed(def) === 'slow') continue;
        const available = p.mana + p.bankedMana + p.spellMana;
        if (available < def.cost) continue;
        try {
          playCard(state, actor, { type: 'play_card', handIndex: i, targetSlot: 0, targetEnemySlot: 0 }, rng);
          played = true;
          break;
        } catch {
          // Continue to the next response card when its default target is illegal.
        }
      }
      if (!played) passPriority(state, actor);
      continue;
    }

    if (state.phase !== 'main') return;
    const actor = state.activePlayer;
    const p = state.players[actor];
    if (!p.isAi) return;

    const attackers = p.board.some((u) => u && !u.isStructure && u.attack > 0);
    if (state.attackTokenAvailable && state.attackTokenPlayer === actor && attackers) {
      startAttackDeclare(state, actor);
      continue;
    }

    let played = false;
    for (let i = 0; i < p.hand.length; i++) {
      const def = getCard(p.hand[i]!);
      if (!def) continue;
      const available = p.mana + p.bankedMana + (def.kind === 'spell' ? p.spellMana : 0);
      if (available < def.cost) continue;
      try {
        if (def.kind === 'unit' || def.kind === 'structure') {
          const slot = firstEmptySlot(p.board);
          if (slot < 0) continue;
          playCard(state, actor, { type: 'play_card', handIndex: i, targetSlot: slot }, rng);
        } else {
          playCard(state, actor, { type: 'play_card', handIndex: i, targetSlot: 0, targetEnemySlot: 0 }, rng);
        }
        played = true;
        break;
      } catch {
        // Try another legal card rather than stalling the match.
      }
    }
    if (!played) {
      passPriority(state, actor);
    }
  }
}

export function applyAction(state: BattleState, actor: 0 | 1, action: PlayerAction): BattleState {
  if (state.phase === 'game_over') throw new Error('Game over');
  const rng = mulberry32(state.seed + state.turn * 13 + state.log.length);

  switch (action.type) {
    case 'confirm_mulligan':
      confirmMulligan(state, actor, action.replaceIndices, rng);
      break;
    case 'play_card':
      playCard(state, actor, action, rng);
      break;
    case 'pass_priority':
      passPriority(state, actor);
      break;
    case 'start_attack':
      startAttackDeclare(state, actor);
      break;
    case 'end_main':
      if (state.attackTokenAvailable && state.attackTokenPlayer === actor) {
        startAttackDeclare(state, actor);
      } else {
        passPriority(state, actor);
      }
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
    case 'pass_swap':
      passSwap(state, actor);
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
  const safe = clone as BattleState & {
    _equip?: unknown;
    _equipApplied?: boolean;
    _runSettled?: boolean;
  };
  delete safe._equip;
  delete safe._equipApplied;
  delete safe._runSettled;
  return clone;
}
