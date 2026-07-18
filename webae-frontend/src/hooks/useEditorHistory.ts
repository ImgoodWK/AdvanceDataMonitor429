import { useCallback, useEffect, useRef, useState } from 'react';
import {
  createEditorHistory,
  historyCanRedo,
  historyCanUndo,
  historyCommit,
  historyRedo,
  historyUndo,
  type EditorHistoryState,
} from '@/utils/editorHistory';

/**
 * Undo/redo stack bound to an editor settings object.
 * Call `commit(next)` for user-visible mutations; `replace(next)` for silent hydrate
 * (e.g. GridStack position writes that should not flood the undo stack mid-drag —
 * prefer commit on dragstop).
 */
export function useEditorHistory<T>(initial: T | (() => T)) {
  const [state, setState] = useState<EditorHistoryState<T>>(() =>
    createEditorHistory(typeof initial === 'function' ? (initial as () => T)() : initial)
  );
  const stateRef = useRef(state);
  stateRef.current = state;

  const commit = useCallback((next: T | ((prev: T) => T)) => {
    setState((prev) => {
      const resolved = typeof next === 'function' ? (next as (p: T) => T)(prev.present) : next;
      return historyCommit(prev, resolved);
    });
  }, []);

  const replace = useCallback((next: T) => {
    setState((prev) => ({ ...prev, present: next }));
  }, []);

  const undo = useCallback((): T | null => {
    const cur = stateRef.current;
    if (!historyCanUndo(cur)) return null;
    const next = historyUndo(cur);
    setState(next);
    return next.present;
  }, []);

  const redo = useCallback((): T | null => {
    const cur = stateRef.current;
    if (!historyCanRedo(cur)) return null;
    const next = historyRedo(cur);
    setState(next);
    return next.present;
  }, []);

  return {
    present: state.present,
    canUndo: historyCanUndo(state),
    canRedo: historyCanRedo(state),
    commit,
    replace,
    undo,
    redo,
  };
}

/** Ctrl/Cmd+Z / Ctrl/Cmd+Shift+Z / Ctrl+Y while enabled. */
export function useUndoRedoHotkeys(
  undo: () => void,
  redo: () => void,
  enabled: boolean
): void {
  useEffect(() => {
    if (!enabled) return;
    const onKeyDown = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement | null;
      const tag = target?.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA' || target?.isContentEditable) return;
      const mod = e.ctrlKey || e.metaKey;
      if (!mod) return;
      const key = e.key.toLowerCase();
      if (key === 'z' && !e.shiftKey) {
        e.preventDefault();
        undo();
        return;
      }
      if ((key === 'z' && e.shiftKey) || key === 'y') {
        e.preventDefault();
        redo();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [enabled, undo, redo]);
}
