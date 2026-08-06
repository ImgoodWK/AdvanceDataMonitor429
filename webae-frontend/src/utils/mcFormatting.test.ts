import { describe, expect, it } from 'vitest';

import { getMcPrimaryColor, parseMcFormatting, stripMcFormatting } from '@/utils/mcFormatting';

describe('parseMcFormatting', () => {
  it('parses color and bold', () => {
    const segments = parseMcFormatting('§a§lTier 4');
    expect(segments).toEqual([
      { text: 'Tier 4', color: '#55FF55', bold: true },
    ]);
  });

  it('parses italic and reset', () => {
    const segments = parseMcFormatting('§oand§r fusion');
    expect(segments).toEqual([
      { text: 'and', italic: true },
      { text: ' fusion' },
    ]);
  });

  it('handles empty and plain text', () => {
    expect(parseMcFormatting('')).toEqual([{ text: '' }]);
    expect(parseMcFormatting('plain')).toEqual([{ text: 'plain' }]);
  });

  it('supports ampersand prefix', () => {
    const segments = parseMcFormatting('&cRed');
    expect(segments).toEqual([{ text: 'Red', color: '#FF5555' }]);
  });
});

describe('stripMcFormatting', () => {
  it('removes format codes for search', () => {
    expect(stripMcFormatting('§a§lWhy Grenades?')).toBe('Why Grenades?');
    expect(stripMcFormatting('&a&lWhy Grenades?')).toBe('Why Grenades?');
  });

  it('handles empty input', () => {
    expect(stripMcFormatting('')).toBe('');
  });
});

describe('getMcPrimaryColor', () => {
  it('returns first color as hex', () => {
    expect(getMcPrimaryColor('§a§lTier 4')).toBe('#55FF55');
    expect(getMcPrimaryColor('plain')).toBeUndefined();
  });
});
