package com.imgood.textech.webae.pattern;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.imgood.textech.assistant.ItemStackUtils;
import com.imgood.textech.webae.dto.PatternBrowseEntryDto;
import com.imgood.textech.webae.dto.PatternDto.PatternItemEntry;

import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

/**
 * Enumerates all registered crafting patterns from {@link ICraftingGrid} without
 * deduplicating by output — each {@link ICraftingPatternDetails} is a separate entry.
 */
public final class CraftingGridPatternQuery {

    private CraftingGridPatternQuery() {}

    /**
     * Collect all grid craftable patterns. Must run on server thread.
     *
     * @param craftingGrid AE crafting grid cache (may be null)
     * @param query        optional fuzzy name filter (empty = all)
     * @return flat list of grid-sourced browse entries
     */
    public static List<PatternBrowseEntryDto> collect(ICraftingGrid craftingGrid, String query) {
        List<PatternBrowseEntryDto> result = new ArrayList<PatternBrowseEntryDto>();
        if (craftingGrid == null) {
            return result;
        }
        String q = query == null ? "" : query.trim();
        try {
            Map<IAEItemStack, ? extends Collection<ICraftingPatternDetails>> patterns = craftingGrid
                .getCraftingPatterns();
            if (patterns == null || patterns.isEmpty()) {
                return result;
            }
            int gridIndex = 0;
            for (Entry<IAEItemStack, ? extends Collection<ICraftingPatternDetails>> entry : patterns.entrySet()) {
                Collection<ICraftingPatternDetails> details = entry.getValue();
                if (details == null || details.isEmpty()) {
                    continue;
                }
                for (ICraftingPatternDetails detail : details) {
                    if (detail == null) {
                        continue;
                    }
                    PatternBrowseEntryDto dto = fromDetail(detail, gridIndex);
                    if (dto == null) {
                        continue;
                    }
                    if (!q.isEmpty() && !matchesQuery(dto, q)) {
                        continue;
                    }
                    result.add(dto);
                    gridIndex++;
                }
            }
        } catch (Throwable ignored) {
            // Grid API unavailable — return empty
        }
        return result;
    }

    /**
     * Return the grid browse entry at {@code targetIndex} (same ordering as {@link #collect}).
     */
    public static PatternBrowseEntryDto getByIndex(ICraftingGrid craftingGrid, int targetIndex) {
        if (craftingGrid == null || targetIndex < 0) {
            return null;
        }
        try {
            Map<IAEItemStack, ? extends Collection<ICraftingPatternDetails>> patterns = craftingGrid
                .getCraftingPatterns();
            if (patterns == null || patterns.isEmpty()) {
                return null;
            }
            int gridIndex = 0;
            for (Entry<IAEItemStack, ? extends Collection<ICraftingPatternDetails>> entry : patterns.entrySet()) {
                Collection<ICraftingPatternDetails> details = entry.getValue();
                if (details == null || details.isEmpty()) {
                    continue;
                }
                for (ICraftingPatternDetails detail : details) {
                    if (detail == null) {
                        continue;
                    }
                    if (gridIndex == targetIndex) {
                        return fromDetail(detail, gridIndex);
                    }
                    gridIndex++;
                }
            }
        } catch (Throwable ignored) {
            // Grid API unavailable
        }
        return null;
    }

    private static PatternBrowseEntryDto fromDetail(ICraftingPatternDetails detail, int gridIndex) {
        PatternBrowseEntryDto dto = new PatternBrowseEntryDto();
        dto.source = "grid";
        dto.gridKey = "grid:" + gridIndex;
        dto.gridIndex = gridIndex;
        dto.patternId = dto.gridKey;
        dto.sourceInterface = "grid";
        dto.sourceInterfaceName = "Crafting Grid";
        dto.slotIndex = gridIndex;
        dto.crafting = isCrafting(detail);
        dto.substitute = false;
        dto.beSubstitute = false;
        dto.author = "";

        IAEItemStack[] condensedInputs = detail.getCondensedInputs();
        IAEItemStack[] condensedOutputs = detail.getCondensedOutputs();
        if (condensedInputs != null) {
            for (IAEItemStack stack : condensedInputs) {
                PatternItemEntry pe = aeStackToEntry(stack);
                if (pe != null) {
                    dto.inputs.add(pe);
                }
            }
        }
        if (condensedOutputs != null) {
            for (IAEItemStack stack : condensedOutputs) {
                PatternItemEntry pe = aeStackToEntry(stack);
                if (pe != null) {
                    dto.outputs.add(pe);
                }
            }
        }
        dto.inputsCount = dto.inputs.size();
        dto.outputsCount = dto.outputs.size();
        if (dto.outputs.isEmpty()) {
            return null;
        }
        PatternItemEntry primary = dto.outputs.get(0);
        dto.displayName = primary.displayName;
        dto.registryName = primary.registryName;
        dto.meta = primary.meta;
        dto.amount = primary.stackSize;
        return dto;
    }

    private static PatternItemEntry aeStackToEntry(IAEItemStack aeStack) {
        if (aeStack == null || aeStack.getStackSize() <= 0) {
            return null;
        }
        ItemStack stack = aeStack.getItemStack();
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        PatternItemEntry entry = new PatternItemEntry();
        Object nameObj = Item.itemRegistry.getNameForObject(stack.getItem());
        entry.registryName = nameObj != null ? nameObj.toString() : "";
        entry.displayName = stack.getDisplayName();
        entry.meta = stack.getItemDamage();
        if (entry.meta == Short.MAX_VALUE) {
            entry.meta = 0;
        }
        entry.stackSize = (int) Math.min(aeStack.getStackSize(), Integer.MAX_VALUE);
        entry.isFluid = entry.registryName.contains("fluid") || entry.registryName.startsWith("ae2fc:");
        return entry;
    }

    private static boolean isCrafting(ICraftingPatternDetails pattern) {
        try {
            java.lang.reflect.Method method = pattern.getClass()
                .getMethod("isCraftable");
            Object value = method.invoke(pattern);
            return !(value instanceof Boolean) || ((Boolean) value).booleanValue();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static boolean matchesQuery(PatternBrowseEntryDto dto, String query) {
        if (ItemStackUtils.fuzzyNameMatches(stackFromEntry(dto.displayName, dto.registryName), query)) {
            return true;
        }
        for (PatternItemEntry pe : dto.outputs) {
            if (pe != null && ItemStackUtils.fuzzyNameMatches(stackFromEntry(pe.displayName, pe.registryName), query)) {
                return true;
            }
        }
        for (PatternItemEntry pe : dto.inputs) {
            if (pe != null && ItemStackUtils.fuzzyNameMatches(stackFromEntry(pe.displayName, pe.registryName), query)) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack stackFromEntry(String displayName, String registryName) {
        if (registryName == null || registryName.isEmpty()) {
            return null;
        }
        Object itemObj = Item.itemRegistry.getObject(registryName);
        if (!(itemObj instanceof Item)) {
            return null;
        }
        ItemStack stack = new ItemStack((Item) itemObj, 1, 0);
        if (displayName != null && !displayName.isEmpty()) {
            stack.setStackDisplayName(displayName);
        }
        return stack;
    }
}
