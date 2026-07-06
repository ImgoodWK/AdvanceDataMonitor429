package com.imgood.textech.webae.recipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.webae.dto.RecipeDto;
import com.imgood.textech.webae.dto.RecipeDto.GridSlot;
import com.imgood.textech.webae.dto.RecipeDto.ItemEntry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Collects recipes directly from game registries (vanilla crafting/smelting, GregTech
 * {@code RecipeMap}), without relying on NEI handler reflection.
 *
 * <p>
 * Safe to run on a background thread — only reads static recipe data.
 * </p>
 */
@SideOnly(Side.CLIENT)
public final class GameRecipeCollector {

    private GameRecipeCollector() {}

    public static boolean isAvailable() {
        return true;
    }

    /**
     * @return all recipes collected from vanilla + GregTech sources
     */
    public static List<RecipeDto> collectAll() {
        List<RecipeDto> all = new ArrayList<RecipeDto>();
        Set<String> seen = new HashSet<String>();

        int vanillaCraft = collectVanillaCrafting(all, seen);
        int smelting = collectSmelting(all, seen);
        int gt = collectGregTech(all, seen);

        AdvanceDataMonitor.LOG.info(
            "[WebAE] Game recipe collect: vanilla={}, smelting={}, gt={}, total={}",
            vanillaCraft,
            smelting,
            gt,
            all.size());
        return all;
    }

    private static int collectVanillaCrafting(List<RecipeDto> out, Set<String> seen) {
        int added = 0;
        List<?> recipes = CraftingManager.getInstance()
            .getRecipeList();
        for (int i = 0; i < recipes.size(); i++) {
            Object obj = recipes.get(i);
            if (!(obj instanceof IRecipe)) continue;
            IRecipe recipe = (IRecipe) obj;
            try {
                ItemStack result = recipe.getRecipeOutput();
                if (result == null || result.getItem() == null) continue;

                RecipeDto dto = new RecipeDto("vanilla:crafting", i, recipeTypeName(recipe));
                dto.recipeType = "crafting";
                dto.outputs.add(itemStackToEntry(result));
                if (recipe instanceof ShapedRecipes) {
                    appendShapedGrid((ShapedRecipes) recipe, dto);
                } else {
                    appendCraftingInputs(recipe, dto.inputs);
                }

                if (dto.inputs.isEmpty()) continue;
                if (!seen.add(dedupKey(dto))) continue;
                out.add(dto);
                added++;
            } catch (Throwable t) {
                if (AdvanceDataMonitor.LOG.isDebugEnabled()) {
                    AdvanceDataMonitor.LOG.debug(
                        "[WebAE] Skip crafting recipe {}",
                        recipe.getClass()
                            .getName(),
                        t);
                }
            }
        }
        return added;
    }

    private static void appendShapedGrid(ShapedRecipes shaped, RecipeDto dto) {
        dto.gridWidth = shaped.recipeWidth;
        dto.gridHeight = shaped.recipeHeight;
        Object[] items = shaped.recipeItems;
        if (items == null) return;
        for (int j = 0; j < items.length; j++) {
            ItemEntry entry = ingredientToEntry(items[j]);
            if (entry == null) continue;
            int col = dto.gridWidth > 0 ? j % dto.gridWidth : j % 3;
            int row = dto.gridWidth > 0 ? j / dto.gridWidth : j / 3;
            dto.inputs.add(entry);
            dto.gridSlots.add(new GridSlot(col, row, entry));
        }
    }

    private static ItemEntry ingredientToEntry(Object ingredient) {
        if (ingredient == null) return null;
        if (ingredient instanceof ItemStack) {
            ItemStack stack = (ItemStack) ingredient;
            if (stack.getItem() != null) return itemStackToEntry(stack);
            return null;
        }
        String token = ingredient.toString();
        if (token.isEmpty()) return null;
        return new ItemEntry("oredict:" + token, token, "oredict:" + token, 0, 1);
    }

