// Icon URL builder — appends the bearer token as a query parameter so that
// browser <img> requests can authenticate via the WebAuthMiddleware query-token
// fallback. Mirrors the server-side IconStore.sanitizeItemId behavior
// (colon → underscore) implicitly via encodeURIComponent.

let iconVersion = Date.now();

export function bumpIconVersion() {
  iconVersion = Date.now();
}

export function getIconVersion() {
  return iconVersion;
}

/** Fluid icon id prefix — must match IconItemId.FLUID_PREFIX on the server. */
export const FLUID_ID_PREFIX = 'fluid:';

/**
 * Build the icon URL for a raw id string (e.g. 'minecraft:iron_ingot' or
 * 'fluid:water'). Returns '' when iconCacheEnabled is false or the id is empty.
 */
export function buildIconUrl(
  id: string | undefined | null,
  iconPack: string,
  token: string,
  iconCacheEnabled: boolean,
  renderMode = 'nei'
): string {
  if (!iconCacheEnabled || !id) return '';
  const params = new URLSearchParams();
  params.set('item', id);
  params.set('pack', iconPack || 'default');
  params.set('mode', renderMode || 'nei');
  params.set('size', '32');
  if (token) {
    params.set('token', token);
  }
  params.set('v', String(iconVersion));
  return '/api/icon?' + params.toString();
}

/** Server-side 404 fallback chain: selected mode → nei → inventory_gl. */
export function iconModeFallbackChain(selectedMode: string): string[] {
  const out: string[] = [];
  const push = (m: string) => {
    if (m && !out.includes(m)) out.push(m);
  };
  push(selectedMode || 'nei');
  push('nei');
  push('inventory_gl');
  return out;
}

/**
 * Ordered icon cache lookup ids — exact itemId first, then registry-only fallback.
 * Mirrors server IconItemId.lookupCandidates.
 */
export function iconLookupIds(
  item?: { itemId?: string; registryName?: string; meta?: number } | null,
  id?: string | null
): string[] {
  const out: string[] = [];
  const push = (v: string | undefined | null) => {
    if (v && !out.includes(v)) out.push(v);
  };

  if (id) {
    push(id);
  }

  if (item?.itemId) {
    push(item.itemId);
  }

  const reg = item?.registryName || '';
  if (reg.startsWith(FLUID_ID_PREFIX) || reg.startsWith('oredict:')) {
    push(item?.itemId || reg);
    return out;
  }

  const meta = item && 'meta' in item ? item.meta : undefined;
  if (reg) {
    if (meta != null && meta > 0) {
      push(`${reg}:${meta}`);
    }
    push(reg);
  }

  return out;
}

/** Primary lookup id for an item or raw id string. */
export function primaryIconId(
  item?: { itemId?: string; registryName?: string; meta?: number } | null,
  id?: string | null
): string {
  const ids = iconLookupIds(item, id);
  return ids[0] || '';
}

/**
 * Build the icon URL from an item object (reads itemId or registryName + meta).
 */
export function buildItemIconUrl(
  item: { itemId?: string; registryName?: string; meta?: number } | null | undefined,
  iconPack: string,
  token: string,
  iconCacheEnabled: boolean,
  renderMode = 'nei'
): string {
  if (!item) return '';
  return buildIconUrl(primaryIconId(item), iconPack, token, iconCacheEnabled, renderMode);
}

/**
 * Short text fallback shown when the icon image fails to load.
 * Takes the first 2 meaningful chars after stripping mod prefix.
 */
export function iconAbbrev(id: string | undefined | null): string {
  if (!id) return '?';
  const clean = String(id).replace(/^[a-z0-9_]+:/, '');
  return clean.substring(0, 2).toUpperCase();
}

/**
 * Short text fallback from an item object.
 */
export function itemAbbrev(
  item: { displayName?: string; registryName?: string; itemId?: string } | null | undefined
): string {
  if (!item) return '?';
  const name = item.displayName || item.registryName || item.itemId || '?';
  const clean = name.replace(/^[a-z0-9_]+:/, '');
  return clean.substring(0, 2).toUpperCase();
}

/**
 * Build a fluid icon id from a fluid name.
 */
export function fluidIconId(fluidName: string): string {
  return FLUID_ID_PREFIX + fluidName;
}
