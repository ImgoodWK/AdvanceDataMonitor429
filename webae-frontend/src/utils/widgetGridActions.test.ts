import { describe, expect, it } from 'vitest';
import {
  copyWidgetConfig,
  exportWidgetsJson,
  parseWidgetsImport,
  WidgetImportError,
} from './widgetGridActions';
import { resetWidgetIdSeqForTests } from './widgetId';
import type { DashboardWidgetConfig } from './presets';
import { migrateDashboardWidgets } from './presets';

function sample(): DashboardWidgetConfig {
  return {
    id: 'w-1',
    type: 'statCard',
    dataSource: 'itemCount',
    scope: 'perNetwork',
    title: 'Items',
    width: 3,
    height: 2,
    x: 0,
    y: 0,
  };
}

describe('widgetGridActions', () => {
  it('copyWidgetConfig deep-copies and assigns unique ids', () => {
    resetWidgetIdSeqForTests();
    const group: DashboardWidgetConfig = {
      ...sample(),
      id: 'g1',
      type: 'group',
      dataSource: 'none',
      children: [sample()],
      colors: { inheritDefault: true, titleColor: '#fff' } as DashboardWidgetConfig['colors'],
      pins: [{ kind: 'item', id: 'minecraft:stone', label: 'Stone' }],
    };
    const a = copyWidgetConfig(group, 'w-');
    const b = copyWidgetConfig(group, 'w-');
    expect(a.id).not.toBe(group.id);
    expect(a.id).not.toBe(b.id);
    expect(a.children?.[0].id).not.toBe(group.children?.[0].id);
    expect(a.children?.[0].id).not.toBe(b.children?.[0].id);
    expect(a.colors).toEqual(group.colors);
    expect(a.colors).not.toBe(group.colors);
    expect(a.pins?.[0]).not.toBe(group.pins?.[0]);
    expect(a.title).toContain('(copy)');
  });

  it('export / import round-trip including empty layout', () => {
    const json = exportWidgetsJson([]);
    expect(parseWidgetsImport(json)).toEqual([]);

    const widgets = [sample()];
    const round = parseWidgetsImport(exportWidgetsJson(widgets));
    expect(round).toHaveLength(1);
    expect(round[0].id).toBe('w-1');
    expect(round[0].title).toBe('Items');
  });

  it('round-trips advanced composite widget types', () => {
    const types: DashboardWidgetConfig['type'][] = [
      'networkHealth', 'powerFlow', 'storageMatrix', 'machineFleet',
      'playerPresence', 'activityStream', 'serverVitals',
    ];
    const widgets = types.map((type, index) => ({
      ...sample(),
      id: `advanced-${index}`,
      type,
      dataSource: type,
    }));
    expect(parseWidgetsImport(exportWidgetsJson(widgets)).map((widget) => widget.type)).toEqual(types);
  });

  it('accepts raw array import and rejects invalid json / duplicate ids', () => {
    expect(() => parseWidgetsImport('not-json')).toThrow(WidgetImportError);
    expect(() => parseWidgetsImport('{}')).toThrow(WidgetImportError);

    const dup = JSON.stringify({
      version: 2,
      widgets: [sample(), { ...sample(), id: 'w-1' }],
    });
    expect(() => parseWidgetsImport(dup)).toThrow(/duplicate/i);

    const badType = JSON.stringify([{ ...sample(), type: 'nope' }]);
    expect(() => parseWidgetsImport(badType)).toThrow(/unsupported/i);
  });

  it('clamps illegal sizes and fills missing ids', () => {
    const raw = JSON.stringify([
      {
        type: 'statCard',
        dataSource: 'itemCount',
        width: 99,
        height: -3,
        x: -5,
        y: 1.7,
      },
    ]);
    const [w] = parseWidgetsImport(raw);
    expect(w.id).toMatch(/^w-import-/);
    expect(w.width).toBe(12);
    expect(w.height).toBe(1);
    expect(w.x).toBe(0);
    expect(w.y).toBe(2);
  });

  it('repairs unsafe persisted geometry before GridStack initialization', () => {
    const [widget] = migrateDashboardWidgets([
      { ...sample(), x: -9, y: Number.NaN, width: 80, height: 0 },
    ]);
    expect(widget).toMatchObject({ x: 0, y: 0, width: 12, height: 1 });
  });

  it('rejects excessive nest depth', () => {
    let node: DashboardWidgetConfig = {
      ...sample(),
      id: 'leaf',
      type: 'statCard',
    };
    for (let i = 0; i < 6; i++) {
      node = {
        ...sample(),
        id: `g${i}`,
        type: 'group',
        dataSource: 'none',
        children: [node],
      };
    }
    expect(() => parseWidgetsImport(JSON.stringify([node]))).toThrow(/nest depth/i);
  });
});
