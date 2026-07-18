import { describe, expect, it } from 'vitest';
import { resolveColumns } from './dashboardColumns';
import type { DashboardWidgetConfig } from './presets';

const widget = (columns?: string[]): DashboardWidgetConfig => ({
  id: 'table',
  type: 'dataTable',
  dataSource: 'topItems',
  scope: 'perNetwork',
  title: '',
  width: 4,
  height: 3,
  x: 0,
  y: 0,
  ...(columns === undefined ? {} : { columns }),
});

describe('resolveColumns', () => {
  it('uses defaults only when no explicit selection was saved', () => {
    expect(resolveColumns(widget())).toEqual(['icon', 'name', 'amount']);
  });

  it('preserves an explicit empty selection', () => {
    expect(resolveColumns(widget([]))).toEqual([]);
  });

  it('preserves the selected columns', () => {
    expect(resolveColumns(widget(['registryName']))).toEqual(['registryName']);
  });
});
