import { cardsByTheme, getCard } from '../data/catalog.js';
import type { ThemeId, VoltageTier } from '../battle/types.js';
import { THEME_SLOTS_BY_VOLTAGE, VOLTAGE_ORDER } from '../battle/types.js';
import type { RewardItem } from '../rewards/pending.js';

export interface StageRewardChoice {
  id: string;
  label: string;
  labelZh: string;
  hard: boolean;
  items: RewardItem[];
  voltageBonus?: number;
  cardIds?: string[];
  powerId?: string;
  skinId?: string;
  /** Stable future MC bridge key; do not infer rewards from display text. */
  rewardKey?: string;
  voltageTier?: VoltageTier;
  delivery?: 'pending_bridge';
}

export type StageKind = 'battle' | 'elite' | 'boss';

export interface StageDef {
  id: string;
  name: string;
  nameZh: string;
  aiThemes: ThemeId[];
  aiVoltage: VoltageTier;
  difficulty: number;
  feature?: string;
  kind: StageKind;
  column: number;
  lane: 0 | 1;
  nextStageIds: string[];
  skinRewardId?: string;
}

export interface RunPowerDef {
  id: string;
  nameZh: string;
  descriptionZh: string;
  nexusHp?: number;
  startSpellMana?: number;
  firstUnitAttack?: number;
  firstUnitHealth?: number;
  firstUnitArmor?: number;
}

export interface RunState {
  runId: string;
  ownerUuid: string;
  seed: number;
  voltage: VoltageTier;
  themes: ThemeId[];
  deck: string[];
  /** Equipment mods applied to first board unit stats conceptually via deck starter */
  equipment: { attack: number; health: number; armor: number };
  stageIndex: number;
  stages: StageDef[];
  availableStageIds: string[];
  completedStageIds: string[];
  currentStageId: string | null;
  powers: string[];
  unlockedSkinIds: string[];
  pendingChoice: StageRewardChoice[] | null;
  completed: boolean;
  victories: number;
}

const ALL_THEMES: ThemeId[] = [
  'vanilla',
  'gt',
  'thaum',
  'forestry',
  'astral',
  'avaritia',
  'ee',
  'genetics',
  'ae',
  'dlb',
];

export const RUN_POWERS: RunPowerDef[] = [
  {
    id: 'power_reinforced_nexus',
    nameZh: '强化主机',
    descriptionZh: '每场战斗 Nexus 最大生命与当前生命 +4。',
    nexusHp: 4,
  },
  {
    id: 'power_spell_cache',
    nameZh: '法术缓存',
    descriptionZh: '每场战斗以 1 点法术法力开始。',
    startSpellMana: 1,
  },
  {
    id: 'power_precision_tools',
    nameZh: '精密工具',
    descriptionZh: '每场第一名非结构单位获得 +1/+1 与 1 点护甲。',
    firstUnitAttack: 1,
    firstUnitHealth: 1,
    firstUnitArmor: 1,
  },
];

function mulberry32(seed: number): () => number {
  let t = seed >>> 0;
  return () => {
    t += 0x6d2b79f5;
    let r = Math.imul(t ^ (t >>> 15), 1 | t);
    r ^= r + Math.imul(r ^ (r >>> 7), 61 | r);
    return ((r ^ (r >>> 14)) >>> 0) / 4294967296;
  };
}

function pickTheme(rng: () => number, exclude: ThemeId[] = []): ThemeId {
  const pool = ALL_THEMES.filter((t) => !exclude.includes(t));
  return pool[Math.floor(rng() * pool.length)]!;
}

function voltageAt(index: number): VoltageTier {
  return VOLTAGE_ORDER[Math.max(0, Math.min(VOLTAGE_ORDER.length - 1, index))]!;
}

/** Build a legal deck for themes under voltage slot cap. */
export function buildDeck(themes: ThemeId[], voltage: VoltageTier, seed: number): string[] {
  const slots = THEME_SLOTS_BY_VOLTAGE[voltage];
  const used = themes.slice(0, slots);
  const rng = mulberry32(seed);
  const deck: string[] = [];
  for (const th of used) {
    const cards = cardsByTheme(th);
    const shuffled = cards.slice().sort(() => rng() - 0.5);
    for (const c of shuffled.slice(0, 40)) {
      deck.push(c.id);
      // genetics: more units — duplicate cheap units
      if (th === 'genetics' && c.kind === 'unit' && c.cost <= 1) {
        deck.push(c.id);
      }
    }
  }
  // pad to 40
  while (deck.length < 40) {
    const th = used[Math.floor(rng() * used.length)]!;
    const cards = cardsByTheme(th);
    deck.push(cards[Math.floor(rng() * cards.length)]!.id);
  }
  return deck;
}

export function validateThemes(themes: ThemeId[], voltage: VoltageTier): string | null {
  const max = THEME_SLOTS_BY_VOLTAGE[voltage];
  if (themes.length === 0) return '至少选择 1 个主题';
  if (themes.length > max) return `电压 ${voltage} 最多 ${max} 个主题`;
  for (const t of themes) {
    if (!ALL_THEMES.includes(t)) return `未知主题 ${t}`;
  }
  return null;
}

