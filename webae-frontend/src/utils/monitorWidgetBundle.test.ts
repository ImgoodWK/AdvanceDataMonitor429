import { describe, expect, it } from 'vitest';
import type { DashboardWidgetConfig } from './presets';
import {
  MAX_MONITOR_WIDGETS,
  buildMonitorWidgetBundle,
  exportMonitorWidgetBundleJson,
  parseMonitorWidgetBundle,
} from './monitorWidgetBundle';

function widget(id: string, type: DashboardWidgetConfig['type'], columns?: string[]): DashboardWidgetConfig {
  return { id, type, dataSource: 'bytesUsed', scope: 'perNetwork', title: id, width: 4, height: 2, x: 0, y: 0, columns };
}

describe('monitor widget bundle', () => {
  it('exports only shared widgets in dashboard order', () => {
    const bundle = buildMonitorWidgetBundle([
      widget('a', 'statCard'),
      widget('web-only', 'radarChart'),
      widget('b', 'lineChart'),
    ], 'Factory', 123);
    expect(bundle).toMatchObject({ format: 'textech-monitor-widget-bundle', version: 1, title: 'Factory', exportedAt: 123 });
    expect(bundle.widgets.map((entry) => entry.kind)).toEqual(['statCard', 'lineChart']);
  });

  it('preserves undefined and explicitly empty columns', () => {
    const raw = exportMonitorWidgetBundleJson([
      widget('default', 'dataTable'),
      widget('hidden', 'dataTable', []),
    ]);
    const parsed = parseMonitorWidgetBundle(raw);
    expect(parsed[0].columns).toBeUndefined();
    expect(parsed[1].columns).toEqual([]);
  });

  it('normalizes JSON null columns to source defaults', () => {
    const parsed = parseMonitorWidgetBundle(JSON.stringify({
      format: 'textech-monitor-widget-bundle',
      version: 1,
      widgets: [{
        kind: 'dataTable',
        sourceKind: 'storage_summary',
        metricKey: 'topItems',
        title: 'Items',
        columns: null,
      }],
    }));
    expect(parsed[0].columns).toBeUndefined();
  });

  it('round-trips explicit sourceKind and metricKey without re-inference', () => {
    const original = {
      ...widget('explicit', 'gauge'),
      dataSource: 'legacyDashboardAlias',
      sourceKind: 'wireless_steam' as const,
      metricKey: 'steamStored',
      targetValue: 12_000,
    };
    const parsed = parseMonitorWidgetBundle(exportMonitorWidgetBundleJson([original]));
    expect(parsed[0]).toMatchObject({
      dataSource: 'steamStored',
      sourceKind: 'wireless_steam',
      metricKey: 'steamStored',
      targetValue: 12_000,
    });
    expect(buildMonitorWidgetBundle(parsed).widgets[0]).toMatchObject({
      sourceKind: 'wireless_steam',
      metricKey: 'steamStored',
    });
  });

  it('rejects more than one monitor face', () => {
    const widgets = Array.from({ length: MAX_MONITOR_WIDGETS + 1 }, (_, index) => widget(String(index), 'statCard'));
    expect(() => buildMonitorWidgetBundle(widgets)).toThrow(/limit/i);
  });

  it('rejects WebAE-only kinds on import', () => {
    expect(() => parseMonitorWidgetBundle(JSON.stringify({
      format: 'textech-monitor-widget-bundle',
      version: 1,
      widgets: [{ kind: 'radarChart', sourceKind: 'ae_metric', metricKey: 'x', title: 'x' }],
    }))).toThrow(/unsupported widget kind/i);
  });
});
