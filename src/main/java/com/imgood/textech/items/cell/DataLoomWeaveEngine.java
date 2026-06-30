package com.imgood.textech.items.cell;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import com.imgood.textech.AdvanceDataMonitor;

import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;

/**
 * 
 * Stand-alone weaving engine. Mutates drive/chest cell {@link ItemStack} NBT on a fixed server-tick schedule.
 * 
 * AE cell handlers only mirror this NBT â€?they never trigger weaving.
 * 
 */

public final class DataLoomWeaveEngine {

    private DataLoomWeaveEngine() {}

    public static void runScheduledPass() {

        List<DataLoomCellSlot> targets = DataLoomCellIndex.INSTANCE.snapshot();

        DataLoomDebugLog.info("Weave pass started â€?indexed slots={}", targets.size());

        for (DataLoomCellSlot slotRef : targets) {

            try {

                processSlot(slotRef);

            } catch (Throwable t) {

                AdvanceDataMonitor.LOG.warn(

                    "[DataLoomWeave] Failed at {} {} {} slot {}: {}",

                    slotRef.x,

                    slotRef.y,

                    slotRef.z,

                    slotRef.slot,

                    t.getMessage());

                if (DataLoomDebugLog.isEnabled()) {

                    t.printStackTrace();

                }

            }

        }

    }

    private static void processSlot(DataLoomCellSlot slotRef) {

        World world = resolveWorld(slotRef.dimensionId);

        if (world == null) {

            DataLoomDebugLog.warn("Skip {} â€?world dim {} not loaded", describeSlot(slotRef), slotRef.dimensionId);

            return;

        }

        TileEntity host = DataLoomCellIndex.INSTANCE.resolveHost(world, slotRef);

        ItemStack stack = DataLoomCellIndex.INSTANCE.resolveStack(world, slotRef);

        if (host == null || stack == null
            || stack.getItem() == null
            || !DataLoomCellUtil.isDataLoomCell(stack.getItem())) {

            DataLoomDebugLog.info(

                "Skip {} â€?host={} stack={}",

                describeSlot(slotRef),

                host == null ? "null"
                    : host.getClass()

                        .getSimpleName(),

                DataLoomDebugLog.describeCell(stack));

            return;

        }

        if (host instanceof TileDrive) {

            DataLoomCellIndex.INSTANCE.registerDrive((TileDrive) host);

        } else if (host instanceof TileChest) {

            DataLoomCellIndex.INSTANCE.registerChest((TileChest) host);

        }

        int intervalTicks = DataLoomCellUtil.getSyncIntervalTicks();

        NBTTagCompound tag = DataLoomCellStorage.getOrCreateTag(stack);

        long nowTick = world.getTotalWorldTime();

        if (!tag.hasKey(DataLoomCellUtil.NBT_NEXT_WEAVE_TICK)) {

            DataLoomCellUtil.ensureInstanceId(stack);
            tag.setLong(DataLoomCellUtil.NBT_NEXT_WEAVE_TICK, nowTick + intervalTicks);

            DataLoomDebugLog.info(

                "Init weave timer {} cell={} nextTick={} (wait {} ticks)",

                describeSlot(slotRef),

                DataLoomDebugLog.describeCell(stack),

                nowTick + intervalTicks,

                intervalTicks);

            DataLoomCellIndex.INSTANCE.commitStack(world, slotRef, stack);

            host.markDirty();

            return;

        }

        long nextTick = tag.getLong(DataLoomCellUtil.NBT_NEXT_WEAVE_TICK);
        if (nowTick < nextTick) {
            return;
        }

        tag.setLong(DataLoomCellUtil.NBT_NEXT_WEAVE_TICK, nowTick + intervalTicks);

        double intervalSeconds = intervalTicks / 20.0D;

        DataLoomCellUtil.tryDrainNetworkEnergyForTicks(host, intervalTicks);

        DataLoomDebugLog.info(

            "Weave tick {} cell={} channel={}",

            describeSlot(slotRef),

            DataLoomDebugLog.describeCell(stack),

            stack.getItem() instanceof AbstractDataLoomFluidCell ? "FLUIDS" : "ITEMS");

        if (stack.getItem() instanceof AbstractDataLoomFluidCell) {

            weaveFluidCell(stack, (AbstractDataLoomFluidCell) stack.getItem(), intervalSeconds, host, slotRef);

        } else if (stack.getItem() instanceof AbstractDataLoomItemCell) {

            weaveItemCell(stack, (AbstractDataLoomItemCell) stack.getItem(), intervalSeconds, host, slotRef);

        } else {

            DataLoomDebugLog.warn(
                "Unknown loom cell type at {}: {}",
                describeSlot(slotRef),
                stack.getItem()

                    .getClass()

                    .getName());

        }

        DataLoomCellIndex.INSTANCE.commitStack(world, slotRef, stack);

        host.markDirty();

    }

