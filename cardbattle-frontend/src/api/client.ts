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
  health: () => api<{ status: string; auth: unknown }>('/api/health'),
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
  beginStage: (runId: string, rewardChoiceId?: string) =>
    api<{ run: RunState; matchId: string; match: BattleState }>(`/api/run/${runId}/stage`, {
      method: 'POST',
      body: JSON.stringify({ rewardChoiceId }),
    }),
  getMatch: (matchId: string) => api<{ match: BattleState }>(`/api/match/${matchId}`),
  action: (matchId: string, action: unknown, runId?: string) =>
    api<{ match: BattleState; run: RunState | null }>(`/api/match/${matchId}/action`, {
      method: 'POST',
      body: JSON.stringify({ action, runId }),
    }),
  claimReward: (runId: string, choiceId: string) =>
    api<{ run: RunState; entryId: string }>(`/api/run/${runId}/claim-reward`, {
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
  cost: number;
  attack?: number;
  health?: number;
  armor?: number;
  keywords?: string[];
  textZh?: string;
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
}

export interface PlayerState {
  name: string;
  nexusHp: number;
  maxNexusHp: number;
  mana: number;
  maxMana: number;
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
  players: [PlayerState, PlayerState];
  attackOrder: number[];
  winner: 0 | 1 | null;
  log: string[];
}

export interface RunState {
  runId: string;
  voltage: string;
  themes: string[];
  stageIndex: number;
  stages: { id: string; nameZh: string; aiThemes: string[]; aiVoltage: string; difficulty: number }[];
  pendingChoice: { id: string; labelZh: string; hard: boolean; items: { displayName?: string; count: number }[] }[] | null;
  completed: boolean;
  victories: number;
  equipment: { attack: number; health: number; armor: number };
}

export interface PendingReward {
  id: string;
  status: string;
  items: { displayName?: string; modid: string; name: string; count: number }[];
  source: { runId: string; stageId: string; label?: string };
}
