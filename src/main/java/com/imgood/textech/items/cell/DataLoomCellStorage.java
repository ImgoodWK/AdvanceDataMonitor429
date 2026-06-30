package com.imgood.textech.items.cell;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;

/**
 * Pure NBT storage for woven cell contents. AE inventory handlers only read/write through this layer.
 */
public final class DataLoomCellStorage {

    private DataLoomCellStorage() {}

    public static NBTTagCompound getOrCreateTag(ItemStack stack) {
        if (stack.getTagCompound() == null) {
            stack.setTagCompound(new NBTTagCompound());
        }
        return stack.getTagCompound();
    }

    public static ItemAccumState readItemState(ItemStack stack) {
        ItemAccumState state = new ItemAccumState();
        if (stack == null || stack.getTagCompound() == null
            || !stack.getTagCompound()
                .hasKey(DataLoomCellUtil.NBT_ITEM_ACCUM)) {
            return state;
        }

        NBTTagList accumList = stack.getTagCompound()
            .getTagList(DataLoomCellUtil.NBT_ITEM_ACCUM, 10);
        for (int i = 0; i < accumList.tagCount(); i++) {
            NBTTagCompound entry = accumList.getCompoundTagAt(i);
            ItemStack stored = ItemStack.loadItemStackFromNBT(entry.getCompoundTag("item"));
            long amount = entry.getLong("amount");
            if (stored == null || amount <= 0L) {
                continue;
            }
            state.items.put(itemKey(stored), new StoredItem(stored, amount));
        }
        state.storedTypes = DataLoomCellCapacity.getStoredTypeCount(state.toAmountMap());
        state.storedCount = DataLoomCellCapacity.getStoredItemCount(state.toAmountMap());
        return state;
    }

    public static void writeItemState(ItemStack stack, ItemAccumState state) {
        NBTTagCompound tag = getOrCreateTag(stack);
        NBTTagList accumList = new NBTTagList();
        for (StoredItem stored : state.items.values()) {
            if (stored.stack == null || stored.amount <= 0L) {
                continue;
            }
            NBTTagCompound entry = new NBTTagCompound();
            NBTTagCompound itemNbt = new NBTTagCompound();
            stored.stack.writeToNBT(itemNbt);
            entry.setTag("item", itemNbt);
            entry.setLong("amount", stored.amount);
            accumList.appendTag(entry);
        }
        tag.setTag(DataLoomCellUtil.NBT_ITEM_ACCUM, accumList);
        state.storedTypes = DataLoomCellCapacity.getStoredTypeCount(state.toAmountMap());
        state.storedCount = DataLoomCellCapacity.getStoredItemCount(state.toAmountMap());
    }

    public static FluidAccumState readFluidState(ItemStack stack) {
        FluidAccumState state = new FluidAccumState();
        if (stack == null || stack.getTagCompound() == null
            || !stack.getTagCompound()
                .hasKey(DataLoomCellUtil.NBT_FLUID_ACCUM)) {
            return state;
        }

        NBTTagList accumList = stack.getTagCompound()
            .getTagList(DataLoomCellUtil.NBT_FLUID_ACCUM, 10);
        for (int i = 0; i < accumList.tagCount(); i++) {
            NBTTagCompound entry = accumList.getCompoundTagAt(i);
            FluidStack stored = FluidStack.loadFluidStackFromNBT(entry.getCompoundTag("fluid"));
            long amount = entry.getLong("amount");
            if (stored == null || amount <= 0L) {
                continue;
            }
            state.fluids.put(fluidKey(stored), new StoredFluid(stored, amount));
        }
        state.storedTypes = DataLoomCellCapacity.getStoredTypeCount(state.toAmountMap());
        state.storedMb = DataLoomCellCapacity.getStoredItemCount(state.toAmountMap());
        return state;
    }

    public static void writeFluidState(ItemStack stack, FluidAccumState state) {
        NBTTagCompound tag = getOrCreateTag(stack);
        NBTTagList accumList = new NBTTagList();
        for (StoredFluid stored : state.fluids.values()) {
            if (stored.fluid == null || stored.amountMb <= 0L) {
                continue;
            }
            NBTTagCompound entry = new NBTTagCompound();
            NBTTagCompound fluidNbt = new NBTTagCompound();
            stored.fluid.writeToNBT(fluidNbt);
            entry.setTag("fluid", fluidNbt);
            entry.setLong("amount", stored.amountMb);
            accumList.appendTag(entry);
        }
        tag.setTag(DataLoomCellUtil.NBT_FLUID_ACCUM, accumList);
        state.storedTypes = DataLoomCellCapacity.getStoredTypeCount(state.toAmountMap());
        state.storedMb = DataLoomCellCapacity.getStoredItemCount(state.toAmountMap());
    }

    public static void addAvailableItems(ItemStack stack, IItemList out) {
        ItemAccumState state = readItemState(stack);
        for (StoredItem stored : state.items.values()) {
            if (stored.amount <= 0L || stored.stack == null) {
                continue;
            }
            IAEItemStack aeStack = AEItemStack.create(stored.stack);
            aeStack.setStackSize(stored.amount);
            out.add(aeStack);
        }
    }