    private static void weaveItemCell(ItemStack stack, AbstractDataLoomItemCell cellItem, double intervalSeconds,

        TileEntity host, DataLoomCellSlot slotRef) {

        List<ItemStack> marked = cellItem.getMarkedItems(stack);

        if (marked.isEmpty()) {

            DataLoomDebugLog.warn(

                "Item weave skipped {} â€?no markers (nbtListSlots={})",

                describeSlot(slotRef),

                DataLoomDebugLog.countConfigListSlots(stack));

            return;

        }

        DataLoomCellStorage.ItemAccumState before = DataLoomCellStorage.readItemState(stack);

        DataLoomCellStorage.ItemAccumState state = DataLoomCellStorage.readItemState(stack);

        int markCount = marked.size();

        if (!DataLoomCellCapacity.hasItemCapacity(state.storedTypes, state.storedCount)) {

            DataLoomDebugLog.warn(

                "Item weave skipped {} â€?full (types={} count={})",

                describeSlot(slotRef),

                state.storedTypes,

                state.storedCount);

            return;

        }

        double multiplier = DataLoomCellUtil.getSpeedMultiplier(stack);

        long totalToAdd = (long) Math.floor(cellItem.getItemRatePerSecond() * multiplier * intervalSeconds);

        if (totalToAdd <= 0L) {

            DataLoomDebugLog.warn(

                "Item weave skipped {} â€?totalToAdd=0 rate={} mult={} interval={}s",

                describeSlot(slotRef),

                cellItem.getItemRatePerSecond(),

                multiplier,

                intervalSeconds);

            return;

        }

        totalToAdd = Math
            .min(totalToAdd, DataLoomCellCapacity.getRemainingItemUnits(state.storedTypes, state.storedCount));

        long perItem = totalToAdd / markCount;

        long remainder = totalToAdd % markCount;

        long woven = 0L;

        for (int i = 0; i < markCount; i++) {

            ItemStack mark = marked.get(i);

            if (mark == null) {

                continue;

            }

            if (cellItem instanceof ItemDataDustLoomCell && !DataLoomCellUtil.isDustItem(mark)) {

                DataLoomDebugLog.warn(

                    "Item weave skipped marker {} â€?not a dust ore-dictionary item",

                    DataLoomDebugLog.describeCell(mark));

                continue;

            }

            long add = perItem + (i < remainder ? 1L : 0L);

            if (add <= 0L) {

                continue;

            }

            String key = DataLoomCellStorage.itemKey(mark);

            DataLoomCellStorage.StoredItem stored = state.items.get(key);

            if (stored == null) {

                if (!DataLoomCellCapacity.canHoldNewItemType(state.storedTypes, state.storedCount)) {

                    continue;

                }

                stored = new DataLoomCellStorage.StoredItem(mark.copy());

                state.items.put(key, stored);

                state.storedTypes++;

            }

            long markerStored = stored.amount;

            long typeRemaining = DataLoomCellCapacity.getRemainingItemUnitsForMarker(

                markCount,

                state.storedTypes,

                state.storedCount,

                markerStored);

            if (typeRemaining <= 0L) {

                continue;

            }

            long actualAdd = Math.min(add, typeRemaining);

            stored.amount += actualAdd;

            state.storedCount += actualAdd;

            woven += actualAdd;

        }

        DataLoomCellStorage.writeItemState(stack, state);

        DataLoomGridNotify.publishItemDelta(host, before, state);

        DataLoomDebugLog.info(

            "Item woven {} +{} units (markers={} total={} types={})",

            describeSlot(slotRef),

            woven,

            markCount,

            state.storedCount,

            state.storedTypes);

    }

