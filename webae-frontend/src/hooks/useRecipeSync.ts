import { useCallback, useEffect, useRef, useState } from 'react';

import { getApiClient } from '@/api/client';
import type { RecipeDto, RecipeHandlerInfo } from '@/types/dto';
import {
  clearLocalRecipes,
  filterLocalRecipes,
  getLocalCatalogMeta,
  loadAllLocalRecipes,
  pageSlice,
  putLocalCatalogMeta,
  putRecipesBatch,
  suggestFromLocal,
  type LocalRecipeCatalogMeta,
} from '@/utils/recipeLocalStore';

export interface RecipeSyncManifest {
  schemaVersion: number;
  revision: string;
  recipeCount: number;
  chunkSize: number;
  chunkCount: number;
  estimatedBytes: number;
  savedAt: number;
  handlers: RecipeHandlerInfo[];
}

interface ManifestResponse {
  success: boolean;
  manifest: RecipeSyncManifest | null;
  message?: string;
}

interface ChunkResponse {
  success: boolean;
  index: number;
  recipes: RecipeDto[];
  count: number;
  message?: string;
}

export type RecipeSyncPhase = 'idle' | 'checking' | 'syncing' | 'ready' | 'error';

export interface UseRecipeSyncState {
  phase: RecipeSyncPhase;
  localMeta: LocalRecipeCatalogMeta | null;
  serverManifest: RecipeSyncManifest | null;
  recipes: RecipeDto[];
  progressDone: number;
  progressTotal: number;
  error: string | null;
  updateAvailable: boolean;
  /** Server has recipes but browser has none / incomplete. */
  needsFetch: boolean;
}

