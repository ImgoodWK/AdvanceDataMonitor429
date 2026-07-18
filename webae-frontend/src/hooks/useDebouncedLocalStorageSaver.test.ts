import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createDebouncedLocalStorageSaver } from './debouncedLocalStorageSaver';

describe('createDebouncedLocalStorageSaver', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('schedules, keeps latest pending, and flushes after debounce', () => {
    const setItem = vi.fn();
    const saver = createDebouncedLocalStorageSaver<{ v: number }>('k', 400, {
      setItem,
      setTimeout,
      clearTimeout,
    });
    saver.schedule({ v: 1 });
    saver.schedule({ v: 2 });
    expect(setItem).not.toHaveBeenCalled();
    vi.advanceTimersByTime(400);
    expect(setItem).toHaveBeenCalledWith('k', JSON.stringify({ v: 2 }));
  });

  it('cancel drops pending so immediate save is not overwritten', () => {
    const setItem = vi.fn();
    const saver = createDebouncedLocalStorageSaver<{ v: number }>('k', 400, {
      setItem,
      setTimeout,
      clearTimeout,
    });
    saver.schedule({ v: 1 });
    saver.cancel();
    setItem('k', JSON.stringify({ v: 99 }));
    setItem.mockClear();
    vi.advanceTimersByTime(400);
    expect(setItem).not.toHaveBeenCalled();
  });

  it('flush writes immediately and clears timer', () => {
    const setItem = vi.fn();
    const saver = createDebouncedLocalStorageSaver<{ v: number }>('k', 400, {
      setItem,
      setTimeout,
      clearTimeout,
    });
    saver.schedule({ v: 3 });
    saver.flush();
    expect(setItem).toHaveBeenCalledWith('k', JSON.stringify({ v: 3 }));
    setItem.mockClear();
    vi.advanceTimersByTime(400);
    expect(setItem).not.toHaveBeenCalled();
  });

  it('swallows storage exceptions', () => {
    const setItem = vi.fn(() => {
      throw new Error('quota');
    });
    const saver = createDebouncedLocalStorageSaver<{ v: number }>('k', 400, {
      setItem,
      setTimeout,
      clearTimeout,
    });
    expect(() => {
      saver.schedule({ v: 1 });
      saver.flush();
    }).not.toThrow();
  });
});
