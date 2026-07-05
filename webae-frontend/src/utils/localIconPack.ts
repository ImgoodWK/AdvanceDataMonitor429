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
