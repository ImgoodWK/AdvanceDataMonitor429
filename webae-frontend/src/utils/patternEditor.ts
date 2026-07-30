import type {
  InterfaceDto,
  PatternItemEntry,
  RecipeDto,
  RecipeItemEntry,
} from '@/types/dto';
import type {
  PatternEditorInputSlot,
  PatternEditorOutputRow,
} from '@/components/patterns/patternEditorTypes';

export interface ParsedPatternId {
  x: number;
  y: number;
  z: number;
  dim: number;
  partSide: string;
  slot: number;
}

export interface RecipePatternDraft {
  crafting: boolean;
  inputs: (PatternEditorInputSlot | null)[];
  outputs: PatternEditorOutputRow[];
}

export function interfaceAddress(iface: InterfaceDto): string {
  if (iface.interfaceId) return iface.interfaceId;
  const base = `${iface.x}:${iface.y}:${iface.z}:${iface.dim}`;
  return iface.partSide ? `${base}@${iface.partSide}` : base;
}

export function parsePatternId(id: string): ParsedPatternId | null {
  const hash = id.lastIndexOf('#');
  if (hash < 0) return null;
  let address = id.slice(0, hash);
  const slot = Number(id.slice(hash + 1));
  let partSide = '';
  const at = address.lastIndexOf('@');
  if (at >= 0) {
    partSide = address.slice(at + 1);
    address = address.slice(0, at);
  }
  const coords = address.split(':').map(Number);
  if (coords.length !== 4 || coords.some(Number.isNaN) || Number.isNaN(slot)) return null;
  return { x: coords[0], y: coords[1], z: coords[2], dim: coords[3], partSide, slot };
}

export function recipeIsCrafting(recipe: RecipeDto): boolean {
  const kind = `${recipe.recipeType || ''} ${recipe.handlerId || ''} ${recipe.handlerName || ''}`.toLowerCase();
  return kind.includes('crafting') && !kind.includes('processing');
}

function isNonConsumable(entry: RecipeItemEntry, inputs: RecipeItemEntry[]): boolean {
  if (entry.nonConsumable || entry.stackSize <= 0) return true;
  return inputs.some(
    (candidate) =>
      candidate.registryName === entry.registryName &&
      (candidate.meta ?? 0) === (entry.meta ?? 0) &&
      (candidate.nonConsumable || candidate.stackSize <= 0)
  );
}

function toInput(entry: RecipeItemEntry, allInputs: RecipeItemEntry[]): PatternEditorInputSlot {
  const nonConsumable = isNonConsumable(entry, allInputs);
  return {
    registryName: entry.registryName,
    displayName: entry.displayName || entry.registryName,
    meta: entry.meta ?? 0,
    stackSize: Math.max(1, entry.stackSize || 1),
    nbt: entry.nbt,
    isFluid: Boolean(entry.registryName?.startsWith('fluid:')),
    nonConsumable,
    programmableCircuit: false,
  };
}

export function recipeToPatternDraft(recipe: RecipeDto): RecipePatternDraft {
  const crafting = recipeIsCrafting(recipe);
  const inputs = new Array<PatternEditorInputSlot | null>(27).fill(null);
  const allInputs = recipe.inputs || [];

  if (crafting && recipe.gridSlots?.length) {
    for (const gridSlot of recipe.gridSlots) {
      if (gridSlot.col < 0 || gridSlot.col >= 3 || gridSlot.row < 0 || gridSlot.row >= 3) continue;
      inputs[gridSlot.row * 3 + gridSlot.col] = toInput(gridSlot.item, allInputs);
    }
  } else {
    allInputs.slice(0, inputs.length).forEach((entry, index) => {
      if (entry?.registryName) inputs[index] = toInput(entry, allInputs);
    });
  }

  const outputs: PatternEditorOutputRow[] = (recipe.outputs || [])
    .filter((entry) => Boolean(entry?.registryName))
    .map((entry, index) => {
      const stackSize = Math.max(1, entry.stackSize || 1);
      return {
        key: String(index + 1),
        registryName: entry.registryName,
        displayName: entry.displayName || entry.registryName,
        meta: entry.meta ?? 0,
        stackSize,
        nbt: entry.nbt,
        originalStackSize: stackSize,
        isFluid: Boolean(entry.registryName?.startsWith('fluid:')),
      };
    });

  return { crafting, inputs, outputs };
}

export function patternEntryToInput(entry: PatternItemEntry): PatternEditorInputSlot {
  return {
    registryName: entry.registryName,
    displayName: entry.displayName || entry.registryName,
    meta: entry.meta ?? 0,
    stackSize: Math.max(1, entry.stackSize || 1),
    nbt: entry.nbt,
    isFluid: Boolean(entry.isFluid),
    nonConsumable: Boolean(entry.nonConsumable || entry.programmableCircuit),
    programmableCircuit: Boolean(entry.programmableCircuit),
  };
}
