import type { WebAiProviderDto } from '@/types/dto';

export type PersonalAiProtocol = 'openai-compatible' | 'anthropic' | 'gemini';
export type AiKeySource = 'server' | 'browser';

export interface PersonalAiMessage {
  role: 'system' | 'user' | 'assistant';
  content: string;
}

export interface PersonalAiProfile {
  id: string;
  name: string;
  enabled: boolean;
  order: number;
  providerId: string;
  protocol: PersonalAiProtocol;
  baseUrl: string;
  model: string;
  apiKey: string;
  timeoutSeconds: number;
  temperature: number;
  maxTokens: number;
}

/** @deprecated single-profile shape kept for migration */
export interface PersonalAiSettings extends Omit<PersonalAiProfile, 'id' | 'name' | 'order'> {
  updatedAt: number;
}

export interface PersonalAiStore {
  version: 2;
  preferredSource: AiKeySource;
  profiles: PersonalAiProfile[];
  updatedAt: number;
}

interface StorageLike {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
}

export interface PersonalAiHttpRequest {
  endpoint: string;
  headers: Record<string, string>;
  body: Record<string, unknown>;
}

const STORAGE_KEY = 'webae_personal_ai_v1';
const PREFERRED_KEY = 'webae_ai_preferred_source_v1';
const MAX_RESPONSE_CHARS = 1_048_576;

function browserStorage(): StorageLike | null {
  try {
    return typeof window === 'undefined' ? null : window.localStorage;
  } catch {
    return null;
  }
}

function scopedStorageKey(scope: string): string {
  const normalized = scope.trim().toLowerCase().replace(/[^a-z0-9_-]/g, '');
  return normalized ? `${STORAGE_KEY}:${normalized}` : STORAGE_KEY;
}

function preferredStorageKey(scope: string): string {
  const normalized = scope.trim().toLowerCase().replace(/[^a-z0-9_-]/g, '');
  return normalized ? `${PREFERRED_KEY}:${normalized}` : PREFERRED_KEY;
}

function firstProvider(providers: WebAiProviderDto[]): WebAiProviderDto | undefined {
  return providers.find((provider) => provider.id === 'deepseek') || providers[0];
}

function newId(): string {
  return Math.random().toString(36).slice(2, 10) + Date.now().toString(36).slice(-4);
}

export function defaultPersonalAiProfile(providers: WebAiProviderDto[], order = 0): PersonalAiProfile {
  const provider = firstProvider(providers);
  return {
    id: newId(),
    name: order === 0 ? 'Default' : `Profile ${order + 1}`,
    enabled: true,
    order,
    providerId: provider?.id || 'custom',
    protocol: provider?.protocol || 'openai-compatible',
    baseUrl: provider?.defaultBaseUrl || 'https://api.deepseek.com',
    model: provider?.defaultModel || 'deepseek-chat',
    apiKey: '',
    timeoutSeconds: 45,
    temperature: 0.1,
    maxTokens: 1200,
  };
}

export function defaultPersonalAiSettings(providers: WebAiProviderDto[]): PersonalAiSettings {
  const profile = defaultPersonalAiProfile(providers);
  return {
    enabled: profile.enabled,
    providerId: profile.providerId,
    protocol: profile.protocol,
    baseUrl: profile.baseUrl,
    model: profile.model,
    apiKey: profile.apiKey,
    timeoutSeconds: profile.timeoutSeconds,
    temperature: profile.temperature,
    maxTokens: profile.maxTokens,
    updatedAt: 0,
  };
}

