// Unified WebAE UI settings export/import bundle (v1).
// Covers all localStorage-backed preferences plus optional server-side data.

import { getApiClient } from '@/api/client';
import { QUEST_PREVIEW_MODE_KEY, QUEST_REFRESH_CD_MS } from '@/components/quest/questUtils';
import {
  getLocalDebugFlag,
  setLocalDebugFlag,
  type DebugFeature,
} from '@/utils/debugLog';
import { getActiveLocalPack } from '@/utils/localIconPack';
import {
  CPU_OVERVIEW_CONFIG_KEY,
  DASHBOARD_CONFIG_KEY,
  DEFAULT_CPU_OVERVIEW_SETTINGS,
  DEFAULT_DASHBOARD_SETTINGS,
  DEFAULT_POWER_SETTINGS,
  DEFAULT_STORAGE_OVERVIEW_SETTINGS,
  loadDashboardSettings,
  loadOverviewSettingsFromStorage,
  mergeOverviewSettings,
  migrateDashboardWidgets,
  POWER_CONFIG_KEY,
  PRESETS_STORAGE_KEY,
  STORAGE_OVERVIEW_CONFIG_KEY,
  type AppPreset,
  type CpuOverviewSettings,
  type DashboardSettings,
  type PowerSettings,
  type StorageOverviewSettings,
} from '@/utils/presets';
import {
  DEFAULT_QUEST_DISPLAY,
  mergeQuestDisplay,
  QUEST_DISPLAY_STORAGE_KEY,
  type QuestDisplaySettings,
} from '@/types/questDisplay';
import {
  DEFAULT_TOPOLOGY_DISPLAY,
  mergeTopologyDisplay,
  TOPOLOGY_DISPLAY_STORAGE_KEY,
  type TopologyDisplaySettings,
} from '@/types/topologyDisplay';
import type { EffectsLevel } from '@/theme/colors';
import type { WebAlertsConfigDto, WebFavoritesDto, OrderTemplate } from '@/types/dto';
import type { DisplayMode, SidebarMode } from '@/context/AppContext';
import type { Lang } from '@/i18n';
import type { NumberFormat } from '@/utils/format';
import type { ThemeColor } from '@/theme/colors';
import type { ThemeLayout } from '@/theme/layouts';

export const UI_SETTINGS_FORMAT = 'textech-webae-ui-settings' as const;
export const UI_SETTINGS_VERSION = 1 as const;
export const UI_INITIALIZED_KEY = 'webae_ui_initialized';

export const RECIPE_LAYOUT_KEY = 'webae-recipe-layout';
export const RECIPE_DISPLAY_MODE_KEY = 'webae-recipe-display-mode';
export const CHAT_SHOW_AVATARS_KEY = 'webae_chat_showAvatars';
export const CHAT_SHOW_PLAYER_INFO_KEY = 'webae_chat_showPlayerInfo';
export const CHAT_MODE_KEY = 'webae_chat_mode';
export const CHAT_PLAYERS_COLLAPSED_KEY = 'webae_chat_players_collapsed';
export const AUTO_REFRESH_KEY = 'webae_auto_refresh';
export const PAUSE_REFRESH_WHEN_HIDDEN_KEY = 'webae_pause_refresh_when_hidden';
export const PINNED_FLUIDS_PREFIX = 'webae-pinned-fluids-';

const DEBUG_FEATURES: DebugFeature[] = ['icons', 'chat', 'dashboard', 'synthesis', 'patterns'];

export type UiSettingsSection =
  | 'global'
  | 'dashboard'
  | 'storageOverview'
  | 'cpuOverview'
  | 'power'
  | 'topology'
  | 'quest'
  | 'recipe'
  | 'chat'
  | 'refresh'
  | 'debug'
  | 'pinnedFluids'
  | 'presets'
  | 'serverAlerts'
  | 'serverFavorites'
  | 'serverOrderTemplates';

export interface UiSettingsGlobal {
  themeColor: string;
  themeLayout: string;
  effectsLevel: EffectsLevel;
  lang: string;
  displayMode: DisplayMode;
  numberFormat: NumberFormat;
  iconPack: string;
  iconRenderMode: string;
  localIconPack: string;
  sidebarMode: SidebarMode;
  iconAutoSyncEnabled: boolean;
  iconWikiEnabled: boolean;
}

