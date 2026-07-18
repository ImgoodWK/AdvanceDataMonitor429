/** Pure undo/redo stack for editor snapshots (immutable snapshots of settings). */

export interface EditorHistoryState<T> {
  past: T[];
  present: T;
  future: T[];
}

export const EDITOR_HISTORY_MAX = 40;

export function createEditorHistory<T>(present: T): EditorHistoryState<T> {
  return { past: [], present, future: [] };
}

/** Push current present onto past and set next as present; clears redo stack. */
export function historyCommit<T>(
  state: EditorHistoryState<T>,
  next: T,
  max = EDITOR_HISTORY_MAX
): EditorHistoryState<T> {
  if (Object.is(state.present, next)) return state;
  const past = [...state.past, state.present];
  while (past.length > max) past.shift();
  return { past, present: next, future: [] };
}

export function historyUndo<T>(state: EditorHistoryState<T>): EditorHistoryState<T> {
  if (state.past.length === 0) return state;
  const past = state.past.slice();
  const previous = past.pop() as T;
  return {
    past,
    present: previous,
    future: [state.present, ...state.future],
  };
}

export function historyRedo<T>(state: EditorHistoryState<T>): EditorHistoryState<T> {
  if (state.future.length === 0) return state;
  const [next, ...rest] = state.future;
  return {
    past: [...state.past, state.present],
    present: next,
    future: rest,
  };
}

export function historyCanUndo<T>(state: EditorHistoryState<T>): boolean {
  return state.past.length > 0;
}

export function historyCanRedo<T>(state: EditorHistoryState<T>): boolean {
  return state.future.length > 0;
}

/** Replace present without recording history (e.g. hydrate from localStorage). */
export function historyReplace<T>(state: EditorHistoryState<T>, present: T): EditorHistoryState<T> {
  return { ...state, present };
}
