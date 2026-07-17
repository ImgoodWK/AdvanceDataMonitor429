package com.imgood.textech.webae.quest;

import java.lang.reflect.Method;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.imgood.textech.Config;
import com.imgood.textech.webae.dto.StorageDto;
import com.imgood.textech.webae.dto.StorageDto.FluidEntry;
import com.imgood.textech.webae.dto.StorageDto.ItemEntry;

/**
 * Fluid ↔ fluid-cell equivalence for WebAE quest analysis and escrow.
 * <p>
 * Default scope: GT / IC2 fluid cells. When {@link Config#webQuestFluidAllContainersOption}
 * is enabled and the request sets {@code includeAllFluidContainers}, any
 * {@link FluidContainerRegistry} container is included.
 */
public final class QuestFluidEquivalence {

    private static volatile Method gtFillFluidContainer;
    private static volatile Method gtGetContainerForFilled;
    private static volatile boolean gtFillResolved;
    private static volatile boolean gtEmptyResolved;

    private QuestFluidEquivalence() {}

    /** Effective wide-container flag: config must allow and request must ask. */
    public static boolean resolveIncludeAll(boolean requestFlag) {
        return Config.webQuestFluidAllContainersOption && requestFlag;
    }

    public static final class StockBreakdown {

        public long freeMb;
        public long fromCellsMb;
        public long totalFluidMb;
        /** Exact matching filled cell / item count in AE. */
        public long filledCellCount;
        /** Empty containers that can become the target filled cell. */
        public long emptyCellCount;
        /** Capacity mB of one target filled cell (0 if unknown). */
        public int capacityMb;
        /** DETECT: filled + floor(free/cap) + optional other-container units. */
        public long detectAvailable;
        /** SUBMIT: filled + min(empty, floor(free/cap)); never free-only. */
        public long submitAvailable;
        /** Other matching fluid containers (wide scope) counted as units for DETECT. */
        public long otherContainerUnits;
    }

    public static ItemStack stackFromKey(String registryName, int meta) {
        if (registryName == null || registryName.isEmpty()) {
            return null;
        }
        Object itemObj = Item.itemRegistry.getObject(registryName);
        if (!(itemObj instanceof Item)) {
            return null;
        }
        return new ItemStack((Item) itemObj, 1, Math.max(0, meta));
    }

    public static boolean isFluidCellTask(String registryName, int meta) {
        ItemStack proto = stackFromKey(registryName, meta);
        return proto != null && QuestFluidIconResolver.resolveFluidName(proto) != null;
    }