export interface UiSettingsPages {
  dashboard?: DashboardSettings;
  storageOverview?: StorageOverviewSettings;
  cpuOverview?: CpuOverviewSettings;
  power?: PowerSettings;
  topology?: TopologyDisplaySettings;
  quest?: QuestDisplaySettings & { previewMode?: boolean };
  recipe?: { layout: string; displayMode: string };
  chat?: {
    showAvatars: boolean;
    showPlayerInfo: boolean;
    chatMode: string;
    playersCollapsed: boolean;
  };
  refresh?: { autoRefresh: boolean; pauseRefreshWhenHidden: boolean };
  debug?: Partial<Record<DebugFeature, boolean>>;
  pinnedFluids?: Record<string, string[]>;
}

export interface WebUiSettingsBundle {
  format: typeof UI_SETTINGS_FORMAT;
  version: typeof UI_SETTINGS_VERSION;
  exportedAt: number;
  meta?: { name?: string; note?: string };
  client: {
    global: UiSettingsGlobal;
    pages: UiSettingsPages;
    presets?: AppPreset[];
  };
  server?: {
    alerts?: WebAlertsConfigDto;
    favorites?: WebFavoritesDto;
    orderTemplates?: OrderTemplate[];
  };
}

export interface CollectUiSettingsOptions {
  meta?: { name?: string; note?: string };
  includePresets?: boolean;
  includeServer?: boolean;
  includeAlerts?: boolean;
  global?: Partial<UiSettingsGlobal>;
}

export interface GlobalSettingsSetters {
  setThemeColor: (c: ThemeColor) => void;
  setThemeLayout: (l: ThemeLayout) => void;
  setEffectsLevel: (e: EffectsLevel) => void;
  setLang: (l: Lang) => void;
  setDisplayMode: (m: DisplayMode) => void;
  setNumberFormat: (f: NumberFormat) => void;
  setIconPack: (p: string) => void;
  setIconRenderMode: (m: string) => void;
  setLocalIconPack: (p: string) => void;
  setSidebarMode: (m: SidebarMode) => void;
  setIconAutoSyncEnabled: (v: boolean) => void;
  setIconWikiEnabled: (v: boolean) => void;
  setAutoRefresh: (v: boolean) => void;
  setPauseRefreshWhenHidden: (v: boolean) => void;
  setPresets?: (presets: AppPreset[]) => void;
}

export interface ApplyUiSettingsOptions {
  merge?: boolean;
  sections?: UiSettingsSection[];
  silent?: boolean;
  globalSetters?: GlobalSettingsSetters;
  markInitialized?: boolean;
  importServer?: {
    alerts?: boolean;
    favorites?: boolean;
    orderTemplates?: boolean;
    canEditAlerts?: boolean;
    tokenType?: string | null;
  };
}

export interface ParseUiSettingsResult {
  bundle: WebUiSettingsBundle;
  legacyPreset?: boolean;
  sections: UiSettingsSection[];
}

function readJson<T>(key: string): T | undefined {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return undefined;
    return JSON.parse(raw) as T;
  } catch {
    return undefined;
  }
}

function writeJson(key: string, value: unknown): void {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    /* ignore quota / private mode */
  }
}

function readBool(key: string, fallback: boolean): boolean {
  try {
    const raw = localStorage.getItem(key);
    if (raw === null) return fallback;
    return raw === 'true' || raw === '1';
  } catch {
    return fallback;
  }
}

function readString(key: string, fallback = ''): string {
  try {
    return localStorage.getItem(key) ?? fallback;
  } catch {
    return fallback;
  }
}

function collectPinnedFluids(): Record<string, string[]> {
  const out: Record<string, string[]> = {};
  try {
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (!key || !key.startsWith(PINNED_FLUIDS_PREFIX)) continue;
      const networkId = key.slice(PINNED_FLUIDS_PREFIX.length);
      const parsed = readJson<string[]>(key);
      if (Array.isArray(parsed) && parsed.length > 0) {
        out[networkId] = parsed.slice(0, 10);
      }
    }
  } catch {
    /* ignore */
  }
  return out;
}