export function useRecipeSync() {
  const [phase, setPhase] = useState<RecipeSyncPhase>('idle');
  const [localMeta, setLocalMeta] = useState<LocalRecipeCatalogMeta | null>(null);
  const [serverManifest, setServerManifest] = useState<RecipeSyncManifest | null>(null);
  const [recipes, setRecipes] = useState<RecipeDto[]>([]);
  const [progressDone, setProgressDone] = useState(0);
  const [progressTotal, setProgressTotal] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const cancelRef = useRef(false);
  const recipesRef = useRef<RecipeDto[]>([]);

  const refreshLocal = useCallback(async () => {
    const meta = await getLocalCatalogMeta();
    setLocalMeta(meta);
    if (meta?.complete) {
      const all = await loadAllLocalRecipes();
      recipesRef.current = all;
      setRecipes(all);
      setPhase('ready');
    } else if (meta && !meta.complete && meta.nextChunkIndex > 0) {
      const all = await loadAllLocalRecipes();
      recipesRef.current = all;
      setRecipes(all);
      setPhase('idle');
    } else {
      recipesRef.current = [];
      setRecipes([]);
      setPhase('idle');
    }
    return meta;
  }, []);

  const checkServer = useCallback(async () => {
    setPhase('checking');
    setError(null);
    try {
      const data = await getApiClient().get<ManifestResponse>('/api/recipes/sync/manifest');
      const manifest = data.success ? data.manifest : null;
      setServerManifest(manifest);
      const meta = await getLocalCatalogMeta();
      setLocalMeta(meta);
      if (meta?.complete) {
        const all = await loadAllLocalRecipes();
        recipesRef.current = all;
        setRecipes(all);
        setPhase('ready');
      } else {
        setPhase('idle');
      }
      return manifest;
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setPhase('error');
      return null;
    }
  }, []);

  useEffect(() => {
    void (async () => {
      await refreshLocal();
      await checkServer();
    })();
  }, [refreshLocal, checkServer]);

  const cancelSync = useCallback(() => {
    cancelRef.current = true;
  }, []);

  const startSync = useCallback(
    async (forceFull = false) => {
      cancelRef.current = false;
      setError(null);
      setPhase('checking');
      try {
        const data = await getApiClient().get<ManifestResponse>('/api/recipes/sync/manifest');
        const manifest = data.success ? data.manifest : null;
        setServerManifest(manifest);
        if (!manifest || manifest.recipeCount <= 0 || manifest.chunkCount <= 0) {
          setError('empty');
          setPhase('idle');
          return { ok: false as const, reason: 'empty' as const };
        }

        let meta = await getLocalCatalogMeta();
        if (
          !forceFull &&
          meta?.complete &&
          meta.revision === manifest.revision
        ) {
          setPhase('ready');
          return { ok: true as const, reason: 'uptodate' as const };
        }

        let startIndex = 0;
        if (
          !forceFull &&
          meta &&
          !meta.complete &&
          meta.revision === manifest.revision &&
          meta.nextChunkIndex > 0
        ) {
          startIndex = meta.nextChunkIndex;
        } else {
          await clearLocalRecipes();
          recipesRef.current = [];
          setRecipes([]);
          meta = {
            revision: manifest.revision,
            recipeCount: manifest.recipeCount,
            chunkCount: manifest.chunkCount,
            chunkSize: manifest.chunkSize,
            handlers: manifest.handlers || [],
            syncedAt: Date.now(),
            nextChunkIndex: 0,
            complete: false,
            estimatedBytes: manifest.estimatedBytes,
          };
          await putLocalCatalogMeta(meta);
          setLocalMeta(meta);
        }

        setProgressDone(startIndex);
        setProgressTotal(manifest.chunkCount);
        setPhase('syncing');

        for (let i = startIndex; i < manifest.chunkCount; i++) {
          if (cancelRef.current) {
            setPhase('idle');
            return { ok: false as const, reason: 'cancelled' as const };
          }
          const chunk = await getApiClient().get<ChunkResponse>(
            `/api/recipes/sync/chunk?index=${i}`
          );
          if (!chunk.success) {
            throw new Error(chunk.message || `chunk ${i} failed`);
          }
          const batch = chunk.recipes || [];
          await putRecipesBatch(batch);
          recipesRef.current = recipesRef.current.concat(batch);
          setRecipes(recipesRef.current.slice());
          setProgressDone(i + 1);
          const nextMeta: LocalRecipeCatalogMeta = {
            revision: manifest.revision,
            recipeCount: manifest.recipeCount,
            chunkCount: manifest.chunkCount,
            chunkSize: manifest.chunkSize,
            handlers: manifest.handlers || [],
            syncedAt: Date.now(),
            nextChunkIndex: i + 1,
            complete: i + 1 >= manifest.chunkCount,
            estimatedBytes: manifest.estimatedBytes,
          };
          await putLocalCatalogMeta(nextMeta);
          setLocalMeta(nextMeta);
        }

        setPhase('ready');
        return { ok: true as const, reason: 'synced' as const };
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e));
        setPhase('error');
        return { ok: false as const, reason: 'error' as const };
      }
    },
    []
  );

  const browseLocal = useCallback(
    (handlers: string[], offset: number, limit: number) => {
      const filtered = filterLocalRecipes(recipesRef.current, { handlers });
      return {
        results: pageSlice(filtered, offset, limit),
        total: filtered.length,
      };
    },
    []
  );

  const searchLocal = useCallback(
    (
      term: string,
      scope: 'all' | 'output' | 'input',
      handlers: string[],
      offset: number,
      limit: number
    ) => {
      const filtered = filterLocalRecipes(recipesRef.current, {
        query: term,
        scope,
        handlers,
      });
      return {
        results: pageSlice(filtered, offset, limit),
        total: filtered.length,
      };
    },
    []
  );

  const searchExactLocal = useCallback(
    (registryName: string, mode: 'output' | 'input', handlers: string[], limit: number) => {
      const filtered = filterLocalRecipes(recipesRef.current, {
        handlers,
        exactOutput: mode === 'output' ? registryName : undefined,
        exactInput: mode === 'input' ? registryName : undefined,
      });
      return {
        results: pageSlice(filtered, 0, limit),
        total: filtered.length,
      };
    },
    []
  );

  const suggestLocal = useCallback((q: string, limit = 20) => {
    return suggestFromLocal(recipesRef.current, q, limit);
  }, []);

  const updateAvailable =
    !!serverManifest &&
    !!localMeta?.complete &&
    localMeta.revision !== serverManifest.revision;

  const needsFetch =
    !!serverManifest &&
    serverManifest.recipeCount > 0 &&
    (!localMeta?.complete || localMeta.revision !== serverManifest.revision);

  return {
    phase,
    localMeta,
    serverManifest,
    recipes,
    progressDone,
    progressTotal,
    error,
    updateAvailable,
    needsFetch,
    startSync,
    cancelSync,
    checkServer,
    browseLocal,
    searchLocal,
    searchExactLocal,
    suggestLocal,
    ready: phase === 'ready' && recipes.length > 0,
  };
}
