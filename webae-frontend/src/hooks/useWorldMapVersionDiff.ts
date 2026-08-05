import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { getApiClient } from '@/api/client';
import type {
  WorldMapAnnotationDto,
  WorldMapAnnotationInput,
  WorldMapAnnotationResponse,
  WorldMapAnnotationsResponse,
  WorldMapVersionDiffResponse,
  WorldMapVersionDto,
  WorldMapVersionsResponse,
} from '@/types/dto';
import {
  buildWorldMapAnnotationsUrl,
  buildWorldMapDeleteAnnotationUrl,
  buildWorldMapDiffUrl,
  buildWorldMapUpdateAnnotationUrl,
  buildWorldMapVersionsUrl,
  filterWorldMapAnnotationsByVersion,
  isWorldMapAnnotationApplicable,
  selectWorldMapVersionPair,
  toWorldMapAnnotationPayload,
  type WorldMapDiffDataState,
  type WorldMapDiffFilter,
  type WorldMapVersionDiffQueryOptions,
  worldMapDiffState,
} from '@/utils/worldMapVersionDiff';

export interface WorldMapVersionDiffApi {
  get<T>(url: string): Promise<T>;
  post<T>(url: string, body?: unknown): Promise<T>;
  put<T>(url: string, body?: unknown): Promise<T>;
  delete<T>(url: string): Promise<T>;
}

export interface UseWorldMapVersionDiffOptions {
  networkId: number | null | undefined;
  enabled?: boolean;
  /** Load the comparison as soon as versions provide a default pair. */
  diffEnabled?: boolean;
  includeTiles?: boolean;
  includeMarkers?: boolean;
  filter?: WorldMapDiffFilter;
  /** Injectable client keeps the hook deterministic in tests and embedded hosts. */
  apiClient?: WorldMapVersionDiffApi;
}

export interface WorldMapVersionState {
  data: WorldMapVersionDto[];
  loading: boolean;
  error: string | null;
  retry: () => Promise<void>;
}

export interface WorldMapDiffState {
  data: WorldMapVersionDiffResponse | null;
  filteredData: WorldMapVersionDiffResponse | null;
  loading: boolean;
  error: string | null;
  state: WorldMapDiffDataState;
  retry: () => Promise<void>;
}

export interface WorldMapAnnotationState {
  data: WorldMapAnnotationDto[];
  /** Version-filtered annotations ready for a map layer. */
  visible: WorldMapAnnotationDto[];
  loading: boolean;
  error: string | null;
  retry: () => Promise<void>;
}

export interface UseWorldMapVersionDiffResult {
  networkId: number | null;
  versions: WorldMapVersionDto[];
  currentVersion: number | null;
  previousVersion: number | null;
  fromVersion: number | null;
  toVersion: number | null;
  setVersionPair: (fromVersion: number | null, toVersion: number | null) => void;
  /** Set only the comparison side that changed. */
  setFromVersion: (version: number | null) => void;
  setToVersion: (version: number | null) => void;
  comparePrevious: () => { fromVersion: number; toVersion: number } | null;
  diffEnabled: boolean;
  setDiffEnabled: (enabled: boolean) => void;
  toggleDiff: (enabled?: boolean) => void;
  includeTiles: boolean;
  includeMarkers: boolean;
  setDiffOptions: (options: Partial<Pick<WorldMapVersionDiffQueryOptions, 'includeTiles' | 'includeMarkers'>>) => void;
  filter: WorldMapDiffFilter;
  setFilter: (patch: Partial<WorldMapDiffFilter>) => void;
  versionState: WorldMapVersionState;
  diffState: WorldMapDiffState;
  annotationState: WorldMapAnnotationState;
  /** Flat aliases are useful for simple consumers. */
  versionsLoading: boolean;
  versionsError: string | null;
  diff: WorldMapVersionDiffResponse | null;
  diffLoading: boolean;
  diffError: string | null;
  annotations: WorldMapAnnotationDto[];
  annotationsLoading: boolean;
  annotationsError: string | null;
  retryVersions: () => Promise<void>;
  retryDiff: () => Promise<void>;
  retryAnnotations: () => Promise<void>;
  createAnnotation: (input: WorldMapAnnotationInput) => Promise<WorldMapAnnotationDto | null>;
  updateAnnotation: (id: string, input: WorldMapAnnotationInput) => Promise<WorldMapAnnotationDto | null>;
  deleteAnnotation: (id: string) => Promise<boolean>;
}

