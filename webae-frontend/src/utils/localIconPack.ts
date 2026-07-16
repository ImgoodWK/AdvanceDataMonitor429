/**
 * Browser-local icon packs stored in IndexedDB (per-browser, never uploaded to server).
 * ZIP import uses fflate; PNG keys are sanitized item ids (colons → underscores).
 */

const DB_NAME = 'webae-local-icons';
const DB_VERSION = 1;
const STORE_PACKS = 'packs';
const STORE_ICONS = 'icons';

export interface LocalIconPackMeta {
  name: string;
  iconCount: number;
  importedAt: number;
}

const LOCAL_PACK_KEY = 'webae_local_icon_pack';
const LOCAL_PACK_LIST_KEY = 'webae_local_icon_packs';
const SYNC_REVISION_PREFIX = 'webae_icon_sync_rev_';

/** Fixed pack name for auto-synced server icon cache in IndexedDB. */
export const SERVER_SYNC_PACK_NAME = 'webae-server-sync';

export interface IconSyncManifest {
  pack: string;
  mode: string;
  iconCount: number;
  version: string;
  uploadedAt?: string;
  idsHash?: string;
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE_PACKS)) {
        db.createObjectStore(STORE_PACKS, { keyPath: 'name' });
      }
      if (!db.objectStoreNames.contains(STORE_ICONS)) {
        db.createObjectStore(STORE_ICONS);
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

export function sanitizeItemId(id: string): string {
  return id.replace(/:/g, '_').replace(/[^a-zA-Z0-9._-]/g, '_');
}

/** Derive item id from a PNG filename inside a zip (e.g. minecraft_iron_ingot.png). */
export function itemIdFromPngName(filename: string): string | null {
  const base = filename.split('/').pop() || '';
  if (!base.toLowerCase().endsWith('.png')) return null;
  const stem = base.slice(0, -4);
  if (!stem) return null;
  // Prefer first underscore as mod separator when it looks like mod_item
  const idx = stem.indexOf('_');
  if (idx > 0 && idx < stem.length - 1) {
    return stem.substring(0, idx) + ':' + stem.substring(idx + 1);
  }
  return stem;
}

export async function listLocalIconPacks(): Promise<LocalIconPackMeta[]> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_PACKS, 'readonly');
    const req = tx.objectStore(STORE_PACKS).getAll();
    req.onsuccess = () => resolve((req.result as LocalIconPackMeta[]) || []);
    req.onerror = () => reject(req.error);
  });
}

export function getActiveLocalPack(): string {
  try {
    return localStorage.getItem(LOCAL_PACK_KEY) || '';
  } catch {
    return '';
  }
}

export function setActiveLocalPack(name: string): void {
  try {
    if (name) localStorage.setItem(LOCAL_PACK_KEY, name);
    else localStorage.removeItem(LOCAL_PACK_KEY);
  } catch {
    /* ignore */
  }
}

const blobUrlCache: Record<string, string> = {};

export async function getLocalIconBlobUrl(packName: string, itemId: string): Promise<string | null> {
  if (!packName || !itemId) return null;
  const cacheKey = packName + '::' + itemId;
  if (blobUrlCache[cacheKey]) return blobUrlCache[cacheKey];

  const db = await openDb();
  const key = packName + '/' + sanitizeItemId(itemId);
  const blob = await new Promise<Blob | null>((resolve, reject) => {
    const tx = db.transaction(STORE_ICONS, 'readonly');
    const req = tx.objectStore(STORE_ICONS).get(key);
    req.onsuccess = () => resolve((req.result as Blob) || null);
    req.onerror = () => reject(req.error);
  });
  if (!blob) return null;
  const url = URL.createObjectURL(blob);
  blobUrlCache[cacheKey] = url;
  return url;
}

/**
 * Resolve local IDB icon. Candidates are ordered exact-first ({@link iconLookupIds});
 * returns the first hit so meta fallback only applies when the exact id is missing.
 */
export async function getLocalIconBlobUrlForCandidates(
  packName: string,
  candidates: string[]
): Promise<string | null> {
  for (const itemId of candidates) {
    const url = await getLocalIconBlobUrl(packName, itemId);
    if (url) return url;
  }
  return null;
}