function collectDebugFlags(): Partial<Record<DebugFeature, boolean>> | undefined {
  const out: Partial<Record<DebugFeature, boolean>> = {};
  for (const f of DEBUG_FEATURES) {
    const v = getLocalDebugFlag(f);
    if (v !== null) out[f] = v;
  }
  return Object.keys(out).length > 0 ? out : undefined;
}

/** Build global settings from localStorage + optional live context overrides. */
export function readGlobalSettingsFromStorage(overrides?: Partial<UiSettingsGlobal>): UiSettingsGlobal {
  return {
    themeColor: overrides?.themeColor ?? readString('webae_theme_color', 'dark'),
    themeLayout: overrides?.themeLayout ?? readString('webae_theme_layout', 'standard'),
    effectsLevel: (overrides?.effectsLevel ?? readString('webae_effects_level', '')) as EffectsLevel,
    lang: overrides?.lang ?? readString('webae_lang', ''),
    displayMode: (overrides?.displayMode ?? readString('webae_display_mode', 'split')) as DisplayMode,
    numberFormat: (overrides?.numberFormat ?? readString('webae_number_format', 'thousands')) as NumberFormat,
    iconPack: overrides?.iconPack ?? readString('webae_icon_pack', 'default'),
    iconRenderMode: overrides?.iconRenderMode ?? readString('webae_icon_render_mode', 'nei'),
    localIconPack: overrides?.localIconPack ?? getActiveLocalPack(),
    sidebarMode: (overrides?.sidebarMode ?? readString('webae_sidebar_mode', 'expanded')) as SidebarMode,
    iconAutoSyncEnabled: overrides?.iconAutoSyncEnabled ?? readBool('webae_icon_auto_sync', false),
    iconWikiEnabled: overrides?.iconWikiEnabled ?? readBool('webae_icon_wiki_enabled', true),
  };
}

/** Collect all client-side UI settings into a v1 bundle. */
export function collectUiSettingsBundle(opts: CollectUiSettingsOptions = {}): WebUiSettingsBundle {
  const dashboard = loadDashboardSettings();
  const storageOverview = loadOverviewSettingsFromStorage(
    STORAGE_OVERVIEW_CONFIG_KEY,
    DEFAULT_STORAGE_OVERVIEW_SETTINGS
  );
  const cpuOverview = loadOverviewSettingsFromStorage(
    CPU_OVERVIEW_CONFIG_KEY,
    DEFAULT_CPU_OVERVIEW_SETTINGS
  );
  const power = loadOverviewSettingsFromStorage(POWER_CONFIG_KEY, DEFAULT_POWER_SETTINGS);
  const topology = mergeTopologyDisplay(readJson<Partial<TopologyDisplaySettings>>(TOPOLOGY_DISPLAY_STORAGE_KEY));
  const questDisplay = mergeQuestDisplay(readJson<Partial<QuestDisplaySettings>>(QUEST_DISPLAY_STORAGE_KEY));
  const previewMode = readString(QUEST_PREVIEW_MODE_KEY) !== '0';

  const pinned = collectPinnedFluids();
  const pages: UiSettingsPages = {
    dashboard,
    storageOverview,
    cpuOverview,
    power,
    topology,
    quest: { ...questDisplay, previewMode },
    recipe: {
      layout: readString(RECIPE_LAYOUT_KEY, 'grid'),
      displayMode: readString(RECIPE_DISPLAY_MODE_KEY, 'compact'),
    },
    chat: {
      showAvatars: readBool(CHAT_SHOW_AVATARS_KEY, true),
      showPlayerInfo: readBool(CHAT_SHOW_PLAYER_INFO_KEY, true),
      chatMode: readString(CHAT_MODE_KEY, 'bubble'),
      playersCollapsed: readBool(CHAT_PLAYERS_COLLAPSED_KEY, false),
    },
    refresh: {
      autoRefresh: readBool(AUTO_REFRESH_KEY, true),
      pauseRefreshWhenHidden: readBool(PAUSE_REFRESH_WHEN_HIDDEN_KEY, true),
    },
    debug: collectDebugFlags(),
    pinnedFluids: Object.keys(pinned).length > 0 ? pinned : undefined,
  };

  const bundle: WebUiSettingsBundle = {
    format: UI_SETTINGS_FORMAT,
    version: UI_SETTINGS_VERSION,
    exportedAt: Date.now(),
    meta: opts.meta,
    client: {
      global: readGlobalSettingsFromStorage(opts.global),
      pages,
      presets: opts.includePresets ? readJson<AppPreset[]>(PRESETS_STORAGE_KEY) : undefined,
    },
  };

  return bundle;
}