type RequestKind = 'versions' | 'diff' | 'annotations' | 'mutation';

const REQUEST_KINDS: RequestKind[] = ['versions', 'diff', 'annotations', 'mutation'];

function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message) return error.message;
  if (typeof error === 'string' && error) return error;
  return fallback;
}

function responseError(response: { success?: boolean; message?: string; code?: string }, fallback: string): Error | null {
  if (response.success !== false) return null;
  return new Error(response.message || response.code || fallback);
}

function isPositiveSnapshotVersion(version: number | null | undefined): version is number {
  return typeof version === 'number' && Number.isFinite(version) && version > 0;
}

function defaultVersionPair(
  response: WorldMapVersionsResponse,
): { current: number | null; previous: number | null; from: number | null; to: number | null } {
  const pair = selectWorldMapVersionPair(response);
  return {
    current: pair.currentVersion,
    previous: pair.previousVersion,
    from: pair.fromVersion,
    to: pair.toVersion,
  };
}

function diffQueryKey(
  from: number | null,
  to: number | null,
  includeTiles: boolean,
  includeMarkers: boolean,
  filter: WorldMapDiffFilter,
): string {
  return [
    from,
    to,
    includeTiles,
    includeMarkers,
    filter.dimension ?? null,
    filter.minX ?? null,
    filter.maxX ?? null,
    filter.minZ ?? null,
    filter.maxZ ?? null,
  ].join('|');
}

function abortError(): Error {
  const error = new Error('Request aborted');
  error.name = 'AbortError';
  return error;
}

/** Race an API-client promise against a controller without requiring API client changes. */
function raceAbort<T>(promise: Promise<T>, signal: AbortSignal): Promise<T> {
  if (signal.aborted) return Promise.reject(abortError());
  return new Promise<T>((resolve, reject) => {
    let settled = false;
    const onAbort = () => {
      if (settled) return;
      settled = true;
      reject(abortError());
    };
    signal.addEventListener('abort', onAbort, { once: true });
    promise.then(
      (value) => {
        if (settled) return;
        settled = true;
        signal.removeEventListener('abort', onAbort);
        resolve(value);
      },
      (error) => {
        if (settled) return;
        settled = true;
        signal.removeEventListener('abort', onAbort);
        reject(error);
      },
    );
  });
}

