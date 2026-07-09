import { describe, expect, it } from 'vitest';

import { worldToScreen, type MapViewport, type WorldMapOrigin } from '@/utils/worldMapProjection';
import {
  chunkBlockColScreenX,
  chunkBlockRowScreenY,
  chunkTileScreenRect,
  pyramidTileScreenRect,
} from '@/utils/worldMapTerrain';

const viewport: MapViewport = { panX: 120, panY: 80, scale: 1.25 };
const origin: WorldMapOrigin = { originX: -32, originZ: 512, pxPerBlock: 8 };

describe('chunkTileScreenRect', () => {
  it('anchors img top-left at chunk north-west in screen space', () => {
    const chunkX = 4;
    const chunkZ = 12;
    const rect = chunkTileScreenRect(chunkX, chunkZ, viewport, origin);
    const northWest = worldToScreen(chunkX * 16, chunkZ * 16, viewport, origin);
    expect(rect.left).toBe(northWest.sx);
    expect(rect.top).toBe(northWest.sy);
    expect(rect.size).toBe(16 * origin.pxPerBlock * viewport.scale);
  });

  it('maps each block row to worldToScreen after scaleY(-1) flip', () => {
    const chunkX = 4;
    const chunkZ = 12;
    for (let lz = 0; lz < 16; lz++) {
      const rowY = chunkBlockRowScreenY(chunkX, chunkZ, lz, viewport, origin);
      const expected = worldToScreen(chunkX * 16, chunkZ * 16 + lz, viewport, origin).sy;
      expect(rowY).toBeCloseTo(expected, 5);
    }
  });

  it('maps each block column to worldToScreen (PNG col 0 = west)', () => {
    const chunkX = 4;
    const chunkZ = 12;
    for (let lx = 0; lx < 16; lx++) {
      const colX = chunkBlockColScreenX(chunkX, chunkZ, lx, viewport, origin);
      const expected = worldToScreen(chunkX * 16 + lx, chunkZ * 16, viewport, origin).sx;
      expect(colX).toBeCloseTo(expected, 5);
    }
  });

  it('spans 16 blocks in screen space between north and south+1 corners', () => {
    const chunkX = 4;
    const chunkZ = 12;
    const rect = chunkTileScreenRect(chunkX, chunkZ, viewport, origin);
    const north = worldToScreen(chunkX * 16, chunkZ * 16, viewport, origin);
    const southEnd = worldToScreen(chunkX * 16, chunkZ * 16 + 16, viewport, origin);
    expect(north.sy - southEnd.sy).toBeCloseTo(rect.size, 5);
  });
});

describe('pyramidTileScreenRect', () => {
  it('anchors pyramid tile like chunk tiles at north-west', () => {
    const tileX = 2;
    const tileZ = 3;
    const zoom = 1;
    const span = 2;
    const rect = pyramidTileScreenRect(tileX, tileZ, zoom, viewport, origin);
    const northWest = worldToScreen(tileX * span * 16, tileZ * span * 16, viewport, origin);
    expect(rect.left).toBe(northWest.sx);
    expect(rect.top).toBe(northWest.sy);
    expect(rect.size).toBe(16 * span * origin.pxPerBlock * viewport.scale);
  });
});
