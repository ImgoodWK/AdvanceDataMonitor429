/**
 * Build a Dynmap deep-link URL for a player position (Phase 6.1).
 * World names follow common Dynmap defaults; custom packs may need config tweaks.
 */
export function dimToDynmapWorldName(dim: number): string {
  if (dim === 0) return 'world';
  if (dim === -1) return 'world_nether';
  if (dim === 1) return 'world_the_end';
  return 'DIM' + dim;
}

export function buildDynmapUrl(
  baseUrl: string,
  x: number,
  y: number,
  z: number,
  dim: number
): string {
  const trimmed = baseUrl.trim().replace(/\/+$/, '');
  if (!trimmed) return '';
  const world = dimToDynmapWorldName(dim);
  const params = new URLSearchParams({
    worldname: world,
    zoom: '0',
    x: String(Math.round(x)),
    y: String(Math.round(y)),
    z: String(Math.round(z)),
  });
  return trimmed + '/?' + params.toString();
}