export function useWorldMapVersionDiff({
  networkId,
  enabled = true,
  diffEnabled: initialDiffEnabled = false,
  includeTiles: initialIncludeTiles = true,
  includeMarkers: initialIncludeMarkers = true,
  filter: initialFilter = {},
  apiClient,
}: UseWorldMapVersionDiffOptions): UseWorldMapVersionDiffResult {
  const networkIdRef = useRef<number | null>(networkId ?? null);
  networkIdRef.current = networkId ?? null;
  const enabledRef = useRef(enabled);
  enabledRef.current = enabled;
  const mountedRef = useRef(true);
  const generationRef = useRef(0);
  const controllerRef = useRef<AbortController | null>(null);
  const requestSequenceRef = useRef<Record<RequestKind, number>>({
    versions: 0,
    diff: 0,
    annotations: 0,
    mutation: 0,
  });

  const [versions, setVersions] = useState<WorldMapVersionDto[]>([]);
  const [currentVersion, setCurrentVersion] = useState<number | null>(null);
  const [previousVersion, setPreviousVersion] = useState<number | null>(null);
  const [fromVersion, setFromVersion] = useState<number | null>(null);
  const [toVersion, setToVersion] = useState<number | null>(null);
  const [diffEnabled, setDiffEnabled] = useState(initialDiffEnabled);
  const [includeTiles, setIncludeTiles] = useState(initialIncludeTiles);
  const [includeMarkers, setIncludeMarkers] = useState(initialIncludeMarkers);
  const [filter, setFilterState] = useState<WorldMapDiffFilter>(initialFilter);
  const diffEnabledRef = useRef(diffEnabled);
  diffEnabledRef.current = diffEnabled;

  const [versionsLoading, setVersionsLoading] = useState(false);
  const [versionsError, setVersionsError] = useState<string | null>(null);
  const [diff, setDiff] = useState<WorldMapVersionDiffResponse | null>(null);
  const [diffLoading, setDiffLoading] = useState(false);
  const [diffError, setDiffError] = useState<string | null>(null);
  const [annotations, setAnnotations] = useState<WorldMapAnnotationDto[]>([]);
  const [annotationsLoading, setAnnotationsLoading] = useState(false);
  const [annotationsError, setAnnotationsError] = useState<string | null>(null);

  const fromVersionRef = useRef(fromVersion);
  const toVersionRef = useRef(toVersion);
  const includeTilesRef = useRef(includeTiles);
  const includeMarkersRef = useRef(includeMarkers);
  const filterRef = useRef(filter);
  fromVersionRef.current = fromVersion;
  toVersionRef.current = toVersion;
  includeTilesRef.current = includeTiles;
  includeMarkersRef.current = includeMarkers;
  filterRef.current = filter;

  const client = useCallback((): WorldMapVersionDiffApi => apiClient ?? getApiClient(), [apiClient]);

  const beginRequest = useCallback((kind: RequestKind) => {
    requestSequenceRef.current[kind] += 1;
    return {
      generation: generationRef.current,
      sequence: requestSequenceRef.current[kind],
      controller: controllerRef.current ?? new AbortController(),
    };
  }, []);

  const isCurrentRequest = useCallback(
    (
      kind: RequestKind,
      generation: number,
      sequence: number,
      controller: AbortController,
      expectedNetworkId: number,
    ) =>
      mountedRef.current &&
      enabledRef.current &&
      networkIdRef.current === expectedNetworkId &&
      generation === generationRef.current &&
      sequence === requestSequenceRef.current[kind] &&
      !controller.signal.aborted,
    [],
  );

  const loadVersions = useCallback(async () => {
    const id = networkIdRef.current;
    if (!enabled || id == null) return;
    const request = beginRequest('versions');
    setVersionsLoading(true);
    setVersionsError(null);
    try {
      const response = await raceAbort(
        client().get<WorldMapVersionsResponse>(buildWorldMapVersionsUrl(id)),
        request.controller.signal,
      );
      const failure = responseError(response, 'World-map versions request failed');
      if (failure) throw failure;
      if (!isCurrentRequest('versions', request.generation, request.sequence, request.controller, id)) return;
      const pair = defaultVersionPair(response);
      setVersions(response.versions ?? []);
      setCurrentVersion(pair.current);
      setPreviousVersion(pair.previous);
      setFromVersion(pair.from);
      setToVersion(pair.to);
    } catch (error) {
      if (error instanceof Error && error.name === 'AbortError') return;
      if (!isCurrentRequest('versions', request.generation, request.sequence, request.controller, id)) return;
      setVersions([]);
      setVersionsError(errorMessage(error, 'World-map versions request failed'));
    } finally {
      if (isCurrentRequest('versions', request.generation, request.sequence, request.controller, id)) {
        setVersionsLoading(false);
      }
    }
  }, [beginRequest, client, enabled, isCurrentRequest]);

  const loadDiff = useCallback(async () => {
    const id = networkIdRef.current;
    const from = fromVersionRef.current;
    const to = toVersionRef.current;
    if (!enabled || id == null || !diffEnabled || from == null || to == null || from === to) return;
    const filterSnapshot = { ...filterRef.current };
    const includeTilesSnapshot = includeTilesRef.current;
    const includeMarkersSnapshot = includeMarkersRef.current;
    const queryKey = diffQueryKey(from, to, includeTilesSnapshot, includeMarkersSnapshot, filterSnapshot);
    const request = beginRequest('diff');
    const requestIsCurrent = () =>
      isCurrentRequest('diff', request.generation, request.sequence, request.controller, id)
      && diffEnabledRef.current
      && queryKey === diffQueryKey(
        fromVersionRef.current,
        toVersionRef.current,
        includeTilesRef.current,
        includeMarkersRef.current,
        filterRef.current,
      );
    setDiff(null);
    setDiffLoading(true);
    setDiffError(null);
    try {
      const response = await raceAbort(
        client().get<WorldMapVersionDiffResponse>(
          buildWorldMapDiffUrl(id, {
            fromVersion: from,
            toVersion: to,
            ...filterSnapshot,
            includeTiles: includeTilesSnapshot,
            includeMarkers: includeMarkersSnapshot,
          }),
        ),
        request.controller.signal,
      );
      const failure = responseError(response, 'World-map diff request failed');
      // A corrupt/missing retained manifest is an explicit conservative
      // "unknown" data state, not a transport failure.
      if (failure && response.status !== 'unknown') throw failure;
      if (!requestIsCurrent()) return;
      setDiff(response);
    } catch (error) {
      if (error instanceof Error && error.name === 'AbortError') return;
      if (!requestIsCurrent()) return;
      setDiff(null);
      setDiffError(errorMessage(error, 'World-map diff request failed'));
    } finally {
      if (requestIsCurrent()) {
        setDiffLoading(false);
      }
    }
  }, [beginRequest, client, diffEnabled, enabled, isCurrentRequest]);

  const loadAnnotations = useCallback(async () => {
    const id = networkIdRef.current;
    const version = toVersionRef.current;
    if (!enabled || id == null || !isPositiveSnapshotVersion(version)) return;
    const request = beginRequest('annotations');
    setAnnotations([]);
    setAnnotationsLoading(true);
    setAnnotationsError(null);
    try {
      const response = await raceAbort(
        client().get<WorldMapAnnotationsResponse>(buildWorldMapAnnotationsUrl(id, version)),
        request.controller.signal,
      );
      const failure = responseError(response, 'World-map annotations request failed');
      if (failure) throw failure;
      if (
        !isCurrentRequest('annotations', request.generation, request.sequence, request.controller, id)
        || toVersionRef.current !== version
      ) return;
      setAnnotations(response.annotations ?? []);
    } catch (error) {
      if (error instanceof Error && error.name === 'AbortError') return;
      if (
        !isCurrentRequest('annotations', request.generation, request.sequence, request.controller, id)
        || toVersionRef.current !== version
      ) return;
      setAnnotations([]);
      setAnnotationsError(errorMessage(error, 'World-map annotations request failed'));
    } finally {
      if (
        isCurrentRequest('annotations', request.generation, request.sequence, request.controller, id)
        && toVersionRef.current === version
      ) {
        setAnnotationsLoading(false);
      }
    }
  }, [beginRequest, client, enabled, isCurrentRequest]);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      controllerRef.current?.abort();
    };
  }, []);

  const networkKey = networkId == null ? null : String(networkId);
  useEffect(() => {
    const generation = ++generationRef.current;
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    for (const kind of REQUEST_KINDS) requestSequenceRef.current[kind] += 1;
    setVersions([]);
    setCurrentVersion(null);
    setPreviousVersion(null);
    setFromVersion(null);
    setToVersion(null);
    setVersionsError(null);
    setDiff(null);
    setDiffError(null);
    setAnnotations([]);
    setAnnotationsError(null);
    if (!enabled || networkId == null) {
      setVersionsLoading(false);
      setDiffLoading(false);
      setAnnotationsLoading(false);
      return () => controller.abort();
    }
    void loadVersions();
    return () => {
      controller.abort();
      if (generation === generationRef.current) controllerRef.current = null;
    };
  }, [enabled, loadVersions, networkId, networkKey]);

  useEffect(() => {
    if (
      !enabled ||
      networkId == null ||
      !diffEnabled ||
      fromVersion == null ||
      toVersion == null ||
      fromVersion === toVersion
    ) {
      // Invalidate an in-flight comparison when the toggle is switched off or
      // the pair is incomplete; its promise may still resolve on API clients
      // that do not expose AbortSignal.
      requestSequenceRef.current.diff += 1;
      setDiff(null);
      setDiffLoading(false);
      setDiffError(null);
      if (diffEnabled && fromVersion != null && fromVersion === toVersion) {
        setDiffEnabled(false);
      }
      return;
    }
    void loadDiff();
  }, [diffEnabled, enabled, filter, fromVersion, loadDiff, networkId, networkKey, toVersion, includeMarkers, includeTiles]);

  useEffect(() => {
    if (!enabled || networkId == null || !isPositiveSnapshotVersion(toVersion)) {
      requestSequenceRef.current.annotations += 1;
      setAnnotations([]);
      setAnnotationsLoading(false);
      setAnnotationsError(null);
      return;
    }
    void loadAnnotations();
  }, [enabled, loadAnnotations, networkId, networkKey, toVersion]);

  const setVersionPair = useCallback((nextFrom: number | null, nextTo: number | null) => {
    setFromVersion(nextFrom);
    setToVersion(nextTo);
  }, []);

  const comparePrevious = useCallback((): { fromVersion: number; toVersion: number } | null => {
    if (
      previousVersion == null ||
      currentVersion == null ||
      previousVersion === currentVersion
    ) return null;
    setDiffEnabled(true);
    setVersionPair(previousVersion, currentVersion);
    return { fromVersion: previousVersion, toVersion: currentVersion };
  }, [currentVersion, previousVersion, setVersionPair]);

  const toggleDiff = useCallback((next?: boolean) => {
    setDiffEnabled((previous) => next ?? !previous);
  }, []);

  const setDiffOptions = useCallback(
    (options: Partial<Pick<WorldMapVersionDiffQueryOptions, 'includeTiles' | 'includeMarkers'>>) => {
      if (typeof options.includeTiles === 'boolean') setIncludeTiles(options.includeTiles);
      if (typeof options.includeMarkers === 'boolean') setIncludeMarkers(options.includeMarkers);
    },
    [],
  );

  const setFilter = useCallback((patch: Partial<WorldMapDiffFilter>) => {
    setFilterState((previous) => ({ ...previous, ...patch }));
  }, []);

  const retryVersions = useCallback(() => loadVersions(), [loadVersions]);
  const retryDiff = useCallback(() => loadDiff(), [loadDiff]);
  const retryAnnotations = useCallback(() => loadAnnotations(), [loadAnnotations]);

  const mutateAnnotation = useCallback(
    async (method: 'post' | 'put', id: string | null, input: WorldMapAnnotationInput): Promise<WorldMapAnnotationDto | null> => {
      const network = networkIdRef.current;
      if (!enabled || network == null) throw new Error('World-map annotations are disabled');
      const request = beginRequest('mutation');
      setAnnotationsError(null);
      try {
        const payload = toWorldMapAnnotationPayload({ ...input, networkId: network });
        const response = await raceAbort(
          method === 'post'
            ? client().post<WorldMapAnnotationResponse | WorldMapAnnotationsResponse>(buildWorldMapAnnotationsUrl(network), payload)
            : client().put<WorldMapAnnotationResponse | WorldMapAnnotationsResponse>(
                buildWorldMapUpdateAnnotationUrl(id ?? '', network),
                payload,
              ),
          request.controller.signal,
        );
        const failure = responseError(response, 'World-map annotation mutation failed');
        if (failure) throw failure;
        if (!isCurrentRequest('mutation', request.generation, request.sequence, request.controller, network)) return null;
        if (isPositiveSnapshotVersion(toVersionRef.current)) await loadAnnotations();
        if ('annotation' in response) return response.annotation ?? null;
        return null;
      } catch (error) {
        if (error instanceof Error && error.name === 'AbortError') throw error;
        if (isCurrentRequest('mutation', request.generation, request.sequence, request.controller, network)) {
          setAnnotationsError(errorMessage(error, 'World-map annotation mutation failed'));
        }
        throw error;
      }
    },
    [beginRequest, client, enabled, isCurrentRequest, loadAnnotations],
  );

  const createAnnotation = useCallback(
    (input: WorldMapAnnotationInput) => mutateAnnotation('post', null, input),
    [mutateAnnotation],
  );

  const updateAnnotation = useCallback(
    (id: string, input: WorldMapAnnotationInput) => mutateAnnotation('put', id, input),
    [mutateAnnotation],
  );

  const deleteAnnotation = useCallback(
    async (id: string): Promise<boolean> => {
      const network = networkIdRef.current;
      if (!enabled || network == null) throw new Error('World-map annotations are disabled');
      const request = beginRequest('mutation');
      setAnnotationsError(null);
      try {
        const response = await raceAbort(
          client().delete<{ success?: boolean; message?: string; code?: string }>(
            buildWorldMapDeleteAnnotationUrl(id, network),
          ),
          request.controller.signal,
        );
        const failure = responseError(response, 'World-map annotation deletion failed');
        if (failure) throw failure;
        if (!isCurrentRequest('mutation', request.generation, request.sequence, request.controller, network)) return false;
        if (isPositiveSnapshotVersion(toVersionRef.current)) await loadAnnotations();
        return true;
      } catch (error) {
        if (error instanceof Error && error.name === 'AbortError') throw error;
        if (isCurrentRequest('mutation', request.generation, request.sequence, request.controller, network)) {
          setAnnotationsError(errorMessage(error, 'World-map annotation deletion failed'));
        }
        throw error;
      }
    },
    [beginRequest, client, enabled, isCurrentRequest, loadAnnotations],
  );

  // Spatial filters are part of the HTTP query. The server applies them
  // before calculating its complete summary, while the detail arrays share a
  // bounded response budget. Re-filtering here would replace that authoritative
  // summary with counts from only the (possibly truncated) detail window.
  const filteredData = diff;
  const visibleAnnotations = useMemo(
    () => filterWorldMapAnnotationsByVersion(annotations, toVersion),
    [annotations, toVersion],
  );
  const diffStatus = useMemo(
    () => worldMapDiffState({
      loading: diffLoading,
      error: diffError,
      diff,
      includeMarkers,
      includeTiles,
    }),
    [diff, diffError, diffLoading, includeMarkers, includeTiles],
  );

  const versionState: WorldMapVersionState = useMemo(
    () => ({ data: versions, loading: versionsLoading, error: versionsError, retry: retryVersions }),
    [retryVersions, versions, versionsError, versionsLoading],
  );
  const diffState: WorldMapDiffState = useMemo(
    () => ({
      data: diff,
      filteredData,
      loading: diffLoading,
      error: diffError,
      state: diffStatus,
      retry: retryDiff,
    }),
    [diff, diffError, diffLoading, diffStatus, filteredData, retryDiff],
  );
  const annotationState: WorldMapAnnotationState = useMemo(
    () => ({
      data: annotations,
      visible: visibleAnnotations,
      loading: annotationsLoading,
      error: annotationsError,
      retry: retryAnnotations,
    }),
    [annotations, annotationsError, annotationsLoading, retryAnnotations, visibleAnnotations],
  );

  return {
    networkId: networkId ?? null,
    versions,
    currentVersion,
    previousVersion,
    fromVersion,
    toVersion,
    setVersionPair,
    setFromVersion,
    setToVersion,
    comparePrevious,
    diffEnabled,
    setDiffEnabled,
    toggleDiff,
    includeTiles,
    includeMarkers,
    setDiffOptions,
    filter,
    setFilter,
    versionState,
    diffState,
    annotationState,
    versionsLoading,
    versionsError,
    diff,
    diffLoading,
    diffError,
    annotations,
    annotationsLoading,
    annotationsError,
    retryVersions,
    retryDiff,
    retryAnnotations,
    createAnnotation,
    updateAnnotation,
    deleteAnnotation,
  };
}

// The shorter hook name is convenient when the component only needs history + annotations.
export const useWorldMapHistory = useWorldMapVersionDiff;
export const useWorldMapVersionHistory = useWorldMapVersionDiff;
export const useWorldMapDiff = useWorldMapVersionDiff;

export { isWorldMapAnnotationApplicable };