function normalizeProfile(profile: Partial<PersonalAiProfile>, providers: WebAiProviderDto[], order: number): PersonalAiProfile {
  const fallback = defaultPersonalAiProfile(providers, order);
  const selected = providers.find((provider) => provider.id === profile.providerId);
  const protocol: PersonalAiProtocol = profile.protocol === 'anthropic' || profile.protocol === 'gemini'
    || selected?.protocol === 'anthropic' || selected?.protocol === 'gemini'
    ? (profile.protocol === 'anthropic' || profile.protocol === 'gemini'
      ? profile.protocol
      : (selected?.protocol || 'openai-compatible'))
    : 'openai-compatible';
  const model = String(profile.model || fallback.model).trim();
  if (!model || model.length > 160 || /[\r\n]/.test(model)) throw new Error('Invalid AI model name.');
  return {
    id: String(profile.id || fallback.id),
    name: String(profile.name || fallback.name).trim().slice(0, 64) || fallback.name,
    enabled: profile.enabled !== false,
    order,
    providerId: String(profile.providerId || fallback.providerId),
    protocol: selected?.protocol || protocol,
    baseUrl: validatePersonalAiBaseUrl(String(profile.baseUrl || fallback.baseUrl)),
    model,
    apiKey: String(profile.apiKey || '').trim(),
    timeoutSeconds: Math.max(5, Math.min(120, Number(profile.timeoutSeconds) || 45)),
    temperature: Math.max(0, Math.min(2, Number(profile.temperature) || 0)),
    maxTokens: Math.max(64, Math.min(8192, Number(profile.maxTokens) || 1200)),
  };
}

function migrateRawStore(raw: unknown, providers: WebAiProviderDto[]): PersonalAiStore {
  if (raw && typeof raw === 'object' && Array.isArray((raw as PersonalAiStore).profiles)) {
    const store = raw as PersonalAiStore;
    return {
      version: 2,
      preferredSource: store.preferredSource === 'browser' ? 'browser' : 'server',
      profiles: store.profiles.map((profile, index) => normalizeProfile(profile, providers, index)),
      updatedAt: Number(store.updatedAt) || 0,
    };
  }
  const legacy = (raw || {}) as Partial<PersonalAiSettings>;
  const profile = normalizeProfile({
    id: 'default',
    name: 'Default',
    enabled: legacy.enabled !== false,
    order: 0,
    providerId: legacy.providerId,
    protocol: legacy.protocol,
    baseUrl: legacy.baseUrl,
    model: legacy.model,
    apiKey: legacy.apiKey,
    timeoutSeconds: legacy.timeoutSeconds,
    temperature: legacy.temperature,
    maxTokens: legacy.maxTokens,
  }, providers, 0);
  return {
    version: 2,
    preferredSource: 'browser',
    profiles: [profile],
    updatedAt: Number(legacy.updatedAt) || 0,
  };
}

export function loadPersonalAiStore(
  providers: WebAiProviderDto[],
  storage: StorageLike | null = browserStorage(),
  scope = ''
): PersonalAiStore {
  if (!storage) {
    return {
      version: 2,
      preferredSource: loadPreferredAiSource(storage, scope, 'browser'),
      profiles: [defaultPersonalAiProfile(providers)],
      updatedAt: 0,
    };
  }
  try {
    const parsed = JSON.parse(storage.getItem(scopedStorageKey(scope)) || '{}');
    const store = migrateRawStore(parsed, providers);
    store.preferredSource = loadPreferredAiSource(storage, scope, store.preferredSource);
    return store;
  } catch {
    return {
      version: 2,
      preferredSource: 'browser',
      profiles: [defaultPersonalAiProfile(providers)],
      updatedAt: 0,
    };
  }
}

export function savePersonalAiStore(
  store: PersonalAiStore,
  providers: WebAiProviderDto[] = [],
  storage: StorageLike | null = browserStorage(),
  scope = ''
): PersonalAiStore {
  if (!storage) throw new Error('Browser local storage is unavailable.');
  const profiles = store.profiles.map((profile, index) => {
    const next = normalizeProfile(profile, providers, index);
    if (next.enabled && !next.apiKey) throw new Error('AI API key is required for enabled profiles.');
    if (next.apiKey.length > 8192) throw new Error('AI API key is too long.');
    return next;
  });
  const normalized: PersonalAiStore = {
    version: 2,
    preferredSource: store.preferredSource === 'server' ? 'server' : 'browser',
    profiles,
    updatedAt: Date.now(),
  };
  storage.setItem(scopedStorageKey(scope), JSON.stringify(normalized));
  savePreferredAiSource(normalized.preferredSource, storage, scope);
  return normalized;
}

