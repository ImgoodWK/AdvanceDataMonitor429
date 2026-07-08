/** World ↔ screen coordinate projection for the topology world map view. */

export interface MapViewport {
  panX: number;
  panY: number;
  scale: number;
}

export interface WorldMapOrigin {
  originX: number;
  originZ: number;
  pxPerBlock: number;
}

export interface WorldBounds {
  minX: number;
  maxX: number;
  minZ: number;
  maxZ: number;
}

export const WORLD_MAP_MIN_SCALE = 0.15;
export const WORLD_MAP_MAX_SCALE = 6;

/** Block world X/Z → screen pixels (Z screen-up is negative). */
export function worldToScreen(
  worldX: number,
  worldZ: number,
  viewport: MapViewport,
  origin: WorldMapOrigin
): { sx: number; sy: number } {
  const localX = (worldX - origin.originX) * origin.pxPerBlock;
  const localZ = (worldZ - origin.originZ) * origin.pxPerBlock;
  return {
    sx: localX * viewport.scale + viewport.panX,
    sy: -localZ * viewport.scale + viewport.panY,
  };
}

/** Screen pixels → block world X/Z (inverse of {@link worldToScreen}). */
export function screenToWorld(
  sx: number,
  sy: number,
  viewport: MapViewport,
  origin: WorldMapOrigin
): { x: number; z: number } {
  const localX = (sx - viewport.panX) / viewport.scale;
  const localZ = -(sy - viewport.panY) / viewport.scale;
  return {
    x: origin.originX + localX / origin.pxPerBlock,
    z: origin.originZ + localZ / origin.pxPerBlock,
  };
}

/** Compute pan/scale to fit world bounds inside a container.
 *  Uses the same origin as {@link originFromBounds} (minX, maxZ) so that
 *  pan calculations match {@link worldToScreen} exactly. */
export function fitViewForBounds(
  bounds: WorldBounds,
  containerWidth: number,
  containerHeight: number,
  pxPerBlock: number,
  padding = 48
): MapViewport {
  const worldW = Math.max(1, bounds.maxX - bounds.minX + 1) * pxPerBlock;
  const worldH = Math.max(1, bounds.maxZ - bounds.minZ + 1) * pxPerBlock;
  const availW = Math.max(1, containerWidth - padding * 2);
  const availH = Math.max(1, containerHeight - padding * 2);
  const scale = clamp(
    Math.min(availW / worldW, availH / worldH),
    WORLD_MAP_MIN_SCALE,
    WORLD_MAP_MAX_SCALE
  );
  const originX = bounds.minX;
  const originZ = bounds.maxZ;
  const cx = (bounds.minX + bounds.maxX) / 2;
  const cz = (bounds.minZ + bounds.maxZ) / 2;
  const localX = (cx - originX) * pxPerBlock;
  const localZ = (cz - originZ) * pxPerBlock;
  return {
    scale,
    panX: containerWidth / 2 - localX * scale,
    panY: containerHeight / 2 + localZ * scale,
  };
}

export function originFromBounds(bounds: WorldBounds): WorldMapOrigin {
  return {
    originX: bounds.minX,
    originZ: bounds.maxZ,
    pxPerBlock: 8,
  };
}

export function boundsFromDimension(
  minX: number,
  maxX: number,
  minZ: number,
  maxZ: number
): WorldBounds {
  return { minX, maxX, minZ, maxZ };
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}
