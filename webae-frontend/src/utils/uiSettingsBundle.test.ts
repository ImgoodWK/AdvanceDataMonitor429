import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  UI_INITIALIZED_KEY,
  UI_SETTINGS_FORMAT,
  applyUiSettingsBundle,
  collectUiSettingsBundle,
  hasExistingUiStorage,
  parseUiSettingsBundle,
  type WebUiSettingsBundle,
} from './uiSettingsBundle';
import { DASHBOARD_CONFIG_KEY, DEFAULT_DASHBOARD_SETTINGS } from './presets';
import { TOPOLOGY_DISPLAY_STORAGE_KEY } from '@/types/topologyDisplay';

const storage = new Map<string, string>();

beforeEach(() => {
  storage.clear();
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => {
      storage.set(key, value);
    },
    removeItem: (key: string) => {
      storage.delete(key);
    },
    key: (index: number) => Array.from(storage.keys())[index] ?? null,
    get length() {
      return storage.size;
    },
    clear: () => storage.clear(),
  });
});

function sampleBundle(): WebUiSettingsBundle {
  return {
    format: UI_SETTINGS_FORMAT,
    version: 1,
    exportedAt: 1_700_000_000_000,
    meta: { name: 'test-pack' },
    client: {
      global: {
        themeColor: 'midnight',
        themeLayout: 'compact',
        pageStyle: 'classic',
        effectsLevel: 'full',
        lang: 'en',
        displayMode: 'merged',
        numberFormat: 'short',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'collapsed',
        iconAutoSyncEnabled: false,
        iconWikiEnabled: false,
      },
      pages: {
        dashboard: {
          ...DEFAULT_DASHBOARD_SETTINGS,
          margin: 20,
          widgets: DEFAULT_DASHBOARD_SETTINGS.widgets.slice(0, 1),
        },
        recipe: { layout: 'list', displayMode: 'detailed' },
        refresh: { autoRefresh: false, pauseRefreshWhenHidden: false },
      },
    },
  };
}

describe('uiSettingsBundle', () => {
  it('collect → apply round-trip writes dashboard and global keys', async () => {
    storage.set('webae_theme_color', 'dark');
    storage.set(DASHBOARD_CONFIG_KEY, JSON.stringify({ ...DEFAULT_DASHBOARD_SETTINGS, margin: 8 }));

    const bundle = sampleBundle();
    await applyUiSettingsBundle(bundle, { merge: false, markInitialized: true });

    expect(storage.get('webae_theme_color')).toBe('midnight');
    expect(storage.get('webae_theme_layout')).toBe('compact');
    expect(storage.get('webae-recipe-layout')).toBe('list');
    expect(storage.get(UI_INITIALIZED_KEY)).toBe('1');

    const dash = JSON.parse(storage.get(DASHBOARD_CONFIG_KEY) || '{}') as { margin: number };
    expect(dash.margin).toBe(20);
  });

  it('upgrades legacy AppPreset JSON', () => {
    const legacy = {
      id: 'preset-1',
      name: 'Legacy',
      createdAt: 100,
      settings: {
        themeColor: 'dracula',
        themeLayout: 'topnav',
        lang: 'en',
        displayMode: 'split',
        numberFormat: 'full',
        iconPack: 'default',
        iconRenderMode: 'nei',
        localIconPack: '',
        sidebarMode: 'expanded',
        effectsLevel: 'subtle',
        dashboard: null,
      },
    };
    const { bundle, legacyPreset } = parseUiSettingsBundle(legacy);
    expect(legacyPreset).toBe(true);
    expect(bundle.client.global.themeColor).toBe('dracula');
    expect(bundle.format).toBe(UI_SETTINGS_FORMAT);
  });

  it('merge mode keeps unspecified overview fields from defaults', async () => {
    const bundle = sampleBundle();
    bundle.client.pages.dashboard = { ...DEFAULT_DASHBOARD_SETTINGS, margin: 99 };
    await applyUiSettingsBundle(bundle, { merge: true, sections: ['dashboard'] });
    const dash = JSON.parse(storage.get(DASHBOARD_CONFIG_KEY) || '{}') as { margin: number; widgetGap: number };
    expect(dash.margin).toBe(99);
    expect(dash.widgetGap).toBe(DEFAULT_DASHBOARD_SETTINGS.widgetGap);
  });

  it('preserves explicit empty dashboard widgets on apply and collect', async () => {
    const bundle = sampleBundle();
    bundle.client.pages.dashboard = { ...DEFAULT_DASHBOARD_SETTINGS, widgets: [] };
    await applyUiSettingsBundle(bundle, { merge: false, sections: ['dashboard'] });
    const dash = JSON.parse(storage.get(DASHBOARD_CONFIG_KEY) || '{}') as { widgets: unknown[] };
    expect(dash.widgets).toEqual([]);

    const collected = collectUiSettingsBundle();
    expect(collected.client.pages.dashboard?.widgets).toEqual([]);
  });

  it('hasExistingUiStorage detects prior webae keys', () => {
    expect(hasExistingUiStorage()).toBe(false);
    storage.set(TOPOLOGY_DISPLAY_STORAGE_KEY, '{}');
    expect(hasExistingUiStorage()).toBe(true);
  });

  it('collectUiSettingsBundle includes format metadata', () => {
    const bundle = collectUiSettingsBundle({ meta: { name: 'x' } });
    expect(bundle.format).toBe(UI_SETTINGS_FORMAT);
    expect(bundle.version).toBe(1);
    expect(bundle.meta?.name).toBe('x');
  });
});
