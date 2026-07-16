/**
 * Browser File System Access API: pick a local folder of WebAE icon PNGs
 * (icons-local from /admweb icons local|pull, or icons/<pack>/nei). Requires secure context
 * (https:// or http://localhost / 127.0.0.1).
 *
 * Permission note: Chrome restores the directory handle from IndexedDB, but read permission
 * often returns to "prompt" after reload. requestPermission() only succeeds from a user
 * gesture — never call it from Icon mount effects alone.
 */

import { sanitizeItemId } from '@/utils/localIconPack';

const DB_NAME = 'webae-local-icon-dir';
const DB_VERSION = 1;
const STORE = 'handles';
const HANDLE_KEY = 'directory';
const META_KEY = 'webae_local_icon_dir_meta';

export const LOCAL_ICON_DIR_READY_EVENT = 'webae-local-icon-dir-ready';
export const LOCAL_ICON_DIR_STATUS_EVENT = 'webae-local-icon-dir-status';

export interface LocalIconDirMeta {
  name: string;
  enabled: boolean;
  indexedAt: number;
  fileCount: number;
  /** Set when handle exists but browser denied/prompted read access without a gesture. */
  needsPermission?: boolean;
}

type DirHandle = FileSystemDirectoryHandle;

declare global {
  interface Window {
    showDirectoryPicker?: (options?: {
      id?: string;
      mode?: 'read' | 'readwrite';
      startIn?: 'desktop' | 'documents' | 'downloads' | FileSystemHandle;
    }) => Promise<FileSystemDirectoryHandle>;
  }
}

const blobCache: Record<string, string> = {};
let fileIndex: Map<string, File> | null = null;
let indexPromise: Promise<Map<string, File>> | null = null;
let permissionResumeArmed = false;
let lastNeedsPermission = false;

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE)) {
        db.createObjectStore(STORE);
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

export function isSecureContextForDirectoryPicker(): boolean {
  if (typeof window === 'undefined') return false;
  if (window.isSecureContext) return true;
  const host = window.location.hostname;
  return host === 'localhost' || host === '127.0.0.1' || host === '[::1]';
}

export function canUseDirectoryPicker(): boolean {
  return typeof window !== 'undefined' && typeof window.showDirectoryPicker === 'function' && isSecureContextForDirectoryPicker();
}

export function getLocalIconDirMeta(): LocalIconDirMeta | null {
  try {
    const raw = localStorage.getItem(META_KEY);
    if (!raw) return null;
    return JSON.parse(raw) as LocalIconDirMeta;
  } catch {
    return null;
  }
}

function setLocalIconDirMeta(meta: LocalIconDirMeta | null): void {
  try {
    if (!meta) localStorage.removeItem(META_KEY);
    else localStorage.setItem(META_KEY, JSON.stringify(meta));
  } catch {
    /* ignore */
  }
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent(LOCAL_ICON_DIR_STATUS_EVENT, { detail: meta }));
  }
}

async function saveHandle(handle: DirHandle): Promise<void> {
  const db = await openDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite');
    tx.objectStore(STORE).put(handle, HANDLE_KEY);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

async function loadHandle(): Promise<DirHandle | null> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readonly');
    const req = tx.objectStore(STORE).get(HANDLE_KEY);
    req.onsuccess = () => resolve((req.result as DirHandle) || null);
    req.onerror = () => reject(req.error);
  });
}

