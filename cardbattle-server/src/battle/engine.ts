import { randomUUID } from 'node:crypto';
import { aeOffDeckPool, getCard } from '../data/catalog.js';
import {
  BOARD_SIZE,
  applyNextBeeMutate,
  applySpellEffect,
  compactBoard,
  dealDamageToUnit,
  findUnitSlot,
  firstEmptySlot,
  resolveTargetKind,
  validateEffectTarget,
} from './effects.js';
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
    attackOrderIds: [],
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

function removeDead(p: PlayerState, state: BattleState): void {
  for (let i = 0; i < p.board.length; i++) {
    const u = p.board[i];
    if (u && u.health <= 0) {
      state.log.push(`${p.name} loses ${u.cardId} at slot ${i}`);
      p.discard.push(u.cardId);
      p.board[i] = null;
    }
  }
  compactBoard(p.board);
  if (state.combatAttacker != null && state.players[state.combatAttacker] === p && state.attackOrderIds.length) {
    syncAttackOrderFromIds(state, p);
  }
  if (state.blockPairs.length) {
    for (const pair of state.blockPairs) {
      if (pair.attackerInstanceId) {
        const slot = findUnitSlot(state.players[state.combatAttacker ?? 0].board, pair.attackerInstanceId);
        if (slot >= 0) pair.attackerSlot = slot;
      }
      if (pair.blockerInstanceId) {
        const defIdx = state.combatAttacker == null ? 1 : oppIndex(state.combatAttacker);
        const slot = findUnitSlot(state.players[defIdx].board, pair.blockerInstanceId);
        pair.blockerSlot = slot >= 0 ? slot : -1;
      }
    }
  }
}

function unitById(board: (BoardUnit | null)[], instanceId: string | undefined | null): BoardUnit | null {
  if (!instanceId) return null;
  const slot = findUnitSlot(board, instanceId);
  return slot >= 0 ? board[slot] : null;
}

