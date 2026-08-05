import type {
  WorldMapAnnotationDto,
  WorldMapAnnotationInput,
  WorldMapDiffMarkerDto,
  WorldMapDiffSummaryDto,
  WorldMapLogicalAvailability,
  WorldMapMarkerChangeDto,
  WorldMapTileChangeDto,
  WorldMapVersionDiffResponse,
} from '@/types/dto';

export type WorldMapDiffStatus = 'added' | 'removed' | 'changed' | 'moved' | 'unchanged' | 'unknown';

/** Stable colors used by all world-map diff consumers. */
export const WORLD_MAP_DIFF_COLORS: Record<WorldMapDiffStatus, string> = {
  added: '#52c41a',
  removed: '#ff4d4f',
  moved: '#1677ff',
  changed: '#fa8c16',
  unchanged: '#8c8c8c',
  unknown: '#8c8c8c',
};

export function normalizeWorldMapDiffStatus(status: unknown): WorldMapDiffStatus {
  if (typeof status !== 'string') return 'unknown';
  const normalized = status.trim().toLowerCase();
  if (
    normalized === 'added' ||
    normalized === 'removed' ||
    normalized === 'changed' ||
    normalized === 'moved' ||
    normalized === 'unchanged'
  ) {
    return normalized;
  }
  return 'unknown';
}

export function worldMapDiffColor(status: unknown): string {
  return WORLD_MAP_DIFF_COLORS[normalizeWorldMapDiffStatus(status)];
}

// Descriptive aliases make the utility convenient for component code and older callers.
export const getWorldMapDiffColor = worldMapDiffColor;
export const colorForWorldMapDiffStatus = worldMapDiffColor;
export const getDiffStatusColor = worldMapDiffColor;

export interface WorldMapLogicalAvailabilitySides {
  from: boolean | null;
  to: boolean | null;
  known: boolean;
}

/**
 * Resolve the API's boolean-or-sides shape. Unknown or one-sided availability is
 * intentionally false so the UI never claims a complete comparison without both
 * logical snapshots.
 */
export function logicalAvailabilitySides(value: WorldMapLogicalAvailability): WorldMapLogicalAvailabilitySides {
  if (typeof value === 'boolean') {
    return { from: value, to: value, known: true };
  }
  if (!value || typeof value !== 'object') {
    return { from: null, to: null, known: false };
  }

  const from = typeof value.from === 'boolean'
    ? value.from
    : typeof value.previous === 'boolean'
      ? value.previous
      : null;
  const to = typeof value.to === 'boolean'
    ? value.to
    : typeof value.current === 'boolean'
      ? value.current
      : null;
  return { from, to, known: from !== null && to !== null };
}

export function normalizeLogicalAvailability(value: WorldMapLogicalAvailability): boolean {
  const sides = logicalAvailabilitySides(value);
  return sides.known && sides.from === true && sides.to === true;
}

// Backward-compatible verb-oriented alias for callers that prefer a helper name.
export const isWorldMapLogicalAvailable = normalizeLogicalAvailability;

export interface WorldMapVersionDiffQueryOptions {
  fromVersion?: number | string | null;
  toVersion?: number | string | null;
  /** Accept `from`/`to` aliases when adapting a form state object. */
  from?: number | string | null;
  to?: number | string | null;
  dimension?: number | string | null;
  minX?: number | string | null;
  maxX?: number | string | null;
  minZ?: number | string | null;
  maxZ?: number | string | null;
  includeTiles?: boolean | null;
  includeMarkers?: boolean | null;
}