/** Fetch optional server sections and attach to bundle (requires login). */
export async function attachServerSettingsToBundle(
  bundle: WebUiSettingsBundle,
  opts: { includeAlerts?: boolean } = {}
): Promise<WebUiSettingsBundle> {
  const next: WebUiSettingsBundle = { ...bundle, server: { ...bundle.server } };
  const client = getApiClient();

  const tasks: Promise<void>[] = [];

  if (opts.includeAlerts) {
    tasks.push(
      client
        .get<{ success: boolean; rules?: WebAlertsConfigDto; canEditRules?: boolean }>('/api/alerts')
        .then((r) => {
          if (r.rules) next.server = { ...next.server, alerts: r.rules };
        })
        .catch(() => {
          /* skip if unavailable */
        })
    );
  }

  tasks.push(
    client
      .get<{ success: boolean; favorites?: WebFavoritesDto }>('/api/favorites')
      .then((r) => {
        if (r.favorites) next.server = { ...next.server, favorites: r.favorites };
      })
      .catch(() => {
        /* skip */
      })
  );

  tasks.push(
    client
      .get<{ success: boolean; templates?: OrderTemplate[] }>('/api/order/templates')
      .then((r) => {
        if (r.templates) next.server = { ...next.server, orderTemplates: r.templates };
      })
      .catch(() => {
        /* skip */
      })
  );

  await Promise.all(tasks);
  return next;
}

function migrateLegacyPreset(raw: Record<string, unknown>): WebUiSettingsBundle | null {
  const settings = raw.settings as AppPreset['settings'] | undefined;
  if (!settings || typeof settings !== 'object') return null;
  const global: UiSettingsGlobal = {
    themeColor: String(settings.themeColor ?? 'dark'),
    themeLayout: String(settings.themeLayout ?? 'standard'),
    effectsLevel: (settings.effectsLevel ?? '') as EffectsLevel,
    lang: String(settings.lang ?? ''),
    displayMode: (settings.displayMode ?? 'split') as DisplayMode,
    numberFormat: (settings.numberFormat ?? 'thousands') as NumberFormat,
    iconPack: String(settings.iconPack ?? 'default'),
    iconRenderMode: String(settings.iconRenderMode ?? 'nei'),
    localIconPack: String(settings.localIconPack ?? ''),
    sidebarMode: (settings.sidebarMode ?? 'expanded') as SidebarMode,
    iconAutoSyncEnabled: readBool('webae_icon_auto_sync', false),
    iconWikiEnabled: readBool('webae_icon_wiki_enabled', true),
  };
  const pages: UiSettingsPages = {};
  if (settings.dashboard) pages.dashboard = settings.dashboard;
  return {
    format: UI_SETTINGS_FORMAT,
    version: UI_SETTINGS_VERSION,
    exportedAt: typeof raw.createdAt === 'number' ? raw.createdAt : Date.now(),
    meta: { name: typeof raw.name === 'string' ? raw.name : undefined },
    client: { global, pages },
  };
}

/** Parse and validate uploaded JSON; upgrades legacy AppPreset exports. */
export function parseUiSettingsBundle(raw: unknown): ParseUiSettingsResult {
  if (!raw || typeof raw !== 'object') {
    throw new Error('invalid_json');
  }
  const obj = raw as Record<string, unknown>;
  let legacyPreset = false;
  let bundle: WebUiSettingsBundle;

  if (obj.format === UI_SETTINGS_FORMAT && obj.version === UI_SETTINGS_VERSION) {
    bundle = obj as unknown as WebUiSettingsBundle;
  } else if (obj.settings) {
    const migrated = migrateLegacyPreset(obj);
    if (!migrated) throw new Error('invalid_preset');
    bundle = migrated;
    legacyPreset = true;
  } else {
    throw new Error('unknown_format');
  }

  if (!bundle.client?.global || !bundle.client?.pages) {
    throw new Error('missing_client');
  }

  return { bundle, legacyPreset, sections: listBundleSections(bundle) };
}