export async function hasLocalIcon(packName: string, itemId: string): Promise<boolean> {
  if (!packName || !itemId) return false;
  const db = await openDb();
  const key = packName + '/' + sanitizeItemId(itemId);
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_ICONS, 'readonly');
    const req = tx.objectStore(STORE_ICONS).getKey(key);
    req.onsuccess = () => resolve(req.result != null);
    req.onerror = () => reject(req.error);
  });
}

export async function putLocalIconBlob(packName: string, itemId: string, blob: Blob): Promise<void> {
  if (!packName || !itemId || !blob) return;
  const cacheKey = packName + '::' + itemId;
  if (blobUrlCache[cacheKey]) {
    URL.revokeObjectURL(blobUrlCache[cacheKey]);
    delete blobUrlCache[cacheKey];
  }
  const db = await openDb();
  const key = packName + '/' + sanitizeItemId(itemId);
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction([STORE_ICONS, STORE_PACKS], 'readwrite');
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
    tx.objectStore(STORE_ICONS).put(blob, key);
  });
  await touchLocalPackMeta(packName);
}

async function touchLocalPackMeta(packName: string): Promise<void> {
  const db = await openDb();
  const existing = await new Promise<LocalIconPackMeta | undefined>((resolve, reject) => {
    const tx = db.transaction(STORE_PACKS, 'readonly');
    const req = tx.objectStore(STORE_PACKS).get(packName);
    req.onsuccess = () => resolve(req.result as LocalIconPackMeta | undefined);
    req.onerror = () => reject(req.error);
  });
  const meta: LocalIconPackMeta = {
    name: packName,
    iconCount: existing?.iconCount ?? 0,
    importedAt: Date.now(),
  };
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE_PACKS, 'readwrite');
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
    tx.objectStore(STORE_PACKS).put(meta);
  });
}

function syncRevisionKey(pack: string, mode: string): string {
  return SYNC_REVISION_PREFIX + pack + '|' + mode;
}

export function getLocalSyncRevision(pack: string, mode: string): string {
  try {
    return localStorage.getItem(syncRevisionKey(pack, mode)) || '';
  } catch {
    return '';
  }
}

export function setLocalSyncRevision(pack: string, mode: string, version: string): void {
  try {
    if (version) localStorage.setItem(syncRevisionKey(pack, mode), version);
    else localStorage.removeItem(syncRevisionKey(pack, mode));
  } catch {
    /* ignore */
  }
}

/** Import server bulk zip (mode/*.png) into IndexedDB under {@link SERVER_SYNC_PACK_NAME}. */
export async function importServerSyncZip(
  zipBytes: ArrayBuffer,
  packLabel = SERVER_SYNC_PACK_NAME
): Promise<LocalIconPackMeta> {
  const { unzipSync } = await import('fflate');
  const entries = unzipSync(new Uint8Array(zipBytes));
  const db = await openDb();
  let iconCount = 0;

  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction([STORE_ICONS, STORE_PACKS], 'readwrite');
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
    const iconStore = tx.objectStore(STORE_ICONS);
    const prefix = packLabel + '/';
    for (const [path, data] of Object.entries(entries)) {
      if (!path.toLowerCase().endsWith('.png')) continue;
      const base = path.split('/').pop() || '';
      if (!base) continue;
      const stem = base.slice(0, -4);
      if (!stem) continue;
      iconStore.put(new Blob([data as unknown as BlobPart], { type: 'image/png' }), prefix + stem);
      iconCount++;
    }
    const meta: LocalIconPackMeta = { name: packLabel, iconCount, importedAt: Date.now() };
    tx.objectStore(STORE_PACKS).put(meta);
  });

  try {
    const listRaw = localStorage.getItem(LOCAL_PACK_LIST_KEY);
    const list: string[] = listRaw ? JSON.parse(listRaw) : [];
    if (list.indexOf(packLabel) < 0) {
      list.push(packLabel);
      localStorage.setItem(LOCAL_PACK_LIST_KEY, JSON.stringify(list));
    }
  } catch {
    /* ignore */
  }

  clearBlobUrlCache();
  return { name: packLabel, iconCount, importedAt: Date.now() };
}

