package com.imgood.advancedatamonitor.items.cell;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.Config;
import com.imgood.advancedatamonitor.compat.ae.AeCompat;

import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.storage.ISaveProvider;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;

public final class DataLoomCellUtil {

    public static final int MAX_WEAVE_AMPLIFIERS = 8;
    public static final String NBT_ITEM_ACCUM = "dataLoomItemAccum";
    public static final String NBT_FLUID_ACCUM = "dataLoomFluidAccum";
    public static final String NBT_LAST_SYNC_MS = "dataLoomLastSyncMs";
    /** Server world-tick schedule for independent weaving (not tied to AE polling). */
    public static final String NBT_NEXT_WEAVE_TICK = "dataLoomNextWeaveTick";
    /** Stable per-stack identity for multi-cell ME drives (AE may not keep the same ItemStack reference). */
    public static final String NBT_INSTANCE_ID = "dataLoomInstanceId";
    /** AE2 / AE2FC Cell Workbench partition list (CellConfig / FluidCellConfig). */
    public static final String NBT_CONFIG_LIST = "list";
    private static final int CONFIG_SLOT_COUNT = 63;

    public static int getSyncIntervalTicks() {
        int seconds = Config.dataLoomCellSyncIntervalSeconds;
        if (seconds < 1) {
            seconds = 1;
        }
        return seconds * 20;
    }

    /**
     * Best-effort AE network energy drain for a completed weave interval. Never blocks weaving.
     */
    public static void tryDrainNetworkEnergyForTicks(Object gridHost, long elapsedTicks) {
        if (elapsedTicks <= 0L || gridHost == null) {
            return;
        }
        double drainPerTick = getEnergyDrainPerTick();
        if (drainPerTick <= 0.0D) {
            return;
        }
        try {
            if (!(gridHost instanceof appeng.api.networking.IGridHost)) {
                return;
            }
            appeng.api.networking.IGridNode node = ((appeng.api.networking.IGridHost) gridHost)
                .getGridNode(net.minecraftforge.common.util.ForgeDirection.UNKNOWN);
            if (node == null) {
                return;
            }
            appeng.api.networking.IGrid grid = node.getGrid();
            if (grid == null) {
                return;
            }
            appeng.api.networking.energy.IEnergyGrid energyGrid = grid
                .getCache(appeng.api.networking.energy.IEnergyGrid.class);
            if (energyGrid == null) {
                return;
            }

            double drainAmount = drainPerTick * elapsedTicks;
            if (drainAmount <= 0.0D) {
                return;
            }

            energyGrid.extractAEPower(
                drainAmount,
                appeng.api.config.Actionable.MODULATE,
                appeng.api.config.PowerMultiplier.CONFIG);
        } catch (Exception e) {
            com.imgood.advancedatamonitor.AdvanceDataMonitor.LOG
                .warn("[DataLoomCell] Failed to drain energy: {}", e.getMessage());
        }
    }

    /**
     * @deprecated Weaving no longer uses wall-clock billing; kept for legacy NBT only.
     */
    @Deprecated
    public static long resolveBillableElapsedMs(long lastSyncMs, long nowMs) {
        if (nowMs <= lastSyncMs) {
            return -1L;
        }
        long syncIntervalMs = getSyncIntervalMs();
        long elapsedMs = nowMs - lastSyncMs;
        if (elapsedMs < syncIntervalMs) {
            return -1L;
        }
        return (elapsedMs / syncIntervalMs) * syncIntervalMs;
    }

    public static long getSyncIntervalMs() {
        int seconds = Config.dataLoomCellSyncIntervalSeconds;
        if (seconds < 1) {
            seconds = 1;
        }
        return seconds * 1000L;
    }