function appendQueryPart(parts: string[], key: string, value: unknown): void {
  if (value === undefined || value === null || value === '') return;
  parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`);
}

function appendBooleanQueryPart(parts: string[], key: string, value: boolean | null | undefined): void {
  if (value === undefined || value === null) return;
  appendQueryPart(parts, key, value ? 'true' : 'false');
}

export function buildWorldMapVersionsUrl(networkId: number | string, basePath = '/api/worldmap/versions'): string {
  const parts: string[] = [];
  appendQueryPart(parts, 'network', networkId);
  return `${basePath}${parts.length ? `?${parts.join('&')}` : ''}`;
}

export interface WorldMapVersionPair {
  currentVersion: number | null;
  previousVersion: number | null;
  fromVersion: number | null;
  toVersion: number | null;
}

/** Select the server-declared previous/current pair, with a sorted-list fallback. */
export function selectWorldMapVersionPair(input: {
  currentVersion?: number | null;
  previousVersion?: number | null;
  versions?: Array<{ version: number }> | null;
}): WorldMapVersionPair {
  // Java DTO primitives use zero when a pointer side is absent. Never expose
  // that sentinel as a selectable snapshot or send it back to the diff API.
  const validVersion = (value: unknown): number | null => {
    const version = finiteNumber(value);
    return version != null && version > 0 ? version : null;
  };
  const sorted = (input.versions ?? [])
    .map((entry) => validVersion(entry.version))
    .filter((version): version is number => version != null)
    .sort((a, b) => b - a);
  const currentVersion = validVersion(input.currentVersion) ?? sorted[0] ?? null;
  const previousVersion =
    validVersion(input.previousVersion) ?? sorted.find((version) => version !== currentVersion) ?? null;
  return {
    currentVersion,
    previousVersion,
    fromVersion: previousVersion ?? currentVersion,
    toVersion: currentVersion ?? previousVersion,
  };
}

export function buildWorldMapDiffQuery(
  networkId: number | string,
  options: WorldMapVersionDiffQueryOptions = {},
): string {
  const parts: string[] = [];
  appendQueryPart(parts, 'network', networkId);
  appendQueryPart(parts, 'from', options.fromVersion ?? options.from);
  appendQueryPart(parts, 'to', options.toVersion ?? options.to);
  appendQueryPart(parts, 'dimension', options.dimension);
  appendQueryPart(parts, 'minX', options.minX);
  appendQueryPart(parts, 'maxX', options.maxX);
  appendQueryPart(parts, 'minZ', options.minZ);
  appendQueryPart(parts, 'maxZ', options.maxZ);
  appendBooleanQueryPart(parts, 'includeTiles', options.includeTiles);
  appendBooleanQueryPart(parts, 'includeMarkers', options.includeMarkers);
  return parts.length ? `?${parts.join('&')}` : '';
}

export function buildWorldMapDiffUrl(
  networkId: number | string,
  options: WorldMapVersionDiffQueryOptions = {},
  basePath = '/api/worldmap/diff',
): string {
  return `${basePath}${buildWorldMapDiffQuery(networkId, options)}`;
}

export function buildWorldMapAnnotationsUrl(
  networkId: number | string,
  version?: number | string | null,
  basePath = '/api/worldmap/annotations',
): string {
  const parts: string[] = [];
  appendQueryPart(parts, 'network', networkId);
  appendQueryPart(parts, 'version', version);
  return `${basePath}${parts.length ? `?${parts.join('&')}` : ''}`;
}

export function buildWorldMapAnnotationUrl(id: string, basePath = '/api/worldmap/annotations'): string {
  return `${basePath}/${encodeURIComponent(id)}`;
}

export function buildWorldMapDeleteAnnotationUrl(
  id: string,
  networkId: number | string,
  basePath = '/api/worldmap/annotations',
): string {
  return `${buildWorldMapAnnotationUrl(id, basePath)}?network=${encodeURIComponent(String(networkId))}`;
}

/** PUT uses the same network guard as DELETE; keep the query construction in one place. */
export function buildWorldMapUpdateAnnotationUrl(
  id: string,
  networkId: number | string,
  basePath = '/api/worldmap/annotations',
): string {
  return `${buildWorldMapAnnotationUrl(id, basePath)}?network=${encodeURIComponent(String(networkId))}`;
}

export const buildWorldMapPutAnnotationUrl = buildWorldMapUpdateAnnotationUrl;

/** Keep mutation requests limited to the public annotation contract. */
export function toWorldMapAnnotationPayload(input: WorldMapAnnotationInput): WorldMapAnnotationInput {
  const payload: WorldMapAnnotationInput = {
    networkId: input.networkId,
    dimension: input.dimension,
    x: input.x,
    y: input.y,
    z: input.z,
    label: input.label,
    note: input.note,
    color: input.color,
  };
  if (input.fromVersion !== undefined) payload.fromVersion = input.fromVersion;
  if (input.toVersion !== undefined) payload.toVersion = input.toVersion;
  return payload;
}

export function markerDimension(marker: WorldMapDiffMarkerDto | null | undefined): number | undefined {
  if (!marker) return undefined;
  return finiteNumber(marker.dimension) ?? finiteNumber(marker.dim);
}

function finiteNumber(value: unknown): number | undefined {
  if (typeof value === 'number') return Number.isFinite(value) ? value : undefined;
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : undefined;
  }
  return undefined;
}

function stringValue(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() !== '' ? value : undefined;
}

export function normalizeWorldMapDiffMarker(
  marker: WorldMapDiffMarkerDto | null | undefined,
): WorldMapDiffMarkerDto | null {
  if (!marker) return null;
  const dimension = markerDimension(marker);
  return {
    ...marker,
    ...(dimension === undefined ? {} : { dimension }),
  };
}

export function normalizeWorldMapMarkerChange(change: WorldMapMarkerChangeDto): WorldMapMarkerChangeDto {
  const from = normalizeWorldMapDiffMarker(change.from);
  const to = normalizeWorldMapDiffMarker(change.to);
  return {
    ...change,
    ...(from ? { from } : {}),
    ...(to ? { to } : {}),
    ...(change.id || from?.id || to?.id ? { id: change.id ?? from?.id ?? to?.id } : {}),
  };
}

export interface WorldMapDiffFilter {
  dimension?: number | null;
  minX?: number | null;
  maxX?: number | null;
  minZ?: number | null;
  maxZ?: number | null;
}

function hasSpatialFilter(filter: WorldMapDiffFilter): boolean {
  return filter.dimension != null
    || filter.minX != null
    || filter.maxX != null
    || filter.minZ != null
    || filter.maxZ != null;
}

/**
 * Adapt the Java diff DTO's flattened `fromX`/`toX` fields to the nested
 * marker shape used by newer adapters. Placement changes may not have a
 * nested marker at all, so a spatial filter must inspect both representations.
 */
function markerChangeSide(
  change: WorldMapMarkerChangeDto,
  side: 'from' | 'to',
): WorldMapDiffMarkerDto | null {
  const nested = side === 'from' ? change.from : change.to;
  if (nested) return normalizeWorldMapDiffMarker(nested);

  const record = change as unknown as Record<string, unknown>;
  const prefix = side === 'from' ? 'from' : 'to';
  const placement = side === 'from' ? change.fromPlacement : change.toPlacement;
  // Java primitives serialize the absent side of added/removed rows as zeroes.
  // The status is the only reliable way to avoid inventing a marker at 0/0/0.
  const status = normalizeWorldMapDiffStatus(change.status);
  if (!placement && ((side === 'from' && status === 'added') || (side === 'to' && status === 'removed'))) {
    return null;
  }

  const x = finiteNumber(placement?.x) ?? finiteNumber(record[`${prefix}X`]);
  const y = finiteNumber(placement?.y) ?? finiteNumber(record[`${prefix}Y`]);
  const z = finiteNumber(placement?.z) ?? finiteNumber(record[`${prefix}Z`]);
  const dimension = finiteNumber(placement?.dimension)
    ?? finiteNumber(placement?.dim)
    ?? finiteNumber(record[`${prefix}Dim`])
    ?? finiteNumber(record[`${prefix}Dimension`]);
  const kind = stringValue(placement?.kind) ?? stringValue(record[`${prefix}Kind`]);
  const type = stringValue(record[`${prefix}Type`]);
  const subtype = stringValue(record[`${prefix}Subtype`]);
  const className = stringValue(placement?.className) ?? stringValue(record[`${prefix}ClassName`]);
  const iconItemId = stringValue(placement?.iconItemId) ?? stringValue(record[`${prefix}IconItemId`]);
  const displayName = stringValue(placement?.displayName) ?? stringValue(record[`${prefix}DisplayName`]);
  const hasData = x !== undefined || y !== undefined || z !== undefined || dimension !== undefined
    || kind !== undefined || type !== undefined || subtype !== undefined
    || className !== undefined || iconItemId !== undefined || displayName !== undefined;
  if (!hasData) return null;
  return {
    id: change.id ?? '',
    ...(x === undefined ? {} : { x }),
    ...(y === undefined ? {} : { y }),
    ...(z === undefined ? {} : { z }),
    ...(dimension === undefined ? {} : { dimension }),
    ...(kind === undefined ? {} : { kind }),
    ...(type === undefined ? {} : { type }),
    ...(subtype === undefined ? {} : { subtype }),
    ...(className === undefined ? {} : { className }),
    ...(iconItemId === undefined ? {} : { iconItemId }),
    ...(displayName === undefined ? {} : { displayName }),
  };
}

function inBounds(marker: WorldMapDiffMarkerDto | null | undefined, filter: WorldMapDiffFilter): boolean {
  if (!marker) return false;
  const dimension = markerDimension(marker);
  if (filter.dimension != null && dimension !== filter.dimension) return false;
  const x = finiteNumber(marker.x);
  const z = finiteNumber(marker.z);
  if (filter.minX != null && (x == null || x < filter.minX)) return false;
  if (filter.maxX != null && (x == null || x > filter.maxX)) return false;
  if (filter.minZ != null && (z == null || z < filter.minZ)) return false;
  if (filter.maxZ != null && (z == null || z > filter.maxZ)) return false;
  return true;
}

export function filterWorldMapMarkerChanges(
  changes: WorldMapMarkerChangeDto[] | null | undefined,
  filter: WorldMapDiffFilter = {},
): WorldMapMarkerChangeDto[] {
  if (!changes) return [];
  const normalized = changes.map(normalizeWorldMapMarkerChange);
  // No spatial filter means the server's complete change list should pass
  // through, including placement changes that intentionally have no marker
  // object in `from`/`to`.
  if (!hasSpatialFilter(filter)) return normalized;
  return normalized.filter((change) =>
    inBounds(markerChangeSide(change, 'from'), filter) || inBounds(markerChangeSide(change, 'to'), filter),
  );
}

export function filterWorldMapTileChanges(
  changes: WorldMapTileChangeDto[] | null | undefined,
  filter: WorldMapDiffFilter = {},
): WorldMapTileChangeDto[] {
  if (!changes) return [];
  if (!hasSpatialFilter(filter)) return [...changes];
  return changes.filter((change) => {
    const dimension = finiteNumber(change.dimension) ?? finiteNumber(change.dim);
    const chunkX = finiteNumber(change.chunkX);
    const chunkZ = finiteNumber(change.chunkZ);
    if (filter.dimension != null && dimension !== filter.dimension) return false;
    if (chunkX == null || chunkZ == null) return false;
    if (filter.minX != null && chunkX * 16 + 15 < filter.minX) return false;
    if (filter.maxX != null && chunkX * 16 > filter.maxX) return false;
    if (filter.minZ != null && chunkZ * 16 + 15 < filter.minZ) return false;
    if (filter.maxZ != null && chunkZ * 16 > filter.maxZ) return false;
    return true;
  });
}

export function filterWorldMapVersionDiff(
  diff: WorldMapVersionDiffResponse | null | undefined,
  filter: WorldMapDiffFilter = {},
): WorldMapVersionDiffResponse | null {
  if (!diff) return null;
  const markerChanges = filterWorldMapMarkerChanges(diff.markerChanges, filter);
  const tileChanges = filterWorldMapTileChanges(diff.tileChanges, filter);
  return {
    ...diff,
    markerChanges,
    tileChanges,
    // The server summary describes its unfiltered result. Once the browser
    // applies a spatial filter, keep the summary aligned with visible rows.
    ...(hasSpatialFilter(filter) ? { summary: summaryFromChanges(markerChanges, tileChanges) } : {}),
  };
}

export interface WorldMapDiffCounts {
  added: number;
  removed: number;
  changed: number;
  moved: number;
  unchanged: number;
  unknown: number;
  total: number;
  markers: number;
  tiles: number;
  truncated: boolean;
  logicalAvailable: boolean;
}

function countStatuses(
  changes: Array<{ status?: unknown }> | null | undefined,
): Record<WorldMapDiffStatus, number> {
  const counts: Record<WorldMapDiffStatus, number> = {
    added: 0,
    removed: 0,
    changed: 0,
    moved: 0,
    unchanged: 0,
    unknown: 0,
  };
  for (const change of changes ?? []) counts[normalizeWorldMapDiffStatus(change.status)] += 1;
  return counts;
}

function summaryFromChanges(
  markerChanges: WorldMapMarkerChangeDto[],
  tileChanges: WorldMapTileChangeDto[],
): WorldMapDiffSummaryDto {
  const markers = countStatuses(markerChanges);
  const tiles = countStatuses(tileChanges);
  const markerTotal = markers.added + markers.removed + markers.changed + markers.moved;
  const tileTotal = tiles.added + tiles.removed + tiles.changed + tiles.unchanged;
  return {
    markersAdded: markers.added,
    markersRemoved: markers.removed,
    markersChanged: markers.changed,
    markersMoved: markers.moved,
    tilesAdded: tiles.added,
    tilesRemoved: tiles.removed,
    tilesChanged: tiles.changed,
    tilesUnchanged: tiles.unchanged,
    markerTotal,
    tileTotal,
    total: markerTotal + tileTotal,
  };
}

export function summarizeWorldMapDiff(diff: WorldMapVersionDiffResponse | null | undefined): WorldMapDiffCounts {
  const markers = diff?.markerChanges ?? [];
  const tiles = diff?.tileChanges ?? [];
  const markerCounts = countStatuses(markers);
  const tileCounts = countStatuses(tiles);
  const markerUnknown = markerCounts.unknown;
  const tileUnknown = tileCounts.unknown;
  const unknown = markerUnknown + tileUnknown;
  const serverSummary = diff?.summary;
  if (serverSummary) {
    // Detail arrays share a bounded response budget and can be shorter than
    // the complete server-side counts. Unknown future statuses are not part
    // of the current Java summary schema, so retain their visible detail count.
    return {
      added: serverSummary.markersAdded + serverSummary.tilesAdded,
      removed: serverSummary.markersRemoved + serverSummary.tilesRemoved,
      changed: serverSummary.markersChanged + serverSummary.tilesChanged,
      moved: serverSummary.markersMoved,
      unchanged: serverSummary.tilesUnchanged,
      unknown,
      total: serverSummary.total,
      markers: serverSummary.markerTotal,
      tiles: serverSummary.tileTotal,
      truncated: diff?.truncated === true,
      logicalAvailable: normalizeLogicalAvailability(diff?.logicalAvailable),
    };
  }
  return {
    added: markerCounts.added + tileCounts.added,
    removed: markerCounts.removed + tileCounts.removed,
    changed: markerCounts.changed + tileCounts.changed,
    moved: markerCounts.moved + tileCounts.moved,
    unchanged: markerCounts.unchanged + tileCounts.unchanged,
    unknown,
    total: markers.length + tiles.length,
    markers: markers.length,
    tiles: tiles.length,
    truncated: diff?.truncated === true,
    logicalAvailable: normalizeLogicalAvailability(diff?.logicalAvailable),
  };
}

export const getWorldMapDiffSummary = summarizeWorldMapDiff;
export const formatWorldMapDiffSummary = summarizeWorldMapDiff;

export interface WorldMapDiffDetail {
  status: WorldMapDiffStatus;
  color: string;
  id?: string;
  kind?: string;
  label: string;
  from?: WorldMapDiffMarkerDto | null;
  to?: WorldMapDiffMarkerDto | null;
}

export function describeWorldMapMarkerChange(change: WorldMapMarkerChangeDto): WorldMapDiffDetail {
  const normalized = normalizeWorldMapMarkerChange(change);
  const status = normalizeWorldMapDiffStatus(normalized.status);
  const from = markerChangeSide(normalized, 'from');
  const to = markerChangeSide(normalized, 'to');
  const source = to ?? from;
  const kind = source?.kind ?? source?.type ?? source?.subtype;
  return {
    status,
    color: worldMapDiffColor(status),
    id: normalized.id ?? from?.id ?? to?.id,
    kind,
    label: source?.displayName ?? source?.className ?? kind ?? normalized.id ?? '',
    from,
    to,
  };
}

export const worldMapDiffDetail = describeWorldMapMarkerChange;
export const describeWorldMapDiffChange = describeWorldMapMarkerChange;

export type WorldMapDiffDataState = 'loading' | 'error' | 'unknown' | 'partial' | 'empty' | 'ready';

export interface WorldMapDiffStateInput {
  loading?: boolean;
  error?: unknown;
  diff?: WorldMapVersionDiffResponse | null;
  includeMarkers?: boolean;
  includeTiles?: boolean;
}

export function worldMapDiffState(input: WorldMapDiffStateInput): WorldMapDiffDataState {
  if (input.loading) return 'loading';
  if (input.error) return 'error';
  const diff = input.diff;
  if (!diff) return 'unknown';
  if (diff.status === 'unknown') return 'unknown';
  if (diff.success === false) return 'error';
  if (diff.code === 'same') return 'empty';
  if (diff.truncated === true) return 'partial';

  const includeMarkers = input.includeMarkers !== false;
  const includeTiles = input.includeTiles !== false;
  if (includeMarkers) {
    const sides = logicalAvailabilitySides(diff.logicalAvailable);
    if (sides.from === false || sides.to === false) return 'partial';
    if (!sides.known) return 'unknown';
    if (!Array.isArray(diff.markerChanges)) return 'partial';
  }
  if (includeTiles && !Array.isArray(diff.tileChanges)) return 'partial';

  const markerTotal = includeMarkers && Array.isArray(diff.markerChanges) ? diff.markerChanges.length : 0;
  const tileTotal = includeTiles && Array.isArray(diff.tileChanges) ? diff.tileChanges.length : 0;
  return markerTotal + tileTotal === 0 ? 'empty' : 'ready';
}

export const getWorldMapDiffState = worldMapDiffState;

export function isVersionInRange(
  version: number,
  fromVersion?: number | null,
  toVersion?: number | null,
): boolean {
  // Query versions must be positive; only annotation bounds use zero as an
  // open-ended sentinel in the Java service contract.
  if (!Number.isFinite(version) || version <= 0) return false;
  const from = fromVersion != null && fromVersion > 0 ? fromVersion : null;
  const to = toVersion != null && toVersion > 0 ? toVersion : null;
  if (from != null && to != null && from > to) return false;
  if (from != null && version < from) return false;
  if (to != null && version > to) return false;
  return true;
}

export function isWorldMapAnnotationApplicable(annotation: Pick<WorldMapAnnotationDto, 'fromVersion' | 'toVersion'>, version: number): boolean {
  return isVersionInRange(version, annotation.fromVersion, annotation.toVersion);
}

export const isAnnotationApplicable = isWorldMapAnnotationApplicable;

export function filterWorldMapAnnotationsByVersion(
  annotations: WorldMapAnnotationDto[] | null | undefined,
  version: number | null | undefined,
): WorldMapAnnotationDto[] {
  if (!annotations) return [];
  if (version == null) return [...annotations];
  return annotations.filter((annotation) => isWorldMapAnnotationApplicable(annotation, version));
}

export const filterAnnotationsByVersion = filterWorldMapAnnotationsByVersion;
export const filterAnnotationsForVersion = filterWorldMapAnnotationsByVersion;