    private static void appendCraftingInputs(IRecipe recipe, List<ItemEntry> inputs) {
        if (recipe instanceof ShapedRecipes) {
            ShapedRecipes shaped = (ShapedRecipes) recipe;
            Object[] items = shaped.recipeItems;
            if (items != null) {
                for (int j = 0; j < items.length; j++) {
                    appendIngredient(items[j], inputs);
                }
            }
            return;
        }
        if (recipe instanceof ShapelessRecipes) {
            ShapelessRecipes shapeless = (ShapelessRecipes) recipe;
            List<?> items = shapeless.recipeItems;
            if (items != null) {
                for (int j = 0; j < items.size(); j++) {
                    appendIngredient(items.get(j), inputs);
                }
            }
            return;
        }
        // Modded IRecipe — try common reflection paths
        try {
            java.lang.reflect.Field f = findField(recipe.getClass(), "recipeItems");
            if (f != null) {
                f.setAccessible(true);
                Object val = f.get(recipe);
                if (val instanceof Object[]) {
                    Object[] arr = (Object[]) val;
                    for (int j = 0; j < arr.length; j++) appendIngredient(arr[j], inputs);
                } else if (val instanceof List) {
                    List<?> list = (List<?>) val;
                    for (int j = 0; j < list.size(); j++) appendIngredient(list.get(j), inputs);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void appendIngredient(Object ingredient, List<ItemEntry> inputs) {
        if (ingredient == null) return;
        if (ingredient instanceof ItemStack) {
            ItemStack stack = (ItemStack) ingredient;
            if (stack.getItem() != null) {
                ItemEntry e = itemStackToEntry(stack);
                if (e != null) inputs.add(e);
            }
            return;
        }
        // OreDictionary string or Item
        String token = ingredient.toString();
        if (token.isEmpty()) return;
        inputs.add(new ItemEntry("oredict:" + token, token, "oredict:" + token, 0, 1));
    }

    private static int collectSmelting(List<RecipeDto> out, Set<String> seen) {
        int added = 0;
        Map<?, ?> smelting = FurnaceRecipes.smelting()
            .getSmeltingList();
        int index = 0;
        for (Map.Entry<?, ?> entry : smelting.entrySet()) {
            try {
                if (!(entry.getKey() instanceof ItemStack) || !(entry.getValue() instanceof ItemStack)) {
                    index++;
                    continue;
                }
                ItemStack input = ((ItemStack) entry.getKey()).copy();
                ItemStack output = ((ItemStack) entry.getValue()).copy();
                if (input.getItem() == null || output.getItem() == null) {
                    index++;
                    continue;
                }

                RecipeDto dto = new RecipeDto("vanilla:smelting", index, "Furnace");
                dto.recipeType = "smelting";
                dto.inputs.add(itemStackToEntry(input));
                dto.outputs.add(itemStackToEntry(output));
                if (!seen.add(dedupKey(dto))) {
                    index++;
                    continue;
                }
                out.add(dto);
                added++;
            } catch (Throwable ignored) {}
            index++;
        }
        return added;
    }

    private static int collectGregTech(List<RecipeDto> out, Set<String> seen) {
        int added = 0;
        try {
            Class<?> mapCls = Class.forName("gregtech.api.recipe.RecipeMap");
            Object mapsObj = mapCls.getField("ALL_RECIPE_MAPS")
                .get(null);
            if (!(mapsObj instanceof Map)) return 0;

            Map<?, ?> allMaps = (Map<?, ?>) mapsObj;
            java.lang.reflect.Method getAllRecipes = mapCls.getMethod("getAllRecipes");

            for (Map.Entry<?, ?> mapEntry : allMaps.entrySet()) {
                Object map = mapEntry.getValue();
                if (map == null) continue;
                String mapId = mapEntry.getKey() != null ? mapEntry.getKey()
                    .toString()
                    : map.getClass()
                        .getName();
                String unlocKey = mapId;
                try {
                    Object unloc = mapCls.getField("unlocalizedName")
                        .get(map);
                    if (unloc != null) unlocKey = unloc.toString();
                } catch (Throwable ignored) {}
                String handlerLabel = RecipeDisplayNames.formatHandlerLabel(unlocKey, mapId);

                Collection<?> recipes;
                try {
                    recipes = (Collection<?>) getAllRecipes.invoke(map);
                } catch (Throwable t) {
                    continue;
                }
                if (recipes == null) continue;

                int recipeIndex = 0;
                for (Object recipeObj : recipes) {
                    if (recipeObj == null) {
                        recipeIndex++;
                        continue;
                    }
                    try {
                        if (isGtFakeRecipe(recipeObj)) {
                            recipeIndex++;
                            continue;
                        }
                        RecipeDto dto = gtRecipeToDto("gt:" + mapId, recipeIndex, handlerLabel, recipeObj);
                        if (dto == null || (dto.inputs.isEmpty() && dto.outputs.isEmpty())) {
                            recipeIndex++;
                            continue;
                        }
                        if (!seen.add(dedupKey(dto))) {
                            recipeIndex++;
                            continue;
                        }
                        out.add(dto);
                        added++;
                    } catch (Throwable ignored) {}
                    recipeIndex++;
                }
            }
        } catch (ClassNotFoundException e) {
            AdvanceDataMonitor.LOG.info("[WebAE] GregTech not loaded; skipping GT recipe export");
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[WebAE] GregTech recipe export failed", t);
        }
        return added;
    }

    private static boolean isGtFakeRecipe(Object recipe) {
        try {
            java.lang.reflect.Field f = recipe.getClass()
                .getField("mFakeRecipe");
            return f.getBoolean(recipe);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static RecipeDto gtRecipeToDto(String handlerId, int index, String handlerName, Object recipe) {
        RecipeDto dto = new RecipeDto(handlerId, index, handlerName);
        dto.recipeType = "gt";

        appendItemStackArray(readItemStackArray(recipe, "mOutputs"), dto.outputs);
        appendItemStackArray(readItemStackArray(recipe, "mInputs"), dto.inputs);
        appendFluidArray(readFluidArray(recipe, "mFluidOutputs"), dto.outputs);
        appendFluidArray(readFluidArray(recipe, "mFluidInputs"), dto.inputs);
        appendGtMetadata(dto, recipe);

        if (dto.inputs.isEmpty() && dto.outputs.isEmpty()) return null;
        return dto;
    }

    private static void appendGtMetadata(RecipeDto dto, Object recipe) {
        try {
            java.lang.reflect.Field eutField = findField(recipe.getClass(), "mEUt");
            if (eutField != null) {
                eutField.setAccessible(true);
                dto.euPerTick = Long.valueOf(eutField.getLong(recipe));
            }
        } catch (Throwable ignored) {}
        try {
            java.lang.reflect.Field durField = findField(recipe.getClass(), "mDuration");
            if (durField != null) {
                durField.setAccessible(true);
                dto.durationTicks = Integer.valueOf(durField.getInt(recipe));
            }
        } catch (Throwable ignored) {}
        try {
            java.lang.reflect.Method tierMethod = recipe.getClass()
                .getMethod("getVoltageTier");
            Object tier = tierMethod.invoke(recipe);
            if (tier != null) dto.voltageTier = tier.toString();
        } catch (Throwable ignored) {}
        if (dto.voltageTier == null) {
            try {
                java.lang.reflect.Field voltField = findField(recipe.getClass(), "mVoltage");
                if (voltField != null) {
                    voltField.setAccessible(true);
                    dto.voltageTier = String.valueOf(voltField.getLong(recipe));
                }
            } catch (Throwable ignored) {}
        }
        if (dto.euPerTick != null && dto.durationTicks != null && dto.durationTicks.intValue() > 0) {
            dto.powerConsumption = Long.valueOf(dto.euPerTick.longValue() * dto.durationTicks.intValue());
        }
        try {
            java.lang.reflect.Field cleanField = findField(recipe.getClass(), "mRequiresCleanRoom");
            if (cleanField != null) {
                cleanField.setAccessible(true);
                dto.requiresCleanroom = Boolean.valueOf(cleanField.getBoolean(recipe));
            }
        } catch (Throwable ignored) {}
    }

    private static ItemStack[] readItemStackArray(Object recipe, String fieldName) {
        try {
            java.lang.reflect.Field f = findField(recipe.getClass(), fieldName);
            if (f == null) return null;
            f.setAccessible(true);
            Object val = f.get(recipe);
            if (val instanceof ItemStack[]) return (ItemStack[]) val;
        } catch (Throwable ignored) {}
        return null;
    }

    private static FluidStack[] readFluidArray(Object recipe, String fieldName) {
        try {
            java.lang.reflect.Field f = findField(recipe.getClass(), fieldName);
            if (f == null) return null;
            f.setAccessible(true);
            Object val = f.get(recipe);
            if (val instanceof FluidStack[]) return (FluidStack[]) val;
        } catch (Throwable ignored) {}
        return null;
    }

    private static void appendItemStackArray(ItemStack[] stacks, List<ItemEntry> target) {
        if (stacks == null) return;
        for (int i = 0; i < stacks.length; i++) {
            if (stacks[i] == null || stacks[i].getItem() == null) continue;
            ItemEntry e = itemStackToEntry(stacks[i]);
            if (e != null) target.add(e);
        }
    }

    private static void appendFluidArray(FluidStack[] stacks, List<ItemEntry> target) {
        if (stacks == null) return;
        for (int i = 0; i < stacks.length; i++) {
            ItemEntry e = fluidStackToEntry(stacks[i]);
            if (e != null) target.add(e);
        }
    }

    private static ItemEntry fluidStackToEntry(FluidStack fs) {
        if (fs == null || fs.getFluid() == null) return null;
        String fluidName = FluidRegistry.getFluidName(fs.getFluid());
        if (fluidName == null || fluidName.isEmpty()) fluidName = fs.getFluid()
            .getName();
        String id = "fluid:" + fluidName;
        String display = fs.getFluid()
            .getLocalizedName(fs);
        return new ItemEntry(id, display, id, 0, fs.amount);
    }

    private static String recipeTypeName(IRecipe recipe) {
        if (recipe instanceof ShapedRecipes) return "Shaped Crafting";
        if (recipe instanceof ShapelessRecipes) return "Shapeless Crafting";
        return recipe.getClass()
            .getSimpleName();
    }

    private static ItemEntry itemStackToEntry(ItemStack stack) {
        return RecipeItemEntries.fromStack(stack);
    }

    private static String dedupKey(RecipeDto dto) {
        StringBuilder sb = new StringBuilder();
        sb.append(dto.handlerId)
            .append('|')
            .append(dto.recipeIndex)
            .append('|');
        appendEntries(sb, dto.outputs);
        sb.append('|');
        appendEntries(sb, dto.inputs);
        return sb.toString();
    }

    private static void appendEntries(StringBuilder sb, List<ItemEntry> entries) {
        if (entries == null) return;
        for (int i = 0; i < entries.size(); i++) {
            ItemEntry e = entries.get(i);
            sb.append(e.registryName)
                .append('#')
                .append(e.meta)
                .append('x')
                .append(e.stackSize)
                .append(';');
        }
    }

    private static java.lang.reflect.Field findField(Class<?> cls, String name) {
        Class<?> current = cls;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
