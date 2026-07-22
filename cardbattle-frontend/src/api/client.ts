const TOKEN_KEY = 'textech_cardbattle_token';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Content-Type', 'application/json');
  const token = getToken();
  if (token) headers.set('Authorization', `Bearer ${token}`);
  const res = await fetch(path, { ...init, headers });
  const data = await res.json();
  if (!res.ok) throw new Error(data.message || data.code || res.statusText);
  return data as T;
}

export const client = {
  health: () =>
    api<{
      status: string;
      auth: unknown;
      rewardDelivery: RewardDeliveryStatus;
    }>('/api/health'),
  meta: () =>
    api<{
      themes: string[];
      voltages: string[];
      themeSlotsByVoltage: Record<string, number>;
      equipment: { id: string; nameZh: string; attack: number; health: number; armor: number }[];
      cardCount: number;
    }>('/api/meta'),
  me: () => api<{ ownerUuid: string; actorName: string }>('/api/me'),
  cards: () => api<{ cards: CardDef[] }>('/api/cards'),
  startRun: (body: {
    themes: string[];
    voltage: string;
    equipmentIds: string[];
  }) => api<{ run: RunState }>('/api/run', { method: 'POST', body: JSON.stringify(body) }),
  getRun: (runId: string) => api<{ run: RunState }>(`/api/run/${runId}`),
  beginStage: (runId: string, stageId?: string) =>
    api<{ run: RunState; matchId: string; match: BattleState }>(`/api/run/${runId}/stage`, {
      method: 'POST',
      body: JSON.stringify({ stageId }),
    }),
  getMatch: (matchId: string) => api<{ match: BattleState }>(`/api/match/${matchId}`),
  action: (matchId: string, action: unknown, runId?: string) =>
    api<{ match: BattleState; run: RunState | null }>(`/api/match/${matchId}/action`, {
      method: 'POST',
      body: JSON.stringify({ action, runId }),
    }),
  claimReward: (runId: string, choiceId: string) =>
    api<{ run: RunState; entryId: string | null; unlockedSkinIds: string[] }>(`/api/run/${runId}/claim-reward`, {
      method: 'POST',
      body: JSON.stringify({ choiceId }),
    }),
  pendingRewards: () => api<{ entries: PendingReward[] }>('/api/rewards/pending'),
};

export interface CardDef {
  id: string;
  name: string;
  nameZh: string;
  theme: string;
  kind: string;
  spellSpeed?: 'slow' | 'fast' | 'burst';
  cost: number;
  attack?: number;
  health?: number;
  armor?: number;
  keywords?: string[];
  aspects?: string[];
  manaPerTurn?: number;
  hiveCooldown?: number;
  textZh?: string;
  rulesZh?: string;
  art?: string;
}

export interface BoardUnit {
  instanceId: string;
  cardId: string;
  attack: number;
  health: number;
  maxHealth: number;
  armor: number;
  keywords: string[];
  aspects: string[];
  isStructure: boolean;
  untargetable: boolean;
  hiveTurnsLeft?: number;
}

export interface PlayerState {
  name: string;
  nexusHp: number;
  maxNexusHp: number;
  mana: number;
  maxMana: number;
  spellMana: number;
  bankedMana: number;
  voltage: string;
  hand: string[];
  board: (BoardUnit | null)[];
  damageReductionPct: number;
  reflectToNexus: boolean;
  singularitiesPlayed: number;
  eternalActive: boolean;
}

export interface BattleState {
  matchId: string;
  turn: number;
  phase: string;
  activePlayer: 0 | 1;
  attackTokenPlayer: 0 | 1;
  attackTokenAvailable: boolean;
  combatAttacker: 0 | 1 | null;
  consecutivePasses: number;
  responsePasses: number;
  responseOriginPlayer: 0 | 1 | null;
  spellStack: {
    stackId: number;
    caster: 0 | 1;
    cardId: string;
    speed: 'slow' | 'fast';
    targetSlot?: number;
    targetEnemySlot?: number;
  }[];
  mulliganDone: [boolean, boolean];
  players: [PlayerState, PlayerState];
  attackOrder: number[];
  blockPairs: { attackerSlot: number; blockerSlot: number }[];
  winner: 0 | 1 | null;
  log: string[];
}

export interface RunState {
  runId: string;
  voltage: string;
  themes: string[];
  stageIndex: number;
  stages: {
    id: string;
    nameZh: string;
    aiThemes: string[];
    aiVoltage: string;
    difficulty: number;
    kind: 'battle' | 'elite' | 'boss';
    column: number;
    lane: 0 | 1;
    nextStageIds: string[];
    skinRewardId?: string;
  }[];
  availableStageIds: string[];
  completedStageIds: string[];
  currentStageId: string | null;
  powers: string[];
  unlockedSkinIds: string[];
  deck: string[];
  pendingChoice: {
    id: string;
    labelZh: string;
    hard: boolean;
    items: { displayName?: string; count: number }[];
    cardIds?: string[];
    powerId?: string;
    skinId?: string;
    rewardKey?: string;
    voltageTier?: string;
  }[] | null;
  completed: boolean;
  victories: number;
  equipment: { attack: number; health: number; armor: number };
}

export interface PendingReward {
  id: string;
  createdAt: number;
  status: string;
  items: { displayName?: string; modid: string; name: string; count: number }[];
  source: {
    runId: string;
    stageId: string;
    label?: string;
    schemaVersion?: number;
    rewardKey?: string;
    voltageTier?: string;
    delivery?: string;
  };
}

export interface RewardDeliveryStatus {
  mode: 'standalone_accumulation' | 'minecraft_shared_directory';
  bridgeClaimEnabled: boolean;
  dataRoot: string;
}