export function listBundleSections(bundle: WebUiSettingsBundle): UiSettingsSection[] {
  const sections: UiSettingsSection[] = [];
  if (bundle.client.global) sections.push('global');
  const p = bundle.client.pages;
  if (p.dashboard) sections.push('dashboard');
  if (p.storageOverview) sections.push('storageOverview');
  if (p.cpuOverview) sections.push('cpuOverview');
  if (p.power) sections.push('power');
  if (p.topology) sections.push('topology');
  if (p.quest) sections.push('quest');
  if (p.recipe) sections.push('recipe');
  if (p.chat) sections.push('chat');
  if (p.refresh) sections.push('refresh');
  if (p.debug) sections.push('debug');
  if (p.pinnedFluids && Object.keys(p.pinnedFluids).length > 0) sections.push('pinnedFluids');
  if (bundle.client.presets?.length) sections.push('presets');
  if (bundle.server?.alerts) sections.push('serverAlerts');
  if (bundle.server?.favorites) sections.push('serverFavorites');
  if (bundle.server?.orderTemplates?.length) sections.push('serverOrderTemplates');
  return sections;
}

function sectionEnabled(sections: UiSettingsSection[] | undefined, id: UiSettingsSection): boolean {
  return !sections || sections.includes(id);
}

function applyDashboardSettings(settings: DashboardSettings, merge: boolean): void {
  const next = merge
    ? {
        ...DEFAULT_DASHBOARD_SETTINGS,
        ...readJson<Partial<DashboardSettings>>(DASHBOARD_CONFIG_KEY),
        ...settings,
        widgets: migrateDashboardWidgets(settings.widgets ?? DEFAULT_DASHBOARD_SETTINGS.widgets),
      }
    : {
        ...DEFAULT_DASHBOARD_SETTINGS,
        ...settings,
        widgets: migrateDashboardWidgets(settings.widgets ?? DEFAULT_DASHBOARD_SETTINGS.widgets),
      };
  writeJson(DASHBOARD_CONFIG_KEY, next);
}

function applyOverviewSettings<T extends StorageOverviewSettings>(
  key: string,
  defaults: T,
  settings: T,
  merge: boolean
): void {
  const next = merge
    ? mergeOverviewSettings(defaults, {
        ...readJson<Partial<T>>(key),
        ...settings,
      })
    : mergeOverviewSettings(defaults, settings);
  writeJson(key, next);
}

function applyGlobalToStorage(global: UiSettingsGlobal): void {
  localStorage.setItem('webae_theme_color', global.themeColor);
  localStorage.setItem('webae_theme_layout', global.themeLayout);
  localStorage.setItem('webae_effects_level', global.effectsLevel || '');
  if (global.lang) localStorage.setItem('webae_lang', global.lang);
  localStorage.setItem('webae_display_mode', global.displayMode);
  localStorage.setItem('webae_number_format', global.numberFormat);
  localStorage.setItem('webae_icon_pack', global.iconPack);
  localStorage.setItem('webae_icon_render_mode', global.iconRenderMode);
  localStorage.setItem('webae_sidebar_mode', global.sidebarMode);
  localStorage.setItem('webae_icon_auto_sync', global.iconAutoSyncEnabled ? 'true' : 'false');
  localStorage.setItem('webae_icon_wiki_enabled', global.iconWikiEnabled ? 'true' : 'false');
  if (global.localIconPack) {
    localStorage.setItem('webae_local_icon_pack', global.localIconPack);
  } else {
    localStorage.removeItem('webae_local_icon_pack');
  }
}

