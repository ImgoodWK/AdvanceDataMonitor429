import type { PageId } from '@/context/AppContext';

/** Pages that poll GET /api/config for connection heartbeat (TopBar online badge). */
export const CONNECTION_CHECK_PAGES: PageId[] = [
  'dashboard',
  'storage',
  'fluids',
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
  'settings',
];

export const CONNECTION_CHECK_INTERVAL_MS = 30_000;