export function loadPreferredAiSource(
  storage: StorageLike | null = browserStorage(),
  scope = '',
  fallback: AiKeySource = 'server'
): AiKeySource {
  if (!storage) return fallback;
  try {
    const value = storage.getItem(preferredStorageKey(scope));
    if (value === 'browser' || value === 'server') return value;
  } catch {
    // ignore
  }
  return fallback;
}

export function savePreferredAiSource(
  source: AiKeySource,
  storage: StorageLike | null = browserStorage(),
  scope = ''
): void {
  if (!storage) return;
  storage.setItem(preferredStorageKey(scope), source === 'browser' ? 'browser' : 'server');
}

export function resolveEffectiveAiSource(options: {
  serverEnabled: boolean;
  browserEnabled: boolean;
  preferred: AiKeySource;
  serverConfigured?: boolean;
  browserConfigured?: boolean;
}): AiKeySource | 'none' {
  const { serverEnabled, browserEnabled, preferred } = options;
  if (!serverEnabled && !browserEnabled) return 'none';
  if (serverEnabled && !browserEnabled) return 'server';
  if (!serverEnabled && browserEnabled) return 'browser';
  const primary = preferred === 'browser' ? 'browser' : 'server';
  const secondary = primary === 'browser' ? 'server' : 'browser';
  const primaryOk = primary === 'server'
    ? options.serverConfigured !== false
    : options.browserConfigured !== false;
  if (primaryOk) return primary;
  const secondaryOk = secondary === 'server'
    ? options.serverConfigured !== false
    : options.browserConfigured !== false;
  return secondaryOk ? secondary : primary;
}

export function loadPersonalAiSettings(
  providers: WebAiProviderDto[],
  storage: StorageLike | null = browserStorage(),
  scope = ''
): PersonalAiSettings {
  const store = loadPersonalAiStore(providers, storage, scope);
  const profile = store.profiles.find((item) => item.enabled && item.apiKey) || store.profiles[0];
  return {
    enabled: profile?.enabled ?? true,
    providerId: profile?.providerId || 'custom',
    protocol: profile?.protocol || 'openai-compatible',
    baseUrl: profile?.baseUrl || '',
    model: profile?.model || '',
    apiKey: profile?.apiKey || '',
    timeoutSeconds: profile?.timeoutSeconds || 45,
    temperature: profile?.temperature || 0.1,
    maxTokens: profile?.maxTokens || 1200,
    updatedAt: store.updatedAt,
  };
}

export function savePersonalAiSettings(
  settings: PersonalAiSettings,
  storage: StorageLike | null = browserStorage(),
  scope = '',
  providers: WebAiProviderDto[] = []
): PersonalAiSettings {
  const store = loadPersonalAiStore(providers, storage, scope);
  const profile = store.profiles[0] || defaultPersonalAiProfile(providers);
  store.profiles = [{
    ...profile,
    enabled: settings.enabled,
    providerId: settings.providerId,
    protocol: settings.protocol,
    baseUrl: settings.baseUrl,
    model: settings.model,
    apiKey: settings.apiKey,
    timeoutSeconds: settings.timeoutSeconds,
    temperature: settings.temperature,
    maxTokens: settings.maxTokens,
    order: 0,
  }];
  savePersonalAiStore(store, providers, storage, scope);
  return loadPersonalAiSettings(providers, storage, scope);
}