function applyGlobalToReact(global: UiSettingsGlobal, setters: GlobalSettingsSetters): void {
  setters.setThemeColor(global.themeColor as ThemeColor);
  setters.setThemeLayout(global.themeLayout as ThemeLayout);
  setters.setEffectsLevel(global.effectsLevel);
  if (global.lang) setters.setLang(global.lang as Lang);
  setters.setDisplayMode(global.displayMode);
  setters.setNumberFormat(global.numberFormat);
  setters.setIconPack(global.iconPack);
  setters.setIconRenderMode(global.iconRenderMode);
  setters.setLocalIconPack(global.localIconPack);
  setters.setSidebarMode(global.sidebarMode);
  setters.setIconAutoSyncEnabled(global.iconAutoSyncEnabled);
  setters.setIconWikiEnabled(global.iconWikiEnabled);
}

/** Apply a settings bundle to localStorage (and optional React setters / server APIs). */
export async function applyUiSettingsBundle(
  bundle: WebUiSettingsBundle,
  opts: ApplyUiSettingsOptions = {}
): Promise<void> {
  const merge = opts.merge !== false;
  const sections = opts.sections;
  const { client } = bundle;
  const { global } = client;
  const pages = client.pages;

  if (sectionEnabled(sections, 'global') && global) {
    applyGlobalToStorage(global);
    if (opts.globalSetters) applyGlobalToReact(global, opts.globalSetters);
  }

  if (sectionEnabled(sections, 'dashboard') && pages.dashboard) {
    applyDashboardSettings(pages.dashboard, merge);
  }
  if (sectionEnabled(sections, 'storageOverview') && pages.storageOverview) {
    applyOverviewSettings(STORAGE_OVERVIEW_CONFIG_KEY, DEFAULT_STORAGE_OVERVIEW_SETTINGS, pages.storageOverview, merge);
  }
  if (sectionEnabled(sections, 'cpuOverview') && pages.cpuOverview) {
    applyOverviewSettings(CPU_OVERVIEW_CONFIG_KEY, DEFAULT_CPU_OVERVIEW_SETTINGS, pages.cpuOverview, merge);
  }
  if (sectionEnabled(sections, 'power') && pages.power) {
    applyOverviewSettings(POWER_CONFIG_KEY, DEFAULT_POWER_SETTINGS, pages.power, merge);
  }
  if (sectionEnabled(sections, 'topology') && pages.topology) {
    writeJson(TOPOLOGY_DISPLAY_STORAGE_KEY, mergeTopologyDisplay(pages.topology));
  }
  if (sectionEnabled(sections, 'quest') && pages.quest) {
    const { previewMode: pm, ...questDisplay } = pages.quest;
    writeJson(QUEST_DISPLAY_STORAGE_KEY, mergeQuestDisplay(questDisplay));
    if (pm !== undefined) {
      localStorage.setItem(QUEST_PREVIEW_MODE_KEY, pm ? '1' : '0');
    }
  }
  if (sectionEnabled(sections, 'recipe') && pages.recipe) {
    localStorage.setItem(RECIPE_LAYOUT_KEY, pages.recipe.layout);
    localStorage.setItem(RECIPE_DISPLAY_MODE_KEY, pages.recipe.displayMode);
  }
  if (sectionEnabled(sections, 'chat') && pages.chat) {
    localStorage.setItem(CHAT_SHOW_AVATARS_KEY, pages.chat.showAvatars ? 'true' : 'false');
    localStorage.setItem(CHAT_SHOW_PLAYER_INFO_KEY, pages.chat.showPlayerInfo ? 'true' : 'false');
    localStorage.setItem(CHAT_MODE_KEY, pages.chat.chatMode);
    localStorage.setItem(CHAT_PLAYERS_COLLAPSED_KEY, pages.chat.playersCollapsed ? 'true' : 'false');
  }
  if (sectionEnabled(sections, 'refresh') && pages.refresh) {
    localStorage.setItem(AUTO_REFRESH_KEY, pages.refresh.autoRefresh ? 'true' : 'false');
    localStorage.setItem(
      PAUSE_REFRESH_WHEN_HIDDEN_KEY,
      pages.refresh.pauseRefreshWhenHidden ? 'true' : 'false'
    );
    if (opts.globalSetters) {
      opts.globalSetters.setAutoRefresh(pages.refresh.autoRefresh);
      opts.globalSetters.setPauseRefreshWhenHidden(pages.refresh.pauseRefreshWhenHidden);
    }
  }
  if (sectionEnabled(sections, 'debug') && pages.debug) {
    for (const f of DEBUG_FEATURES) {
      if (pages.debug[f] !== undefined) setLocalDebugFlag(f, !!pages.debug[f]);
    }
  }
  if (sectionEnabled(sections, 'pinnedFluids') && pages.pinnedFluids) {
    for (const [networkId, fluids] of Object.entries(pages.pinnedFluids)) {
      writeJson(PINNED_FLUIDS_PREFIX + networkId, fluids);
    }
  }
  if (sectionEnabled(sections, 'presets') && client.presets && opts.globalSetters?.setPresets) {
    opts.globalSetters.setPresets(client.presets);
    writeJson(PRESETS_STORAGE_KEY, client.presets);
  } else if (sectionEnabled(sections, 'presets') && client.presets) {
    writeJson(PRESETS_STORAGE_KEY, client.presets);
  }

  const importServer = opts.importServer;
  if (importServer && bundle.server) {
    const clientApi = getApiClient();
    if (
      sectionEnabled(sections, 'serverAlerts') &&
      importServer.alerts &&
      importServer.canEditAlerts &&
      bundle.server.alerts
    ) {
      await clientApi.put('/api/alerts/rules', bundle.server.alerts);
    }
    if (
      sectionEnabled(sections, 'serverFavorites') &&
      importServer.favorites &&
      importServer.tokenType !== 'guest' &&
      bundle.server.favorites
    ) {
      await clientApi.put('/api/favorites', { favorites: bundle.server.favorites });
    }
    if (
      sectionEnabled(sections, 'serverOrderTemplates') &&
      importServer.orderTemplates &&
      importServer.tokenType !== 'guest' &&
      bundle.server.orderTemplates
    ) {
      await clientApi.put('/api/order/templates', { templates: bundle.server.orderTemplates });
    }
  }

  if (opts.markInitialized !== false) {
    localStorage.setItem(UI_INITIALIZED_KEY, '1');
  }
}

