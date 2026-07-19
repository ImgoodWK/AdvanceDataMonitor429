import { describe, expect, it } from 'vitest';
import { resolveActiveNetworkId } from './networkSelection';

describe('resolveActiveNetworkId', () => {
  it('keeps a preferred network while it remains selected', () => {
    expect(resolveActiveNetworkId([1, 2], 2)).toBe(2);
  });

  it('falls back when the preferred network was deselected', () => {
    expect(resolveActiveNetworkId([3, 4], 2)).toBe(3);
  });

  it('uses the neutral network id when nothing is selected', () => {
    expect(resolveActiveNetworkId([], null)).toBe(0);
  });
});