export async function syncServerIconPack(options: {
  pack: string;
  mode: string;
  token?: string;
  force?: boolean;
}): Promise<{ updated: boolean; iconCount: number; version: string }> {
  const pack = options.pack || 'default';
  const mode = options.mode || 'nei';
  const params = new URLSearchParams({ pack, mode });
  if (options.token) params.set('token', options.token);

  const manifestResp = await fetch('/api/icon/sync/manifest?' + params.toString());
  if (!manifestResp.ok) {
    throw new Error('Icon sync manifest failed: ' + manifestResp.status);
  }
  const manifestJson = (await manifestResp.json()) as {
    success?: boolean;
    manifest?: IconSyncManifest;
  };
  const manifest = manifestJson.manifest;
  if (!manifestJson.success || !manifest || manifest.iconCount <= 0) {
    return { updated: false, iconCount: 0, version: manifest?.version || '' };
  }

  const localRev = getLocalSyncRevision(pack, mode);
  if (!options.force && localRev && localRev === manifest.version) {
    return { updated: false, iconCount: manifest.iconCount, version: manifest.version };
  }

  const bulkResp = await fetch('/api/icon/sync/bulk?' + params.toString());
  if (!bulkResp.ok) {
    throw new Error('Icon sync bulk failed: ' + bulkResp.status);
  }
  const zipBuf = await bulkResp.arrayBuffer();
  const meta = await importServerSyncZip(zipBuf, SERVER_SYNC_PACK_NAME);
  setLocalSyncRevision(pack, mode, manifest.version);
  return { updated: true, iconCount: meta.iconCount, version: manifest.version };
}

/** 释放所有缓存的 blob URL，避免切换图标包后残留 ObjectURL 泄漏。 */
export function clearBlobUrlCache() {
  for (const k of Object.keys(blobUrlCache)) {
    URL.revokeObjectURL(blobUrlCache[k]);
    delete blobUrlCache[k];
  }
}

export async function importLocalIconPackZip(file: File, packName?: string): Promise<LocalIconPackMeta> {
  const { unzipSync } = await import('fflate');
  const buf = new Uint8Array(await file.arrayBuffer());
  const entries = unzipSync(buf);
  const name = (packName || file.name.replace(/\.zip$/i, '') || 'local-pack').trim();
  if (!name) throw new Error('Invalid pack name');

  const db = await openDb();
  let iconCount = 0;

  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction([STORE_ICONS, STORE_PACKS], 'readwrite');
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);

    for (const [path, data] of Object.entries(entries)) {
      if (!path.toLowerCase().endsWith('.png')) continue;
      const itemId = itemIdFromPngName(path);
      if (!itemId) continue;
      const blob = new Blob([data as unknown as BlobPart], { type: 'image/png' });
      tx.objectStore(STORE_ICONS).put(blob, name + '/' + sanitizeItemId(itemId));
      iconCount++;
    }

    const meta: LocalIconPackMeta = { name, iconCount, importedAt: Date.now() };
    tx.objectStore(STORE_PACKS).put(meta);
  });

  try {
    const listRaw = localStorage.getItem(LOCAL_PACK_LIST_KEY);
    const list: string[] = listRaw ? JSON.parse(listRaw) : [];
    if (list.indexOf(name) < 0) {
      list.push(name);
      localStorage.setItem(LOCAL_PACK_LIST_KEY, JSON.stringify(list));
    }
  } catch {
    /* ignore */
  }

  return { name, iconCount, importedAt: Date.now() };
}

export async function deleteLocalIconPack(name: string): Promise<void> {
  const db = await openDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction([STORE_ICONS, STORE_PACKS], 'readwrite');
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
    tx.objectStore(STORE_PACKS).delete(name);
    const iconStore = tx.objectStore(STORE_ICONS);
    const prefix = name + '/';
    const cursorReq = iconStore.openCursor();
    cursorReq.onsuccess = () => {
      const cursor = cursorReq.result;
      if (!cursor) return;
      const k = String(cursor.key);
      if (k.startsWith(prefix)) iconStore.delete(cursor.key);
      cursor.continue();
    };
  });
  if (getActiveLocalPack() === name) setActiveLocalPack('');
  clearBlobUrlCache();
}
