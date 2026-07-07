import Supercluster from 'supercluster';

import type { WorldMapMarkerDto } from '@/types/dto';

export interface ClusterFeatureProperties {
  cluster: boolean;
  cluster_id?: number;
  point_count?: number;
  point_count_abbreviated?: string | number;
  marker?: WorldMapMarkerDto;
}

export type MarkerClusterIndex = Supercluster<ClusterFeatureProperties, ClusterFeatureProperties>;

const DEFAULT_RADIUS = 48;
const DEFAULT_MAX_ZOOM = 18;

/** Build a supercluster index from world-map markers (GeoJSON point features). */
export function buildMarkerClusterIndex(
  markers: WorldMapMarkerDto[],
  radius = DEFAULT_RADIUS,
  maxZoom = DEFAULT_MAX_ZOOM
): MarkerClusterIndex {
  const index = new Supercluster<ClusterFeatureProperties, ClusterFeatureProperties>({
    radius,
    maxZoom,
    map: (props) => props,
    reduce: (_acc, _props) => {},
  });

  const features = markers.map((marker) => ({
    type: 'Feature' as const,
    properties: {
      cluster: false as const,
      marker,
    },
    geometry: {
      type: 'Point' as const,
      coordinates: [marker.x, marker.z] as [number, number],
    },
  }));

  index.load(features);
  return index;
}

/** Query clusters/leaves for a screen bounding box at the given zoom level. */
export function queryClusters(
  index: MarkerClusterIndex,
  bbox: [number, number, number, number],
  zoom: number
): Supercluster.PointFeature<ClusterFeatureProperties>[] | Supercluster.ClusterFeature<ClusterFeatureProperties>[] {
  return index.getClusters(bbox, Math.floor(zoom));
}

/** Convert viewport screen rect to GeoJSON bbox [west, south, east, north] in world X/Z. */
export function screenBBoxToWorldBBox(
  left: number,
  top: number,
  right: number,
  bottom: number,
  toWorld: (sx: number, sy: number) => { x: number; z: number }
): [number, number, number, number] {
  const nw = toWorld(left, top);
  const se = toWorld(right, bottom);
  const west = Math.min(nw.x, se.x);
  const east = Math.max(nw.x, se.x);
  const north = Math.max(nw.z, se.z);
  const south = Math.min(nw.z, se.z);
  return [west, south, east, north];
}

/** Map viewport scale to an approximate supercluster zoom level. */
export function scaleToClusterZoom(scale: number, pxPerBlock: number): number {
  const pxPerBlockScaled = pxPerBlock * scale;
  if (pxPerBlockScaled >= 32) return DEFAULT_MAX_ZOOM;
  if (pxPerBlockScaled >= 16) return 14;
  if (pxPerBlockScaled >= 8) return 12;
  if (pxPerBlockScaled >= 4) return 10;
  if (pxPerBlockScaled >= 2) return 8;
  return 6;
}
