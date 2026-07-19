import { describe, expect, it } from 'vitest';

import { THEME_COLORS } from './colors';
import {
  DESIGN_PACK_CATEGORIES,
  DESIGN_PACKS,
  filterDesignPacks,
  isDesignPackActive,
} from './designPacks';
import { THEME_LAYOUTS } from './layouts';
import { PAGE_STYLES } from './pageStyles';

describe('design packs', () => {
  it('keeps every curated reference inside the four appearance catalogs', () => {
    const ids = new Set<string>();
    for (const pack of DESIGN_PACKS) {
      expect(ids.has(pack.id)).toBe(false);
      ids.add(pack.id);
      expect(DESIGN_PACK_CATEGORIES).toContain(pack.category);
      expect(THEME_COLORS).toContain(pack.themeColor);
      expect(THEME_LAYOUTS).toContain(pack.themeLayout);
      expect(PAGE_STYLES).toContain(pack.pageStyle);
      expect(['none', 'subtle', 'full']).toContain(pack.effectsLevel);
    }
    expect(DESIGN_PACKS.length).toBeGreaterThanOrEqual(28);
  });

  it('filters by category, favorites and multilingual search terms', () => {
    expect(filterDesignPacks(DESIGN_PACKS, '', 'gregtech').length).toBeGreaterThanOrEqual(7);
    expect(filterDesignPacks(DESIGN_PACKS, '', 'featured').slice(0, 3).map((pack) => pack.id)).toEqual([
      'hextech-piltover-forge',
      'koprulu-terran-bridge',
      'aiur-protoss-nexus',
    ]);
    expect(filterDesignPacks(DESIGN_PACKS, '流浪地球', 'all').map((pack) => pack.id)).toContain(
      'ueg-earth-engine'
    );
    expect(
      filterDesignPacks(DESIGN_PACKS, '', 'favorites', new Set(['gtnh-stargate-command'])).map(
        (pack) => pack.id
      )
    ).toEqual(['gtnh-stargate-command']);
  });

  it('only marks an exact four-axis combination active', () => {
    const pack = DESIGN_PACKS[0];
    expect(isDesignPackActive(pack, pack)).toBe(true);
    expect(isDesignPackActive(pack, { ...pack, effectsLevel: 'none' })).toBe(false);
  });
});
