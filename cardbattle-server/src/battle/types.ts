/** Shared card-battle domain types (server authority). */

export type ThemeId =
  | 'vanilla'
  | 'gt'
  | 'thaum'
  | 'forestry'
  | 'astral'
  | 'avaritia'
  | 'ee'
  | 'genetics'
  | 'ae'
  | 'dlb';

export type VoltageTier =
  | 'ULV'
  | 'LV'
  | 'MV'
  | 'HV'
  | 'EV'
  | 'IV'
  | 'LuV'
  | 'ZPM'
  | 'UV'
  | 'UHV';

export const VOLTAGE_ORDER: VoltageTier[] = [
  'ULV',
  'LV',
  'MV',
  'HV',
  'EV',
  'IV',
  'LuV',
  'ZPM',
  'UV',
  'UHV',
];

/** Max theme packs allowed in a deck by player voltage. */
export const THEME_SLOTS_BY_VOLTAGE: Record<VoltageTier, number> = {
  ULV: 1,
  LV: 1,
  MV: 2,
  HV: 2,
  EV: 3,
  IV: 3,
  LuV: 4,
  ZPM: 4,
  UV: 5,
  UHV: 5,
};

export type CardKind = 'unit' | 'spell' | 'structure' | 'equipment';

export type SpellSpeed = 'slow' | 'fast' | 'burst';

export type Keyword =
  | 'lifesteal'
  | 'aoe'
  | 'stealth'
  | 'untargetable'
  | 'machine'
  | 'capacitor'
  | 'beehive'
  | 'bee'
  | 'aspect'
  | 'singularity'
  | 'eternal_singularity'
  | 'accelerator'
  | 'reflect'
  | 'reduce_damage';

export type AspectId = 'ordo' | 'aer' | 'ignis' | 'aqua' | 'terra' | 'perditio';

export type SpellTargetKind =
  | 'none'
  | 'enemy_unit'
  | 'friendly_unit'
  | 'enemy_machine'
  | 'enemy_stealth'
  | 'friendly_cooldown'
  | 'friendly_unit_clone';

export type SpellEffectId =
  | 'damage_unit'
  | 'heal_unit'
  | 'buff_unit'
  | 'buff_all'
  | 'armor_unit'
  | 'draw'
  | 'gain_mana'
  | 'nexus_damage'
  | 'nexus_heal'
  | 'nexus_max_heal'
  | 'summon_token'
  | 'strip_stealth'
  | 'destroy_machine'
  | 'hive_cooldown'
  | 'add_aspects'
  | 'damage_and_aspect'
  | 'singularity'
  | 'eternal'
  | 'ae_generate'
  | 'steal_attack_token'
  | 'enemy_lose_mana'
  | 'random_enemy_damage'
  | 'damage_reduction'
  | 'reflect'
  | 'clone_unit'
  | 'summon_tokens'
  | 'reduce_dlb_interval';

export interface SpellEffect {
  id: SpellEffectId;
  target?: SpellTargetKind;
  amount?: number;
  amount2?: number;
  tokenCardId?: string;
  tokenCount?: number;
  aspects?: AspectId[];
  keywordsAdd?: Keyword[];
}

export interface CardDef {
  id: string;
  name: string;
  nameZh: string;
  theme: ThemeId;
  kind: CardKind;
  /** Slow starts only in main, fast may respond, burst resolves immediately. */
  spellSpeed?: SpellSpeed;
  cost: number;
  attack?: number;
  health?: number;
  armor?: number;
  keywords?: Keyword[];
  aspects?: AspectId[];
  /** GT: mana produced each turn while on board */
  manaPerTurn?: number;
  /** Forestry: turns until bee spawn / merge */
  hiveCooldown?: number;
  /** Text blurb */
  text?: string;
  textZh?: string;
  /** Exact player-facing rules text. Flavor/summary belongs in text/textZh. */
  rulesZh?: string;
  art?: string;
  /** Data-driven spell resolution payload. */
  effect?: SpellEffect;
}