async function clearHandle(): Promise<void> {
  const db = await openDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite');
    tx.objectStore(STORE).delete(HANDLE_KEY);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

/**
 * @param interactive When true, may call requestPermission (must be user gesture).
 *   Background Icon lookups must pass false so Chrome does not silently deny.
 */
async function ensurePermission(handle: DirHandle, interactive: boolean): Promise<'granted' | 'prompt' | 'denied' | 'unknown'> {
  const opts = { mode: 'read' as const };
  const anyHandle = handle as FileSystemDirectoryHandle & {
    queryPermission?: (o: { mode: 'read' }) => Promise<PermissionState>;
    requestPermission?: (o: { mode: 'read' }) => Promise<PermissionState>;
  };
  if (typeof anyHandle.queryPermission !== 'function') {
    return 'granted';
  }
  let state = await anyHandle.queryPermission(opts);
  if (state === 'granted') return 'granted';
  if (!interactive) return state === 'denied' ? 'denied' : 'prompt';
  if (typeof anyHandle.requestPermission === 'function') {
    state = await anyHandle.requestPermission(opts);
    return state === 'granted' ? 'granted' : state === 'denied' ? 'denied' : 'prompt';
  }
  return 'unknown';
}

function stemFromPath(path: string): string | null {
  const base = path.split('/').pop() || '';
  if (!base.toLowerCase().endsWith('.png')) return null;
  return base.slice(0, -4);
}

/** Index PNG files under nei/, <pack>/nei/, or flat *.png */
async function buildIndex(root: DirHandle): Promise<Map<string, File>> {
  const map = new Map<string, File>();

  const addFile = async (name: string, file: File) => {
    const stem = stemFromPath(name);
    if (!stem) return;
    const key = sanitizeItemId(stem.replace(/:/g, '_'));
    map.set(key, file);
    map.set(sanitizeItemId(stem), file);
  };

  const walk = async (dir: DirHandle, prefix: string, depth: number) => {
    if (depth > 4) return;
    const anyDir = dir as DirHandle & {
      entries?: () => AsyncIterableIterator<[string, FileSystemHandle]>;
      values?: () => AsyncIterableIterator<FileSystemHandle>;
    };
    const handleEntry = async (name: string, handle: FileSystemHandle) => {
      if (handle.kind === 'file' && name.toLowerCase().endsWith('.png')) {
        const file = await (handle as FileSystemFileHandle).getFile();
        await addFile(prefix ? `${prefix}/${name}` : name, file);
      } else if (handle.kind === 'directory' && depth < 3) {
        const lower = name.toLowerCase();
        // depth 0: any child (icons→default); deeper: nei / icons-local only
        if (lower === 'nei' || depth === 0 || lower === 'icons-local') {
          await walk(handle as FileSystemDirectoryHandle, prefix ? `${prefix}/${name}` : name, depth + 1);
        }
      }
    };
    if (typeof anyDir.entries === 'function') {
      for await (const [name, handle] of anyDir.entries()) {
        await handleEntry(name, handle);
      }
      return;
    }
    if (typeof anyDir.values === 'function') {
      for await (const handle of anyDir.values()) {
        await handleEntry(handle.name, handle);
      }
    }
  };

  await walk(root, '', 0);
  return map;
}

function updateMetaAfterIndex(handle: DirHandle, index: Map<string, File>, needsPermission: boolean): LocalIconDirMeta {
  const prev = getLocalIconDirMeta();
  const meta: LocalIconDirMeta = {
    name: handle.name || prev?.name || 'icons',
    enabled: prev?.enabled !== false,
    indexedAt: Date.now(),
    fileCount: index.size,
    needsPermission,
  };
  setLocalIconDirMeta(meta);
  lastNeedsPermission = needsPermission;
  return meta;
}

async function getOrBuildIndex(handle?: DirHandle | null, interactive = false): Promise<Map<string, File>> {
  if (fileIndex && !interactive) return fileIndex;
  if (indexPromise && !interactive) return indexPromise;
  const run = (async () => {
    const h = handle || (await loadHandle());
    if (!h) {
      fileIndex = new Map();
      return fileIndex;
    }
    const perm = await ensurePermission(h, interactive);
    if (perm !== 'granted') {
      fileIndex = new Map();
      updateMetaAfterIndex(h, fileIndex, true);
      return fileIndex;
    }
    fileIndex = await buildIndex(h);
    updateMetaAfterIndex(h, fileIndex, false);
    return fileIndex;
  })();
  if (!interactive) {
    indexPromise = run;
    try {
      return await indexPromise;
    } finally {
      indexPromise = null;
    }
  }
  return run;
}

export async function pickLocalIconDirectory(): Promise<LocalIconDirMeta> {
  if (!canUseDirectoryPicker()) {
    throw new Error('DIRECTORY_PICKER_UNAVAILABLE');
  }
  const handle = await window.showDirectoryPicker!({ id: 'webae-icons-local', mode: 'read' });
  await saveHandle(handle);
  fileIndex = null;
  indexPromise = null;
  const index = await getOrBuildIndex(handle, true);
  const meta = updateMetaAfterIndex(handle, index, index.size === 0);
  window.dispatchEvent(new CustomEvent(LOCAL_ICON_DIR_READY_EVENT, { detail: meta }));
  return meta;
}

export async function refreshLocalIconDirectoryIndex(): Promise<LocalIconDirMeta | null> {
  const handle = await loadHandle();
  if (!handle) return null;
  fileIndex = null;
  indexPromise = null;
  const index = await getOrBuildIndex(handle, true);
  const meta = updateMetaAfterIndex(handle, index, index.size === 0);
  if (index.size > 0) {
    window.dispatchEvent(new CustomEvent(LOCAL_ICON_DIR_READY_EVENT, { detail: meta }));
  }
  return meta;
}

export function setLocalIconDirectoryEnabled(enabled: boolean): void {
  const prev = getLocalIconDirMeta();
  if (!prev) return;
  setLocalIconDirMeta({ ...prev, enabled });
}

export async function clearLocalIconDirectory(): Promise<void> {
  for (const url of Object.values(blobCache)) {
    try {
      URL.revokeObjectURL(url);
    } catch {
      /* ignore */
    }
  }
  for (const k of Object.keys(blobCache)) delete blobCache[k];
  fileIndex = null;
  indexPromise = null;
  permissionResumeArmed = false;
  lastNeedsPermission = false;
  await clearHandle();
  setLocalIconDirMeta(null);
}

export function localIconDirNeedsPermission(): boolean {
  return lastNeedsPermission || !!getLocalIconDirMeta()?.needsPermission;
}

/**
 * After reload, silently probe permission. If blocked, arm a one-shot pointerdown
 * handler to requestPermission under a user gesture, then reindex and notify icons.
 */
export function armLocalIconDirectoryPermissionResume(): void {
  if (typeof window === 'undefined' || permissionResumeArmed) return;
  const meta = getLocalIconDirMeta();
  if (!meta?.enabled) return;
  permissionResumeArmed = true;

  void (async () => {
    const index = await getOrBuildIndex(null, false);
    if (index.size > 0) {
      window.dispatchEvent(new CustomEvent(LOCAL_ICON_DIR_READY_EVENT, { detail: getLocalIconDirMeta() }));
      return;
    }
    if (!localIconDirNeedsPermission()) return;

    const onGesture = () => {
      window.removeEventListener('pointerdown', onGesture, true);
      void (async () => {
        fileIndex = null;
        indexPromise = null;
        const rebuilt = await getOrBuildIndex(null, true);
        if (rebuilt.size > 0) {
          window.dispatchEvent(new CustomEvent(LOCAL_ICON_DIR_READY_EVENT, { detail: getLocalIconDirMeta() }));
        }
      })();
    };
    window.addEventListener('pointerdown', onGesture, true);
  })();
}

export async function getDirectoryIconBlobUrl(itemId: string): Promise<string | null> {
  const meta = getLocalIconDirMeta();
  if (!meta?.enabled || !itemId) return null;
  const cacheKey = itemId;
  if (blobCache[cacheKey]) return blobCache[cacheKey];
  try {
    const index = await getOrBuildIndex(null, false);
    if (index.size === 0) return null;
    const candidates = [sanitizeItemId(itemId), sanitizeItemId(itemId.replace(/:/g, '_'))];
    let file: File | undefined;
    for (const c of candidates) {
      file = index.get(c);
      if (file) break;
    }
    if (!file) return null;
    const url = URL.createObjectURL(file);
    blobCache[cacheKey] = url;
    return url;
  } catch {
    return null;
  }
}

/**
 * Resolve directory icons. Candidates are exact-first; first hit wins (meta fallback
 * only when the exact filename is absent).
 */
export async function getDirectoryIconBlobUrlForCandidates(candidates: string[]): Promise<string | null> {
  for (const id of candidates) {
    const url = await getDirectoryIconBlobUrl(id);
    if (url) return url;
  }
  return null;
}
