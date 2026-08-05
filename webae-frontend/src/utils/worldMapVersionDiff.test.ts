import { describe, expect, it } from 'vitest';

import type { WorldMapVersionDiffResponse } from '@/types/dto';
import {
  WORLD_MAP_DIFF_COLORS,
  buildWorldMapAnnotationUrl,
  buildWorldMapAnnotationsUrl,
  buildWorldMapDeleteAnnotationUrl,
  buildWorldMapDiffUrl,
  buildWorldMapUpdateAnnotationUrl,
  buildWorldMapVersionsUrl,
  describeWorldMapMarkerChange,
  filterWorldMapAnnotationsByVersion,
  filterWorldMapMarkerChanges,
  filterWorldMapTileChanges,
  filterWorldMapVersionDiff,
  getWorldMapDiffState,
  isVersionInRange,
  normalizeLogicalAvailability,
  normalizeWorldMapMarkerChange,
  selectWorldMapVersionPair,
  summarizeWorldMapDiff,
  toWorldMapAnnotationPayload,
  worldMapDiffColor,
} from './worldMapVersionDiff';

const baseDiff = (patch: Partial<WorldMapVersionDiffResponse> = {}): WorldMapVersionDiffResponse => ({
  success: true,
  fromVersion: 1,
  toVersion: 2,
  logicalAvailable: true,
  markerChanges: [],
  tileChanges: [],
  ...patch,
});

describe('world-map version URL contracts', () => {
  it('encodes network/version/range values and preserves deterministic query order', () => {
    expect(buildWorldMapVersionsUrl('network 1')).toBe('/api/worldmap/versions?network=network%201');
    expect(
      buildWorldMapDiffUrl('net/1', {
        fromVersion: 2,
        toVersion: 9,
        dimension: -1,
        minX: -16,
        maxX: 32,
        minZ: 0,
        maxZ: 48,
        includeTiles: false,
        includeMarkers: true,
      }),
    ).toBe(
      '/api/worldmap/diff?network=net%2F1&from=2&to=9&dimension=-1&minX=-16&maxX=32&minZ=0&maxZ=48&includeTiles=false&includeMarkers=true',
    );
  });

  it('encodes annotation ids and keeps delete network scoping explicit', () => {
    expect(buildWorldMapAnnotationsUrl(7, 3)).toBe('/api/worldmap/annotations?network=7&version=3');
    expect(buildWorldMapAnnotationUrl('a/b c')).toBe('/api/worldmap/annotations/a%2Fb%20c');
    expect(buildWorldMapDeleteAnnotationUrl('a/b c', 7)).toBe(
      '/api/worldmap/annotations/a%2Fb%20c?network=7',
    );
    expect(buildWorldMapUpdateAnnotationUrl('a/b c', 7)).toBe(
      '/api/worldmap/annotations/a%2Fb%20c?network=7',
    );
  });
});

describe('world-map version selection and availability', () => {
  it('defaults to previous -> current, falling back to sorted versions', () => {
    expect(
      selectWorldMapVersionPair({ versions: [{ version: 4 }, { version: 9 }, { version: 6 }] }),
    ).toEqual({ currentVersion: 9, previousVersion: 6, fromVersion: 6, toVersion: 9 });
    expect(
      selectWorldMapVersionPair({ currentVersion: 8, previousVersion: 3, versions: [{ version: 8 }] }),
    ).toEqual({ currentVersion: 8, previousVersion: 3, fromVersion: 3, toVersion: 8 });
    expect(
      selectWorldMapVersionPair({ currentVersion: 8, previousVersion: 0, versions: [{ version: 8 }] }),
    ).toEqual({ currentVersion: 8, previousVersion: null, fromVersion: 8, toVersion: 8 });
    expect(selectWorldMapVersionPair({ currentVersion: 0, previousVersion: 0, versions: [] })).toEqual({
      currentVersion: null,
      previousVersion: null,
      fromVersion: null,
      toVersion: null,
    });
  });

  it('requires both sides of an object response before claiming logical availability', () => {
    expect(normalizeLogicalAvailability(true)).toBe(true);
    expect(normalizeLogicalAvailability({ from: true, to: true })).toBe(true);
    expect(normalizeLogicalAvailability({ from: true, to: false })).toBe(false);
    expect(normalizeLogicalAvailability({ from: true })).toBe(false);
    expect(normalizeLogicalAvailability(undefined)).toBe(false);
  });
});

