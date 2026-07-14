import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { message, notification } from 'antd';
import { initApiClient, getApiClient, updateApiClientOptions } from '@/api/client';
import {
  useLocalStorage,
  useLocalStorageString,
} from '@/hooks/useLocalStorage';
import { usePageVisibility } from '@/hooks/usePageVisibility';
import { useVisibilityAwarePolling } from '@/hooks/useVisibilityAwarePolling';
import {
  CONNECTION_CHECK_INTERVAL_MS,
  CONNECTION_CHECK_PAGES,
} from '@/constants/pagePolling';
import { setServerDebugFlags, debugLog } from '@/utils/debugLog';
import { I18nProvider, type Lang } from '@/i18n';
import { zh as zhDict } from '@/i18n/zh';
import { en as enDict } from '@/i18n/en';
import { applyCssVars } from '@/theme/antdTheme';
import { COLOR_SCHEMES, type ThemeColor, type EffectsLevel } from '@/theme/colors';
import { LAYOUT_PRESETS, type ThemeLayout } from '@/theme/layouts';
import { formatNumber, type NumberFormat } from '@/utils/format';
import { bumpIconVersion, iconLookupIds } from '@/utils/icon';
import {
  getActiveLocalPack,
  listLocalIconPacks,
  setActiveLocalPack,
  type LocalIconPackMeta,
} from '@/utils/localIconPack';
import {
  builtinPresets,
  PRESETS_STORAGE_KEY,
  type AppPreset,
} from '@/utils/presets';
import {
  applyBundledDefaultsIfNeeded,
  applyUiSettingsBundle,
  attachServerSettingsToBundle,
  collectUiSettingsBundle,
  downloadUiSettingsBundle,
  fetchUiDefaults,
  markUiInitialized,
  parseUiSettingsBundle,
  type GlobalSettingsSetters,
  type UiSettingsSection,
} from '@/utils/uiSettingsBundle';
import { parseUrlNavigation, syncUrlNavigation } from '@/utils/urlNavigation';
import {
  filterHealthyNetworkIds,
  findFirstHealthyNetworkId,
} from '@/utils/networkHealth';
import type {
  AdminCapabilities,
  AdminElevateResponse,
  AdminMeResponse,
  ConfigResponse,
  IconPackInfo,
  IconPacksResponse,
  NetworkInfo,
  NetworksResponse,
  ServerConfig,
} from '@/types/dto';

/** One-time: previous default was auto-sync on; force off after icon perf change. */
const ICON_AUTO_SYNC_MIGRATE_KEY = 'webae_icon_auto_sync_migrated_default_off';
try {
  if (typeof localStorage !== 'undefined' && !localStorage.getItem(ICON_AUTO_SYNC_MIGRATE_KEY)) {
    localStorage.setItem('webae_icon_auto_sync', JSON.stringify(false));
    localStorage.setItem(ICON_AUTO_SYNC_MIGRATE_KEY, '1');
  }
} catch {
  /* ignore */
}

export type SidebarMode = 'expanded' | 'collapsed' | 'hidden';
export type DisplayMode = 'split' | 'merged';
export type PageId =
  | 'dashboard'
  | 'storage'
  | 'fluids'
  | 'essentia'
  | 'cpu'
  | 'power'
  | 'topology'
  | 'gtmachines'
  | 'recipes'
  | 'pattern'
  | 'order'
  | 'chat'
  | 'linkscanner'
  | 'monitorbindings'
  | 'planner'
  | 'quests'
  | 'assistant'
  | 'alertshistory'
  | 'diagnostics'
  | 'admin'
  | 'settings';

export interface OrderNavigationState {
  tab: 'query' | 'patterns';
  view?: 'byProduct' | 'byPattern';
  search: string;
}

export interface PageSearchPrefill {
  page: PageId;
  query: string;
  networkId?: number;
}

interface LoginResponse {
  status: string;
  token?: string;
  playerUuid?: string;
  ownerUuid?: string;
  ownerName?: string;
  actorUuid?: string;
  actorName?: string;
  tokenType?: string;
  code?: string;
}

interface AppContextValue {
  // Auth
  token: string;
  setToken: (t: string) => void;
  isLoggedIn: boolean;
  /** @deprecated use ownerUuid — kept for backward compatibility */
  playerUuid: string | null;
  ownerUuid: string | null;
  ownerName: string | null;
  actorUuid: string | null;
  actorName: string | null;
  tokenType: string | null;
  authSessionLabel: string | null;
  login: (token: string) => Promise<boolean>;
  exchangeLoginCode: (code: string) => Promise<boolean>;
  logout: () => void;
  authError: string | null;
  networksError: string | null;

  // Admin
  adminToken: string;
  isAdmin: boolean;
  isOnlineOp: boolean;
  adminCapabilities: AdminCapabilities | null;
  elevateAdmin: (code: string, label?: string) => Promise<boolean>;
  revokeAdmin: () => Promise<boolean>;
  checkAdminStatus: () => Promise<void>;

  // Server config
  serverConfig: ServerConfig | null;

  // Networks
  networks: NetworkInfo[];
  selectedNetworks: number[];
  setSelectedNetworks: (ids: number[]) => void;

  // i18n
  lang: Lang;
  setLang: (l: Lang) => void;

  // Theme
  themeColor: ThemeColor;
  setThemeColor: (c: ThemeColor) => void;
  themeLayout: ThemeLayout;
  setThemeLayout: (l: ThemeLayout) => void;
  // Effects intensity (gates glassmorphism/animation via data-effects-level)
  effectsLevel: EffectsLevel;
  setEffectsLevel: (e: EffectsLevel) => void;

