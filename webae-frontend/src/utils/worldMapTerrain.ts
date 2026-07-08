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

export type WorldMapQualityTierId = 'low' | 'medium' | 'high' | 'ultra';

export interface WorldMapZoomLevelInfo {
  level: number;
  chunkSpan: number;
  tilePx: number;
  pxPerBlock: number;
}

const QUALITY_ORDER: WorldMapQualityTierId[] = ['low', 'medium', 'high', 'ultra'];

export function clampWorldMapQuality(
  requested: WorldMapQualityTierId,
  maxTier: WorldMapQualityTierId
): WorldMapQualityTierId {
  const reqIdx = QUALITY_ORDER.indexOf(requested);
  const maxIdx = QUALITY_ORDER.indexOf(maxTier);
  const safeReq = reqIdx >= 0 ? reqIdx : QUALITY_ORDER.indexOf('medium');
  const safeMax = maxIdx >= 0 ? maxIdx : QUALITY_ORDER.length - 1;
  return QUALITY_ORDER[Math.min(safeReq, safeMax)];
}

export function chunkSpanForZoom(zoom: number): number {
  if (zoom <= 0) return 1;
  return 1 << zoom;
}

export function tileIndexForChunk(chunkCoord: number, zoom: number): number {
  const span = chunkSpanForZoom(zoom);
  if (chunkCoord >= 0) {
    return Math.floor(chunkCoord / span);
  }
  return Math.floor((chunkCoord - span + 1) / span);
}

/** Pick pyramid zoom level from viewport scale (higher scale → finer z0). */
export function selectWorldMapZoomLevel(
  scale: number,
  pxPerBlock: number,
  maxLevel: number
): number {
  const safeMax = Math.max(0, maxLevel - 1);
  const pxPerBlockScaled = pxPerBlock * scale;
  if (pxPerBlockScaled >= 6 || safeMax <= 0) return 0;
  if (pxPerBlockScaled >= 3 && safeMax >= 1) return 1;
  return safeMax >= 2 ? 2 : safeMax;
}

export interface WorldMapTileCoord {
  tileX: number;
  tileZ: number;
  zoom: number;
}

export function tileKey(tileX: number, tileZ: number, zoom = 0): string {
  return zoom > 0 ? `${zoom}:${tileX},${tileZ}` : chunkKey(tileX, tileZ);
}

export function buildWorldMapTileUrl(
  dim: number,
  tileX: number,
  tileZ: number,
  token: string | null,
  networkId: number,
  view = 'flat',
  layer: WorldMapTileLayer = 'terrain',
  quality: WorldMapQualityTierId = 'medium',
  zoom = 0
): string {
  const params = new URLSearchParams();
  params.set('network', String(networkId));
  params.set('quality', quality);
  if (zoom > 0) {
    params.set('zoom', String(zoom));
  }
  if (token) {
    params.set('token', token);
  }
  const qs = params.toString();
  const layerSegment = layer === 'ae' ? '/ae' : '';
  return `/api/worldmap/tiles/${view}${layerSegment}/${dim}/${tileX}/${tileZ}.png?${qs}`;
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

/** Visible tile coords at a zoom pyramid level (tile indices, not chunk indices when zoom > 0). */
export function visibleTilesForViewport(
  viewport: MapViewport,
  origin: WorldMapOrigin,
  containerWidth: number,
  containerHeight: number,
  zoom = 0,
  paddingTiles = 1
): WorldMapTileCoord[] {
  const chunks = visibleChunksForViewport(
    viewport,
    origin,
    containerWidth,
    containerHeight,
    paddingTiles
  );
  if (zoom <= 0) {
    return chunks.map((c) => ({ tileX: c.chunkX, tileZ: c.chunkZ, zoom: 0 }));
  }
  const seen = new Set<string>();
  const out: WorldMapTileCoord[] = [];
  for (const c of chunks) {
    const tileX = tileIndexForChunk(c.chunkX, zoom);
    const tileZ = tileIndexForChunk(c.chunkZ, zoom);
    const key = tileKey(tileX, tileZ, zoom);
    if (seen.has(key)) continue;
    seen.add(key);
    out.push({ tileX, tileZ, zoom });
  }
  return out;
}

/** Screen position and size for a chunk tile image (z0). */
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

/** Screen rect for a pyramid tile (z0 = one chunk, z1 = 2×2 chunks, …). */
export function pyramidTileScreenRect(
  tileX: number,
  tileZ: number,
  zoom: number,
  viewport: MapViewport,
  origin: WorldMapOrigin
): { left: number; top: number; size: number } {
  const span = chunkSpanForZoom(zoom);
  const tileScreenSize = 16 * span * origin.pxPerBlock * viewport.scale;
  const worldX = tileX * span * 16;
  const worldZ = tileZ * span * 16 + span * 16 - 1;
  const { sx, sy } = worldToScreen(worldX, worldZ, viewport, origin);
  return { left: sx, top: sy, size: tileScreenSize };
}

/** Rounded screen rect to reduce sub-pixel tile seams. */
export function chunkTileScreenStyle(
  chunkX: number,
  chunkZ: number,
  viewport: MapViewport,
  origin: WorldMapOrigin
): { left: number; top: number; width: number; height: number } {
  const rect = chunkTileScreenRect(chunkX, chunkZ, viewport, origin);
  return {
    left: Math.round(rect.left),
    top: Math.round(rect.top),
    width: Math.round(rect.size),
    height: Math.round(rect.size),
  };
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