describe('world-map diff presentation/filtering', () => {
  it('maps statuses to stable semantic colors', () => {
    expect(worldMapDiffColor('added')).toBe(WORLD_MAP_DIFF_COLORS.added);
    expect(worldMapDiffColor('removed')).toBe(WORLD_MAP_DIFF_COLORS.removed);
    expect(worldMapDiffColor('moved')).toBe(WORLD_MAP_DIFF_COLORS.moved);
    expect(worldMapDiffColor('changed')).toBe(WORLD_MAP_DIFF_COLORS.changed);
    expect(worldMapDiffColor('not-a-status')).toBe(WORLD_MAP_DIFF_COLORS.unknown);
  });

  it('normalizes dim aliases and filters marker/tile records by dimension and bounds', () => {
    const marker = normalizeWorldMapMarkerChange({
      status: 'moved',
      from: { id: 'm', dim: 0, x: 1, y: 64, z: 2, displayName: 'Old' },
      to: { id: 'm', dimension: 0, x: 32, y: 64, z: 2, displayName: 'New' },
    });
    expect(marker.to?.dimension).toBe(0);
    expect(filterWorldMapMarkerChanges([marker], { dimension: 0, minX: 30, maxX: 40 })).toHaveLength(1);
    expect(filterWorldMapMarkerChanges([marker], { dimension: 1 })).toHaveLength(0);
    expect(
      filterWorldMapTileChanges(
        [
          { status: 'changed', key: 'a', layer: 'terrain', dimension: 0, chunkX: 2, chunkZ: 3 },
          { status: 'changed', key: 'b', layer: 'terrain', dimension: 0, chunkX: 8, chunkZ: 3 },
        ],
        { dimension: 0, minX: 32, maxX: 63, minZ: 48, maxZ: 63 },
      ),
    ).toHaveLength(1);
  });

  it('keeps placement-only changes and reads flattened Java DTO coordinates', () => {
    const placement = {
      id: 'placement',
      status: 'moved',
      source: 'placement',
      fromDim: 0,
      fromX: 1,
      fromY: 64,
      fromZ: 2,
      toDim: 0,
      toX: 33,
      toY: 64,
      toZ: 2,
      toKind: 'drive',
    };
    expect(filterWorldMapMarkerChanges([placement])).toHaveLength(1);
    expect(filterWorldMapMarkerChanges([placement], { dimension: 0, minX: 32, maxX: 40 })).toHaveLength(1);
    expect(filterWorldMapMarkerChanges([placement], { dimension: 1 })).toHaveLength(0);
    expect(describeWorldMapMarkerChange(placement)).toMatchObject({
      id: 'placement',
      kind: 'drive',
      from: { dimension: 0, x: 1, y: 64, z: 2 },
      to: { dimension: 0, x: 33, y: 64, z: 2 },
    });
    expect(
      filterWorldMapTileChanges(
        [{ status: 'changed', key: 'terrain:0:2:3', layer: 'terrain', dim: 0, chunkX: 2, chunkZ: 3 }],
        { dimension: 0 },
      ),
    ).toHaveLength(1);

    const nestedPlacement = {
      id: 'nested-placement',
      status: 'changed',
      source: 'placement',
      fromPlacement: {
        dim: 0,
        x: 4,
        y: 65,
        z: 6,
        kind: 'block',
        className: 'OldDrive',
        iconItemId: 'appliedenergistics2:item.ItemMultiMaterial:36',
        displayName: 'Old Drive',
      },
      toPlacement: {
        dim: 0,
        x: 36,
        y: 65,
        z: 6,
        kind: 'block',
        className: 'NewDrive',
        iconItemId: 'appliedenergistics2:item.ItemMultiMaterial:37',
        displayName: 'New Drive',
      },
    };
    expect(filterWorldMapMarkerChanges([nestedPlacement], { dimension: 0, minX: 32 })).toHaveLength(1);
    expect(describeWorldMapMarkerChange(nestedPlacement)).toMatchObject({
      id: 'nested-placement',
      kind: 'block',
      label: 'New Drive',
      from: { dimension: 0, x: 4, className: 'OldDrive' },
      to: { dimension: 0, x: 36, className: 'NewDrive' },
    });

    // The Java DTO emits zero-valued flattened primitives for the absent side.
    // An added row must not grow a phantom `from` marker at the origin.
    expect(
      describeWorldMapMarkerChange({
        id: 'added-placement',
        status: 'added',
        fromDim: 0,
        fromX: 0,
        fromY: 0,
        fromZ: 0,
        toDim: 0,
        toX: 8,
        toY: 64,
        toZ: 8,
        toKind: 'cable',
      }),
    ).toMatchObject({ from: null, to: { dimension: 0, x: 8, y: 64, z: 8 } });
  });

  it('recomputes the server-shaped summary after client-side spatial filtering', () => {
    const summary = {
      markersAdded: 10,
      markersRemoved: 11,
      markersChanged: 12,
      markersMoved: 13,
      tilesAdded: 14,
      tilesRemoved: 15,
      tilesChanged: 16,
      tilesUnchanged: 17,
      markerTotal: 46,
      tileTotal: 62,
      total: 108,
    };
    const diff = baseDiff({
      summary,
      markerChanges: [
        { status: 'added', id: 'visible', to: { id: 'visible', dim: 0, x: 2, y: 64, z: 2 } },
        { status: 'moved', id: 'hidden', to: { id: 'hidden', dim: 1, x: 2, y: 64, z: 2 } },
        { status: 'future-status', id: 'unknown', to: { id: 'unknown', dim: 0, x: 3, y: 64, z: 3 } },
      ],
      tileChanges: [
        { status: 'changed', key: 'visible-tile', layer: 'terrain', dim: 0, chunkX: 0, chunkZ: 0 },
        { status: 'unchanged', key: 'hidden-tile', layer: 'terrain', dim: 1, chunkX: 0, chunkZ: 0 },
      ],
    });

    expect(filterWorldMapVersionDiff(diff)?.summary).toBe(summary);
    const filtered = filterWorldMapVersionDiff(diff, { dimension: 0 });
    expect(filtered?.markerChanges).toHaveLength(2);
    expect(filtered?.tileChanges).toHaveLength(1);
    expect(filtered?.summary).toEqual({
      markersAdded: 1,
      markersRemoved: 0,
      markersChanged: 0,
      markersMoved: 0,
      tilesAdded: 0,
      tilesRemoved: 0,
      tilesChanged: 1,
      tilesUnchanged: 0,
      markerTotal: 1,
      tileTotal: 1,
      total: 2,
    });
  });

  it('summarizes changes and distinguishes unknown, partial, empty, and ready states', () => {
    expect(getWorldMapDiffState({ diff: null })).toBe('unknown');
    expect(getWorldMapDiffState({ diff: baseDiff({ success: false, status: 'unknown' }) })).toBe('unknown');
    expect(getWorldMapDiffState({ diff: baseDiff({ success: true, status: 'unknown' }) })).toBe('unknown');
    expect(getWorldMapDiffState({ diff: baseDiff({ success: false, status: 'error' }) })).toBe('error');
    expect(getWorldMapDiffState({ diff: baseDiff({ code: 'same', logicalAvailable: false }) })).toBe('empty');
    expect(getWorldMapDiffState({ diff: baseDiff({ logicalAvailable: { from: true, to: false } }) })).toBe('partial');
    expect(
      getWorldMapDiffState({
        diff: baseDiff({
          logicalAvailable: false,
          markerChanges: null,
          tileChanges: [{ status: 'changed', key: 'tile', layer: 'terrain', dim: 0, chunkX: 0, chunkZ: 0 }],
        }),
        includeMarkers: false,
        includeTiles: true,
      }),
    ).toBe('ready');
    expect(
      getWorldMapDiffState({
        diff: baseDiff({ logicalAvailable: false, markerChanges: null, tileChanges: [] }),
        includeMarkers: false,
        includeTiles: true,
      }),
    ).toBe('empty');
    expect(
      getWorldMapDiffState({
        diff: baseDiff({ logicalAvailable: false, markerChanges: [], tileChanges: null }),
        includeMarkers: true,
        includeTiles: false,
      }),
    ).toBe('partial');
    expect(
      getWorldMapDiffState({
        diff: baseDiff({ logicalAvailable: false, markerChanges: null, tileChanges: [], truncated: true }),
        includeMarkers: false,
        includeTiles: true,
      }),
    ).toBe('partial');
    expect(getWorldMapDiffState({ diff: baseDiff() })).toBe('empty');
    expect(
      getWorldMapDiffState({
        diff: baseDiff({ markerChanges: [{ status: 'added', id: 'm', to: { id: 'm' } }] }),
      }),
    ).toBe('ready');
    expect(
      summarizeWorldMapDiff(
        baseDiff({
          markerChanges: [{ status: 'added' }, { status: 'moved' }],
          tileChanges: [{ status: 'removed', key: 't', layer: 'terrain', dimension: 0, chunkX: 0, chunkZ: 0 }],
          truncated: true,
        }),
      ),
    ).toMatchObject({ added: 1, moved: 1, removed: 1, total: 3, markers: 2, tiles: 1, truncated: true });
    expect(
      summarizeWorldMapDiff(
        baseDiff({
          summary: {
            markersAdded: 4,
            markersRemoved: 0,
            markersChanged: 0,
            markersMoved: 0,
            tilesAdded: 0,
            tilesRemoved: 0,
            tilesChanged: 3,
            tilesUnchanged: 0,
            markerTotal: 4,
            tileTotal: 3,
            total: 7,
          },
          markerChanges: [{ status: 'added' }],
          tileChanges: [{ status: 'changed', key: 'first-detail', layer: 'terrain', dim: 0, chunkX: 0, chunkZ: 0 }],
          truncated: true,
        }),
      ),
    ).toMatchObject({ added: 4, changed: 3, total: 7, markers: 4, tiles: 3, truncated: true });
    expect(describeWorldMapMarkerChange({ status: 'changed', to: { id: 'x', kind: 'drive' } })).toMatchObject({
      status: 'changed',
      id: 'x',
      kind: 'drive',
    });
  });
});