  // Icons
  iconPack: string;
  setIconPack: (p: string) => void;
  iconRenderMode: string;
  setIconRenderMode: (m: string) => void;
  iconPacks: IconPackInfo[];
  localIconPack: string;
  setLocalIconPack: (p: string) => void;
  localIconPacks: LocalIconPackMeta[];
  refreshLocalIconPacks: () => Promise<void>;
  iconCacheEnabled: boolean;
  iconAutoSyncEnabled: boolean;
  setIconAutoSyncEnabled: (v: boolean) => void;
  // Number format
  numberFormat: NumberFormat;
  setNumberFormat: (f: NumberFormat) => void;

  // Display mode
  displayMode: DisplayMode;
  setDisplayMode: (m: DisplayMode) => void;

  // Icons (continued)
  iconUploadEnabled: boolean;
  iconPackEnabled: boolean;
  failedIcons: Record<string, boolean>;
  markIconFailed: (id: string) => void;
  refreshIconPacks: () => Promise<void>;

  // Sidebar
  sidebarMode: SidebarMode;
  setSidebarMode: (m: SidebarMode) => void;

  // Navigation
  activePage: PageId;
  setActivePage: (p: PageId) => void;
  orderNavigation: OrderNavigationState | null;
  setOrderNavigation: (nav: OrderNavigationState | null) => void;
  setPageSearchPrefill: (prefill: PageSearchPrefill | null) => void;
  consumePageSearchPrefill: (page: PageId) => PageSearchPrefill | null;

  // Icon click behavior
  iconWikiEnabled: boolean;
  setIconWikiEnabled: (v: boolean) => void;

  // Connection
  online: boolean;
  checkConnection: () => Promise<void>;

  /** Per-page last successful fetch (for stale-data banner on page switch). */
  pageFetchTimes: Partial<Record<PageId, { at: number; pollMs: number }>>;
  reportPageFetch: (page: PageId, pollMs: number, at?: number) => void;

  // Refresh
  autoRefresh: boolean;
  setAutoRefresh: (v: boolean) => void;
  pauseRefreshWhenHidden: boolean;
  setPauseRefreshWhenHidden: (v: boolean) => void;
  /** True when auto-refresh polling is paused because the tab is hidden. */
  refreshPaused: boolean;
  refreshCountdown: number;
  refreshIntervalMs: number;
  triggerRefresh: () => void;
  refreshTick: number;
  // Wall-clock timestamp (ms) of the last refresh trigger — used by Settings to
  // show data freshness. null until the first refresh fires.
  lastUpdateTime: number | null;

  // Auto login
  autoLogin: boolean;
  setAutoLogin: (v: boolean) => void;

  // Presets
  presets: AppPreset[];
  savePreset: (name: string) => void;
  applyPreset: (id: string) => void;
  deletePreset: (id: string) => void;
  renamePreset: (id: string, name: string) => void;
  overwritePreset: (id: string) => void;
  exportPreset: (id: string) => void;
  importPreset: (file: File) => Promise<void>;

  // Full UI settings backup / restore
  exportUiSettingsBundle: (opts: {
    name?: string;
    note?: string;
    includePresets?: boolean;
    includeServer?: boolean;
    includeAlerts?: boolean;
  }) => Promise<void>;
  importUiSettingsBundle: (
    file: File,
    opts: {
      merge?: boolean;
      sections?: UiSettingsSection[];
      importServer?: {
        alerts?: boolean;
        favorites?: boolean;
        orderTemplates?: boolean;
        canEditAlerts?: boolean;
      };
    }
  ) => Promise<boolean>;
  restorePackUiDefaults: () => Promise<boolean>;

  // Notification helper
  notify: (msg: string, type?: 'success' | 'error' | 'info' | 'warning') => void;
  fmtNum: (v: number | undefined | null) => string;
}

const AppContext = createContext<AppContextValue | null>(null);

