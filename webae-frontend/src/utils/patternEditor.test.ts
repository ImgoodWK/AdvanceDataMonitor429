import { describe, expect, it } from 'vitest';
import { interfaceAddress, parsePatternId, recipeToPatternDraft } from './patternEditor';
import type { InterfaceDto, RecipeDto } from '@/types/dto';

function recipe(partial: Partial<RecipeDto>): RecipeDto {
  return {
    handlerId: 'gt:assembler',
    recipeIndex: 1,
    handlerName: 'Assembler',
    inputs: [],
    outputs: [],
    ...partial,
  };
}

describe('pattern editor mapping', () => {
  it('keeps shaped crafting positions instead of compacting empty slots', () => {
    const draft = recipeToPatternDraft(
      recipe({
        recipeType: 'crafting',
        handlerId: 'vanilla:crafting',
        inputs: [{ registryName: 'minecraft:iron_ingot', displayName: 'Iron', meta: 0, stackSize: 1 }],
        outputs: [{ registryName: 'minecraft:bucket', displayName: 'Bucket', meta: 0, stackSize: 1 }],
        gridSlots: [{ col: 2, row: 1, item: { registryName: 'minecraft:iron_ingot', displayName: 'Iron', meta: 0, stackSize: 1 } }],
      })
    );
    expect(draft.crafting).toBe(true);
    expect(draft.inputs[5]?.registryName).toBe('minecraft:iron_ingot');
    expect(draft.inputs.filter(Boolean)).toHaveLength(1);
  });

  it('marks GT stack-size-zero inputs as non-consumable', () => {
    const draft = recipeToPatternDraft(
      recipe({
        inputs: [{ registryName: 'gregtech:gt.metaitem.01', displayName: 'Mold', meta: 100, stackSize: 0 }],
        outputs: [{ registryName: 'minecraft:iron_ingot', displayName: 'Iron', meta: 0, stackSize: 1 }],
      })
    );
    expect(draft.inputs[0]?.stackSize).toBe(1);
    expect(draft.inputs[0]?.nonConsumable).toBe(true);
  });

  it('preserves output metadata and item NBT from a recipe', () => {
    const draft = recipeToPatternDraft(
      recipe({
        outputs: [{
          registryName: 'gregtech:gt.metaitem.01',
          displayName: 'Configured item',
          meta: 32710,
          stackSize: 1,
          nbt: '{"mode":{"type":"TAG_Int","value":4}}',
        }],
      })
    );
    expect(draft.outputs[0]?.meta).toBe(32710);
    expect(draft.outputs[0]?.nbt).toContain('mode');
  });

  it('parses cable-part pattern ids', () => {
    expect(parsePatternId('10:64:-4:0@NORTH#8')).toEqual({
      x: 10,
      y: 64,
      z: -4,
      dim: 0,
      partSide: 'NORTH',
      slot: 8,
    });
  });

  it('uses the backend interface id for part addresses', () => {
    const iface = {
      interfaceId: '1:2:3:0@UP',
      x: 1,
      y: 2,
      z: 3,
      dim: 0,
    } as InterfaceDto;
    expect(interfaceAddress(iface)).toBe('1:2:3:0@UP');
  });
});
