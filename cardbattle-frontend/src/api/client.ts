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
  me: () =>
    api<{
      ownerUuid: string;
      actorUuid: string;
      actorName: string;
      type: string;
      accountId: string | null;
      username: string | null;
      role: string | null;
      mcUuid: string | null;
      mcName: string | null;
      authSource: string | null;
      binding: { bound: boolean; mcUuid?: string | null; mcName?: string | null };
    }>('/api/me'),
  register: (body: { username: string; password: string; displayName?: string }) =>
    api<{ token: string; user: AccountUser }>('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  login: (body: { username: string; password: string }) =>
    api<{ token: string; user: AccountUser }>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  logout: () => api<{ status: string }>('/api/auth/logout', { method: 'POST' }),
  changePassword: (body: { currentPassword: string; newPassword: string }) =>
    api<{ status: string }>('/api/auth/change-password', { method: 'POST', body: JSON.stringify(body) }),
  updateProfile: (body: { displayName: string }) =>
    api<{ user: AccountUser }>('/api/me', { method: 'PATCH', body: JSON.stringify(body) }),
  bindMc: (code: string) =>
    api<{ user: AccountUser }>('/api/me/bind', { method: 'POST', body: JSON.stringify({ code }) }),
  unbindMc: () => api<{ user: AccountUser }>('/api/me/bind', { method: 'DELETE' }),
  adminUsers: () => api<{ users: AccountUser[] }>('/api/admin/users'),
  adminPatchUser: (id: string, body: { role?: string; disabled?: boolean; displayName?: string }) =>
    api<{ user: AccountUser }>(`/api/admin/users/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),
  adminResetPassword: (id: string, password: string) =>
    api<{ status: string }>(`/api/admin/users/${id}/reset-password`, {
      method: 'POST',
      body: JSON.stringify({ password }),
    }),
  adminBindings: () => api<{ bindings: AccountBinding[] }>('/api/admin/bindings'),
  adminUnbind: (userId: string) =>
    api<{ user: AccountUser }>(`/api/admin/bindings/${userId}`, { method: 'DELETE' }),
  adminForceBind: (userId: string, body: { mcUuid: string; mcName?: string }) =>
    api<{ user: AccountUser }>(`/api/admin/bindings/${userId}`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),
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

export interface AccountUser {
  id: string;
  username: string;
  displayName: string;
  role: string;
  mcUuid: string | null;
  mcName: string | null;
  boundAt: number | null;
  createdAt: number;
  disabled: boolean;
}

export interface AccountBinding {
  userId: string;
  username: string;
  displayName: string;
  mcUuid: string;
  mcName: string;
  boundAt: number;
}

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
  effect?: {
    id: string;
    target?: string;
    amount?: number;
    amount2?: number;
    tokenCardId?: string;
    tokenCount?: number;
    aspects?: string[];
    keywordsAdd?: string[];
  };
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
  attackOrderIds?: string[];
  blockPairs: {
    attackerSlot: number;
    blockerSlot: number;
    attackerInstanceId?: string;
    blockerInstanceId?: string | null;
  }[];
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
