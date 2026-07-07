/** Chunk tile URL and visible-chunk helpers for the world map terrain layer. */

import {
  screenToWorld,
  worldToScreen,
  type MapViewport,
  type WorldMapOrigin,
} from '@/utils/worldMapProjection';

export interface ChunkCoord {
  chunkX: number;
  chunkZ: number;
}

export interface ChunkScope {
  minChunkX: number;
  maxChunkX: number;
  minChunkZ: number;
  maxChunkZ: number;
  allowedChunks?: string[] | null;
}

export function chunkKey(chunkX: number, chunkZ: number): string {
  return `${chunkX},${chunkZ}`;
}

export type WorldMapTileLayer = 'terrain' | 'ae';

export function buildWorldMapTileUrl(
  dim: number,
  chunkX: number,
  chunkZ: number,
  token: string | null,
  networkId: number,
  view = 'flat',
  layer: WorldMapTileLayer = 'terrain'
): string {
  const params = new URLSearchParams();
  params.set('network', String(networkId));
  if (token) {
    params.set('token', token);
  }
  const qs = params.toString();
  const layerSegment = layer === 'ae' ? '/ae' : '';
  return `/api/worldmap/tiles/${view}${layerSegment}/${dim}/${chunkX}/${chunkZ}.png?${qs}`;
}

export function isChunkInScope(chunkX: number, chunkZ: number, scope: ChunkScope | null): boolean {
  if (!scope) {
    return true;
  }
  if (scope.allowedChunks && scope.allowedChunks.length > 0) {
    return scope.allowedChunks.includes(chunkKey(chunkX, chunkZ));
  }
  if (scope.maxChunkX < scope.minChunkX || scope.maxChunkZ < scope.minChunkZ) {
    return false;
  }
  return (
    chunkX >= scope.minChunkX &&
    chunkX <= scope.maxChunkX &&
    chunkZ >= scope.minChunkZ &&
    chunkZ <= scope.maxChunkZ
  );
}

/** Intersect viewport-visible chunks with the allowed chunk scope. */
export function filterChunksByScope(chunks: ChunkCoord[], scope: ChunkScope | null): ChunkCoord[] {
  if (!scope) {
    return chunks;
  }
  return chunks.filter((c) => isChunkInScope(c.chunkX, c.chunkZ, scope));
}

/** Visible chunk coords for the current viewport (with optional padding). */
export function visibleChunksForViewport(
  viewport: MapViewport,
  origin: WorldMapOrigin,
  containerWidth: number,
  containerHeight: number,
  paddingChunks = 1
): ChunkCoord[] {
  if (containerWidth <= 0 || containerHeight <= 0) {
    return [];
  }

  const corners = [
    screenToWorld(0, 0, viewport, origin),
    screenToWorld(containerWidth, 0, viewport, origin),
    screenToWorld(0, containerHeight, viewport, origin),
    screenToWorld(containerWidth, containerHeight, viewport, origin),
  ];

  let minX = Infinity;
  let maxX = -Infinity;
  let minZ = Infinity;
  let maxZ = -Infinity;
  for (const c of corners) {
    minX = Math.min(minX, c.x);
    maxX = Math.max(maxX, c.x);
    minZ = Math.min(minZ, c.z);
    maxZ = Math.max(maxZ, c.z);
  }

  const minChunkX = Math.floor(minX / 16) - paddingChunks;
  const maxChunkX = Math.floor(maxX / 16) + paddingChunks;
  const minChunkZ = Math.floor(minZ / 16) - paddingChunks;
  const maxChunkZ = Math.floor(maxZ / 16) + paddingChunks;

  const out: ChunkCoord[] = [];
  for (let cx = minChunkX; cx <= maxChunkX; cx++) {
    for (let cz = minChunkZ; cz <= maxChunkZ; cz++) {
      out.push({ chunkX: cx, chunkZ: cz });
    }
  }
  return out;
}

/** Screen position and size for a chunk tile image. */
export function chunkTileScreenRect(
  chunkX: number,
  chunkZ: number,
  viewport: MapViewport,
  origin: WorldMapOrigin
): { left: number; top: number; size: number } {
  const tileScreenSize = 16 * origin.pxPerBlock * viewport.scale;
  const { sx, sy } = worldToScreen(chunkX * 16, chunkZ * 16 + 15, viewport, origin);
  return { left: sx, top: sy, size: tileScreenSize };
}

/** Block bounds from allowed chunk bbox (for fitBounds). */
export function boundsFromChunkScope(scope: ChunkScope | null): {
  minX: number;
  maxX: number;
  minZ: number;
  maxZ: number;
} | null {
  if (!scope || scope.maxChunkX < scope.minChunkX || scope.maxChunkZ < scope.minChunkZ) {
    return null;
  }
  return {
    minX: scope.minChunkX * 16,
    maxX: scope.maxChunkX * 16 + 15,
    minZ: scope.minChunkZ * 16,
    maxZ: scope.maxChunkZ * 16 + 15,
  };
}
