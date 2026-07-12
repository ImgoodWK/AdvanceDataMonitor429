import { describe, expect, it } from 'vitest';
import { WORLD_MAP_AE_CATEGORY_ICON_IDS } from '@/utils/worldMapAeCategories';

describe('WORLD_MAP_AE_CATEGORY_ICON_IDS', () => {
  it('uses colon meta suffix for storage cell (matches IconItemId.lookupCandidates)', () => {
    expect(WORLD_MAP_AE_CATEGORY_ICON_IDS.cell).toBe('appeng:item.ItemBasicStorageCell:16384');
    expect(WORLD_MAP_AE_CATEGORY_ICON_IDS.cell).not.toContain('.16384');
  });

  it('uses crafting storage tile for cpu category icon', () => {
    expect(WORLD_MAP_AE_CATEGORY_ICON_IDS.cpu).toBe('appliedenergistics2:tile.BlockCraftingUnit');
  });

  it('uses valid registry-style ids for bus and terminal', () => {
    expect(WORLD_MAP_AE_CATEGORY_ICON_IDS.bus).toMatch(/^appeng:item\.ItemMultiPart:\d+$/);
    expect(WORLD_MAP_AE_CATEGORY_ICON_IDS.terminal).toMatch(/^appeng:item\.ItemCraftingTerminal$/);
  });
});
