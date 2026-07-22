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

export interface CardDef {
  id: string;
  name: string;
  nameZh: string;
  theme: ThemeId;
  kind: CardKind;
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
  art?: string;
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
  | 'attack_declare'
  | 'block_declare'
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
  attackerSlot: number;
  /** -1 = nexus */
  blockerSlot: number;
}

export interface BattleState {
  matchId: string;
  seed: number;
  turn: number;
  phase: BattlePhase;
  activePlayer: 0 | 1;
  players: [PlayerState, PlayerState];
  attackOrder: number[];
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
}
