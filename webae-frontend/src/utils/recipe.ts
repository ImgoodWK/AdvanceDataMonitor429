import type { RecipeDto, RecipeItemEntry } from '@/types/dto';
import { FLUID_ID_PREFIX } from '@/utils/icon';

export const FLUID_REGISTRY_PREFIX = FLUID_ID_PREFIX;

export function isFluidEntry(entry: RecipeItemEntry | null | undefined): boolean {
  return Boolean(entry?.registryName?.startsWith(FLUID_REGISTRY_PREFIX));
}

export function splitEntries(entries: RecipeItemEntry[]): {
  items: RecipeItemEntry[];
  fluids: RecipeItemEntry[];
} {
  const items: RecipeItemEntry[] = [];
  const fluids: RecipeItemEntry[] = [];
  for (const e of entries) {
    if (isFluidEntry(e)) fluids.push(e);
    else items.push(e);
  }
  return { items, fluids };
}

export function primaryOutput(recipe: { outputs: RecipeItemEntry[] }): RecipeItemEntry | null {
  if (!recipe.outputs?.length) return null;
  return recipe.outputs.find((o) => !isFluidEntry(o)) || recipe.outputs[0];
}

export function recipeKey(recipe: { handlerId: string; recipeIndex: number }, idx?: number): string {
  return `${recipe.handlerId}_${recipe.recipeIndex}_${idx ?? 0}`;
}

export interface RecipeMergedGroup {
  primaryOutputKey: string;
  primaryOutput: RecipeItemEntry;
  recipes: RecipeDto[];
}

/** Group recipes by primary output registryName (client-side merge for display). */
export function groupByPrimaryOutput(recipes: RecipeDto[]): RecipeMergedGroup[] {
  const map = new Map<string, RecipeMergedGroup>();
  for (const recipe of recipes) {
    const main = primaryOutput(recipe);
    if (!main) continue;
    const key = main.registryName;
    let group = map.get(key);
    if (!group) {
      group = { primaryOutputKey: key, primaryOutput: main, recipes: [] };
      map.set(key, group);
    }
    group.recipes.push(recipe);
  }
  return Array.from(map.values());
}

export function filterByHandlers(recipes: RecipeDto[], handlerIds: string[]): RecipeDto[] {
  if (handlerIds.length === 0) return recipes;
  const set = new Set(handlerIds);
  return recipes.filter((r) => set.has(r.handlerId));
}

import { primaryIconId } from '@/utils/icon';

/** Icon cache id aligned with server RecipeItemEntries / IconItemId. */
export function resolveIconItemId(
  item: RecipeItemEntry | { itemId?: string; registryName?: string; meta?: number } | null | undefined
): string {
  return primaryIconId(item);
}