    private static void weaveFluidCell(ItemStack stack, AbstractDataLoomFluidCell cellItem, double intervalSeconds,

        TileEntity host, DataLoomCellSlot slotRef) {

        List<FluidStack> markers = cellItem.getMarkedFluids(stack);

        if (markers.isEmpty()) {

            DataLoomDebugLog.warn(

                "Fluid weave skipped {} â€?no markers (nbtListSlots={})",

                describeSlot(slotRef),

                DataLoomDebugLog.countConfigListSlots(stack));

            return;

        }

        DataLoomCellStorage.FluidAccumState before = DataLoomCellStorage.readFluidState(stack);

        DataLoomCellStorage.FluidAccumState state = DataLoomCellStorage.readFluidState(stack);

        int markCount = markers.size();

        int maxTypes = cellItem.getMaxFluidTypes();

        if (!DataLoomCellCapacity.hasFluidCapacity(state.storedTypes, state.storedMb, maxTypes)) {

            DataLoomDebugLog.warn(

                "Fluid weave skipped {} â€?full (types={} mb={})",

                describeSlot(slotRef),

                state.storedTypes,

                state.storedMb);

            return;

        }

        double multiplier = DataLoomCellUtil.getSpeedMultiplier(stack);

        long totalToAdd = (long) Math.floor(cellItem.getFluidRatePerSecond() * multiplier * intervalSeconds);

        if (totalToAdd <= 0L) {

            DataLoomDebugLog.warn(

                "Fluid weave skipped {} â€?totalToAdd=0 rate={} mult={} interval={}s",

                describeSlot(slotRef),

                cellItem.getFluidRatePerSecond(),

                multiplier,

                intervalSeconds);

            return;

        }

        totalToAdd = Math.min(totalToAdd, DataLoomCellCapacity.getRemainingFluidMb(state.storedTypes, state.storedMb));

        long perFluid = totalToAdd / markCount;

        long remainder = totalToAdd % markCount;

        long wovenMb = 0L;

        for (int i = 0; i < markCount; i++) {

            FluidStack mark = markers.get(i);

            if (mark == null || mark.getFluid() == null) {

                continue;

            }

            long add = perFluid + (i < remainder ? 1L : 0L);

            if (add <= 0L) {

                continue;

            }

            String key = DataLoomCellStorage.fluidKey(mark);

            DataLoomCellStorage.StoredFluid stored = state.fluids.get(key);

            if (stored == null) {

                if (!DataLoomCellCapacity.canHoldNewFluidType(state.storedTypes, state.storedMb, maxTypes)) {

                    DataLoomDebugLog.warn(

                        "Fluid weave {} â€?cannot add new type {} (types={} mb={})",

                        describeSlot(slotRef),

                        mark.getFluid()

                            .getName(),

                        state.storedTypes,

                        state.storedMb);

                    continue;

                }

                stored = new DataLoomCellStorage.StoredFluid(new FluidStack(mark.getFluid(), 1000));

                state.fluids.put(key, stored);

                state.storedTypes++;

            }

            long markerStored = stored.amountMb;

            long typeRemaining = DataLoomCellCapacity.getRemainingFluidMbForMarker(

                markCount,

                state.storedTypes,

                state.storedMb,

                markerStored,

                maxTypes);

            if (typeRemaining <= 0L) {

                DataLoomDebugLog.warn(

                    "Fluid weave {} â€?marker budget exhausted for {} (stored={} remaining={})",

                    describeSlot(slotRef),

                    mark.getFluid()

                        .getName(),

                    markerStored,

                    typeRemaining);

                continue;

            }

            long actualAdd = Math.min(add, typeRemaining);

            stored.amountMb += actualAdd;

            state.storedMb += actualAdd;

            wovenMb += actualAdd;

        }

        DataLoomCellStorage.writeFluidState(stack, state);

        DataLoomGridNotify.publishFluidDelta(host, before, state);

        DataLoomDebugLog.info(

            "Fluid woven {} +{} mB (markers={} totalMb={} types={}) accumTag={}",

            describeSlot(slotRef),

            wovenMb,

            markCount,

            state.storedMb,

            state.storedTypes,

            stack.hasTagCompound() && stack.getTagCompound()

                .hasKey(DataLoomCellUtil.NBT_FLUID_ACCUM));

    }

    private static String describeSlot(DataLoomCellSlot slotRef) {

        return "dim" + slotRef.dimensionId
            + "@"
            + slotRef.x
            + ","
            + slotRef.y
            + ","
            + slotRef.z
            + "#"

            + slotRef.slot;

    }

    private static World resolveWorld(int dimensionId) {

        net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();

        if (server == null) {

            return null;

        }

        for (net.minecraft.world.WorldServer world : server.worldServers) {

            if (world != null && world.provider.dimensionId == dimensionId) {

                return world;

            }

        }

        return null;

    }

}
