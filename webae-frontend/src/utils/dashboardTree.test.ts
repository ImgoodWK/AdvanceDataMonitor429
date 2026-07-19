import { describe, expect, it } from 'vitest';
import {
  addChildToGroup,
  applyOuterNodePositions,
  defaultDataSourceForWidgetType,
  findWidgetById,
  isLayoutOrFeedType,
  removeWidgetById,
  updateWidgetById,
  widgetLayoutSignature,
  widgetRemountSignature,
  widgetStructureSignature,
} from './dashboardTree';
import type { DashboardWidgetConfig } from './presets';

function w(
  partial: Partial<DashboardWidgetConfig> & Pick<DashboardWidgetConfig, 'id' | 'type'>
): DashboardWidgetConfig {
  return {
    dataSource: 'itemCount',
    scope: 'perNetwork',
    title: '',
    width: 3,
    height: 2,
    x: 0,
    y: 0,
    ...partial,
  };
}

describe('dashboardTree', () => {
  it('find / update / remove nested widgets', () => {
    const tree: DashboardWidgetConfig[] = [
      w({
        id: 'g1',
        type: 'group',
        children: [w({ id: 'c1', type: 'statCard', title: 'A' })],
      }),
    ];
    expect(findWidgetById(tree, 'c1')?.title).toBe('A');
    const updated = updateWidgetById(tree, 'c1', (x) => ({ ...x, title: 'B' }));
    expect(findWidgetById(updated, 'c1')?.title).toBe('B');
    expect(removeWidgetById(updated, 'missing')).toEqual(updated);
    expect(removeWidgetById(updated, 'c1')[0].children).toEqual([]);
  });

  it('addChildToGroup appends into the target group', () => {
    const tree = [w({ id: 'g1', type: 'group', children: [] })];
    const next = addChildToGroup(tree, 'g1', w({ id: 'c1', type: 'gauge' }));
    expect(next[0].children?.[0].id).toBe('c1');
  });

  it('applyOuterNodePositions updates top-level geometry only', () => {
    const widgets = [
      w({ id: 'a', type: 'statCard', x: 0, y: 0, width: 3, height: 2 }),
      w({
        id: 'g',
        type: 'group',
        children: [w({ id: 'c', type: 'statCard', x: 1, y: 1 })],
      }),
    ];
    const next = applyOuterNodePositions(widgets, [
      { id: 'a', x: 2, y: 3, w: 4, h: 5 },
      { id: 'c', x: 9, y: 9, w: 9, h: 9 },
    ]);
    expect(next[0]).toMatchObject({ x: 2, y: 3, width: 4, height: 5 });
    expect(next[1].children?.[0]).toMatchObject({ x: 1, y: 1 });
  });

  it('layout signature is sensitive to x/y and structure signature ignores child x/y', () => {
    const a = [w({ id: 'a', type: 'statCard', x: 0, y: 0 })];
    const b = [w({ id: 'a', type: 'statCard', x: 1, y: 0 })];
    expect(widgetLayoutSignature(a)).not.toBe(widgetLayoutSignature(b));

    const g1 = [
      w({
        id: 'g',
        type: 'group',
        x: 0,
        y: 0,
        children: [w({ id: 'c', type: 'statCard', x: 0, y: 0 })],
      }),
    ];
    const g2 = [
      w({
        id: 'g',
        type: 'group',
        x: 0,
        y: 0,
        children: [w({ id: 'c', type: 'statCard', x: 5, y: 5 })],
      }),
    ];
    expect(widgetLayoutSignature(g1)).toBe(widgetLayoutSignature(g2));
    expect(widgetStructureSignature(g1[0].children || [])).toBe(
      widgetStructureSignature(g2[0].children || [])
    );
    expect(widgetLayoutSignature(g1[0].children || [])).not.toBe(
      widgetLayoutSignature(g2[0].children || [])
    );
  });

  it('layout signature reacts to constraint flags and type', () => {
    const base = w({ id: 'a', type: 'statCard' });
    expect(widgetLayoutSignature([base])).not.toBe(
      widgetLayoutSignature([{ ...base, locked: true }])
    );
    expect(widgetLayoutSignature([base])).not.toBe(
      widgetLayoutSignature([{ ...base, type: 'gauge' }])
    );
  });

  it('remount signature ignores geometry but reacts to structure and flags', () => {
    const base = w({ id: 'a', type: 'statCard', x: 0, y: 0, width: 3, height: 2 });
    expect(widgetRemountSignature([base])).toBe(
      widgetRemountSignature([{ ...base, x: 4, y: 5, width: 6, height: 7 }])
    );
    expect(widgetRemountSignature([base])).not.toBe(
      widgetRemountSignature([{ ...base, locked: true }])
    );
    expect(widgetRemountSignature([base])).not.toBe(
      widgetRemountSignature([{ ...base, type: 'gauge' }])
    );
    expect(widgetRemountSignature([base])).not.toBe(
      widgetRemountSignature([base, w({ id: 'b', type: 'statCard' })])
    );

    const g1 = [
      w({
        id: 'g',
        type: 'group',
        children: [w({ id: 'c', type: 'statCard', x: 0, y: 0, width: 2, height: 2 })],
      }),
    ];
    const g2 = [
      w({
        id: 'g',
        type: 'group',
        x: 3,
        y: 4,
        width: 8,
        height: 6,
        children: [w({ id: 'c', type: 'statCard', x: 5, y: 5, width: 4, height: 3 })],
      }),
    ];
    expect(widgetRemountSignature(g1)).toBe(widgetRemountSignature(g2));
    expect(widgetRemountSignature(g1)).not.toBe(
      widgetRemountSignature([
        w({
          id: 'g',
          type: 'group',
          children: [w({ id: 'c2', type: 'statCard' })],
        }),
      ])
    );
  });

  it('maps composite widgets to stable special data sources', () => {
    expect(isLayoutOrFeedType('networkHealth')).toBe(true);
    expect(defaultDataSourceForWidgetType('networkHealth')).toBe('networkHealth');
    expect(defaultDataSourceForWidgetType('activityStream')).toBe('activityStream');
    expect(defaultDataSourceForWidgetType('statCard')).toBe('itemCount');
  });
});
