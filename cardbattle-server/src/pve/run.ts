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
}

export interface StageDef {
  id: string;
  name: string;
  nameZh: string;
  aiThemes: ThemeId[];
  aiVoltage: VoltageTier;
  difficulty: number;
  feature?: string;
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
    for (const c of shuffled.slice(0, 10)) {
      deck.push(c.id);
      // genetics: more units — duplicate cheap units
      if (th === 'genetics' && c.kind === 'unit' && c.cost <= 1) {
        deck.push(c.id);
      }
    }
  }
  // pad to ~40
  while (deck.length < 30) {
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

function pumpReward(tier: VoltageTier, hard: boolean): RewardItem {
  const metaByTier: Record<string, number> = {
    ULV: 0,
    LV: 1,
    MV: 2,
    HV: 3,
    EV: 4,
    IV: 5,
  };
  return {
    modid: 'gregtech',
    name: 'gt.metaitem.01',
    meta: 32610 + (metaByTier[tier] ?? 0),
    count: hard ? 64 : 16,
    displayName: `${tier} Pump x${hard ? 64 : 16}`,
  };
}

export function generateStages(seed: number, playerVoltage: VoltageTier, count = 5): StageDef[] {
  const rng = mulberry32(seed);
  const stages: StageDef[] = [];
  const baseIdx = VOLTAGE_ORDER.indexOf(playerVoltage);
  for (let i = 0; i < count; i++) {
    const aiTheme = pickTheme(rng);
    const aiVoltage = voltageAt(baseIdx + Math.floor(i / 2));
    stages.push({
      id: `stage_${i + 1}`,
      name: `Stage ${i + 1}`,
      nameZh: `关卡 ${i + 1} · ${aiTheme}`,
      aiThemes: [aiTheme],
      aiVoltage,
      difficulty: 1 + i * 0.35,
      feature: aiTheme === 'dlb' ? 'dlb_force' : aiTheme === 'forestry' ? 'hive' : undefined,
    });
  }
  return stages;
}

export function rewardChoices(stage: StageDef, playerVoltage: VoltageTier): StageRewardChoice[] {
  return [
    {
      id: 'safe',
      label: 'Safe reward',
      labelZh: '稳妥奖励（小包泵）',
      hard: false,
      items: [pumpReward(playerVoltage, false)],
    },
    {
      id: 'hard',
      label: 'Hard reward',
      labelZh: '对标奖励（整组泵，关卡更难）',
      hard: true,
      items: [pumpReward(playerVoltage, true)],
      voltageBonus: 1,
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
