/**
 * Browser-local recipe cache in IndexedDB (per-origin).
 * Synced manually via GET /api/recipes/sync/* — never auto-downloaded on page open.
 */

import type { RecipeDto, RecipeHandlerInfo } from '@/types/dto';

const DB_NAME = 'webae-recipes';
const DB_VERSION = 1;
const STORE_META = 'meta';
const STORE_RECIPES = 'recipes';
const META_KEY = 'catalog';

export interface LocalRecipeCatalogMeta {
  revision: string;
  recipeCount: number;
  chunkCount: number;
  chunkSize: number;
  handlers: RecipeHandlerInfo[];
  syncedAt: number;
  /** Next chunk index to fetch when resuming an incomplete sync. */
  nextChunkIndex: number;
  complete: boolean;
  estimatedBytes?: number;
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE_META)) {
        db.createObjectStore(STORE_META);
      }
      if (!db.objectStoreNames.contains(STORE_RECIPES)) {
        db.createObjectStore(STORE_RECIPES, { keyPath: 'key' });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

export function recipeStorageKey(recipe: { handlerId: string; recipeIndex: number }): string {
  return `${recipe.handlerId}:${recipe.recipeIndex}`;
}

type StoredRecipe = RecipeDto & { key: string };

export async function getLocalCatalogMeta(): Promise<LocalRecipeCatalogMeta | null> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_META, 'readonly');
    const req = tx.objectStore(STORE_META).get(META_KEY);
    req.onsuccess = () => resolve((req.result as LocalRecipeCatalogMeta) || null);
    req.onerror = () => reject(req.error);
  });
}

export async function putLocalCatalogMeta(meta: LocalRecipeCatalogMeta): Promise<void> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_META, 'readwrite');
    tx.objectStore(STORE_META).put(meta, META_KEY);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

export async function putRecipesBatch(recipes: RecipeDto[]): Promise<void> {
  if (!recipes.length) return;
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_RECIPES, 'readwrite');
    const store = tx.objectStore(STORE_RECIPES);
    for (const recipe of recipes) {
      const row: StoredRecipe = { ...recipe, key: recipeStorageKey(recipe) };
      store.put(row);
    }
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

export async function loadAllLocalRecipes(): Promise<RecipeDto[]> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_RECIPES, 'readonly');
    const req = tx.objectStore(STORE_RECIPES).getAll();
    req.onsuccess = () => {
      const rows = (req.result as StoredRecipe[]) || [];
      resolve(
        rows.map(({ key: _key, ...rest }) => rest as RecipeDto)
      );
    };
    req.onerror = () => reject(req.error);
  });
}

export async function clearLocalRecipes(): Promise<void> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction([STORE_META, STORE_RECIPES], 'readwrite');
    tx.objectStore(STORE_META).clear();
    tx.objectStore(STORE_RECIPES).clear();
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

function entryMatch(entry: { registryName?: string; displayName?: string; itemId?: string } | undefined, q: string): boolean {
  if (!entry) return false;
  const ql = q.toLowerCase();
  return (
    (entry.displayName && entry.displayName.toLowerCase().includes(ql)) ||
    (entry.registryName && entry.registryName.toLowerCase().includes(ql)) ||
    (entry.itemId && entry.itemId.toLowerCase().includes(ql)) ||
    false
  );
}

export function filterLocalRecipes(
  recipes: RecipeDto[],
  opts: {
    handlers?: string[];
    query?: string;
    scope?: 'all' | 'output' | 'input';
    exactOutput?: string;
    exactInput?: string;
  }
): RecipeDto[] {
  const handlerSet =
    opts.handlers && opts.handlers.length > 0 ? new Set(opts.handlers) : null;
  let out = recipes;
  if (handlerSet) {
    out = out.filter((r) => handlerSet.has(r.handlerId));
  }
  if (opts.exactOutput) {
    const term = opts.exactOutput.toLowerCase();
    out = out.filter((r) =>
      (r.outputs || []).some(
        (e) =>
          e.registryName?.toLowerCase() === term ||
          e.itemId?.toLowerCase() === term ||
          e.displayName?.toLowerCase() === term
      )
    );
    return out;
  }
  if (opts.exactInput) {
    const term = opts.exactInput.toLowerCase();
    out = out.filter((r) =>
      (r.inputs || []).some(
        (e) =>
          e.registryName?.toLowerCase() === term ||
          e.itemId?.toLowerCase() === term ||
          e.displayName?.toLowerCase() === term
      )
    );
    return out;
  }
  const q = opts.query?.trim();
  if (!q) return out;
  const scope = opts.scope || 'all';
  return out.filter((r) => {
    if (scope === 'output' || scope === 'all') {
      if ((r.outputs || []).some((e) => entryMatch(e, q))) return true;
    }
    if (scope === 'input' || scope === 'all') {
      if ((r.inputs || []).some((e) => entryMatch(e, q))) return true;
    }
    if (scope === 'all') {
      if (r.handlerName?.toLowerCase().includes(q.toLowerCase())) return true;
      if (r.handlerId?.toLowerCase().includes(q.toLowerCase())) return true;
    }
    return false;
  });
}

export function suggestFromLocal(
  recipes: RecipeDto[],
  q: string,
  limit = 20
): { registryName: string; displayName: string; itemId?: string }[] {
  const ql = q.trim().toLowerCase();
  if (!ql) return [];
  const seen = new Map<string, { registryName: string; displayName: string; itemId?: string }>();
  for (const r of recipes) {
    for (const e of [...(r.outputs || []), ...(r.inputs || [])]) {
      if (!e?.registryName) continue;
      if (
        e.displayName?.toLowerCase().includes(ql) ||
        e.registryName.toLowerCase().includes(ql) ||
        e.itemId?.toLowerCase().includes(ql)
      ) {
        if (!seen.has(e.registryName)) {
          seen.set(e.registryName, {
            registryName: e.registryName,
            displayName: e.displayName || e.registryName,
            itemId: e.itemId,
          });
        }
        if (seen.size >= limit) return Array.from(seen.values());
      }
    }
  }
  return Array.from(seen.values());
}

export function pageSlice<T>(items: T[], offset: number, limit: number): T[] {
  return items.slice(offset, offset + limit);
}

export function formatBytes(n: number | undefined): string {
  if (n == null || n <= 0) return '';
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
}
