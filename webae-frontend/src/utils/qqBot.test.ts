import { describe, expect, it } from 'vitest';
import { joinQqBotList, qqBotConnectionColor, splitQqBotList } from './qqBot';

describe('qq bot admin helpers', () => {
  it('splits, trims, and deduplicates ids', () => {
    expect(splitQqBotList(' g1\ng2, g1\n')).toEqual(['g1', 'g2']);
  });

  it('joins ids one per line', () => {
    expect(joinQqBotList(['group:g1', 'c2c:u1'])).toBe('group:g1\nc2c:u1');
  });

  it('maps connection phases to stable tag colors', () => {
    expect(qqBotConnectionColor(true, 'ready')).toBe('success');
    expect(qqBotConnectionColor(false, 'reconnecting')).toBe('processing');
    expect(qqBotConnectionColor(false, 'unconfigured')).toBe('error');
  });
});