    public static boolean isInScope(ItemStack stack, boolean includeAll) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        if (includeAll) {
            return QuestFluidIconResolver.resolveFluidName(stack) != null
                || FluidContainerRegistry.isContainer(stack);
        }
        return isGtOrIc2Cell(stack);
    }

    public static boolean isGtOrIc2Cell(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        Object nameObj = Item.itemRegistry.getNameForObject(stack.getItem());
        String registry = nameObj != null ? nameObj.toString() : "";
        if ("IC2:itemCellEmpty".equals(registry)) {
            return true;
        }
        if (registry.indexOf("gregtech") >= 0 && registry.indexOf("metaitem") >= 0) {
            return QuestFluidIconResolver.resolveFluidName(stack) != null || isEmptyCellCandidate(stack);
        }
        // Filled GT cells often still resolve via GTUtility / FCR.
        if (QuestFluidIconResolver.resolveFluidName(stack) != null && registry.indexOf("gregtech") >= 0) {
            return true;
        }
        return false;
    }

    public static int capacityMb(ItemStack filled) {
        if (filled == null) {
            return 0;
        }
        FluidStack fs = FluidContainerRegistry.getFluidForFilledItem(filled);
        if (fs != null && fs.amount > 0) {
            return fs.amount;
        }
        String name = QuestFluidIconResolver.resolveFluidName(filled);
        if (name != null) {
            // GT cells default to 1000 mB when FCR has no entry.
            return 1000;
        }
        return 0;
    }

    public static ItemStack emptyForFilled(ItemStack filled) {
        if (filled == null) {
            return null;
        }
        // Forge 1.7.10: prefer GTUtility / IC2 heuristics; FCR has no universal empty lookup on all builds.
        Method m = gtEmptyMethod();
        if (m != null) {
            try {
                Object result = m.invoke(null, filled, Boolean.TRUE);
                if (result instanceof ItemStack) {
                    return (ItemStack) result;
                }
            } catch (Throwable ignored) {}
        }
        Object nameObj = Item.itemRegistry.getNameForObject(filled.getItem());
        String registry = nameObj != null ? nameObj.toString() : "";
        if ("IC2:itemCellEmpty".equals(registry)) {
            return new ItemStack(filled.getItem(), 1, 0);
        }
        // Common GT empty cell meta.
        if (registry.indexOf("gregtech") >= 0 && registry.indexOf("metaitem") >= 0) {
            Object emptyItem = Item.itemRegistry.getObject("gregtech:gt.metaitem.01");
            if (emptyItem instanceof Item) {
                return new ItemStack((Item) emptyItem, 1, 32100);
            }
        }
        return null;
    }

    public static ItemStack fillEmpty(ItemStack empty, FluidStack fluid) {
        return fillEmpty(empty, fluid, null);
    }

    /**
     * Fill an empty container. When {@code targetFilled} is set (quest cell meta), only a stack
     * matching that item+damage is accepted — FCR/GT often return a different cell type, which
     * would make BQ reject the submit while empties/fluid were already consumed.
     * <p>
     * GT cells (same item, damage = fluid): materialize the exact quest stack after empty+fluid
     * are accounted for.
     */
    public static ItemStack fillEmpty(ItemStack empty, FluidStack fluid, ItemStack targetFilled) {
        if (empty == null || fluid == null || fluid.amount <= 0) {
            return null;
        }
        // GT damage-coded cells: prefer exact quest meta before FCR (avoids wrong IC2/etc. product).
        ItemStack materialized = materializeTargetCell(empty, fluid, targetFilled);
        if (materialized != null) {
            return materialized;
        }
        ItemStack filled = FluidContainerRegistry.fillFluidContainer(fluid, empty);
        if (matchesTargetFilled(filled, targetFilled)) {
            return filled;
        }
        Method m = gtFillMethod();
        if (m != null) {
            try {
                Object result = m.invoke(null, fluid, empty, Boolean.TRUE);
                if (result instanceof ItemStack) {
                    ItemStack viaGt = (ItemStack) result;
                    if (matchesTargetFilled(viaGt, targetFilled)) {
                        return viaGt;
                    }
                    // Same item, wrong damage → coerce to quest meta when fluid matches.
                    if (targetFilled != null && viaGt.getItem() == targetFilled.getItem()) {
                        return materializeTargetCell(empty, fluid, targetFilled);
                    }
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static boolean matchesTargetFilled(ItemStack filled, ItemStack targetFilled) {
        if (filled == null || filled.getItem() == null) {
            return false;
        }
        if (targetFilled == null) {
            return true;
        }
        return filled.getItem() == targetFilled.getItem()
            && filled.getItemDamage() == targetFilled.getItemDamage();
    }

    private static ItemStack materializeTargetCell(ItemStack empty, FluidStack fluid, ItemStack targetFilled) {
        if (targetFilled == null || empty == null || fluid == null || fluid.getFluid() == null) {
            return null;
        }
        if (empty.getItem() != targetFilled.getItem()) {
            return null;
        }
        String want = QuestFluidIconResolver.resolveFluidName(targetFilled);
        if (want == null || !want.equalsIgnoreCase(fluid.getFluid()
            .getName())) {
            return null;
        }
        int need = capacityMb(targetFilled);
        if (need <= 0) {
            need = 1000;
        }
        if (fluid.amount < need) {
            return null;
        }
        ItemStack exact = targetFilled.copy();
        exact.stackSize = 1;
        return exact;
    }

    public static long freeFluidMb(StorageDto storage, String fluidName) {
        if (storage == null || storage.fluids == null || fluidName == null) {
            return 0L;
        }
        long total = 0L;
        for (FluidEntry fluid : storage.fluids) {
            if (fluid != null && fluidName.equalsIgnoreCase(fluid.fluidName)) {
                total += fluid.amount;
            }
        }
        return total;
    }

    public static long itemCount(StorageDto storage, String registryName, int meta) {
        if (storage == null || storage.items == null || registryName == null) {
            return 0L;
        }
        long total = 0L;
        for (ItemEntry item : storage.items) {
            if (item == null) {
                continue;
            }
            if (registryName.equals(item.registryName) || registryName.equals(item.itemId)) {
                if (meta <= 0 || item.meta == meta) {
                    total += item.amount;
                }
            }
        }
        return total;
    }

    /**
     * Breakdown for a true {@code bq_standard:fluid} requirement.
     */
    public static StockBreakdown analyzeTrueFluid(StorageDto storage, String fluidName, boolean includeAll) {
        StockBreakdown out = new StockBreakdown();
        if (fluidName == null || fluidName.isEmpty()) {
            return out;
        }
        out.freeMb = freeFluidMb(storage, fluidName);
        out.fromCellsMb = containerFluidMb(storage, fluidName, includeAll);
        out.totalFluidMb = out.freeMb + out.fromCellsMb;
        out.capacityMb = 1000;
        return out;
    }

    /**
     * Breakdown for an item retrieval that is a filled fluid cell.
     */
    public static StockBreakdown analyzeCellItem(StorageDto storage, String registryName, int meta,
        boolean includeAll) {
        StockBreakdown out = new StockBreakdown();
        ItemStack filledProto = stackFromKey(registryName, meta);
        if (filledProto == null) {
            return out;
        }
        String fluidName = QuestFluidIconResolver.resolveFluidName(filledProto);
        if (fluidName == null) {
            return out;
        }
        out.capacityMb = capacityMb(filledProto);
        if (out.capacityMb <= 0) {
            out.capacityMb = 1000;
        }
        out.filledCellCount = itemCount(storage, registryName, meta);
        out.freeMb = freeFluidMb(storage, fluidName);
        ItemStack emptyProto = emptyForFilled(filledProto);
        if (emptyProto != null) {
            Object emptyName = Item.itemRegistry.getNameForObject(emptyProto.getItem());
            if (emptyName != null) {
                out.emptyCellCount = itemCount(storage, emptyName.toString(), emptyProto.getItemDamage());
            }
        }
        long fromFreeUnits = out.freeMb / out.capacityMb;
        long fillable = Math.min(out.emptyCellCount, fromFreeUnits);
        out.submitAvailable = out.filledCellCount + fillable;
        if (includeAll) {
            out.otherContainerUnits = otherContainerUnits(storage, fluidName, registryName, meta);
        }
        out.detectAvailable = out.filledCellCount + fromFreeUnits + out.otherContainerUnits;
        out.fromCellsMb = out.filledCellCount * (long) out.capacityMb;
        if (includeAll) {
            out.fromCellsMb += containerFluidMbExcluding(storage, fluidName, registryName, meta, true);
        }
        out.totalFluidMb = out.freeMb + out.fromCellsMb;
        return out;
    }

    private static long containerFluidMb(StorageDto storage, String fluidName, boolean includeAll) {
        return containerFluidMbExcluding(storage, fluidName, null, -1, includeAll);
    }

    private static long containerFluidMbExcluding(StorageDto storage, String fluidName, String excludeRegistry,
        int excludeMeta, boolean includeAll) {
        if (storage == null || storage.items == null || fluidName == null) {
            return 0L;
        }
        long total = 0L;
        for (ItemEntry item : storage.items) {
            if (item == null || item.registryName == null) {
                continue;
            }
            if (excludeRegistry != null && excludeRegistry.equals(item.registryName) && item.meta == excludeMeta) {
                continue;
            }
            ItemStack stack = stackFromKey(item.registryName, item.meta);
            if (stack == null || !isInScope(stack, includeAll)) {
                continue;
            }
            String name = QuestFluidIconResolver.resolveFluidName(stack);
            if (name == null || !fluidName.equalsIgnoreCase(name)) {
                continue;
            }
            int cap = capacityMb(stack);
            if (cap <= 0) {
                continue;
            }
            total += cap * item.amount;
        }
        return total;
    }

    private static long otherContainerUnits(StorageDto storage, String fluidName, String excludeRegistry,
        int excludeMeta) {
        if (storage == null || storage.items == null || fluidName == null) {
            return 0L;
        }
        long units = 0L;
        for (ItemEntry item : storage.items) {
            if (item == null || item.registryName == null) {
                continue;
            }
            if (excludeRegistry.equals(item.registryName) && item.meta == excludeMeta) {
                continue;
            }
            ItemStack stack = stackFromKey(item.registryName, item.meta);
            if (stack == null || QuestFluidIconResolver.resolveFluidName(stack) == null) {
                continue;
            }
            if (!isInScope(stack, true)) {
                continue;
            }
            String name = QuestFluidIconResolver.resolveFluidName(stack);
            if (name == null || !fluidName.equalsIgnoreCase(name)) {
                continue;
            }
            int cap = capacityMb(stack);
            if (cap <= 0) {
                continue;
            }
            // Count each container as one unit toward DETECT equivalence (same as one cell).
            units += item.amount;
        }
        return units;
    }

    private static boolean isEmptyCellCandidate(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        Object nameObj = Item.itemRegistry.getNameForObject(stack.getItem());
        String registry = nameObj != null ? nameObj.toString() : "";
        if ("IC2:itemCellEmpty".equals(registry) && stack.getItemDamage() == 0) {
            return true;
        }
        // GT Empty Cell meta (gt.metaitem.01:32100).
        return registry.indexOf("gregtech") >= 0 && stack.getItemDamage() == 32100;
    }

    private static Method gtFillMethod() {
        if (gtFillResolved) {
            return gtFillFluidContainer;
        }
        synchronized (QuestFluidEquivalence.class) {
            if (gtFillResolved) {
                return gtFillFluidContainer;
            }
            try {
                Class<?> util = Class.forName("gregtech.api.util.GTUtility");
                gtFillFluidContainer = util.getMethod("fillFluidContainer", FluidStack.class, ItemStack.class,
                    boolean.class);
            } catch (Throwable ignored) {
                gtFillFluidContainer = null;
            }
            gtFillResolved = true;
            return gtFillFluidContainer;
        }
    }

    private static Method gtEmptyMethod() {
        if (gtEmptyResolved) {
            return gtGetContainerForFilled;
        }
        synchronized (QuestFluidEquivalence.class) {
            if (gtEmptyResolved) {
                return gtGetContainerForFilled;
            }
            try {
                Class<?> util = Class.forName("gregtech.api.util.GTUtility");
                gtGetContainerForFilled = util.getMethod("getContainerForFilledItem", ItemStack.class, boolean.class);
            } catch (Throwable ignored) {
                gtGetContainerForFilled = null;
            }
            gtEmptyResolved = true;
            return gtGetContainerForFilled;
        }
    }

    public static FluidStack parseFluid(String fluidName, long amount) {
        if (fluidName == null || fluidName.isEmpty() || amount <= 0) {
            return null;
        }
        if (FluidRegistry.getFluid(fluidName) == null) {
            return null;
        }
        int capped = (int) Math.min(Integer.MAX_VALUE, amount);
        return new FluidStack(FluidRegistry.getFluid(fluidName), capped);
    }
}
