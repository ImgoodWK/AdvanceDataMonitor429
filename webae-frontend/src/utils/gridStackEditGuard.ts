/** CSS class + GridStack cancel selector so edit buttons do not start a drag. */
export const GRID_EDIT_NO_DRAG_CLASS = 'gs-no-drag';

export const GRID_DRAG_CANCEL_SELECTOR =
  'input,textarea,button,.ant-btn,.dashboard-grid-edit-actions,.overview-grid-edit-actions,.power-grid-edit-actions,.widget-group-header-actions,.gs-no-drag';

/** Stop pointer/mouse down from bubbling into GridStack drag handle. */
export function stopGridDragPointer(e: { stopPropagation: () => void; preventDefault?: () => void }): void {
  e.stopPropagation();
}