export function clearPersonalAiKey(
  providers: WebAiProviderDto[],
  storage: StorageLike | null = browserStorage(),
  scope = ''
): PersonalAiSettings {
  const store = loadPersonalAiStore(providers, storage, scope);
  const cleared: PersonalAiStore = {
    ...store,
    profiles: store.profiles.map((profile) => ({ ...profile, apiKey: '', enabled: false })),
    updatedAt: Date.now(),
  };
  if (storage) storage.setItem(scopedStorageKey(scope), JSON.stringify(cleared));
  return loadPersonalAiSettings(providers, storage, scope);
}

export function isPersonalAiConfigured(settings: PersonalAiSettings): boolean {
  return settings.enabled && !!settings.apiKey && !!settings.baseUrl && !!settings.model;
}

export function isPersonalAiStoreConfigured(store: PersonalAiStore): boolean {
  return store.profiles.some((profile) => profile.enabled && !!profile.apiKey && !!profile.baseUrl && !!profile.model);
}

export function enabledPersonalProfiles(store: PersonalAiStore): PersonalAiProfile[] {
  return [...store.profiles]
    .filter((profile) => profile.enabled && !!profile.apiKey && !!profile.baseUrl && !!profile.model)
    .sort((a, b) => a.order - b.order);
}

export function personalAiKeyHint(apiKey: string): string {
  return apiKey ? `••••${apiKey.slice(-Math.min(4, apiKey.length))}` : '';
}

export function validatePersonalAiBaseUrl(value: string): string {
  let url: URL;
  try {
    url = new URL(value.trim());
  } catch {
    throw new Error('Invalid AI API base URL.');
  }
  const loopback = url.hostname === 'localhost' || url.hostname === '127.0.0.1' || url.hostname === '::1';
  if (url.username || url.password || url.search || url.hash) {
    throw new Error('AI API base URL must not contain credentials, query, or fragment.');
  }
  if (url.protocol !== 'https:' && !(url.protocol === 'http:' && loopback)) {
    throw new Error('AI API base URL must use HTTPS; HTTP is allowed only for loopback.');
  }
  if (typeof window !== 'undefined' && url.origin === window.location.origin) {
    throw new Error('AI API base URL must not point to the WebAE server.');
  }
  return value.trim().replace(/\/+$/, '');
}

export function redactPersonalAiSecret(value: string, apiKey: string): string {
  return apiKey ? value.split(apiKey).join('[REDACTED]') : value;
}

function normalizeSettings(settings: PersonalAiSettings): PersonalAiSettings {
  const protocol: PersonalAiProtocol = settings.protocol === 'anthropic' || settings.protocol === 'gemini'
    ? settings.protocol
    : 'openai-compatible';
  const model = String(settings.model || '').trim();
  if (!model || model.length > 160 || /[\r\n]/.test(model)) throw new Error('Invalid AI model name.');
  return {
    ...settings,
    protocol,
    baseUrl: validatePersonalAiBaseUrl(String(settings.baseUrl || '')),
    model,
    apiKey: String(settings.apiKey || '').trim(),
    timeoutSeconds: Math.max(5, Math.min(120, Number(settings.timeoutSeconds) || 45)),
    temperature: Math.max(0, Math.min(2, Number(settings.temperature) || 0)),
    maxTokens: Math.max(64, Math.min(8192, Number(settings.maxTokens) || 1200)),
    updatedAt: Number(settings.updatedAt) || 0,
  };
}

function trimSlash(value: string): string {
  return value.replace(/\/+$/, '');
}

function appendPath(base: string, path: string): string {
  return `${trimSlash(base)}${path.startsWith('/') ? path : `/${path}`}`;
}

function openAiEndpoint(settings: PersonalAiProfile | PersonalAiSettings): string {
  const base = trimSlash(settings.baseUrl);
  const lower = base.toLowerCase();
  if (lower.endsWith('/chat/completions')) return base;
  if (settings.providerId === 'zhipu') return appendPath(base, '/v4/chat/completions');
  if (settings.providerId === 'volcengine') return appendPath(base, '/v3/chat/completions');
  if (lower.endsWith('/v1') || lower.endsWith('/v3') || lower.endsWith('/v4')) {
    return appendPath(base, '/chat/completions');
  }
  return appendPath(base, '/v1/chat/completions');
}

