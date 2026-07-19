import type { DashboardWidgetConfig } from '@/utils/presets';

/** Widget types that do not use AE scalar/list data sources. */
export const LAYOUT_OR_FEED_TYPES: ReadonlyArray<DashboardWidgetConfig['type']> = [
  'group',
  'textNote',
  'spacer',
  'alertsSummary',
  'craftingQueue',
  'networkHealth',
  'powerFlow',
  'storageMatrix',
  'machineFleet',
  'playerPresence',
  'activityStream',
  'serverVitals',
];

const SPECIAL_WIDGET_DATA_SOURCES: Partial<Record<DashboardWidgetConfig['type'], string>> = {
  alertsSummary: 'alertsActive',
  craftingQueue: 'craftingBusy',
  networkHealth: 'networkHealth',
  powerFlow: 'powerFlow',
  storageMatrix: 'storageMatrix',
  machineFleet: 'machineFleet',
  playerPresence: 'playerPresence',
  activityStream: 'activityStream',
  serverVitals: 'serverVitals',
  group: 'none',
  textNote: 'none',
  spacer: 'none',
};

export function isLayoutOrFeedType(type: DashboardWidgetConfig['type']): boolean {
  return (LAYOUT_OR_FEED_TYPES as ReadonlyArray<string>).includes(type);
}

export function defaultDataSourceForWidgetType(type: DashboardWidgetConfig['type']): string {
  return SPECIAL_WIDGET_DATA_SOURCES[type] || 'itemCount';
}

/** Walk the widget tree depth-first (groups include themselves, then children). */
export function flattenWidgets(widgets: DashboardWidgetConfig[]): DashboardWidgetConfig[] {
  const out: DashboardWidgetConfig[] = [];
  for (const w of widgets) {
    out.push(w);
    if (w.type === 'group' && w.children?.length) {
      out.push(...flattenWidgets(w.children));
    }
  }
  return out;
}

export function findWidgetById(
  widgets: DashboardWidgetConfig[],
  id: string
): DashboardWidgetConfig | null {
  for (const w of widgets) {
    if (w.id === id) return w;
    if (w.type === 'group' && w.children?.length) {
      const found = findWidgetById(w.children, id);
      if (found) return found;
    }
  }
  return null;
}

export function updateWidgetById(
  widgets: DashboardWidgetConfig[],
  id: string,
  updater: (w: DashboardWidgetConfig) => DashboardWidgetConfig
): DashboardWidgetConfig[] {
  return widgets.map((w) => {
    if (w.id === id) return updater(w);
    if (w.type === 'group' && w.children?.length) {
      return { ...w, children: updateWidgetById(w.children, id, updater) };
    }
    return w;
  });
}

export function removeWidgetById(
  widgets: DashboardWidgetConfig[],
  id: string
): DashboardWidgetConfig[] {
  const out: DashboardWidgetConfig[] = [];
  for (const w of widgets) {
    if (w.id === id) continue;
    if (w.type === 'group' && w.children?.length) {
      out.push({ ...w, children: removeWidgetById(w.children, id) });
    } else {
      out.push(w);
    }
  }
  return out;
}

/** Append a child into a group (or no-op if groupId not found). */
export function addChildToGroup(
  widgets: DashboardWidgetConfig[],
  groupId: string,
  child: DashboardWidgetConfig
): DashboardWidgetConfig[] {
  return updateWidgetById(widgets, groupId, (g) => {
    if (g.type !== 'group') return g;
    return { ...g, children: [...(g.children || []), child] };
  });
}

/**
 * Structure-only signature (ids, size, type, flags, nesting) — no x/y.
 * Used inside groups so outer grids do not remount when children are dragged.
 */
export function widgetStructureSignature(widgets: DashboardWidgetConfig[]): string {
  return widgets
    .map((w) => {
      const flags = [
        w.locked ? 'L' : '',
        w.noMove ? 'M' : '',
        w.noResize ? 'R' : '',
        w.sizeToContent ? 'S' : '',
      ].join('');
      const base = `${w.id}_${w.width}_${w.height}_${w.type}_${flags}`;
      if (w.type === 'group') {
        return `${base}{${widgetStructureSignature(w.children || [])}}`;
      }
      return base;
    })
    .join(',');
}

/**
 * Signature covering ids + geometry at every nesting level for a single grid.
 * Includes x/y so callers can detect import or external coordinate replacement.
 * Nested group children use structure-only signatures so outer grids do not
 * treat inner drag commits as outer geometry changes.
 *
 * Prefer {@link widgetRemountSignature} for GridStack useEffect deps — do not
 * remount on dragstop/resizestop geometry writes.
 */
export function widgetLayoutSignature(widgets: DashboardWidgetConfig[]): string {
  return widgets
    .map((w) => {
      const flags = [
        w.locked ? 'L' : '',
        w.noMove ? 'M' : '',
        w.noResize ? 'R' : '',
        w.sizeToContent ? 'S' : '',
      ].join('');
      const base = `${w.id}_${w.x ?? 0}_${w.y ?? 0}_${w.width}_${w.height}_${w.type}_${flags}`;
      if (w.type === 'group') {
        return `${base}{${widgetStructureSignature(w.children || [])}}`;
      }
      return base;
    })
    .join(',');
}

/**
 * Remount signature for GridStack init/destroy: ids, type, constraint flags,
 * and nested membership — never x/y/width/height.
 *
 * Drag/resize persistence must not remount; sync external size edits via
 * `grid.update` instead.
 */
export function widgetRemountSignature(widgets: DashboardWidgetConfig[]): string {
  return widgets
    .map((w) => {
      const flags = [
        w.locked ? 'L' : '',
        w.noMove ? 'M' : '',
        w.noResize ? 'R' : '',
        w.sizeToContent ? 'S' : '',
      ].join('');
      const base = `${w.id}_${w.type}_${flags}`;
      if (w.type === 'group') {
        return `${base}{${widgetRemountSignature(w.children || [])}}`;
      }
      return base;
    })
    .join(',');
}

/** Apply outer-grid node positions onto top-level widgets only. */
export function applyOuterNodePositions(
  widgets: DashboardWidgetConfig[],
  nodes: Array<{ id?: string | number; x?: number; y?: number; w?: number; h?: number }>
): DashboardWidgetConfig[] {
  return widgets.map((w) => {
    const node = nodes.find((n) => String(n.id) === w.id);
    if (!node) return w;
    return {
      ...w,
      x: node.x ?? w.x,
      y: node.y ?? w.y,
      width: node.w ?? w.width,
      height: node.h ?? w.height,
    };
  });
}

/** Apply sub-grid node positions onto a group's children. */
export function applyChildNodePositions(
  children: DashboardWidgetConfig[],
  nodes: Array<{ id?: string | number; x?: number; y?: number; w?: number; h?: number }>
): DashboardWidgetConfig[] {
  return children.map((w) => {
    const node = nodes.find((n) => String(n.id) === w.id);
    if (!node) return w;
    return {
      ...w,
      x: node.x ?? w.x,
      y: node.y ?? w.y,
      width: node.w ?? w.width,
      height: node.h ?? w.height,
    };
  });
}
