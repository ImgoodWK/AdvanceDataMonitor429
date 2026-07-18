import { describe, expect, it } from 'vitest';
import {
  createEditorHistory,
  historyCanRedo,
  historyCanUndo,
  historyCommit,
  historyRedo,
  historyUndo,
} from './editorHistory';

describe('editorHistory', () => {
  it('commit / undo / redo and clears future on new commit', () => {
    let state = createEditorHistory({ n: 0 });
    state = historyCommit(state, { n: 1 });
    state = historyCommit(state, { n: 2 });
    expect(historyCanUndo(state)).toBe(true);
    state = historyUndo(state);
    expect(state.present).toEqual({ n: 1 });
    expect(historyCanRedo(state)).toBe(true);
    state = historyRedo(state);
    expect(state.present).toEqual({ n: 2 });
    state = historyUndo(state);
    state = historyCommit(state, { n: 9 });
    expect(historyCanRedo(state)).toBe(false);
    expect(state.present).toEqual({ n: 9 });
  });

  it('undo on empty past is a no-op', () => {
    const state = createEditorHistory('a');
    expect(historyUndo(state)).toBe(state);
    expect(historyCanUndo(state)).toBe(false);
  });
});