export function isUiInitialized(): boolean {
  return readString(UI_INITIALIZED_KEY) === '1';
}

export function markUiInitialized(): void {
  localStorage.setItem(UI_INITIALIZED_KEY, '1');
}

export function downloadUiSettingsBundle(bundle: WebUiSettingsBundle, filename?: string): void {
  const name =
    filename ||
    `webae-ui-settings-${bundle.meta?.name?.replace(/[^\w.-]+/g, '_') || Date.now()}.json`;
  const blob = new Blob([JSON.stringify(bundle, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = name.endsWith('.json') ? name : `${name}.json`;
  a.click();
  URL.revokeObjectURL(url);
}

export interface UiDefaultsResponse {
  success: boolean;
  defaults: WebUiSettingsBundle | null;
  source?: 'instance' | 'jar' | null;
}

/** Fetch pack/mod default UI settings from server. */
export async function fetchUiDefaults(): Promise<WebUiSettingsBundle | null> {
  try {
    const resp = await fetch('/api/ui-defaults', {
      headers: { 'Content-Type': 'application/json' },
    });
    if (!resp.ok) return null;
    const data = (await resp.json()) as UiDefaultsResponse;
    if (!data.success || !data.defaults) return null;
    const parsed = parseUiSettingsBundle(data.defaults);
    return parsed.bundle;
  } catch {
    return null;
  }
}

/** Apply bundled defaults on first launch when sentinel is unset. */
export function hasExistingUiStorage(): boolean {
  try {
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (!key || key === UI_INITIALIZED_KEY) continue;
      if (key.startsWith('webae_') || key.startsWith('webae-')) return true;
    }
  } catch {
    /* ignore */
  }
  return false;
}

/** Apply bundled defaults on first launch when sentinel is unset. */
export async function applyBundledDefaultsIfNeeded(
  globalSetters?: GlobalSettingsSetters
): Promise<boolean> {
  if (isUiInitialized() || hasExistingUiStorage()) {
    if (hasExistingUiStorage() && !isUiInitialized()) {
      markUiInitialized();
    }
    return false;
  }
  const defaults = await fetchUiDefaults();
  if (!defaults) return false;
  await applyUiSettingsBundle(defaults, {
    merge: false,
    silent: true,
    globalSetters,
    markInitialized: true,
  });
  return true;
}
