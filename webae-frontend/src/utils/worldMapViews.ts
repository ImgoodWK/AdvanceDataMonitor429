/** Oblique orbit direction (mineshot-style compass). */
export type WorldMapObliqueDirection = 'se' | 'sw' | 'ne' | 'nw';

export const DEFAULT_WORLD_MAP_OBLIQUE_DIRECTION: WorldMapObliqueDirection = 'se';

export function obliqueTileViewId(direction: WorldMapObliqueDirection): string {
  return `oblique_${direction}`;
}

/** Resolve UI tab id to tile API view id. */
export function resolveWorldMapTileViewId(
  uiViewId: string,
  obliqueDirection: WorldMapObliqueDirection
): string {
  if (uiViewId === 'oblique') {
    return obliqueTileViewId(obliqueDirection);
  }
  return uiViewId;
}

export function buildWorldMapInvalidateViews(
  obliqueDirection: WorldMapObliqueDirection,
  includeFlat = true
): string {
  const parts: string[] = [];
  if (includeFlat) {
    parts.push('flat');
  }
  parts.push(obliqueTileViewId(obliqueDirection));
  return parts.join(',');
}