export interface EquipmentMod {
  id: string;
  name: string;
  nameZh: string;
  attack?: number;
  health?: number;
  armor?: number;
}

export interface BoardUnit {
  instanceId: string;
  cardId: string;
  attack: number;
  health: number;
  maxHealth: number;
  armor: number;
  keywords: Keyword[];
  aspects: AspectId[];
  /** Structure / machine flags */
  isStructure: boolean;
  untargetable: boolean;
  hiveTurnsLeft?: number;
  equipment: string[];
  /** Avaritia: singularity progress flags */
  singularityPlayed?: boolean;
}

export type BattlePhase =
  | 'mulligan'
  | 'main'
  | 'spell_response'
  | 'attack_declare'
  | 'block_declare'
  | 'combat_response'
  | 'resolve'
  | 'swap_extra'
  | 'turn_end'
  | 'game_over';

export interface PlayerState {
  id: string;
  name: string;
  isAi: boolean;
  nexusHp: number;
  maxNexusHp: number;
  mana: number;
  maxMana: number;
  /** LoR-style reserve: only spells may spend this, capped at 3. */
  spellMana: number;
  /** GT capacitor bank stored overflow */
  bankedMana: number;
  voltage: VoltageTier;
  hand: string[];
  deck: string[];
  discard: string[];
  board: (BoardUnit | null)[];
  themes: ThemeId[];
  /** Astral player buffs */
  damageReductionPct: number;
  reflectToNexus: boolean;
  /** Avaritia */
  singularitiesPlayed: number;
  eternalReady: boolean;
  eternalActive: boolean;
  /** Genetics swarm hint */
  unitBias: boolean;
}

export interface AttackPair {
  /** Bench slot of attacker at declare time (kept for UI/compat). */
  attackerSlot: number;
  /** -1 = nexus / unblocked */
  blockerSlot: number;
  /** Stable identity preferred by LoR-style board UI. */
  attackerInstanceId?: string;
  blockerInstanceId?: string | null;
}

export interface SpellStackItem {
  stackId: number;
  caster: 0 | 1;
  cardId: string;
  speed: Exclude<SpellSpeed, 'burst'>;
  targetSlot?: number;
  targetEnemySlot?: number;
  targetInstanceId?: string;
}

export interface BattleState {
  matchId: string;
  seed: number;
  turn: number;
  phase: BattlePhase;
  /** Player currently holding action priority. */
  activePlayer: 0 | 1;
  /** Player who owns this round's attack token. */
  attackTokenPlayer: 0 | 1;
  attackTokenAvailable: boolean;
  /** Frozen attacker while block/resolve windows are open. */
  combatAttacker: 0 | 1 | null;
  /** Two consecutive priority passes end the round. */
  consecutivePasses: number;
  /** Response passes are isolated from main-round passes. */
  responsePasses: number;
  /** Bottom spell caster; priority returns to their opponent after main-stack resolution. */
  responseOriginPlayer: 0 | 1 | null;
  /** Public LIFO spell stack. Burst spells never enter this zone. */
  spellStack: SpellStackItem[];
  nextStackId: number;
  /** Each player confirms exactly one opening-hand replacement. */
  mulliganDone: [boolean, boolean];
  players: [PlayerState, PlayerState];
  /**
   * Attack order left-to-right as bench slot indices at declare time.
   * Prefer resolving units via attackOrderIds when present.
   */
  attackOrder: number[];
  /** LoR battlefield order: attacker instanceIds left → right. */
  attackOrderIds: string[];
  blockPairs: AttackPair[];
  swapUsedThisCombat: boolean;
  /** DLB: force role swap every N turns */
  dlbForceEvery: number;
  lastForcedSwapTurn: number;
  winner: 0 | 1 | null;
  log: string[];
  /** AE: off-deck card pool ids */
  aePool: string[];
}

export interface AuthSession {
  token: string;
  ownerUuid: string;
  actorUuid: string;
  actorName: string;
  type: string;
  accountId?: string;
  username?: string;
  role?: string;
  mcUuid?: string | null;
  mcName?: string | null;
  authSource?: 'account' | 'dev' | 'webae';
}