    /** GTNH stores Thaumcraft essentia as fluids named after aspect tags. */
    public static boolean isEssentiaFluid(net.minecraftforge.fluids.Fluid fluid) {
        if (fluid == null) {
            return false;
        }
        try {
            Class<?> aspectClass = Class.forName("thaumcraft.api.aspects.Aspect");
            Object aspect = aspectClass.getMethod("getAspect", String.class)
                .invoke(null, fluid.getName());
            return aspect != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private DataLoomCellUtil() {}

    public static boolean isWeaveAmplifier(Item item) {
        return item instanceof IWeaveAmplifierCard;
    }

    public static IInventory createUpgradesInventory(ItemStack cellStack) {
        return new DataLoomCellUpgrades(cellStack, MAX_WEAVE_AMPLIFIERS);
    }

    public static int countWeaveAmplifiers(ItemStack cellStack) {
        int[] byType = countAmplifiersByType(cellStack);
        return byType[0] + byType[1];
    }

    /** @return int[2] = { normal Weave Amplifier count, Super Weave Amplifier count } */
    public static int[] countAmplifiersByType(ItemStack cellStack) {
        int normal = 0;
        int superCount = 0;
        if (cellStack != null) {
            IInventory upgrades = createUpgradesInventory(cellStack);
            for (int slot = 0; slot < upgrades.getSizeInventory(); slot++) {
                ItemStack upgradeStack = upgrades.getStackInSlot(slot);
                if (upgradeStack == null || upgradeStack.getItem() == null) {
                    continue;
                }
                if (upgradeStack.getItem() instanceof IUpgradeModule) {
                    Upgrades upgradeType = ((IUpgradeModule) upgradeStack.getItem()).getType(upgradeStack);
                    if (upgradeType == Upgrades.SUPERSPEED) {
                        superCount++;
                    } else if (upgradeType == Upgrades.SPEED) {
                        normal++;
                    }
                    continue;
                }
                if (upgradeStack.getItem() instanceof ItemSuperWeaveAmplifier) {
                    superCount++;
                } else if (upgradeStack.getItem() instanceof ItemWeaveAmplifier) {
                    normal++;
                }
            }
        }
        int total = normal + superCount;
        if (total > MAX_WEAVE_AMPLIFIERS) {
            int overflow = total - MAX_WEAVE_AMPLIFIERS;
            while (overflow > 0 && superCount > 0) {
                superCount--;
                overflow--;
            }
            while (overflow > 0 && normal > 0) {
                normal--;
                overflow--;
            }
        }
        return new int[] { normal, superCount };
    }

    public static double getSpeedMultiplier(ItemStack cellStack) {
        int[] counts = countAmplifiersByType(cellStack);
        double multiplier = 1.0D;
        for (int i = 0; i < counts[0]; i++) {
            multiplier *= DataLoomAmplifierRates.NORMAL_MULTIPLIER;
        }
        for (int i = 0; i < counts[1]; i++) {
            multiplier *= DataLoomAmplifierRates.SUPER_MULTIPLIER;
        }
        return multiplier;
    }

    /** True when the item belongs to this mod (used to block loom cell partition markers). */
    public static boolean isModOwnItem(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        String registryName = Item.itemRegistry.getNameForObject(stack.getItem());
        return registryName != null && registryName.startsWith(AdvanceDataMonitor.MODID + ":");
    }

    public static double getEnergyDrainPerTick() {
        return Config.dataLoomCellEnergyDrainPerTick;
    }

    /**
     * Best-effort AE network energy drain. Never blocks weaving when energy is insufficient.
     */
    public static void tryDrainNetworkEnergy(ISaveProvider saveProvider, long elapsedMs) {
        if (elapsedMs <= 0L || saveProvider == null) {
            return;
        }
        double drainPerTick = getEnergyDrainPerTick();
        if (drainPerTick <= 0.0D) {
            return;
        }
        try {
            if (!(saveProvider instanceof appeng.api.networking.IGridHost)) {
                return;
            }
            appeng.api.networking.IGridNode node = ((appeng.api.networking.IGridHost) saveProvider)
                .getGridNode(net.minecraftforge.common.util.ForgeDirection.UNKNOWN);
            if (node == null) {
                return;
            }
            appeng.api.networking.IGrid grid = node.getGrid();
            if (grid == null) {
                return;
            }
            appeng.api.networking.energy.IEnergyGrid energyGrid = grid
                .getCache(appeng.api.networking.energy.IEnergyGrid.class);
            if (energyGrid == null) {
                return;
            }

            double ticks = elapsedMs / 50.0D;
            double drainAmount = drainPerTick * ticks;
            if (drainAmount <= 0.0D) {
                return;
            }

            energyGrid.extractAEPower(
                drainAmount,
                appeng.api.config.Actionable.MODULATE,
                appeng.api.config.PowerMultiplier.CONFIG);
        } catch (Exception e) {
            com.imgood.advancedatamonitor.AdvanceDataMonitor.LOG
                .warn("[DataLoomCell] Failed to drain energy: {}", e.getMessage());
        }
    }

    /**
     * GT dust ore-dictionary prefixes (longest first). Same family as TakoTech {@code OreStorageType} dust cells:
     * item must register at least one equivalent starting with one of these prefixes.
     */
    private static final String[] DUST_ORE_DICT_PREFIXES = { "dusttiny", "dustsmall", "dustimpure", "dustpure",
        "dustrefined", "dust", };

    /** True when the stack has an ore-dictionary name with a GT dust prefix (dust / dustTiny / …). */
    public static boolean isDustItem(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }

        int[] oreIds = OreDictionary.getOreIDs(stack);
        if (oreIds == null || oreIds.length == 0) {
            return false;
        }

        for (int oreId : oreIds) {
            String oreName = OreDictionary.getOreName(oreId);
            if (oreName == null || oreName.isEmpty()) {
                continue;
            }
            if (matchesDustOreDictName(oreName.toLowerCase(Locale.ENGLISH))) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesDustOreDictName(String oreNameLower) {
        for (String prefix : DUST_ORE_DICT_PREFIXES) {
            if (oreNameLower.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** Read Cell Workbench item markers without relying on Item singleton identity. */
    public static List<ItemStack> resolveMarkedItems(ItemStack cellStack) {
        List<ItemStack> items = new ArrayList<>();
        if (cellStack == null || !(cellStack.getItem() instanceof AbstractDataLoomItemCell)) {
            return items;
        }

        Set<String> seen = new HashSet<>();
        AbstractDataLoomItemCell cellItem = (AbstractDataLoomItemCell) cellStack.getItem();
        appendPartitionItemMarkers(items, seen, cellItem.getConfigInventory(cellStack));
        appendPartitionItemMarkersFromNbt(items, seen, cellStack);
        if (cellItem instanceof ItemDataDustLoomCell) {
            return filterDustLoomMarkers(items);
        }
        return items;
    }

    private static List<ItemStack> filterDustLoomMarkers(List<ItemStack> markers) {
        List<ItemStack> filtered = new ArrayList<>();
        for (ItemStack marker : markers) {
            if (isDustItem(marker)) {
                filtered.add(marker);
            } else if (marker != null && marker.getItem() != null) {
                DataLoomDebugLog.warn(
                    "Dust loom ignored invalid marker {} — no dust ore-dictionary prefix",
                    DataLoomDebugLog.describeCell(marker));
            }
        }
        return filtered;
    }

    /** Read Cell Workbench fluid markers — same two-source merge as {@link #resolveMarkedItems}. */
    public static List<FluidStack> resolveMarkedFluids(ItemStack cellStack) {
        List<FluidStack> fluids = newFluidList();
        if (cellStack == null || cellStack.getItem() == null) {
            return fluids;
        }

        Item item = cellStack.getItem();
        if (item instanceof ItemDataSourceLoomCell) {
            return ((ItemDataSourceLoomCell) item).getMarkedFluids(cellStack);
        }
        if (!(item instanceof AbstractDataLoomFluidCell)) {
            return fluids;
        }

        Set<String> seen = new HashSet<>();
        AbstractDataLoomFluidCell fluidCell = (AbstractDataLoomFluidCell) item;
        IInventory config = fluidCell.getConfigInventory(cellStack);
        appendPartitionFluidMarkers(fluids, seen, config);
        appendPartitionFluidMarkersFromNbt(fluids, seen, cellStack);

        if (DataLoomDebugLog.isEnabled() && fluids.isEmpty()) {
            DataLoomDebugLog.warn(
                "resolveMarkedFluids empty for {} — configSlots={} nbtListSlots={} hasTag={}",
                DataLoomDebugLog.describeCell(cellStack),
                countConfigInventorySlots(config),
                countConfigListSlots(cellStack),
                cellStack.hasTagCompound());
            logUnresolvedPartitionSlots(config, cellStack);
        }
        return fluids;
    }

    private static int countConfigInventorySlots(IInventory config) {
        return DataLoomDebugLog.countConfigInventorySlots(config);
    }

    private static int countConfigListSlots(ItemStack cellStack) {
        return DataLoomDebugLog.countConfigListSlots(cellStack);
    }

    private static void logUnresolvedPartitionSlots(IInventory config, ItemStack cellStack) {
        if (config != null) {
            for (int slot = 0; slot < Math.min(config.getSizeInventory(), 8); slot++) {
                ItemStack marker = config.getStackInSlot(slot);
                if (marker == null) {
                    continue;
                }
                DataLoomDebugLog
                    .warn("  config#{} marker {} -> fluid unresolved", slot, DataLoomDebugLog.describeCell(marker));
            }
        }
        if (cellStack != null && cellStack.hasTagCompound()
            && cellStack.getTagCompound()
                .hasKey(NBT_CONFIG_LIST, 10)) {
            NBTTagCompound listTag = cellStack.getTagCompound()
                .getCompoundTag(NBT_CONFIG_LIST);
            for (int slot = 0; slot < 8; slot++) {
                if (!listTag.hasKey("#" + slot, 10)) {
                    continue;
                }
                ItemStack marker = ItemStack.loadItemStackFromNBT(listTag.getCompoundTag("#" + slot));
                if (marker == null) {
                    continue;
                }
                FluidStack resolved = resolveMarkerFluid(marker);
                DataLoomDebugLog.warn(
                    "  nbt#{} marker {} -> {}",
                    slot,
                    DataLoomDebugLog.describeCell(marker),
                    resolved == null ? "unresolved" : DataLoomDebugLog.describeFluid(resolved));
            }
        }
    }

    private static void appendPartitionFluidMarkers(List<FluidStack> out, Set<String> seen, IInventory config) {
        if (config == null) {
            return;
        }
        for (int slot = 0; slot < config.getSizeInventory(); slot++) {
            ItemStack markerItem = config.getStackInSlot(slot);
            if (markerItem == null) {
                continue;
            }
            FluidStack fluid = resolveMarkerFluid(markerItem);
            if (fluid == null || fluid.getFluid() == null) {
                continue;
            }
            String key = fluid.getFluid()
                .getName();
            if (seen.add(key)) {
                out.add(new FluidStack(fluid.getFluid(), 1000));
            }
        }
    }

    /** Fallback: read fluid partition markers directly from cell NBT {@code list} slots. */
    private static void appendPartitionFluidMarkersFromNbt(List<FluidStack> out, Set<String> seen,
        ItemStack cellStack) {
        if (cellStack == null || !cellStack.hasTagCompound()) {
            return;
        }
        NBTTagCompound listTag = cellStack.getTagCompound()
            .getCompoundTag(NBT_CONFIG_LIST);
        if (listTag == null || listTag.hasNoTags()) {
            return;
        }
        for (int slot = 0; slot < CONFIG_SLOT_COUNT; slot++) {
            if (!listTag.hasKey("#" + slot, 10)) {
                continue;
            }
            ItemStack markerItem = ItemStack.loadItemStackFromNBT(listTag.getCompoundTag("#" + slot));
            if (markerItem == null || markerItem.getItem() == null) {
                continue;
            }
            FluidStack fluid = resolveMarkerFluid(markerItem);
            if (fluid == null || fluid.getFluid() == null) {
                continue;
            }
            String key = fluid.getFluid()
                .getName();
            if (seen.add(key)) {
                out.add(new FluidStack(fluid.getFluid(), 1000));
            }
        }
    }

    private static void appendPartitionItemMarkers(List<ItemStack> out, Set<String> seen, IInventory config) {
        if (config == null) {
            return;
        }
        for (int slot = 0; slot < config.getSizeInventory(); slot++) {
            ItemStack marked = config.getStackInSlot(slot);
            if (marked == null || marked.getItem() == null) {
                continue;
            }
            ItemStack copy = marked.copy();
            copy.stackSize = 1;
            String key = DataLoomCellStorage.itemKey(copy);
            if (seen.add(key)) {
                out.add(copy);
            }
        }
    }

    /** Fallback: read AE2 {@code list} partition slots directly from cell NBT. */
    private static void appendPartitionItemMarkersFromNbt(List<ItemStack> out, Set<String> seen, ItemStack cellStack) {
        if (cellStack == null || !cellStack.hasTagCompound()) {
            return;
        }
        NBTTagCompound listTag = cellStack.getTagCompound()
            .getCompoundTag(NBT_CONFIG_LIST);
        if (listTag == null || listTag.hasNoTags()) {
            return;
        }
        for (int slot = 0; slot < CONFIG_SLOT_COUNT; slot++) {
            if (!listTag.hasKey("#" + slot, 10)) {
                continue;
            }
            ItemStack marked = ItemStack.loadItemStackFromNBT(listTag.getCompoundTag("#" + slot));
            if (marked == null || marked.getItem() == null) {
                continue;
            }
            marked.stackSize = 1;
            String key = DataLoomCellStorage.itemKey(marked);
            if (seen.add(key)) {
                out.add(marked);
            }
        }
    }

    /** Fallback: read fluid partition markers directly from cell NBT {@code list} slots. */
    public static List<FluidStack> readPartitionFluidsFromNbt(ItemStack cellStack) {
        List<FluidStack> fluids = newFluidList();
        Set<String> seen = new HashSet<>();
        appendPartitionFluidMarkersFromNbt(fluids, seen, cellStack);
        return fluids;
    }

    public static boolean isDataLoomCell(Item item) {
        return item instanceof ItemDataDustLoomCell || item instanceof ItemDataFormLoomCell
            || item instanceof ItemDataFlowCell
            || item instanceof ItemDataTideLoomCell
            || item instanceof ItemDataSourceLoomCell;
    }

    public static List<FluidStack> newFluidList() {
        return new ArrayList<>();
    }

    /** Assign a persistent random id once so multiple cells in one ME Drive stay distinguishable. */
    public static void ensureInstanceId(ItemStack stack) {
        if (stack == null) {
            return;
        }
        NBTTagCompound tag = DataLoomCellStorage.getOrCreateTag(stack);
        if (!tag.hasKey(NBT_INSTANCE_ID)) {
            tag.setLong(NBT_INSTANCE_ID, stack.hashCode() ^ System.nanoTime());
        }
    }

    public static long readInstanceId(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()
            || !stack.getTagCompound()
                .hasKey(NBT_INSTANCE_ID)) {
            return 0L;
        }
        return stack.getTagCompound()
            .getLong(NBT_INSTANCE_ID);
    }

    /**
     * ME drive slot holding this handler's cell, or chest slot {@code 0}. Returns {@code -1} when unknown.
     */
    public static int resolveHostSlot(ItemStack cellStack, ISaveProvider saveProvider) {
        if (cellStack == null || saveProvider == null) {
            return -1;
        }
        if (saveProvider instanceof TileChest) {
            return 0;
        }
        if (!(saveProvider instanceof TileDrive)) {
            return -1;
        }
        IInventory inv = ((TileDrive) saveProvider).getInternalInventory();
        for (int slot = 0; slot < inv.getSizeInventory(); slot++) {
            if (inv.getStackInSlot(slot) == cellStack) {
                return slot;
            }
        }
        long instanceId = readInstanceId(cellStack);
        if (instanceId != 0L) {
            for (int slot = 0; slot < inv.getSizeInventory(); slot++) {
                ItemStack candidate = inv.getStackInSlot(slot);
                if (candidate != null && readInstanceId(candidate) == instanceId) {
                    return slot;
                }
            }
        }
        return -1;
    }

    /** Read the authoritative cell stack from its ME host inventory slot. */
    public static ItemStack resolveLiveCellStack(ItemStack handlerStack, ISaveProvider saveProvider, int hostSlot) {
        if (saveProvider instanceof TileDrive && hostSlot >= 0) {
            IInventory inv = ((TileDrive) saveProvider).getInternalInventory();
            if (hostSlot < inv.getSizeInventory()) {
                ItemStack live = inv.getStackInSlot(hostSlot);
                if (live != null && isDataLoomCell(live.getItem())) {
                    return live;
                }
            }
        } else if (saveProvider instanceof TileChest) {
            ItemStack live = ((TileChest) saveProvider).getInternalInventory()
                .getStackInSlot(0);
            if (live != null && isDataLoomCell(live.getItem())) {
                return live;
            }
        }
        return handlerStack;
    }

    /** Resolve a Cell Workbench partition marker to a fluid type (profile-aware). */
    public static FluidStack resolveMarkerFluid(ItemStack markerItem) {
        return AeCompat.fluidMarkers()
            .resolveMarkerFluid(markerItem);
    }

    public static List<FluidStack> readPartitionFluids(IInventory config) {
        List<FluidStack> fluids = newFluidList();
        Set<String> seen = new HashSet<>();
        appendPartitionFluidMarkers(fluids, seen, config);
        return fluids;
    }

    /** @return long[3] = { storedTypes, storedUnits (items or mB), usedBytes } */
    public static long[] readItemStorageStats(ItemStack stack) {
        long storedTypes = 0L;
        long storedCount = 0L;
        if (stack == null || stack.getTagCompound() == null
            || !stack.getTagCompound()
                .hasKey(NBT_ITEM_ACCUM)) {
            return new long[] { storedTypes, storedCount, 0L };
        }

        NBTTagList accumList = stack.getTagCompound()
            .getTagList(NBT_ITEM_ACCUM, 10);
        for (int i = 0; i < accumList.tagCount(); i++) {
            NBTTagCompound entry = accumList.getCompoundTagAt(i);
            long amount = entry.getLong("amount");
            if (amount <= 0L) {
                continue;
            }
            storedTypes++;
            storedCount += amount;
        }

        long usedBytes = DataLoomCellCapacity.getUsedItemBytes(storedTypes, storedCount);
        return new long[] { storedTypes, storedCount, usedBytes };
    }

    /** @return long[3] = { storedTypes, storedMb, usedBytes } */
    public static long[] readFluidStorageStats(ItemStack stack) {
        long storedTypes = 0L;
        long storedMb = 0L;
        if (stack == null || stack.getTagCompound() == null
            || !stack.getTagCompound()
                .hasKey(NBT_FLUID_ACCUM)) {
            return new long[] { storedTypes, storedMb, 0L };
        }

        NBTTagList accumList = stack.getTagCompound()
            .getTagList(NBT_FLUID_ACCUM, 10);
        for (int i = 0; i < accumList.tagCount(); i++) {
            NBTTagCompound entry = accumList.getCompoundTagAt(i);
            long amount = entry.getLong("amount");
            if (amount <= 0L) {
                continue;
            }
            storedTypes++;
            storedMb += amount;
        }

        long usedBytes = DataLoomCellCapacity.getUsedFluidBytes(storedTypes, storedMb);
        return new long[] { storedTypes, storedMb, usedBytes };
    }
}
