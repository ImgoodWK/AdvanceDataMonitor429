import type { GridStack, GridStackNode } from 'gridstack';

/** CSS class + GridStack cancel selector so edit buttons do not start a drag. */
export const GRID_EDIT_NO_DRAG_CLASS = 'gs-no-drag';

export const GRID_DRAG_CANCEL_SELECTOR =
  'input,textarea,button,.ant-btn,.dashboard-grid-edit-actions,.overview-grid-edit-actions,.power-grid-edit-actions,.widget-group-header-actions,.gs-no-drag';

/** Stop pointer/mouse down from bubbling into GridStack drag handle. */
export function stopGridDragPointer(e: { stopPropagation: () => void; preventDefault?: () => void }): void {
  e.stopPropagation();
}

export interface GridWidgetGeometry {
  id: string;
  x: number;
  y: number;
  w: number;
  h: number;
}

function finiteInt(value: unknown, fallback: number): number {
  return typeof value === 'number' && Number.isFinite(value) ? Math.round(value) : fallback;
}

/** Copy and clamp engine-owned geometry before leaving the GridStack event callback. */
export function snapshotGridGeometry(nodes: GridStackNode[]): GridWidgetGeometry[] {
  return nodes
    .filter((node) => node.id != null)
    .map((node) => ({
      id: String(node.id),
      x: Math.max(0, finiteInt(node.x, 0)),
      y: Math.max(0, finiteInt(node.y, 0)),
      w: Math.max(1, Math.min(12, finiteInt(node.w, 1))),
      h: Math.max(1, Math.min(20, finiteInt(node.h, 1))),
    }));
}

export function adaptiveGridCellHeight(
  preferred: number,
  minimum: number,
  viewportHeight = typeof window === 'undefined' ? 900 : window.innerHeight
): number {
  const ratio = Math.max(0.65, Math.min(1, viewportHeight / 900));
  return Math.max(minimum, Math.round(preferred * ratio));
}

/** Recompute cell height on viewport changes without rebuilding the grid instance. */
export function observeGridViewport(
  grid: GridStack,
  preferredCellHeight: number,
  minimumCellHeight: number
): () => void {
  let frame: number | null = null;
  const apply = () => {
    frame = null;
    try {
      grid.cellHeight(adaptiveGridCellHeight(preferredCellHeight, minimumCellHeight));
    } catch {
      // A navigation can destroy the grid between the resize event and this frame.
    }
  };
  const onResize = () => {
    if (frame != null) window.cancelAnimationFrame(frame);
    frame = window.requestAnimationFrame(apply);
  };
  apply();
  window.addEventListener('resize', onResize, { passive: true });
  return () => {
    window.removeEventListener('resize', onResize);
    if (frame != null) window.cancelAnimationFrame(frame);
  };
}

/** Commit a copied layout on the next frame, outside GridStack's DOM mutation stack. */
export function scheduleGridLayoutCommit(
  grid: GridStack | null,
  pendingFrame: { current: number | null },
  commit: (nodes: GridWidgetGeometry[]) => void
): void {
  if (!grid) return;
  const snapshot = snapshotGridGeometry(grid.engine.nodes);
  if (!snapshot.length) return;
  if (pendingFrame.current != null) window.cancelAnimationFrame(pendingFrame.current);
  pendingFrame.current = window.requestAnimationFrame(() => {
    pendingFrame.current = null;
    commit(snapshot);
  });
}

export function cancelGridLayoutCommit(pendingFrame: { current: number | null }): void {
  if (pendingFrame.current == null) return;
  window.cancelAnimationFrame(pendingFrame.current);
  pendingFrame.current = null;
}

/**
 * Push React widget geometry into a live GridStack node without remounting.
 * Used after Edit Modal size changes (drag/resize already update the engine).
 */
export function syncWidgetGeometryToGrid(widget: {
  id: string;
  x?: number;
  y?: number;
  width: number;
  height: number;
}): boolean {
  if (typeof document === 'undefined') return false;
  const el = Array.from(document.querySelectorAll<HTMLElement>('.grid-stack-item')).find(
    (candidate) => candidate.getAttribute('gs-id') === widget.id
  );
  if (!el) return false;
  const node = (el as HTMLElement & { gridstackNode?: { grid?: { update: (e: HTMLElement, o: object) => void } } })
    .gridstackNode;
  const grid = node?.grid;
  if (!grid) return false;
  try {
    grid.update(el, {
      x: widget.x == null ? undefined : Math.max(0, finiteInt(widget.x, 0)),
      y: widget.y == null ? undefined : Math.max(0, finiteInt(widget.y, 0)),
      w: Math.max(1, Math.min(12, finiteInt(widget.width, 1))),
      h: Math.max(1, Math.min(20, finiteInt(widget.height, 1))),
    });
    return true;
  } catch {
    // Grid may be mid-destroy; next structural remount will pick up attributes.
    return false;
  }
}