    public static IAEItemStack findAvailableItem(ItemStack stack, IAEItemStack request) {
        if (request == null) {
            return null;
        }
        ItemAccumState state = readItemState(stack);
        for (StoredItem stored : state.items.values()) {
            if (stored.stack == null || stored.amount <= 0L) {
                continue;
            }
            IAEItemStack probe = AEItemStack.create(stored.stack);
            if (!probe.isSameType(request)) {
                continue;
            }
            IAEItemStack result = probe.copy();
            result.setStackSize(stored.amount);
            return result;
        }
        return null;
    }

    public static IAEItemStack extractItem(ItemStack stack, IAEItemStack request) {
        if (request == null) {
            return null;
        }
        ItemAccumState state = readItemState(stack);
        for (Map.Entry<String, StoredItem> entry : state.items.entrySet()) {
            StoredItem stored = entry.getValue();
            if (stored.stack == null) {
                continue;
            }
            IAEItemStack probe = AEItemStack.create(stored.stack);
            if (!probe.isSameType(request)) {
                continue;
            }
            long available = stored.amount;
            long toExtract = Math.min(available, request.getStackSize());
            if (toExtract <= 0L) {
                return null;
            }
            stored.amount = available - toExtract;
            writeItemState(stack, state);
            IAEItemStack result = probe.copy();
            result.setStackSize(toExtract);
            return result;
        }
        return null;
    }

    public static void addAvailableFluids(ItemStack stack, IItemList out) {
        FluidAccumState state = readFluidState(stack);
        for (StoredFluid stored : state.fluids.values()) {
            if (stored.amountMb <= 0L || stored.fluid == null) {
                continue;
            }
            IAEFluidStack aeStack = AEFluidStack.create(stored.fluid);
            aeStack.setStackSize(stored.amountMb);
            out.add(aeStack);
        }
    }

    public static IAEFluidStack findAvailableFluid(ItemStack stack, IAEFluidStack request) {
        if (request == null) {
            return null;
        }
        FluidAccumState state = readFluidState(stack);
        for (StoredFluid stored : state.fluids.values()) {
            if (stored.fluid == null || stored.amountMb <= 0L || stored.fluid.getFluid() != request.getFluid()) {
                continue;
            }
            IAEFluidStack result = AEFluidStack.create(stored.fluid);
            result.setStackSize(stored.amountMb);
            return result;
        }
        return null;
    }

    public static IAEFluidStack extractFluid(ItemStack stack, IAEFluidStack request) {
        if (request == null) {
            return null;
        }
        FluidAccumState state = readFluidState(stack);
        for (Map.Entry<String, StoredFluid> entry : state.fluids.entrySet()) {
            StoredFluid stored = entry.getValue();
            if (stored.fluid == null || stored.fluid.getFluid() != request.getFluid()) {
                continue;
            }
            long available = stored.amountMb;
            long toExtract = Math.min(available, request.getStackSize());
            if (toExtract <= 0L) {
                return null;
            }
            stored.amountMb = available - toExtract;
            writeFluidState(stack, state);
            IAEFluidStack result = AEFluidStack.create(stored.fluid);
            result.setStackSize(toExtract);
            return result;
        }
        return null;
    }

    public static String itemKey(ItemStack is) {
        ItemStack copy = is.copy();
        copy.stackSize = 1;
        int nbtHash = 0;
        if (copy.hasTagCompound()) {
            nbtHash = copy.getTagCompound()
                .hashCode();
        }
        return copy.getItem()
            .getUnlocalizedName() + "@"
            + copy.getItemDamage()
            + "@"
            + nbtHash;
    }

    public static String fluidKey(FluidStack fs) {
        return fs.getFluid()
            .getName();
    }

    public static final class ItemAccumState {

        public final Map<String, StoredItem> items = new LinkedHashMap<String, StoredItem>();
        public long storedTypes;
        public long storedCount;

        public Map<String, Long> toAmountMap() {
            Map<String, Long> amounts = new LinkedHashMap<String, Long>();
            for (Map.Entry<String, StoredItem> entry : items.entrySet()) {
                amounts.put(entry.getKey(), entry.getValue().amount);
            }
            return amounts;
        }
    }

    public static final class StoredItem {

        public final ItemStack stack;
        public long amount;

        public StoredItem(ItemStack stack) {
            this.stack = stack;
            this.stack.stackSize = 1;
            this.amount = 0L;
        }

        public StoredItem(ItemStack stack, long amount) {
            this.stack = stack;
            this.stack.stackSize = 1;
            this.amount = amount;
        }
    }

    public static final class FluidAccumState {

        public final Map<String, StoredFluid> fluids = new LinkedHashMap<String, StoredFluid>();
        public long storedTypes;
        public long storedMb;

        public Map<String, Long> toAmountMap() {
            Map<String, Long> amounts = new LinkedHashMap<String, Long>();
            for (Map.Entry<String, StoredFluid> entry : fluids.entrySet()) {
                amounts.put(entry.getKey(), entry.getValue().amountMb);
            }
            return amounts;
        }
    }

    public static final class StoredFluid {

        public final FluidStack fluid;
        public long amountMb;

        public StoredFluid(FluidStack fluid) {
            this.fluid = fluid;
            this.amountMb = 0L;
        }

        public StoredFluid(FluidStack fluid, long amountMb) {
            this.fluid = fluid;
            this.amountMb = amountMb;
        }
    }
}