function safeMessages(messages: PersonalAiMessage[]): PersonalAiMessage[] {
  if (!messages.length) throw new Error('AI messages are empty.');
  return messages.slice(-20).map((message) => ({
    role: message.role === 'system' || message.role === 'assistant' ? message.role : 'user',
    content: String(message.content || '').slice(0, 32_000),
  }));
}

export function buildPersonalAiRequest(
  input: PersonalAiSettings | PersonalAiProfile,
  messages: PersonalAiMessage[]
): PersonalAiHttpRequest {
  const settings = 'name' in input
    ? input
    : { ...normalizeSettings(input as PersonalAiSettings), id: 'legacy', name: 'Default', order: 0 };
  if (!settings.apiKey || !settings.baseUrl || !settings.model) throw new Error('Personal AI is not configured.');
  const bounded = safeMessages(messages);
  const headers: Record<string, string> = {
    Accept: 'application/json',
    'Content-Type': 'application/json;charset=UTF-8',
  };

  if (settings.protocol === 'anthropic') {
    const system = bounded.filter((message) => message.role === 'system').map((message) => message.content).join('\n');
    headers['x-api-key'] = settings.apiKey;
    headers['anthropic-version'] = '2023-06-01';
    headers['anthropic-dangerous-direct-browser-access'] = 'true';
    return {
      endpoint: appendPath(settings.baseUrl, '/v1/messages'),
      headers,
      body: {
        model: settings.model,
        max_tokens: settings.maxTokens,
        temperature: settings.temperature,
        ...(system ? { system } : {}),
        messages: bounded.filter((message) => message.role !== 'system').map((message) => ({
          role: message.role === 'assistant' ? 'assistant' : 'user',
          content: message.content,
        })),
      },
    };
  }

  if (settings.protocol === 'gemini') {
    const system = bounded.filter((message) => message.role === 'system').map((message) => message.content).join('\n');
    headers['x-goog-api-key'] = settings.apiKey;
    return {
      endpoint: appendPath(settings.baseUrl, `/v1beta/models/${encodeURIComponent(settings.model)}:generateContent`),
      headers,
      body: {
        ...(system ? { systemInstruction: { parts: [{ text: system }] } } : {}),
        contents: bounded.filter((message) => message.role !== 'system').map((message) => ({
          role: message.role === 'assistant' ? 'model' : 'user',
          parts: [{ text: message.content }],
        })),
        generationConfig: {
          temperature: settings.temperature,
          maxOutputTokens: settings.maxTokens,
        },
      },
    };
  }

  headers.Authorization = `Bearer ${settings.apiKey}`;
  return {
    endpoint: openAiEndpoint(settings),
    headers,
    body: {
      model: settings.model,
      temperature: settings.temperature,
      max_tokens: settings.maxTokens,
      messages: bounded,
    },
  };
}

function responseText(protocol: PersonalAiProtocol, response: Record<string, any>): string {
  if (protocol === 'anthropic') {
    return Array.isArray(response.content)
      ? response.content.map((part: Record<string, unknown>) => typeof part?.text === 'string' ? part.text : '').join('')
      : '';
  }
  if (protocol === 'gemini') {
    const parts = response.candidates?.[0]?.content?.parts;
    return Array.isArray(parts)
      ? parts.map((part: Record<string, unknown>) => typeof part?.text === 'string' ? part.text : '').join('')
      : '';
  }
  return response.choices?.[0]?.message?.content || response.choices?.[0]?.message?.reasoning_content || '';
}

function safeProviderError(raw: string, apiKey: string, status: number): string {
  let message = `AI provider request failed (HTTP ${status}).`;
  try {
    const parsed = JSON.parse(raw) as Record<string, any>;
    message = parsed.error?.message || parsed.error || parsed.message || message;
  } catch {
    // Keep the bounded generic status message for non-JSON provider errors.
  }
  const safe = String(message).split(apiKey).join('[REDACTED]').replace(/[\r\n]/g, ' ').trim();
  return safe.slice(0, 300);
}

