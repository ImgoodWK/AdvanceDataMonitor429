/**
 * Coordinate mappings between Minecraft world coordinates and Dynmap tile indices.
 * Mirrors server-side {@code WorldMapDynmapCoordMapper}.
 */

/** Dynmap tiles at zoom 0 cover 128x128 blocks. */
export const DYNMAP_TILE_BLOCKS_Z0 = 128;

/**
 * Converts a Dynmap tile coordinate to a Leaflet latitude.
 * Dynmap uses a Z-axis-flipped projection where +Z (south) maps to positive latitude.
 *
 * @param tileY  Dynmap tile Y index (Z-axis in Minecraft)
 * @param zoom   zoom level
 * @returns      Leaflet latitude
 */
export function dynmapTileToLat(tileY: number, zoom: number): number {
  const span = tileBlockSpan(zoom);
  // Each tile spans `span` blocks. We return the center of the tile in block coords
  // then convert to latitude. Dynmap: 1 block = 1 unit → lat ≈ block_z / 128 for z0
  const blockZ = (tileY + 0.5) * span;
  return blockZ / DYNMAP_TILE_BLOCKS_Z0;
}

/**
 * Converts a Dynmap tile coordinate to a Leaflet longitude.
 *
 * @param tileX  Dynmap tile X index
 * @param zoom   zoom level
 * @returns      Leaflet longitude
 */
export function dynmapTileToLng(tileX: number, zoom: number): number {
  const span = tileBlockSpan(zoom);
  const blockX = (tileX + 0.5) * span;
  return blockX / DYNMAP_TILE_BLOCKS_Z0;
}

/**
 * Returns the block span of a single Dynmap tile at the given zoom level.
 */
export function tileBlockSpan(zoom: number): number {
  if (zoom < 0) zoom = 0;
  return DYNMAP_TILE_BLOCKS_Z0 << zoom;
}

/**
 * Converts Minecraft world X to Dynmap tile X at the given zoom level.
 */
export function worldToTileX(worldX: number, zoom: number): number {
  const span = tileBlockSpan(zoom);
  return Math.floor(worldX / span);
}

/**
 * Converts Minecraft world Z to Dynmap tile Z at the given zoom level.
 */
export function worldToTileZ(worldZ: number, zoom: number): number {
  const span = tileBlockSpan(zoom);
  return Math.floor(worldZ / span);
}

/**
 * Converts tile coordinates back to the world block coordinate of the tile origin.
 */
export function tileToWorldX(tileX: number, zoom: number): number {
  return tileX * tileBlockSpan(zoom);
}

export function tileToWorldZ(tileZ: number, zoom: number): number {
  return tileZ * tileBlockSpan(zoom);
}

/** Default max native zoom for GWM/Dynmap tile trees (highest resolution layer). */
export const DYNMAP_MAX_NATIVE_ZOOM = 6;

/**
 * Maps Leaflet display zoom to highest-resolution tile coordinates.
 * Always fetches tiles at {@code maxNativeZoom} and lets Leaflet scale them.
 */
export function mapDynmapTileCoords(
  displayZoom: number,
  tileX: number,
  tileY: number,
  maxNativeZoom = DYNMAP_MAX_NATIVE_ZOOM
): { zoom: number; tileX: number; tileY: number } {
  const safeDisplay = Math.max(0, displayZoom);
  const safeMax = Math.max(0, maxNativeZoom);
  if (safeDisplay >= safeMax) {
    return { zoom: safeMax, tileX, tileY };
  }
  const scale = 1 << (safeMax - safeDisplay);
  return {
    zoom: safeMax,
    tileX: tileX * scale,
    tileY: tileY * scale,
  };
}

/**
 * Builds a tile URL, optionally remapping to max native zoom for single-resolution mode.
 */
export function buildDynmapTileUrlAtDisplayZoom(
  template: string,
  worldName: string,
  displayZoom: number,
  tileX: number,
  tileY: number,
  maxNativeZoom = DYNMAP_MAX_NATIVE_ZOOM
): string {
  const mapped = mapDynmapTileCoords(displayZoom, tileX, tileY, maxNativeZoom);
  return buildDynmapTileUrl(template, worldName, mapped.zoom, mapped.tileX, mapped.tileY);
}

/**
 * Builds a tile URL from the server template and parameters.
 */
export function buildDynmapTileUrl(
  template: string,
  worldName: string,
  zoom: number,
  tileX: number,
  tileY: number,
): string {
  return template
    .replace('{world}', encodeURIComponent(worldName))
    .replace('{z}', String(zoom))
    .replace(/\{x\}/g, String(tileX))
    .replace(/\{y\}/g, String(tileY));
}

/**
 * Converts a world bounding box (Minecraft block coords) to Leaflet LatLngBounds.
 * The conversion matches the tile coordinate mapping: block / TILE_BLOCKS_Z0 → lat/lng.
 */
export function worldBoundsToLatLngBounds(
  minX: number,
  maxX: number,
  minZ: number,
  maxZ: number,
): [[number, number], [number, number]] {
  const south = minZ / DYNMAP_TILE_BLOCKS_Z0;
  const north = maxZ / DYNMAP_TILE_BLOCKS_Z0;
  const west = minX / DYNMAP_TILE_BLOCKS_Z0;
  const east = maxX / DYNMAP_TILE_BLOCKS_Z0;
  return [[south, west], [north, east]];
}

/**
 * WebAE view id to Dynmap perspective prefix.
 */
export function toDynmapPerspective(webaeViewId: string): string {
  if (!webaeViewId) return 'flat';
  switch (webaeViewId.trim().toLowerCase()) {
    case 'flat':
      return 'flat';
    case 'oblique':
    case 'oblique_se':
    case 'iso_se':
      return 'iso_SE_30_hires';
    default:
      return 'flat';
  }
}