export function AppProvider({ children }: { children: ReactNode }) {
  const [urlBoot] = useState(() => parseUrlNavigation());

  // ---- Auth state ----
  const [token, setTokenState] = useLocalStorageString('webae_token', '');
  const [autoLogin, setAutoLogin] = useLocalStorage<boolean>('webae_autologin', false);
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [playerUuid, setPlayerUuid] = useState<string | null>(null);
  const [ownerUuid, setOwnerUuid] = useState<string | null>(null);
  const [ownerName, setOwnerName] = useState<string | null>(null);
  const [actorUuid, setActorUuid] = useState<string | null>(null);
  const [actorName, setActorName] = useState<string | null>(null);
  const [tokenType, setTokenType] = useState<string | null>(null);
  const [authError, setAuthError] = useState<string | null>(null);
  const [networksError, setNetworksError] = useState<string | null>(null);

  // ---- Admin state ----
  const [adminToken, setAdminTokenState] = useLocalStorageString('webae_admin_token', '');
  const [isAdmin, setIsAdmin] = useState(false);
  const [isOnlineOp, setIsOnlineOp] = useState(false);
  const [adminCapabilities, setAdminCapabilities] = useState<AdminCapabilities | null>(null);
  const adminTokenRef = useRef(adminToken);

  // ---- Server config ----
  const [serverConfig, setServerConfig] = useState<ServerConfig | null>(null);

  // ---- Networks ----
  const [networks, setNetworks] = useState<NetworkInfo[]>([]);
  const [selectedNetworks, setSelectedNetworksState] = useState<number[]>(urlBoot.networks ?? []);

  // ---- i18n ----
  const [lang, setLangState] = useLocalStorageString('webae_lang', '') as [
    Lang,
    (l: Lang) => void
  ];
  // Initialize lang from browser if not saved
  useEffect(() => {
    if (!lang) {
      const browserLang = (navigator.language || '').startsWith('zh') ? 'zh' : 'en';
      setLangState(browserLang);
    } else {
      document.documentElement.setAttribute('lang', lang);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  const setLang = useCallback(
    (l: Lang) => {
      setLangState(l);
      document.documentElement.setAttribute('lang', l);
    },
    [setLangState]
  );

  // ---- Theme ----
  const [themeColor, setThemeColorState] = useLocalStorageString(
    'webae_theme_color',
    'dark'
  ) as [ThemeColor, (c: ThemeColor) => void];
  const [themeLayout, setThemeLayoutState] = useLocalStorageString(
    'webae_theme_layout',
    'standard'
  ) as [ThemeLayout, (l: ThemeLayout) => void];
  const [effectsLevel, setEffectsLevelState] = useLocalStorageString(
    'webae_effects_level',
    ''
  ) as [EffectsLevel, (e: EffectsLevel) => void];
  // Resolve the effective effects level: explicit user choice, else the
  // current theme's default (so switching themes without a saved preference
  // picks a sensible intensity).
  const resolvedEffectsLevel: EffectsLevel =
    effectsLevel || COLOR_SCHEMES[themeColor || 'dark'].effectsLevel;

  const setThemeColor = useCallback(
    (c: ThemeColor) => {
      setThemeColorState(c);
      const level = effectsLevel || COLOR_SCHEMES[c].effectsLevel;
      applyCssVars(c, themeLayout, level);
    },
    [setThemeColorState, themeLayout, effectsLevel]
  );
  const setThemeLayout = useCallback(
    (l: ThemeLayout) => {
      setThemeLayoutState(l);
      applyCssVars(themeColor, l, resolvedEffectsLevel);
    },
    [setThemeLayoutState, themeColor, resolvedEffectsLevel]
  );
  const setEffectsLevel = useCallback(
    (e: EffectsLevel) => {
      setEffectsLevelState(e);
      applyCssVars(themeColor, themeLayout, e);
    },
    [setEffectsLevelState, themeColor, themeLayout]
  );

  // ---- Number format ----
  const [numberFormat, setNumberFormat] = useLocalStorageString(
    'webae_number_format',
    'thousands'
  ) as [NumberFormat, (f: NumberFormat) => void];

  // ---- Display mode ----
  const [displayMode, setDisplayMode] = useLocalStorageString(
    'webae_display_mode',
    'split'
  ) as [DisplayMode, (m: DisplayMode) => void];

  // ---- Icons ----
  const [iconPack, setIconPackState] = useLocalStorageString('webae_icon_pack', 'default');
  const [iconRenderMode, setIconRenderModeState] = useLocalStorageString('webae_icon_render_mode', 'nei');
  const [iconAutoSyncEnabled, setIconAutoSyncEnabled] = useLocalStorage<boolean>('webae_icon_auto_sync', false);
  const [iconPacks, setIconPacks] = useState<IconPackInfo[]>([]);
  const [localIconPack, setLocalIconPackState] = useState(getActiveLocalPack);
  const [localIconPacks, setLocalIconPacks] = useState<LocalIconPackMeta[]>([]);
  const [failedIcons, setFailedIcons] = useState<Record<string, boolean>>({});

  const iconCacheEnabled = !!serverConfig?.iconCacheEnabled;
  const iconUploadEnabled = !!serverConfig?.iconUploadEnabled;
  const iconPackEnabled = !!serverConfig?.iconPackEnabled;

  const setIconPack = useCallback(
    (p: string) => {
      setIconPackState(p);
      bumpIconVersion();
      setFailedIcons({});
    },
    [setIconPackState]
  );

  const setIconRenderMode = useCallback(
    (m: string) => {
      setIconRenderModeState(m || 'nei');
      bumpIconVersion();
      setFailedIcons({});
    },
    [setIconRenderModeState]
  );

  const setLocalIconPack = useCallback((p: string) => {
    setLocalIconPackState(p);
    setActiveLocalPack(p);
    bumpIconVersion();
    setFailedIcons({});
  }, []);

  const refreshLocalIconPacks = useCallback(async () => {
    try {
      const packs = await listLocalIconPacks();
      setLocalIconPacks(packs);
    } catch {
      /* ignore */
    }
  }, []);

  const markIconFailed = useCallback((id: string) => {
    setFailedIcons((prev) => {
      const next = { ...prev };
      let changed = false;
      for (const candidate of iconLookupIds(undefined, id)) {
        if (!next[candidate]) {
          next[candidate] = true;
          changed = true;
        }
      }
      return changed ? next : prev;
    });
  }, []);

  const refreshIconPacks = useCallback(async () => {
    if (!tokenRef.current) return;
    try {
      const data = await getApiClient().get<IconPacksResponse>('/api/icon/packs');
      if (data.success && data.packs) {
        setIconPacks(data.packs);
        bumpIconVersion();
        setFailedIcons({});
        const names = data.packs.map((p) => p.packName);
        const current = iconPackRef.current;
        const def = data.defaultPack;
        if (def && names.indexOf(current) < 0 && names.indexOf(def) >= 0) {
          setIconPackState(def);
          bumpIconVersion();
          setFailedIcons({});
        } else if (names.length > 0 && names.indexOf(current) < 0) {
          setIconPackState(names[0]);
          bumpIconVersion();
          setFailedIcons({});
        }
      }
    } catch {
      /* ignore */
    }
  }, [setIconPackState]);

  // ---- Sidebar ----
  const [sidebarMode, setSidebarMode] = useLocalStorageString(
    'webae_sidebar_mode',
    'expanded'
  ) as [SidebarMode, (m: SidebarMode) => void];

  // ---- Navigation ----
  const [activePage, setActivePage] = useState<PageId>(urlBoot.page ?? 'dashboard');
  const [orderNavigation, setOrderNavigation] = useState<OrderNavigationState | null>(null);
  const pageSearchPrefillRef = useRef<PageSearchPrefill | null>(null);
  const setPageSearchPrefill = useCallback((prefill: PageSearchPrefill | null) => {
    pageSearchPrefillRef.current = prefill;
  }, []);
  const consumePageSearchPrefill = useCallback((page: PageId) => {
    const prefill = pageSearchPrefillRef.current;
    if (prefill && prefill.page === page) {
      pageSearchPrefillRef.current = null;
      return prefill;
    }
    return null;
  }, []);
  const [iconWikiEnabled, setIconWikiEnabled] = useLocalStorage<boolean>('webae_icon_wiki_enabled', true);

  // URL deep linking: ?page=order&network=0 or ?networks=0,1
  useEffect(() => {
    syncUrlNavigation(activePage, selectedNetworks);
  }, [activePage, selectedNetworks]);

  // ---- Connection ----
  const [online, setOnline] = useState(false);

  // ---- Refresh ----
  const [autoRefresh, setAutoRefresh] = useLocalStorage<boolean>('webae_auto_refresh', true);
  const [pauseRefreshWhenHidden, setPauseRefreshWhenHidden] = useLocalStorage<boolean>(
    'webae_pause_refresh_when_hidden',
    true
  );
  const isPageVisible = usePageVisibility();
  const refreshPaused = pauseRefreshWhenHidden && !isPageVisible;
  const [refreshCountdown, setRefreshCountdown] = useState(0);
  const [refreshTick, setRefreshTick] = useState(0);
  const [lastUpdateTime, setLastUpdateTime] = useState<number | null>(null);
  const [pageFetchTimes, setPageFetchTimes] = useState<
    Partial<Record<PageId, { at: number; pollMs: number }>>
  >({});

  const reportPageFetch = useCallback((page: PageId, pollMs: number, at?: number) => {
    const ts = at ?? Date.now();
    setPageFetchTimes((prev) => ({ ...prev, [page]: { at: ts, pollMs } }));
  }, []);
  const refreshIntervalMs = serverConfig?.refreshIntervalMs ?? 1000;

  // ---- Presets ----
  const [presets, setPresets] = useLocalStorage<AppPreset[]>(PRESETS_STORAGE_KEY, []);

  // ---- API client init (tokenRef avoids stale closure; silent re-login on 401) ----
  const tokenRef = useRef(token);
  const iconPackRef = useRef(iconPack);
  const selectedNetworksRef = useRef(selectedNetworks);
  const authFailureHandling = useRef(false);

  useEffect(() => {
    tokenRef.current = token;
  }, [token]);
  useEffect(() => {
    selectedNetworksRef.current = selectedNetworks;
  }, [selectedNetworks]);
  useEffect(() => {
    iconPackRef.current = iconPack;
  }, [iconPack]);

  const applyLoginResponse = useCallback((data: LoginResponse) => {
    const resolvedOwner = data.ownerUuid || data.playerUuid || null;
    setOwnerUuid(resolvedOwner);
    setPlayerUuid(resolvedOwner);
    setOwnerName(data.ownerName || null);
    setActorUuid(data.actorUuid || resolvedOwner);
    setActorName(data.actorName || null);
    setTokenType(data.tokenType || 'owner');
    setIsLoggedIn(true);
    setAuthError(null);
    setOnline(true);
  }, []);

  const authSessionLabel = useMemo(() => {
    if (!isLoggedIn) return null;
    const owner = ownerName || ownerUuid || '?';
    if (tokenType === 'guest') {
      const actor = actorName || actorUuid || '?';
      return `${actor} → ${owner}`;
    }
    return owner;
  }, [isLoggedIn, tokenType, ownerName, ownerUuid, actorName, actorUuid]);

  const handleAuthFailure = useCallback(
    async (code: string) => {
      if (authFailureHandling.current) return;
      authFailureHandling.current = true;
      try {
        // Account ban: never try silent /api/auth/login recovery
        if (code === 'webae_disabled') {
          setTokenState('');
          tokenRef.current = '';
          setAuthError(code);
          setIsLoggedIn(false);
          return;
        }
        const tok = tokenRef.current;
        if (tok) {
          try {
            const resp = await fetch('/api/auth/login', {
              headers: { Authorization: 'Bearer ' + tok },
            });
            if (resp.ok) {
              const data = (await resp.json()) as LoginResponse;
              if (data.status === 'ok') {
                applyLoginResponse(data);
                return;
              }
              if (data.status === 'error' && (data as { code?: string }).code === 'webae_disabled') {
                setTokenState('');
                tokenRef.current = '';
                setAuthError('webae_disabled');
                setIsLoggedIn(false);
                return;
              }
            } else if (resp.status === 401) {
              try {
                const err = (await resp.json()) as { code?: string; error?: string };
                if (err.code === 'webae_disabled' || err.error === 'webae_disabled') {
                  setTokenState('');
                  tokenRef.current = '';
                  setAuthError('webae_disabled');
                  setIsLoggedIn(false);
                  return;
                }
              } catch {
                /* ignore */
              }
            }
          } catch {
            /* silent retry failed */
          }
        }
        setTokenState('');
        tokenRef.current = '';
        setAuthError(code);
        setIsLoggedIn(false);
      } finally {
        authFailureHandling.current = false;
      }
    },
    [applyLoginResponse, setTokenState]
  );

  const setToken = useCallback(
    (t: string) => {
      setTokenState(t);
      tokenRef.current = t;
    },
    [setTokenState]
  );

  // ---- Notification helper (moved before apiClient init so admin helpers can use it) ----
  const [msgApi, msgHolder] = message.useMessage();
  const notify = useCallback(
    (msg: string, type: 'success' | 'error' | 'info' | 'warning' = 'info') => {
      msgApi[type](msg);
    },
    [msgApi]
  );

  // ---- Admin helpers (must precede apiClient init useEffect) ----
  useEffect(() => {
    adminTokenRef.current = adminToken;
  }, [adminToken]);

  const clearAdminState = useCallback(() => {
    setIsAdmin(false);
    setIsOnlineOp(false);
    setAdminCapabilities(null);
    adminTokenRef.current = '';
    setAdminTokenState('');
  }, [setAdminTokenState]);

  const handleAdminRequired = useCallback(() => {
    clearAdminState();
    const adminDict = (lang || 'en') === 'zh' ? zhDict : enDict;
    const hint = (adminDict as Record<string, string>).adminRequiredHint
      ?? 'This operation requires admin privileges. Redeem an elevation key in Settings.';
    message.warning(hint);
  }, [clearAdminState, lang]);

  // ---- API client init (tokenRef avoids stale closure; silent re-login on 401) ----
  const apiInitRef = useRef(false);
  useEffect(() => {
    if (apiInitRef.current) {
      updateApiClientOptions({
        getToken: () => tokenRef.current,
        getAdminToken: () => adminTokenRef.current,
        onAuthFailure: handleAuthFailure,
        onAdminRequired: handleAdminRequired,
      });
      return;
    }
    apiInitRef.current = true;
    initApiClient({
      getToken: () => tokenRef.current,
      getAdminToken: () => adminTokenRef.current,
      onAuthFailure: handleAuthFailure,
      onAdminRequired: handleAdminRequired,
    });
    }, [handleAuthFailure, handleAdminRequired]);

  const setSelectedNetworks = useCallback(
    (ids: number[]) => {
      const filtered = filterHealthyNetworkIds(ids, networks);
      if (filtered.length < ids.length) {
        const dict = (lang || 'en') === 'zh' ? zhDict : enDict;
        notify(dict.networkUnavailableSelect ?? enDict.networkUnavailableSelect, 'warning');
      }
      setSelectedNetworksState(filtered);
    },
    [networks, lang, notify]
  );

  const fmtNum = useCallback(
    (v: number | undefined | null) => formatNumber(v, numberFormat),
    [numberFormat]
  );

  // ---- Apply CSS vars on mount and when theme/effects change ----
  useEffect(() => {
    applyCssVars(themeColor, themeLayout, resolvedEffectsLevel);
  }, [themeColor, themeLayout, resolvedEffectsLevel]);

  // ---- Login ----
  const login = useCallback(
    async (tok: string): Promise<boolean> => {
      if (!tok.trim()) {
        setAuthError('empty_token');
        return false;
      }
      tokenRef.current = tok;
      setTokenState(tok);
      updateApiClientOptions({
        getToken: () => tokenRef.current,
        getAdminToken: () => adminTokenRef.current,
        onAuthFailure: handleAuthFailure,
        onAdminRequired: handleAdminRequired,
      });
      try {
        const resp = await getApiClient().get<LoginResponse>('/api/auth/login');
        if (resp.status === 'ok') {
          applyLoginResponse(resp);
          return true;
        }
        setAuthError((resp as { code?: string }).code || 'auth_failed');
        return false;
      } catch (e) {
        const code = (e as { code?: string }).code || (e as Error).message || 'auth_failed';
        setAuthError(code);
        if (code === 'webae_disabled') {
          setTokenState('');
          tokenRef.current = '';
        }
        setIsLoggedIn(false);
        setOnline(false);
        return false;
      }
    },
    [setTokenState, handleAuthFailure, applyLoginResponse]
  );

  const exchangeLoginCode = useCallback(
    async (code: string): Promise<boolean> => {
      const trimmed = code.trim();
      if (!/^\d{6}$/.test(trimmed)) {
        setAuthError('invalid_code');
        return false;
      }
      setAuthError(null);
      try {
        const resp = await fetch('/api/auth/exchange', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ code: trimmed }),
        });
        const data = (await resp.json()) as LoginResponse;
        if (!resp.ok || data.status !== 'ok' || !data.token) {
          setAuthError(data.code || 'auth_failed');
          return false;
        }
        return login(data.token);
      } catch (e) {
        setAuthError((e as Error).message || 'auth_failed');
        return false;
      }
    },
    [login]
  );

  const logout = useCallback(() => {
    setIsLoggedIn(false);
    setPlayerUuid(null);
    setOwnerUuid(null);
    setOwnerName(null);
    setActorUuid(null);
    setActorName(null);
    setTokenType(null);
    setTokenState('');
    setOnline(false);
    setNetworksError(null);
    clearAdminState();
  }, [setTokenState, clearAdminState]);

  // ---- Admin functions ----
  const setAdminToken = useCallback(
    (t: string) => {
      setAdminTokenState(t);
      adminTokenRef.current = t;
    },
    [setAdminTokenState]
  );

  const checkAdminStatus = useCallback(async () => {
    if (!tokenRef.current) return;
    try {
      const data = await getApiClient().get<AdminMeResponse>('/api/auth/admin/me');
      if (data.status === 'ok') {
        setIsAdmin(data.isAdmin);
        setIsOnlineOp(data.isOnlineOp);
        setAdminCapabilities(data.capabilities);
      }
    } catch {
      setIsAdmin(false);
      setIsOnlineOp(false);
      setAdminCapabilities(null);
    }
  }, []);

  const elevateAdmin = useCallback(
    async (code: string, label?: string): Promise<boolean> => {
      const adminDict = (lang || 'en') === 'zh' ? zhDict : enDict;
      const d = adminDict as Record<string, string>;
      if (!tokenRef.current || tokenType === 'guest') {
        notify(d.adminElevateGuestDenied ?? 'Guest tokens cannot request admin elevation.', 'error');
        return false;
      }
      try {
        const body: Record<string, string> = { code };
        if (label) body.label = label;
        const data = await getApiClient().post<AdminElevateResponse>('/api/auth/admin/elevate', body);
        if (data.status === 'ok' && data.adminToken) {
          setAdminToken(data.adminToken);
          await checkAdminStatus();
          notify(d.adminElevateSuccess ?? 'Admin elevation successful.', 'success');
          return true;
        }
        notify(data.message || d.adminElevateFailed || 'Elevation failed.', 'error');
        return false;
      } catch (e) {
        const err = e as { code?: string; message?: string; status?: number };
        if (err.code === 'admin_elevate_denied') {
          notify(d.adminElevateInvalidCode ?? 'Invalid elevation key.', 'error');
        } else if (err.status === 429 || err.code === 'rate_limited') {
          notify(d.adminElevateRateLimited ?? 'Too many attempts.', 'warning');
        } else {
          notify(err.message || d.adminElevateFailed || 'Elevation failed.', 'error');
        }
        return false;
      }
    },
    [tokenType, lang, notify, setAdminToken, checkAdminStatus]
  );

  const revokeAdmin = useCallback(async (): Promise<boolean> => {
    if (!adminTokenRef.current) return false;
    try {
      const data = await getApiClient().post<{ status: string; revoked?: boolean }>('/api/auth/admin/revoke-self', {});
      if (data.status === 'ok' && data.revoked) {
        clearAdminState();
        const adminDict = (lang || 'en') === 'zh' ? zhDict : enDict;
        notify((adminDict as Record<string, string>).adminRevokeSuccess ?? 'Admin elevation revoked.', 'success');
        return true;
      }
    } catch {
      /* ignore */
    }
    return false;
  }, [lang, notify, clearAdminState]);

  // ---- Fetch server config ----
  const fetchServerConfig = useCallback(async () => {
    if (!token) return;
    try {
      const data = await getApiClient().get<ConfigResponse>('/api/config');
      if (data.success && data.config) {
        setServerConfig(data.config);
        if (data.config.debugFlags) {
          setServerDebugFlags(data.config.debugFlags);
        }
        debugLog(
          'dashboard',
          'debug',
          'frontend server config loaded: iconCacheEnabled={} iconPack={}',
          data.config.iconCacheEnabled,
          iconPackRef.current
        );
      }
    } catch {
      /* ignore */
    }
  }, [token]);

  // ---- Fetch networks ----
  const fetchNetworks = useCallback(async () => {
    if (!token) return;
    try {
      const data = await getApiClient().get<NetworksResponse>('/api/networks');
      if (data.success && data.networks) {
        setNetworks(data.networks);
        setNetworksError(null);
        const currentSelected = selectedNetworksRef.current;
        if (currentSelected.length === 0 && data.networks.length > 0) {
          const firstHealthy = findFirstHealthyNetworkId(data.networks);
          if (firstHealthy != null) {
            setSelectedNetworksState([firstHealthy]);
          }
        } else {
          const filtered = filterHealthyNetworkIds(currentSelected, data.networks);
          if (filtered.length !== currentSelected.length) {
            setSelectedNetworksState(filtered);
            const dict = (lang || 'en') === 'zh' ? zhDict : enDict;
            notify(
              dict.networkUnavailableDeselected ?? enDict.networkUnavailableDeselected,
              'warning'
            );
          }
        }
      } else {
        const msg = 'networksFetchFailed';
        setNetworksError(msg);
        notify(msg, 'error');
      }
    } catch {
      const msg = 'networksFetchFailed';
      setNetworksError(msg);
      notify(msg, 'error');
    }
  }, [token, lang, notify]);

  // ---- Check connection (raw fetch — avoid triggering logout on heartbeat 401) ----
  const checkConnection = useCallback(async () => {
    const tok = tokenRef.current;
    if (!tok) {
      setOnline(false);
      return;
    }
    try {
      const resp = await fetch('/api/config', {
        headers: { Authorization: 'Bearer ' + tok },
      });
      setOnline(resp.ok);
    } catch {
      setOnline(false);
    }
  }, []);

  // ---- Trigger refresh (signals all pages to re-fetch) ----
  const triggerRefresh = useCallback(() => {
    setRefreshTick((t) => t + 1);
    setLastUpdateTime(Date.now());
  }, []);

  // ---- Auto-login on mount ----
  useEffect(() => {
    if (token && autoLogin && !isLoggedIn) {
      login(token);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ---- On login: fetch config, networks, icon packs, admin status ----
  useEffect(() => {
    if (isLoggedIn && token) {
      fetchServerConfig();
      fetchNetworks();
      refreshIconPacks();
      refreshLocalIconPacks();
      checkAdminStatus();
    }
  }, [isLoggedIn, token, fetchServerConfig, fetchNetworks, refreshIconPacks, refreshLocalIconPacks, checkAdminStatus]);

  // ---- Periodic connection check (only on pages that show live connection status) ----
  const connectionCheckActive = CONNECTION_CHECK_PAGES.includes(activePage);
  useVisibilityAwarePolling(
    checkConnection,
    token && connectionCheckActive ? CONNECTION_CHECK_INTERVAL_MS : null,
    pauseRefreshWhenHidden
  );

  // ---- Auto refresh countdown + tick ----
  const prevPageVisibleRef = useRef(isPageVisible);
  useEffect(() => {
    if (!autoRefresh || !isLoggedIn) {
      setRefreshCountdown(0);
      return;
    }
    const intervalMs = refreshIntervalMs;
    const seconds = Math.max(1, Math.round(intervalMs / 1000));
    if (refreshPaused) {
      return;
    }
    setRefreshCountdown(seconds);
    const refreshId = setInterval(() => {
      setRefreshTick((t) => t + 1);
      setLastUpdateTime(Date.now());
      setRefreshCountdown(seconds);
    }, intervalMs);
    const countdownId = setInterval(() => {
      setRefreshCountdown((c) => (c > 0 ? c - 1 : 0));
    }, 1000);
    return () => {
      clearInterval(refreshId);
      clearInterval(countdownId);
    };
  }, [autoRefresh, isLoggedIn, refreshIntervalMs, refreshPaused]);

  // Immediate refresh when tab becomes visible again
  useEffect(() => {
    if (
      pauseRefreshWhenHidden &&
      !prevPageVisibleRef.current &&
      isPageVisible &&
      autoRefresh &&
      isLoggedIn
    ) {
      setRefreshTick((t) => t + 1);
      setLastUpdateTime(Date.now());
      const seconds = Math.max(1, Math.round(refreshIntervalMs / 1000));
      setRefreshCountdown(seconds);
    }
    prevPageVisibleRef.current = isPageVisible;
  }, [isPageVisible, pauseRefreshWhenHidden, autoRefresh, isLoggedIn, refreshIntervalMs]);

  // ---- Initialize built-in presets on first launch ----
  useEffect(() => {
    if (presets.length === 0) {
      const dict = lang === 'zh' ? zhDict : enDict;
      const builtins = builtinPresets((k: string) => dict[k] || k);
      setPresets(builtins);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const globalSettingsSetters = useMemo<GlobalSettingsSetters>(
    () => ({
      setThemeColor,
      setThemeLayout,
      setEffectsLevel,
      setLang,
      setDisplayMode,
      setNumberFormat,
      setIconPack,
      setIconRenderMode,
      setLocalIconPack,
      setSidebarMode,
      setIconAutoSyncEnabled,
      setIconWikiEnabled,
      setAutoRefresh,
      setPauseRefreshWhenHidden,
      setPresets,
    }),
    [
      setThemeColor,
      setThemeLayout,
      setEffectsLevel,
      setLang,
      setDisplayMode,
      setNumberFormat,
      setIconPack,
      setIconRenderMode,
      setLocalIconPack,
      setSidebarMode,
      setIconAutoSyncEnabled,
      setIconWikiEnabled,
      setAutoRefresh,
      setPauseRefreshWhenHidden,
      setPresets,
    ]
  );

  // Apply pack/mod ui-defaults.json on first visit (before login).
  useEffect(() => {
    void applyBundledDefaultsIfNeeded(globalSettingsSetters);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ---- Preset operations ----
  const getCurrentSettings = useCallback((): AppPreset['settings'] => {
    const dashRaw = localStorage.getItem('webae_dashboard_config');
    let dashboard: AppPreset['settings']['dashboard'] = null;
    if (dashRaw) {
      try {
        dashboard = JSON.parse(dashRaw);
      } catch {
        /* ignore */
      }
    }
    return {
      themeColor,
      themeLayout,
      lang,
      displayMode,
      numberFormat,
      iconPack,
      iconRenderMode: iconRenderMode || 'nei',
      localIconPack,
      sidebarMode,
      effectsLevel: resolvedEffectsLevel,
      dashboard,
    };
  }, [themeColor, themeLayout, lang, displayMode, numberFormat, iconPack, iconRenderMode, localIconPack, sidebarMode, resolvedEffectsLevel]);

  const savePreset = useCallback(
    (name: string) => {
      const preset: AppPreset = {
        id: 'preset-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8),
        name,
        createdAt: Date.now(),
        settings: getCurrentSettings(),
      };
      setPresets((prev) => [...prev, preset]);
      notify('presetSaved', 'success');
    },
    [getCurrentSettings, setPresets, notify]
  );

  const applyPreset = useCallback(
    (id: string) => {
      const preset = presets.find((p) => p.id === id);
      if (!preset) return;
      const s = preset.settings;
      setThemeColorState(s.themeColor as ThemeColor);
      setThemeLayoutState(s.themeLayout as ThemeLayout);
      setLangState(s.lang as Lang);
      setDisplayMode(s.displayMode as DisplayMode);
      setNumberFormat(s.numberFormat as NumberFormat);
      setIconPackState(s.iconPack);
      if (s.iconRenderMode) setIconRenderModeState(s.iconRenderMode);
      if (s.localIconPack !== undefined) {
        setLocalIconPackState(s.localIconPack);
        setActiveLocalPack(s.localIconPack);
      }
      setSidebarMode(s.sidebarMode);
      const presetLevel: EffectsLevel =
        s.effectsLevel || COLOR_SCHEMES[s.themeColor as ThemeColor].effectsLevel;
      setEffectsLevelState(presetLevel);
      if (s.dashboard) {
        try {
          localStorage.setItem('webae_dashboard_config', JSON.stringify(s.dashboard));
        } catch {
          /* ignore */
        }
      }
      applyCssVars(s.themeColor as ThemeColor, s.themeLayout as ThemeLayout, presetLevel);
      notify('presetApplied', 'success');
    },
    [presets, setThemeColorState, setThemeLayoutState, setLangState, setDisplayMode, setNumberFormat, setIconPackState, setIconRenderModeState, setSidebarMode, setEffectsLevelState, notify]
  );

  const deletePreset = useCallback(
    (id: string) => {
      setPresets((prev) => prev.filter((p) => p.id !== id));
      notify('presetDeleted', 'info');
    },
    [setPresets, notify]
  );

  const renamePreset = useCallback(
    (id: string, name: string) => {
      setPresets((prev) => prev.map((p) => (p.id === id ? { ...p, name } : p)));
    },
    [setPresets]
  );

  const overwritePreset = useCallback(
    (id: string) => {
      setPresets((prev) =>
        prev.map((p) => (p.id === id ? { ...p, settings: getCurrentSettings() } : p))
      );
      notify('presetSaved', 'success');
    },
    [setPresets, getCurrentSettings, notify]
  );

  const exportPreset = useCallback(
    (id: string) => {
      const preset = presets.find((p) => p.id === id);
      if (!preset) return;
      const blob = new Blob([JSON.stringify(preset, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `webae-preset-${preset.name}.json`;
      a.click();
      URL.revokeObjectURL(url);
      notify('presetExported', 'success');
    },
    [presets, notify]
  );

  const importPreset = useCallback(
    async (file: File) => {
      try {
        const text = await file.text();
        const parsed = JSON.parse(text) as AppPreset;
        if (!parsed.id || !parsed.name || !parsed.settings) {
          notify('presetImportFailed', 'error');
          return;
        }
        // Give it a new id to avoid conflicts
        parsed.id = 'imported-' + Date.now();
        setPresets((prev) => [...prev, parsed]);
        notify('presetImported', 'success');
      } catch {
        notify('presetImportFailed', 'error');
      }
    },
    [setPresets, notify]
  );

  const exportUiSettingsBundleFn = useCallback(
    async (opts: {
      name?: string;
      note?: string;
      includePresets?: boolean;
      includeServer?: boolean;
      includeAlerts?: boolean;
    }) => {
      let bundle = collectUiSettingsBundle({
        meta: { name: opts.name, note: opts.note },
        includePresets: opts.includePresets,
        global: {
          themeColor,
          themeLayout,
          effectsLevel: resolvedEffectsLevel,
          lang: lang || 'en',
          displayMode: (displayMode || 'split') as DisplayMode,
          numberFormat: (numberFormat || 'thousands') as NumberFormat,
          iconPack,
          iconRenderMode: iconRenderMode || 'nei',
          localIconPack,
          sidebarMode: (sidebarMode || 'expanded') as SidebarMode,
          iconAutoSyncEnabled,
          iconWikiEnabled,
        },
      });
      if (opts.includeServer && isLoggedIn) {
        bundle = await attachServerSettingsToBundle(bundle, {
          includeAlerts: opts.includeAlerts,
        });
      }
      downloadUiSettingsBundle(bundle);
      notify('uiBundleExported', 'success');
    },
    [
      themeColor,
      themeLayout,
      resolvedEffectsLevel,
      lang,
      displayMode,
      numberFormat,
      iconPack,
      iconRenderMode,
      localIconPack,
      sidebarMode,
      iconAutoSyncEnabled,
      iconWikiEnabled,
      isLoggedIn,
      notify,
    ]
  );

  const importUiSettingsBundleFn = useCallback(
    async (
      file: File,
      opts: {
        merge?: boolean;
        sections?: UiSettingsSection[];
        importServer?: {
          alerts?: boolean;
          favorites?: boolean;
          orderTemplates?: boolean;
          canEditAlerts?: boolean;
        };
      }
    ): Promise<boolean> => {
      try {
        const text = await file.text();
        const { bundle, sections } = parseUiSettingsBundle(JSON.parse(text));
        await applyUiSettingsBundle(bundle, {
          merge: opts.merge,
          sections: opts.sections ?? sections,
          globalSetters: globalSettingsSetters,
          importServer: opts.importServer
            ? {
                alerts: opts.importServer.alerts,
                favorites: opts.importServer.favorites,
                orderTemplates: opts.importServer.orderTemplates,
                canEditAlerts: !!opts.importServer.canEditAlerts,
                tokenType,
              }
            : undefined,
        });
        notify('uiBundleImported', 'success');
        return true;
      } catch {
        notify('uiBundleImportFailed', 'error');
        return false;
      }
    },
    [globalSettingsSetters, notify, tokenType]
  );

  const restorePackUiDefaults = useCallback(async (): Promise<boolean> => {
    try {
      const defaults = await fetchUiDefaults();
      if (!defaults) {
        notify('uiBundleNoDefaults', 'warning');
        return false;
      }
      await applyUiSettingsBundle(defaults, {
        merge: false,
        globalSetters: globalSettingsSetters,
        markInitialized: true,
      });
      notify('uiBundleDefaultsRestored', 'success');
      return true;
    } catch {
      notify('uiBundleImportFailed', 'error');
      return false;
    }
  }, [globalSettingsSetters, notify]);

  const value: AppContextValue = {
    token,
    setToken,
    isLoggedIn,
    playerUuid,
    ownerUuid,
    ownerName,
    actorUuid,
    actorName,
    tokenType,
    authSessionLabel,
    login,
    exchangeLoginCode,
    logout,
    authError,
    networksError,
    adminToken,
    isAdmin,
    isOnlineOp,
    adminCapabilities,
    elevateAdmin,
    revokeAdmin,
    checkAdminStatus,
    serverConfig,
    networks,
    selectedNetworks,
    setSelectedNetworks,
    lang: (lang || 'en') as Lang,
    setLang,
    themeColor: (themeColor || 'dark') as ThemeColor,
    setThemeColor,
    themeLayout: (themeLayout || 'standard') as ThemeLayout,
    setThemeLayout,
    effectsLevel: resolvedEffectsLevel,
    setEffectsLevel,
    numberFormat: (numberFormat || 'thousands') as NumberFormat,
    setNumberFormat,
    displayMode: (displayMode || 'split') as DisplayMode,
    setDisplayMode,
    iconPack,
    setIconPack,
    iconRenderMode: iconRenderMode || 'nei',
    setIconRenderMode,
    iconPacks,
    localIconPack,
    setLocalIconPack,
    localIconPacks,
    refreshLocalIconPacks,
    iconCacheEnabled,
    iconAutoSyncEnabled,
    setIconAutoSyncEnabled,
    iconUploadEnabled,
    iconPackEnabled,
    failedIcons,
    markIconFailed,
    refreshIconPacks,
    sidebarMode: (sidebarMode || 'expanded') as SidebarMode,
    setSidebarMode,
    activePage,
    setActivePage,
    orderNavigation,
    setOrderNavigation,
    setPageSearchPrefill,
    consumePageSearchPrefill,
    iconWikiEnabled,
    setIconWikiEnabled,
    online,
    checkConnection,
    pageFetchTimes,
    reportPageFetch,
    autoRefresh,
    setAutoRefresh,
    pauseRefreshWhenHidden,
    setPauseRefreshWhenHidden,
    refreshPaused,
    refreshCountdown,
    refreshIntervalMs,
    triggerRefresh,
    refreshTick,
    lastUpdateTime,
    autoLogin,
    setAutoLogin,
    presets,
    savePreset,
    applyPreset,
    deletePreset,
    renamePreset,
    overwritePreset,
    exportPreset,
    importPreset,
    exportUiSettingsBundle: exportUiSettingsBundleFn,
    importUiSettingsBundle: importUiSettingsBundleFn,
    restorePackUiDefaults,
    notify,
    fmtNum,
  };

  return (
    <AppContext.Provider value={value}>
      <I18nProvider lang={(lang || 'en') as Lang}>
        {msgHolder}
        {children}
      </I18nProvider>
    </AppContext.Provider>
  );
}

export function useAppContext() {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useAppContext must be used within AppProvider');
  return ctx;
}

// Re-export useI18n for convenience
export { useI18n } from '@/i18n';
