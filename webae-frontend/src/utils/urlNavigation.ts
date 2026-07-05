import type { PageId } from '@/context/AppContext';

const VALID_PAGES: PageId[] = [
  'dashboard',
  'storage',
  'essentia',
  'cpu',
  'power',
  'topology',
  'gtmachines',
  'recipes',
  'pattern',
  'order',
  'chat',
  'linkscanner',
  'monitorbindings',
  'planner',
  'assistant',
  'alertshistory',
  'settings',
];

export interface UrlNavigationState {
  page?: PageId;
  networks?: number[];
}

function parseNetworkIds(raw: string | null): number[] | undefined {
  if (!raw) return undefined;
  const ids = raw
    .split(',')
    .map((s) => Number(s.trim()))
    .filter((n) => !Number.isNaN(n) && n >= 0);
  return ids.length > 0 ? ids : undefined;
}

/** Read `?page=` and `?network=` / `?networks=` from the current URL. */
export function parseUrlNavigation(): UrlNavigationState {
  const params = new URLSearchParams(window.location.search);
  const pageRaw = params.get('page');
  const page =
    pageRaw && (VALID_PAGES as string[]).includes(pageRaw) ? (pageRaw as PageId) : undefined;
  const networks =
    parseNetworkIds(params.get('networks')) ?? parseNetworkIds(params.get('network'));
  return { page, networks };
}

/** Build query string for bookmarkable navigation state. */
export function buildUrlQuery(page: PageId, selectedNetworks: number[]): string {
  const params = new URLSearchParams();
  if (page !== 'dashboard') params.set('page', page);
  if (selectedNetworks.length === 1) {
    params.set('network', String(selectedNetworks[0]));
  } else if (selectedNetworks.length > 1) {
    params.set('networks', selectedNetworks.join(','));
  }
  return params.toString();
}

/** Sync active page + selected networks into the address bar (replaceState). */
export function syncUrlNavigation(page: PageId, selectedNetworks: number[]): void {
  const query = buildUrlQuery(page, selectedNetworks);
  const next = query
    ? `${window.location.pathname}?${query}`
    : window.location.pathname + window.location.hash;
  const current = window.location.pathname + window.location.search + window.location.hash;
  if (next !== current) {
    window.history.replaceState(null, '', next);
  }
}
