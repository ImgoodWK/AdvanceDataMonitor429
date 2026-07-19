import { describe, expect, it } from 'vitest';
import {
  buildDashboardGameDisplaySnapshot,
  GAME_DISPLAY_FORMAT,
  normalizeGameDisplayColor,
} from './dashboardGameDisplayExport';

describe('dashboard game display export', () => {
  it('normalizes CSS colors into game ARGB colors', () => {
    expect(normalizeGameDisplayColor('rgb(1, 2, 3)')).toBe('#FF010203');
    expect(normalizeGameDisplayColor('rgba(10, 20, 30, 0.5)')).toBe('#800A141E');
    expect(normalizeGameDisplayColor('#abc')).toBe('#FFAABBCC');
    expect(normalizeGameDisplayColor('transparent')).toBeNull();
  });

  it('builds a bounded v1 snapshot and removes unusable primitives', () => {
    const snapshot = buildDashboardGameDisplaySnapshot({
      title: '  Factory   overview  ',
      width: 4000,
      height: 2000,
      background: '#102030',
      exportedAt: 123,
      primitives: [
        { kind: 'rect', x: 1, y: 2, w: 100, h: 50, fill: 'rgba(1, 2, 3, 0.5)' },
        { kind: 'text', x: 2, y: 3, w: 80, h: 20, text: '  TPS   20.0 ', color: '#fff', size: 14 },
        { kind: 'polyline', points: [0, 0, 10, 10, Number.NaN, 3], color: '#00ff00' },
        { kind: 'ellipse', x: 0, y: 0, w: 0, h: 10, fill: '#fff' },
      ],
    });

    expect(snapshot.format).toBe(GAME_DISPLAY_FORMAT);
    expect(snapshot.version).toBe(1);
    expect(snapshot.exportedAt).toBe(123);
    expect(snapshot.title).toBe('Factory overview');
    expect(snapshot.viewport).toEqual({ width: 960, height: 720, background: '#FF102030' });
    expect(snapshot.primitives).toHaveLength(2);
    expect(snapshot.primitives[1]).toMatchObject({ kind: 'text', text: 'TPS 20.0' });
  });
});
