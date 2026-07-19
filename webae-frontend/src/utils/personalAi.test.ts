import { describe, expect, it } from 'vitest';
import type { WebAiProviderDto } from '@/types/dto';
import {
  buildPersonalAiRequest,
  defaultPersonalAiSettings,
  loadPersonalAiSettings,
  loadPersonalAiStore,
  redactPersonalAiSecret,
  resolveEffectiveAiSource,
  savePersonalAiSettings,
  savePersonalAiStore,
  validatePersonalAiBaseUrl,
} from './personalAi';

const providers: WebAiProviderDto[] = [{
  id: 'deepseek',
  displayName: 'DeepSeek',
  defaultBaseUrl: 'https://api.deepseek.com',
  defaultModel: 'deepseek-chat',
  protocol: 'openai-compatible',
  models: ['deepseek-chat'],
}];

function memoryStorage(initial: Record<string, string> = {}) {
  const data = { ...initial };
  return {
    getItem(key: string) {
      return Object.prototype.hasOwnProperty.call(data, key) ? data[key] : null;
    },
    setItem(key: string, value: string) {
      data[key] = value;
    },
  };
}

describe('personalAi', () => {
  it('stores a single legacy profile in scoped localStorage', () => {
    const storage = memoryStorage();
    const saved = savePersonalAiSettings({
      ...defaultPersonalAiSettings(providers),
      apiKey: 'sk-personal-only',
    }, storage, '', providers);
    expect(saved.apiKey).toBe('sk-personal-only');
    expect(loadPersonalAiSettings(providers, storage).apiKey).toBe('sk-personal-only');
  });

  it('keeps actor-scoped stores separate', () => {
    const storage = memoryStorage();
    savePersonalAiSettings({
      ...defaultPersonalAiSettings(providers),
      apiKey: 'sk-alice',
    }, storage, 'alice-uuid', providers);
    savePersonalAiSettings({
      ...defaultPersonalAiSettings(providers),
      apiKey: 'sk-bob',
    }, storage, 'bob-uuid', providers);
    expect(loadPersonalAiSettings(providers, storage, 'alice-uuid').apiKey).toBe('sk-alice');
    expect(loadPersonalAiSettings(providers, storage, 'bob-uuid').apiKey).toBe('sk-bob');
  });

  it('migrates legacy single settings into a profile list', () => {
    const storage = memoryStorage();
    savePersonalAiSettings({
      ...defaultPersonalAiSettings(providers),
      apiKey: 'sk-legacy',
      model: 'deepseek-chat',
    }, storage, '', providers);
    const store = loadPersonalAiStore(providers, storage);
    expect(store.profiles).toHaveLength(1);
    expect(store.profiles[0].apiKey).toBe('sk-legacy');
  });

  it('saves multiple enabled profiles in failover order', () => {
    const storage = memoryStorage();
    const store = savePersonalAiStore({
      version: 2,
      preferredSource: 'browser',
      updatedAt: 0,
      profiles: [
        { ...defaultPersonalAiSettings(providers), id: 'a', name: 'A', order: 0, enabled: true, apiKey: 'sk-a' },
        { ...defaultPersonalAiSettings(providers), id: 'b', name: 'B', order: 1, enabled: true, apiKey: 'sk-b' },
      ],
    }, providers, storage);
    expect(store.profiles.map((profile) => profile.id)).toEqual(['a', 'b']);
  });

  it('builds an OpenAI-compatible request without credentials mode', () => {
    const request = buildPersonalAiRequest({
      ...defaultPersonalAiSettings(providers),
      apiKey: 'sk-test',
    }, [{ role: 'user', content: 'ping' }]);
    expect(request.endpoint).toContain('/chat/completions');
    expect(request.headers.Authorization).toBe('Bearer sk-test');
  });

  it('rejects insecure or credentialed base URLs', () => {
    expect(() => validatePersonalAiBaseUrl('http://api.example.com/v1')).toThrow();
    expect(() => validatePersonalAiBaseUrl('https://user:secret@api.example.com/v1')).toThrow();
    expect(validatePersonalAiBaseUrl('http://127.0.0.1:11434/v1/')).toBe('http://127.0.0.1:11434/v1');
  });

  it('redacts reflected secrets', () => {
    expect(redactPersonalAiSecret('result sk-personal-only end', 'sk-personal-only'))
      .toBe('result [REDACTED] end');
  });

  it('resolves effective source from dual flags and preference', () => {
    expect(resolveEffectiveAiSource({
      serverEnabled: true,
      browserEnabled: true,
      preferred: 'browser',
      serverConfigured: true,
      browserConfigured: true,
    })).toBe('browser');
    expect(resolveEffectiveAiSource({
      serverEnabled: true,
      browserEnabled: false,
      preferred: 'browser',
    })).toBe('server');
    expect(resolveEffectiveAiSource({
      serverEnabled: false,
      browserEnabled: false,
      preferred: 'server',
    })).toBe('none');
    expect(resolveEffectiveAiSource({
      serverEnabled: true,
      browserEnabled: true,
      preferred: 'server',
      serverConfigured: false,
      browserConfigured: true,
    })).toBe('browser');
  });
});
