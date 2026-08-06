import {
  buildIconUrl,
  iconLookupIds,
  iconModeFallbackChain,
  iconIsMarkedFailed,
} from '@/utils/icon';
import {
  getLocalIconBlobUrlForCandidates,
  hasLocalIcon,
  putLocalIconBlob,
  SERVER_SYNC_PACK_NAME,
} from '@/utils/localIconPack';
import { blockIconIdForNode, AE_CPU_COMPONENT_ICON_IDS } from '@/utils/aeCableColors';
import type { TopologyNodeDto, WorldMapMarkerDto } from '@/types/dto';

export interface IconPrefetchOptions {
  iconPack?: string;
  iconRenderMode?: string;
  token?: string;
  iconCacheEnabled?: boolean;
  failedIcons?: Record<string, boolean>;
  localPack?: string;
}

function uniqueIds(ids: string[]): string[] {
  const out: string[] = [];
  for (const id of ids) {
    const trimmed = id?.trim();
    if (trimmed && !out.includes(trimmed)) out.push(trimmed);
  }
  return out;
}

export function collectIconIdsFromMarkers(markers: Pick<WorldMapMarkerDto, 'iconItemId' | 'type' | 'subtype'>[]): string[] {
  const ids: string[] = [];
  for (const marker of markers) {
    ids.push(blockIconIdForNode(marker.type, marker.iconItemId));
  }
  return uniqueIds(ids);
}

export function collectIconIdsFromTopology(
  nodes: Pick<TopologyNodeDto, 'type' | 'iconItemId' | 'devices'>[]
): string[] {
  const ids: string[] = [];
  let hasCpu = false;
  for (const node of nodes) {
    ids.push(blockIconIdForNode(node.type, node.iconItemId));
    if (node.type === 'cpu') hasCpu = true;
    for (const device of node.devices ?? []) {
      const deviceIcon = device.iconItemId?.trim();
      if (deviceIcon) {
        ids.push(deviceIcon);
        hasCpu = true;
      }
    }
  }
  if (hasCpu) {
    for (const fallback of Object.values(AE_CPU_COMPONENT_ICON_IDS)) {
      ids.push(fallback);
    }
  }
  return uniqueIds(ids);
}

async function idbHasAnyCandidate(localPack: string, candidates: string[]): Promise<boolean> {
  for (const candidate of candidates) {
    if (await hasLocalIcon(localPack, candidate)) return true;
  }
  return false;
}

/**
 * Resolve local IndexedDB blob URLs for the given item ids (no network).
 * Keys are the original item ids from `rawIds`.
 */
export async function resolveLocalIconUrls(
  rawIds: string[],
  localPack: string = SERVER_SYNC_PACK_NAME
): Promise<Record<string, string>> {
  const ids = uniqueIds(rawIds);
  const out: Record<string, string> = {};
  if (!localPack || ids.length === 0) return out;

  for (const itemId of ids) {
    const candidates = iconLookupIds(undefined, itemId);
    if (candidates.length === 0) continue;
    const url = await getLocalIconBlobUrlForCandidates(localPack, candidates);
    if (url) out[itemId] = url;
  }
  return out;
}

/**
 * Warm / count local IndexedDB hits only — never hits `/api/icon`.
 * Kept for topology/world-map callers that previously used network prefetch.
 */
export async function prefetchIcons(
  rawIds: string[],
  options: IconPrefetchOptions = {}
): Promise<{ requested: number; cached: number }> {
  const ids = uniqueIds(rawIds);
  if (ids.length === 0) return { requested: 0, cached: 0 };

  const localPack = options.localPack || SERVER_SYNC_PACK_NAME;
  let cached = 0;
  for (const itemId of ids) {
    const candidates = iconLookupIds(undefined, itemId);
    if (candidates.length === 0) continue;
    if (localPack && (await idbHasAnyCandidate(localPack, candidates))) {
      cached++;
    }
  }
  return { requested: ids.length, cached };
}

/**
 * Settings: for visible (or provided) ids missing from local pack, GET `/api/icon` once each.
 * Disk hit → write IDB; 404 → server enqueues async client render (SSE later).
 */
export async function fillMissingIconsFromServer(
  rawIds: string[],
  options: IconPrefetchOptions = {}
): Promise<{ requested: number; fetched: number; missing: number }> {
  const ids = uniqueIds(rawIds);
  if (ids.length === 0) return { requested: 0, fetched: 0, missing: 0 };

  const {
    iconPack = 'default',
    iconRenderMode = 'nei',
    token = '',
    iconCacheEnabled = true,
    failedIcons = {},
    localPack = SERVER_SYNC_PACK_NAME,
  } = options;

  let fetched = 0;
  let missing = 0;
  const concurrency = 4;
  let index = 0;

  async function worker() {
    while (index < ids.length) {
      const i = index++;
      const itemId = ids[i];
      const candidates = iconLookupIds(undefined, itemId);
      if (candidates.length === 0) continue;
      if (candidates.some((c) => iconIsMarkedFailed(failedIcons, c))) continue;
      if (localPack && (await idbHasAnyCandidate(localPack, candidates))) continue;

      const modes = iconModeFallbackChain(iconRenderMode);
      let ok = false;
      for (const mode of modes) {
        for (const candidate of candidates) {
          const url = buildIconUrl(candidate, iconPack, token, iconCacheEnabled, mode);
          if (!url) continue;
          try {
            const resp = await fetch(url, { method: 'GET', cache: 'no-store' });
            if (!resp.ok) continue;
            const blob = await resp.blob();
            if (!blob || blob.size === 0) continue;
            await putLocalIconBlob(localPack || SERVER_SYNC_PACK_NAME, candidate, blob);
            ok = true;
            fetched++;
            break;
          } catch {
            /* try next */
          }
        }
        if (ok) break;
      }
      if (!ok) missing++;
    }
  }

  const workers: Promise<void>[] = [];
  for (let w = 0; w < Math.min(concurrency, ids.length); w++) {
    workers.push(worker());
  }
  await Promise.all(workers);
  return { requested: ids.length, fetched, missing };
}
