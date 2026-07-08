import type L from 'leaflet';

import { DYNMAP_TILE_BLOCKS_Z0 } from '@/utils/dynmapTiles';
import type { MapViewport, WorldMapOrigin } from '@/utils/worldMapProjection';

/**
 * Derives a self-mode {@link MapViewport} from a Leaflet CRS.Simple map so AE chunk tiles
 * align with Dynmap/GWM terrain tiles.
 */
export function viewportFromLeafletMap(
  map: L.Map,
  origin: WorldMapOrigin,
  containerWidth: number,
  containerHeight: number
): MapViewport {
  if (containerWidth <= 0 || containerHeight <= 0) {
    return { panX: 0, panY: 0, scale: 1 };
  }

  const nw = map.containerPointToLatLng([0, 0]);
  const se = map.containerPointToLatLng([containerWidth, containerHeight]);
  const worldX0 = nw.lng * DYNMAP_TILE_BLOCKS_Z0;
  const worldZ0 = nw.lat * DYNMAP_TILE_BLOCKS_Z0;
  const worldX1 = se.lng * DYNMAP_TILE_BLOCKS_Z0;
  const worldZ1 = se.lat * DYNMAP_TILE_BLOCKS_Z0;

  const blockSpanX = Math.max(1, Math.abs(worldX1 - worldX0));
  const blockSpanZ = Math.max(1, Math.abs(worldZ1 - worldZ0));
  const scaleX = containerWidth / (blockSpanX * origin.pxPerBlock);
  const scaleZ = containerHeight / (blockSpanZ * origin.pxPerBlock);
  const scale = (scaleX + scaleZ) / 2;

  const worldX = (worldX0 + worldX1) / 2;
  const worldZ = (worldZ0 + worldZ1) / 2;
  const localX = (worldX - origin.originX) * origin.pxPerBlock;
  const localZ = (worldZ - origin.originZ) * origin.pxPerBlock;

  return {
    scale,
    panX: containerWidth / 2 - localX * scale,
    panY: containerHeight / 2 + localZ * scale,
  };
}