function syncAttackOrderFromIds(state: BattleState, attacker: PlayerState): void {
  state.attackOrder = state.attackOrderIds
    .map((id) => findUnitSlot(attacker.board, id))
    .filter((slot) => slot >= 0);
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
  | {
      type: 'play_card';
      handIndex: number;
      targetSlot?: number;
      targetEnemySlot?: number;
      targetInstanceId?: string;
    }
  | { type: 'pass_priority' }
  | { type: 'start_attack' }
  | { type: 'declare_attacks'; slots?: number[]; instanceIds?: string[] }
  | { type: 'declare_blocks'; pairs: AttackPair[] }
  | { type: 'swap_slots'; a: number; b: number }
  | { type: 'pass_swap' }
  | { type: 'reorder_bench'; from: number; to: number }
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

function validateSpellTarget(
  state: BattleState,
  actor: 0 | 1,
  def: CardDef,
  action: Extract<PlayerAction, { type: 'play_card' }>,
): void {
  if (def.id === 'av_eternal' && !state.players[actor].eternalReady && state.players[actor].singularitiesPlayed < 3) {
    throw new Error('Eternal Singularity requires 3 singularities');
  }
  if (def.effect) {
    validateEffectTarget(state, actor, def, action, { oppIndex });
    return;
  }
  // Fallback for any spell still missing effect metadata.
  const kind = resolveTargetKind(def);
  if (kind !== 'none') {
    validateEffectTarget(state, actor, def, action, { oppIndex });
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
    compactBoard(me.board);
    // LoR-style: optional preferSlot, otherwise first empty (auto-pack left).
    let slot = action.targetSlot;
    if (slot == null || !Number.isInteger(slot) || slot < 0 || slot >= BOARD_SIZE || me.board[slot]) {
      slot = firstEmptySlot(me.board);
    }
    if (slot < 0) throw new Error('No empty slot');
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

  // Legacy special: next hive bee mutates.
  if (def.id === 'fo_plugin_strong') {
    applyNextBeeMutate(me);
    state.log.push('Next hive bee will mutate (applied on spawn)');
    return;
  }

  const handled = applySpellEffect(state, actor, def, action, rng, {
    oppIndex,
    draw,
    addToHand,
    addMana,
    pickAeCard,
    makeUnit,
  });
  if (!handled) {
    state.log.push(`Spell ${def.id} has no special handler`);
  }
  compactBoard(me.board);
  compactBoard(state.players[oppIndex(actor)].board);
}

function playCard(state: BattleState, actor: 0 | 1, action: Extract<PlayerAction, { type: 'play_card' }>, rng: () => number): void {
  ensurePhase(state, 'main', 'spell_response', 'combat_response');
  if (state.activePlayer !== actor) throw new Error('Not your turn');
  const me = state.players[actor];
  const you = state.players[oppIndex(actor)];
  const cardId = me.hand[action.handIndex];
  if (!cardId) throw new Error('Invalid hand index');
  const def = getCard(cardId);
  if (!def) throw new Error(`Unknown card ${cardId}`);

  // Resolve targetInstanceId → slot indices before validation.
  if (action.targetInstanceId) {
    const friend = findUnitSlot(me.board, action.targetInstanceId);
    const enemy = findUnitSlot(you.board, action.targetInstanceId);
    if (friend >= 0) action.targetSlot = friend;
    else if (enemy >= 0) action.targetEnemySlot = enemy;
  }

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
    compactBoard(me.board);
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
      ...(action.targetInstanceId != null ? { targetInstanceId: action.targetInstanceId } : {}),
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
  state.attackOrderIds = [];
  state.blockPairs = [];
  state.swapUsedThisCombat = false;
}

function declareAttacks(
  state: BattleState,
  actor: 0 | 1,
  slots: number[] | undefined,
  instanceIds?: string[],
): void {
  ensurePhase(state, 'attack_declare');
  if (state.combatAttacker !== actor) throw new Error('Not attacker');
  const me = state.players[actor];

  let ids: string[] = [];
  if (instanceIds && instanceIds.length) {
    ids = [...new Set(instanceIds)];
  } else if (slots && slots.length) {
    const unique = [...new Set(slots)];
    for (const s of unique) {
      const u = me.board[s];
      if (!u || u.isStructure || u.attack <= 0) throw new Error(`Slot ${s} cannot attack`);
      ids.push(u.instanceId);
    }
  }

  if (ids.length === 0) {
    state.phase = 'main';
    state.combatAttacker = null;
    state.attackOrder = [];
    state.attackOrderIds = [];
    state.blockPairs = [];
    state.log.push(`${me.name} cancels the attack declaration`);
    return;
  }

  for (const id of ids) {
    const u = unitById(me.board, id);
    if (!u || u.isStructure || u.attack <= 0) throw new Error(`Unit ${id} cannot attack`);
  }
  state.attackOrderIds = ids;
  syncAttackOrderFromIds(state, me);
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
  const usedBlockerIds = new Set<string>();
  const mapped = new Map<number, { slot: number; id: string | null }>();

  syncAttackOrderFromIds(state, atk);

  for (const p of pairs) {
    let aSlot = p.attackerSlot;
    if (p.attackerInstanceId) {
      const found = findUnitSlot(atk.board, p.attackerInstanceId);
      if (found < 0) throw new Error('Invalid attacker');
      aSlot = found;
    }
    if (!state.attackOrder.includes(aSlot) && !state.attackOrderIds.includes(p.attackerInstanceId ?? '')) {
      if (!state.attackOrder.includes(aSlot)) throw new Error('Invalid attacker');
    }
    let bSlot = p.blockerSlot;
    if (p.blockerInstanceId) {
      bSlot = findUnitSlot(def.board, p.blockerInstanceId);
      if (bSlot < 0) bSlot = -1;
    }
    if (bSlot >= 0) {
      if (usedBlockers.has(bSlot) || (p.blockerInstanceId && usedBlockerIds.has(p.blockerInstanceId))) {
        throw new Error('Blocker reused');
      }
      const bu = def.board[bSlot];
      const au = atk.board[aSlot];
      if (!bu || !au) throw new Error('Missing unit');
      if (bu.untargetable) throw new Error('Cannot block with untargetable');
      if (!canBlock(au, bu)) throw new Error('Stealth cannot be blocked');
      usedBlockers.add(bSlot);
      usedBlockerIds.add(bu.instanceId);
      mapped.set(aSlot, { slot: bSlot, id: bu.instanceId });
    } else {
      mapped.set(aSlot, { slot: -1, id: null });
    }
  }
  state.blockPairs = state.attackOrder.map((a) => {
    const m = mapped.get(a);
    const attacker = atk.board[a];
    return {
      attackerSlot: a,
      blockerSlot: m?.slot ?? -1,
      attackerInstanceId: attacker?.instanceId,
      blockerInstanceId: m?.id ?? null,
    };
  });

  const canSwap = def.board.some((u) => u && hasOrdoAer(u));
  if (canSwap && !state.swapUsedThisCombat) {
    state.phase = 'swap_extra';
  } else {
    openCombatResponse(state);
  }
}

function reorderBench(state: BattleState, actor: 0 | 1, from: number, to: number): void {
  ensurePhase(state, 'main', 'attack_declare');
  if (state.phase === 'main' && state.activePlayer !== actor) throw new Error('Not your priority');
  if (state.phase === 'attack_declare' && state.combatAttacker !== actor) throw new Error('Not attacker');
  const p = state.players[actor];
  if (
    !Number.isInteger(from) ||
    !Number.isInteger(to) ||
    from < 0 ||
    to < 0 ||
    from >= BOARD_SIZE ||
    to >= BOARD_SIZE ||
    !p.board[from]
  ) {
    throw new Error('Invalid bench reorder');
  }
  const unit = p.board[from];
  p.board.splice(from, 1);
  p.board.splice(to, 0, unit);
  while (p.board.length < BOARD_SIZE) p.board.push(null);
  p.board.length = BOARD_SIZE;
  compactBoard(p.board);
  if (state.combatAttacker === actor) syncAttackOrderFromIds(state, p);
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
  state.attackOrderIds = [];
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
  state.attackOrderIds = [];
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
      declareAttacks(state, actor, action.slots, action.instanceIds);
      break;
    case 'declare_blocks':
      declareBlocks(state, actor, action.pairs);
      break;
    case 'pass_block':
      declareBlocks(
        state,
        actor,
        state.attackOrder.map((s, i) => ({
          attackerSlot: s,
          blockerSlot: -1,
          attackerInstanceId: state.attackOrderIds[i],
          blockerInstanceId: null,
        })),
      );
      break;
    case 'swap_slots':
      swapSlots(state, actor, action.a, action.b);
      break;
    case 'pass_swap':
      passSwap(state, actor);
      break;
    case 'reorder_bench':
      reorderBench(state, actor, action.from, action.to);
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