function pumpReward(tier: VoltageTier, count: number): RewardItem {
  const metaByTier: Record<string, number> = {
    ULV: 0,
    LV: 1,
    MV: 2,
    HV: 3,
    EV: 4,
    IV: 5,
    LuV: 6,
    ZPM: 7,
    UV: 8,
    UHV: 9,
  };
  return {
    modid: 'gregtech',
    name: 'gt.metaitem.01',
    meta: 32610 + (metaByTier[tier] ?? 0),
    count,
    displayName: `${tier} Pump x${count}`,
  };
}

export function generateStages(seed: number, playerVoltage: VoltageTier, _count = 6): StageDef[] {
  const rng = mulberry32(seed);
  const baseIdx = VOLTAGE_ORDER.indexOf(playerVoltage);
  const makeStage = (
    id: string,
    column: number,
    lane: 0 | 1,
    kind: StageKind,
    nextStageIds: string[],
  ): StageDef => {
    const aiTheme = pickTheme(rng);
    const aiVoltage = voltageAt(baseIdx + Math.floor(column / 2) + (kind === 'boss' ? 1 : 0));
    return {
      id,
      name: `${kind} ${column + 1}${lane ? 'B' : 'A'}`,
      nameZh: `${kind === 'boss' ? '终局首领' : kind === 'elite' ? '精英节点' : '战斗节点'} · ${aiTheme}`,
      aiThemes: [aiTheme],
      aiVoltage,
      difficulty: 1 + column * 0.35 + (kind === 'elite' ? 0.4 : kind === 'boss' ? 0.9 : 0),
      feature: aiTheme === 'dlb' ? 'dlb_force' : aiTheme === 'forestry' ? 'hive' : undefined,
      kind,
      column,
      lane,
      nextStageIds,
      skinRewardId: kind === 'boss' ? 'overclocked_nexus' : undefined,
    };
  };
  return [
    makeStage('stage_1a', 0, 0, 'battle', ['stage_2a', 'stage_2b']),
    makeStage('stage_1b', 0, 1, 'battle', ['stage_2a', 'stage_2b']),
    makeStage('stage_2a', 1, 0, 'battle', ['stage_3a']),
    makeStage('stage_2b', 1, 1, 'battle', ['stage_3b']),
    makeStage('stage_3a', 2, 0, 'elite', ['stage_boss']),
    makeStage('stage_3b', 2, 1, 'elite', ['stage_boss']),
    makeStage('stage_boss', 3, 0, 'boss', []),
  ];
}

export function rewardChoices(stage: StageDef, playerVoltage: VoltageTier): StageRewardChoice[] {
  const themeCards = cardsByTheme(stage.aiThemes[0]!).slice(0, stage.kind === 'boss' ? 3 : 2).map((c) => c.id);
  const power = RUN_POWERS[Math.abs(stage.id.split('').reduce((n, c) => n + c.charCodeAt(0), 0)) % RUN_POWERS.length]!;
  const count = stage.kind === 'boss' ? 64 : stage.kind === 'elite' ? 32 : 16;
  const skinId = stage.skinRewardId;
  return [
    {
      id: 'cards',
      label: 'Add cards',
      labelZh: `扩充卡组（${themeCards.length} 张 ${stage.aiThemes[0]} 卡）`,
      hard: false,
      items: [],
      cardIds: themeCards,
      skinId,
    },
    {
      id: 'power',
      label: 'Run power',
      labelZh: `获得能力：${power.nameZh}`,
      hard: false,
      items: [],
      powerId: power.id,
      skinId,
    },
    {
      id: 'voltage_cache',
      label: 'Voltage cache',
      labelZh: `${playerVoltage} 电压奖励缓存（待游戏内桥接）`,
      hard: false,
      items: [pumpReward(playerVoltage, count)],
      rewardKey: `textech.cardbattle.voltage.${playerVoltage.toLowerCase()}.cache`,
      voltageTier: playerVoltage,
      delivery: 'pending_bridge',
      skinId,
    },
  ];
}

export function applyEquipment(
  deck: string[],
  eq: { attack: number; health: number; armor: number },
): { deck: string[]; note: string } {
  // Equipment is tracked on run; battle applies to first unit via starter — encode as log note
  void deck;
  return {
    deck,
    note: `Equipment A${eq.attack}/H${eq.health}/Ar${eq.armor} ready for first unit`,
  };
}

export const STARTER_EQUIPMENT = [
  { id: 'leather', nameZh: '皮革套', attack: 0, health: 1, armor: 1 },
  { id: 'wood_sword', nameZh: '木剑', attack: 1, health: 0, armor: 0 },
  { id: 'iron_chest', nameZh: '铁胸甲', attack: 0, health: 0, armor: 2 },
];

export function getCardSafe(id: string) {
  return getCard(id);
}

export { ALL_THEMES };
