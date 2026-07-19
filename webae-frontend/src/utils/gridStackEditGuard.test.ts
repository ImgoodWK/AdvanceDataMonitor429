import { describe, expect, it } from 'vitest';
import { adaptiveGridCellHeight, snapshotGridGeometry } from './gridStackEditGuard';

describe('gridStackEditGuard', () => {
  it('copies and clamps unstable engine geometry', () => {
    const nodes = snapshotGridGeometry([
      { id: 'a', x: -2, y: 3.6, w: 99, h: 0 },
      { id: 'b', x: Number.NaN, y: -4, w: 4.2, h: 30 },
      { x: 1, y: 1, w: 1, h: 1 },
    ]);
    expect(nodes).toEqual([
      { id: 'a', x: 0, y: 4, w: 12, h: 1 },
      { id: 'b', x: 0, y: 0, w: 4, h: 20 },
    ]);
  });

  it('adapts cell height without making compact viewports unreadable', () => {
    expect(adaptiveGridCellHeight(64, 40, 900)).toBe(64);
    expect(adaptiveGridCellHeight(64, 40, 600)).toBe(43);
    expect(adaptiveGridCellHeight(64, 40, 300)).toBe(42);
  });
});
