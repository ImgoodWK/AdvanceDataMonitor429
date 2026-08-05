import { describe, expect, it } from 'vitest';
import { clampProgressPercent, resolveProgressPercent } from './ScalarWidgetRenderer';

describe('ScalarWidgetRenderer progress semantics', () => {
  it('keeps unknown absolute capacity unavailable', () => {
    expect(clampProgressPercent(undefined)).toBeUndefined();
    expect(clampProgressPercent(Number.NaN)).toBeUndefined();
  });

  it('clamps real maximum or explicit target ratios', () => {
    expect(clampProgressPercent(42.5)).toBe(42.5);
    expect(clampProgressPercent(-1)).toBe(0);
    expect(clampProgressPercent(125)).toBe(100);
  });

  it('prefers a real maximum and keeps ordinary percentages direct', () => {
    expect(resolveProgressPercent({ value: 500, realMaximum: 1000, targetValue: 2000 })).toBe(50);
    expect(resolveProgressPercent({ value: 42.5, percentMetric: true })).toBe(42.5);
  });

  it('requires a target for steam-like metrics with unknown capacity', () => {
    expect(resolveProgressPercent({
      value: 0,
      absoluteValue: 500,
      percentMetric: true,
      capacityKnown: false,
    })).toBeUndefined();
    expect(resolveProgressPercent({
      value: 0,
      absoluteValue: 500,
      percentMetric: true,
      capacityKnown: false,
      targetValue: 1000,
    })).toBe(50);
  });
});