describe('world-map annotation ranges and payloads', () => {
  it('uses inclusive version ranges and preserves open-ended annotations', () => {
    expect(isVersionInRange(5, 5, 7)).toBe(true);
    expect(isVersionInRange(7, 5, 7)).toBe(true);
    expect(isVersionInRange(8, 5, 7)).toBe(false);
    expect(isVersionInRange(3, 7, 5)).toBe(false);
    expect(isVersionInRange(8, 5, 0)).toBe(true);
    expect(isVersionInRange(1, 0, 4)).toBe(true);
    expect(isVersionInRange(0, 5, 7)).toBe(false);
    const annotations = [
      { id: 'a', fromVersion: 2, toVersion: 4 },
      { id: 'b', fromVersion: null, toVersion: null },
      { id: 'c', fromVersion: 5, toVersion: null },
      { id: 'd', fromVersion: 5, toVersion: 0 },
    ] as any;
    expect(filterWorldMapAnnotationsByVersion(annotations, 3).map((entry) => entry.id)).toEqual(['a', 'b']);
    expect(filterWorldMapAnnotationsByVersion(annotations, 6).map((entry) => entry.id)).toEqual(['b', 'c', 'd']);
    expect(filterWorldMapAnnotationsByVersion(annotations, 0)).toEqual([]);
    expect(filterWorldMapAnnotationsByVersion(annotations, null)).toEqual(annotations);
  });

  it('sends only the public CRUD fields', () => {
    expect(
      toWorldMapAnnotationPayload({
        id: 'private',
        ownerUuid: 'owner',
        createdAt: 1,
        networkId: 4,
        dimension: 0,
        x: 1,
        y: 2,
        z: 3,
        label: 'Portal',
        note: 'N',
        color: '#fff',
        fromVersion: 2,
        toVersion: null,
      } as any),
    ).toEqual({
      networkId: 4,
      dimension: 0,
      x: 1,
      y: 2,
      z: 3,
      label: 'Portal',
      note: 'N',
      color: '#fff',
      fromVersion: 2,
      toVersion: null,
    });
  });
});