function isProviderSideFailure(error: unknown): boolean {
  const message = String((error as Error)?.message || error || '').toLowerCase();
  return /timeout|timed out|connection|unreachable|refused|http 401|http 403|http 429|http 5\d\d|quota|rate limit|insufficient|billing|balance|credit|exceeded|empty|invalid json/.test(message);
}

export async function completePersonalAi(
  settings: PersonalAiSettings | PersonalAiProfile,
  messages: PersonalAiMessage[],
  fetcher: typeof fetch = fetch
): Promise<string> {
  const request = buildPersonalAiRequest(settings, messages);
  const timeoutSeconds = 'timeoutSeconds' in settings ? settings.timeoutSeconds : 45;
  const apiKey = settings.apiKey;
  const protocol = settings.protocol;
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), timeoutSeconds * 1000);
  try {
    const response = await fetcher(request.endpoint, {
      method: 'POST',
      headers: request.headers,
      body: JSON.stringify(request.body),
      signal: controller.signal,
      credentials: 'omit',
      cache: 'no-store',
      referrerPolicy: 'no-referrer',
    });
    const raw = await response.text();
    if (raw.length > MAX_RESPONSE_CHARS) throw new Error('AI provider response exceeded the size limit.');
    if (!response.ok) throw new Error(safeProviderError(raw, apiKey, response.status));
    let parsed: Record<string, any>;
    try {
      parsed = JSON.parse(raw) as Record<string, any>;
    } catch {
      throw new Error('AI provider returned invalid JSON.');
    }
    const content = responseText(protocol, parsed).trim();
    if (!content) throw new Error('AI provider response content was empty.');
    return content;
  } finally {
    window.clearTimeout(timeout);
  }
}

export async function completePersonalAiWithFailover(
  store: PersonalAiStore,
  messages: PersonalAiMessage[],
  fetcher: typeof fetch = fetch
): Promise<{ content: string; profileId: string; apiKey: string }> {
  const profiles = enabledPersonalProfiles(store);
  if (!profiles.length) throw new Error('Personal AI is not configured.');
  let lastError: unknown;
  for (let index = 0; index < profiles.length; index += 1) {
    const profile = profiles[index];
    try {
      const content = await completePersonalAi(profile, messages, fetcher);
      return { content, profileId: profile.id, apiKey: profile.apiKey };
    } catch (error) {
      lastError = error;
      if (!isProviderSideFailure(error) || index + 1 >= profiles.length) throw error;
    }
  }
  throw lastError instanceof Error ? lastError : new Error('All personal AI profiles failed.');
}

function firstJsonObject(value: string): Record<string, unknown> | null {
  const start = value.indexOf('{');
  if (start < 0) return null;
  let depth = 0;
  let inString = false;
  let escaped = false;
  for (let index = start; index < value.length; index += 1) {
    const character = value[index];
    if (inString) {
      if (escaped) escaped = false;
      else if (character === '\\') escaped = true;
      else if (character === '"') inString = false;
      continue;
    }
    if (character === '"') inString = true;
    else if (character === '{') depth += 1;
    else if (character === '}') {
      depth -= 1;
      if (depth === 0) {
        try {
          return JSON.parse(value.slice(start, index + 1)) as Record<string, unknown>;
        } catch {
          return null;
        }
      }
    }
  }
  return null;
}

export function isChatOnlyAssistantPlan(value: string): boolean {
  const parsed = firstJsonObject(value);
  if (!parsed || !Array.isArray(parsed.tasks) || parsed.tasks.length !== 1) return false;
  const task = parsed.tasks[0] as Record<string, unknown> | null;
  return String(task?.type || '').toUpperCase() === 'CHAT';
}
