package com.imgood.textech.webae.quest;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.imgood.textech.compat.bq.BqApiFacade;
import com.imgood.textech.webae.dto.QuestTaskDto;
import com.imgood.textech.webae.icon.IconItemId;
import com.imgood.textech.webae.recipe.RecipeItemEntries;

/**
 * Maps bq_standard task types to unified {@link QuestTaskDto} with Web action hints.
 */
public final class QuestTaskDeserializer {

    public static final String WEB_SUBMIT = "SUBMIT";
    public static final String WEB_DETECT = "DETECT";
    public static final String WEB_IN_GAME = "IN_GAME_ONLY";

    private QuestTaskDeserializer() {}

    public static QuestTaskDto deserialize(int index, String taskId, Object task, UUID questingUuid) {
        if (task == null) {
            return null;
        }
        QuestTaskDto dto = new QuestTaskDto();
        dto.index = index;
        dto.taskId = taskId != null && !taskId.isEmpty() ? taskId : String.valueOf(index);
        dto.factoryId = readFactoryId(task);
        dto.name = localize(readUnlocalizedName(task));
        dto.complete = BqApiFacade.isTaskComplete(task, questingUuid);
        classifyWebAction(dto);
        readProgress(dto, task, questingUuid);
        readRequirements(dto, task);
        reclassifyFluidHoldDetect(dto, task);
        reclassifyRetrievalConsume(dto, task);
        if (dto.description == null || dto.description.isEmpty()) {
            dto.description = dto.name;
        }
        return dto;
    }

    /** TaskFluid with consume=false is AE hold-detect (retrieveFluids), not AE submit. */
    private static void reclassifyFluidHoldDetect(QuestTaskDto dto, Object task) {
        if (dto == null || task == null) {
            return;
        }
        String fid = dto.factoryId != null ? dto.factoryId.toLowerCase(Locale.ROOT) : "";
        if (!fid.contains("fluid") || fid.contains("retrieval")) {
            return;
        }
        if (!readConsumeFlag(task, true)) {
            dto.webAction = WEB_DETECT;
            dto.reasonKey = "adm.quest.task.retrieval_fluid";
        }
    }

    /**
     * Item {@code bq_standard:retrieval} with {@code consume=true} is AE submit (submitItem), not DETECT.
     * BQ's retrieveItems no-ops when consume is set; only submitItem deducts stacks.
     */
    private static void reclassifyRetrievalConsume(QuestTaskDto dto, Object task) {
        if (dto == null || task == null) {
            return;
        }
        String fid = dto.factoryId != null ? dto.factoryId.toLowerCase(Locale.ROOT) : "";
        if (!fid.contains("retrieval") || fid.contains("fluid")) {
            return;
        }
        if (readConsumeFlag(task, false)) {
            dto.webAction = WEB_SUBMIT;
            dto.reasonKey = "adm.quest.task.submit_item";
        }
    }

    /** Reads BQ task {@code consume} field; {@code defaultIfMissing} when absent / unreadable. */
    private static boolean readConsumeFlag(Object task, boolean defaultIfMissing) {
        try {
            Field f = findField(task.getClass(), "consume");
            if (f != null) {
                f.setAccessible(true);
                Object val = f.get(task);
                if (val instanceof Boolean) {
                    return ((Boolean) val).booleanValue();
                }
            }
        } catch (Throwable ignored) {}
        return defaultIfMissing;
    }

    private static void classifyWebAction(QuestTaskDto dto) {
        String fid = dto.factoryId != null ? dto.factoryId.toLowerCase(Locale.ROOT) : "";
        if (fid.contains("retrieval") || fid.contains("checkbox")) {
            if (fid.contains("fluid")) {
                dto.webAction = WEB_DETECT;
                dto.reasonKey = "adm.quest.task.retrieval_fluid";
            } else {
                dto.webAction = WEB_DETECT;
                dto.reasonKey = "adm.quest.task.retrieval";
            }
            return;
        }
        if (fid.contains("item") || fid.contains("consume") || fid.contains("optional")) {
            dto.webAction = WEB_SUBMIT;
            dto.reasonKey = "adm.quest.task.submit_item";
            return;
        }
        if (fid.contains("fluid") && !fid.contains("retrieval")) {
            // TaskFluid with consume=false is hold/detect (same as item Retrieval); consume=true submits.
            dto.webAction = WEB_SUBMIT;
            dto.reasonKey = "adm.quest.task.submit_fluid";
            return;
        }
        if (fid.contains("craft")) {
            dto.webAction = WEB_IN_GAME;
            dto.reasonKey = "adm.quest.task.crafting";
            return;
        }
        if (fid.contains("hunt") || fid.contains("kill") || fid.contains("location")
            || fid.contains("dimension") || fid.contains("meeting") || fid.contains("scoreboard")
            || fid.contains("advancement") || fid.contains("break")) {
            dto.webAction = WEB_IN_GAME;
            dto.reasonKey = "adm.quest.task.in_game";
            return;
        }
        dto.webAction = WEB_IN_GAME;
        dto.reasonKey = "adm.quest.task.unsupported";
    }

    private static void readProgress(QuestTaskDto dto, Object task, UUID questingUuid) {
        NBTTagCompound full = BqApiFacade.writeTaskNbt(task);
        if (questingUuid != null) {
            try {
                NBTTagCompound prog = new NBTTagCompound();
                for (java.lang.reflect.Method m : task.getClass()
                    .getMethods()) {
                    if ("readProgressFromNBT".equals(m.getName()) && m.getParameterTypes().length == 2) {
                        m.invoke(task, prog, questingUuid);
                        mergeProgress(dto, prog, full);
                        return;
                    }
                }
            } catch (Throwable ignored) {}
        }
        mergeProgress(dto, full, full);
    }

    private static void mergeProgress(QuestTaskDto dto, NBTTagCompound progress, NBTTagCompound config) {
        long configRequired = config.getLong("required");
        if (configRequired <= 0) {
            configRequired = config.getInteger("amount");
        }

        dto.progress = progress.getLong("progress");
        if (dto.progress <= 0) {
            dto.progress = progress.getInteger("amount");
        }

        readItemFromNbt(dto, config);
        readFluidFromNbt(dto, config);
        readOreDictFromNbt(dto, config);
        readRequiredItemsFromNbt(dto, config);
        readRequiredFluidsFromNbt(dto, config);

        dto.acceptAnyMeta = dto.factoryId != null
            && dto.factoryId.toLowerCase(Locale.ROOT).contains("retrieval")
            && (config.hasKey("oreDict") || config.hasKey("tag"));

        if ((dto.registryName == null || dto.registryName.isEmpty()) && config.hasKey("items")) {
            NBTTagList items = config.getTagList("items", 10);
            if (items.tagCount() > 0) {
                NBTTagCompound first = items.getCompoundTagAt(0);
                readItemTag(dto, first);
                long itemCount = itemCountFromNbt(first);
                if (itemCount > 0) {
                    dto.required = itemCount;
                }
                if (items.tagCount() > 1) {
                    dto.extraItemCount = items.tagCount() - 1;
                }
            }
        }

        if (configRequired > 0) {
            dto.required = configRequired;
        } else if (dto.required <= 0) {
            dto.required = 1;
        }
    }

    private static void readRequiredItemsFromNbt(QuestTaskDto dto, NBTTagCompound config) {
        if (!config.hasKey("requiredItems")) {
            return;
        }
        NBTTagList items = config.getTagList("requiredItems", 10);
        if (items.tagCount() <= 0) {
            return;
        }
        NBTTagCompound first = items.getCompoundTagAt(0);
        if (dto.registryName == null || dto.registryName.isEmpty()) {
            readItemTag(dto, first);
        }
        long count = itemCountFromNbt(first);
        if (count > 0) {
            dto.required = count;
        }
        if (items.tagCount() > 1) {
            dto.extraItemCount = items.tagCount() - 1;
        }
    }

    private static void readRequiredFluidsFromNbt(QuestTaskDto dto, NBTTagCompound config) {
        if (!config.hasKey("requiredFluids")) {
            return;
        }
        NBTTagList fluids = config.getTagList("requiredFluids", 10);
        if (fluids.tagCount() <= 0) {
            return;
        }
        NBTTagCompound first = fluids.getCompoundTagAt(0);
        if (first != null) {
            if (dto.fluidName == null || dto.fluidName.isEmpty()) {
                dto.fluidName = first.getString("FluidName");
            }
            if (first.hasKey("Amount")) {
                dto.fluidRequired = first.getInteger("Amount");
            }
            if (dto.fluidName != null && !dto.fluidName.isEmpty()) {
                FluidStack fs = parseFluidStack(dto.fluidName, Math.max(1L, dto.fluidRequired));
                if (fs != null) {
                    applyFluidDisplayName(dto, fs);
                }
            }
            applyFluidIconName(dto, dto.fluidName);
        }
        if (fluids.tagCount() > 1) {
            dto.extraItemCount = Math.max(dto.extraItemCount, fluids.tagCount() - 1);
        }
    }

    private static long itemCountFromNbt(NBTTagCompound tag) {
        if (tag == null) {
            return 0L;
        }
        if (tag.hasKey("Count")) {
            return tag.getInteger("Count");
        }
        if (tag.hasKey("StackSize")) {
            return tag.getInteger("StackSize");
        }
        return 0L;
    }

    private static void readRequirements(QuestTaskDto dto, Object task) {
        try {
            Field requiredItems = findField(task.getClass(), "requiredItems");
            if (requiredItems != null) {
                requiredItems.setAccessible(true);
                Object list = requiredItems.get(task);
                if (list instanceof List) {
                    List<?> reqList = (List<?>) list;
                    int count = reqList.size();
                    if (count > 0) {
                        Object first = reqList.get(0);
                        ItemStack stack = bigStackToItem(first);
                        if (stack != null) {
                            fillItem(dto, stack);
                            long amount = bigStackAmount(first);
                            if (amount > 0) {
                                dto.required = amount;
                            }
                        }
                        if (count > 1) {
                            dto.extraItemCount = count - 1;
                        }
                    }
                }
            }
            Field targetFluid = findField(task.getClass(), "targetFluid");
            if (targetFluid != null) {
                targetFluid.setAccessible(true);
                Object fs = targetFluid.get(task);
                if (fs instanceof FluidStack) {
                    fillFluid(dto, (FluidStack) fs);
                }
            }
            Field required = findField(task.getClass(), "required");
            if (required != null) {
                required.setAccessible(true);
                Object val = required.get(task);
                if (val instanceof Number && (dto.required <= 0 || dto.required == 1)) {
                    long fieldVal = ((Number) val).longValue();
                    if (fieldVal > 1) {
                        dto.required = fieldVal;
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void readItemFromNbt(QuestTaskDto dto, NBTTagCompound tag) {
        if (tag.hasKey("id")) {
            readItemTag(dto, tag);
        }
        if (tag.hasKey("targetItem")) {
            readItemTag(dto, tag.getCompoundTag("targetItem"));
        }
        if (tag.hasKey("targetStack")) {
            readItemTag(dto, tag.getCompoundTag("targetStack"));
        }
    }

    private static void readItemTag(QuestTaskDto dto, NBTTagCompound tag) {
        if (tag == null) {
            return;
        }
        try {
            ItemStack stack = ItemStack.loadItemStackFromNBT(tag);
            if (stack != null) {
                fillItem(dto, stack);
            }
        } catch (Throwable ignored) {}
    }

    private static void readOreDictFromNbt(QuestTaskDto dto, NBTTagCompound tag) {
        if (tag == null) {
            return;
        }
        if (tag.hasKey("oreDict")) {
            String ore = tag.getString("oreDict");
            if (ore != null && !ore.isEmpty()) {
                appendDescriptionHint(dto, ore);
            }
        }
        if (tag.hasKey("tag")) {
            String oreTag = tag.getString("tag");
            if (oreTag != null && !oreTag.isEmpty()) {
                appendDescriptionHint(dto, oreTag);
            }
        }
    }

    private static void appendDescriptionHint(QuestTaskDto dto, String hint) {
        if (hint == null || hint.isEmpty()) {
            return;
        }
        if (dto.description == null || dto.description.isEmpty()) {
            dto.description = hint;
            return;
        }
        if (!dto.description.contains(hint)) {
            dto.description = dto.description + " (" + hint + ")";
        }
    }

    private static void readFluidFromNbt(QuestTaskDto dto, NBTTagCompound tag) {
        if (tag.hasKey("FluidName")) {
            dto.fluidName = tag.getString("FluidName");
            dto.fluidRequired = tag.getInteger("Amount");
            dto.fluidProgress = tag.getInteger("progress");
            FluidStack named = parseFluidStack(dto.fluidName, Math.max(1L, dto.fluidRequired));
            if (named != null) {
                applyFluidDisplayName(dto, named);
            }
            applyFluidIconName(dto, dto.fluidName);
        }
        if (tag.hasKey("targetFluid")) {
            NBTTagCompound fluidTag = tag.getCompoundTag("targetFluid");
            if (fluidTag != null) {
                dto.fluidName = fluidTag.getString("FluidName");
                dto.fluidRequired = fluidTag.getInteger("Amount");
                FluidStack named = parseFluidStack(dto.fluidName, Math.max(1L, dto.fluidRequired));
                if (named != null) {
                    applyFluidDisplayName(dto, named);
                }
                applyFluidIconName(dto, dto.fluidName);
            }
        }
    }

    private static void fillItem(QuestTaskDto dto, ItemStack stack) {
        dto.registryName = registryNameForStack(stack);
        int meta = stack.getItemDamage();
        if (meta == Short.MAX_VALUE) {
            meta = 0;
        }
        dto.meta = meta;
        dto.itemId = RecipeItemEntries.buildItemId(dto.registryName, meta);
        applyItemDisplayName(dto, stack);
        // Display only: filled fluid cells → fluid:xxx (same as recipe page). Do NOT set
        // fluidName here — analyzer would treat the step as AE fluid inventory matching.
        applyFluidIconFromStack(dto, stack);
    }

    private static void fillFluid(QuestTaskDto dto, FluidStack fs) {
        if (fs == null || fs.getFluid() == null) {
            return;
        }
        dto.fluidName = fs.getFluid()
            .getName();
        dto.fluidRequired = fs.amount;
        applyFluidDisplayName(dto, fs);
        applyFluidIconName(dto, dto.fluidName);
    }

    private static void applyItemDisplayName(QuestTaskDto dto, ItemStack stack) {
        if (dto == null || stack == null) {
            return;
        }
        try {
            String name = stack.getDisplayName();
            if (name != null && !name.isEmpty()) {
                dto.displayName = name;
            }
        } catch (Throwable ignored) {}
    }

    private static void applyFluidDisplayName(QuestTaskDto dto, FluidStack fs) {
        if (dto == null || fs == null || fs.getFluid() == null) {
            return;
        }
        try {
            String name = fs.getLocalizedName();
            if (name != null && !name.isEmpty()) {
                dto.displayName = name;
            }
        } catch (Throwable ignored) {}
    }

    /** Prefer recipe-style {@code fluid:} icon when the item stack carries a fluid. */
    private static void applyFluidIconFromStack(QuestTaskDto dto, ItemStack stack) {
        if (dto == null || stack == null) {
            return;
        }
        applyFluidIconName(dto, QuestFluidIconResolver.resolveFluidName(stack));
    }

    private static void applyFluidIconName(QuestTaskDto dto, String fluidName) {
        if (dto == null || fluidName == null || fluidName.isEmpty()) {
            return;
        }
        if (dto.iconItemId == null || dto.iconItemId.isEmpty()) {
            dto.iconItemId = IconItemId.FLUID_PREFIX + fluidName;
        }
    }

    private static ItemStack bigStackToItem(Object bigStack) {
        if (bigStack == null) {
            return null;
        }
        if (bigStack instanceof ItemStack) {
            return (ItemStack) bigStack;
        }
        try {
            java.lang.reflect.Method m = bigStack.getClass()
                .getMethod("getBaseStack");
            Object stack = m.invoke(bigStack);
            if (stack instanceof ItemStack) {
                return (ItemStack) stack;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Reads quantity from BQ {@code BigItemStack} (not {@link ItemStack#stackSize} on the base template). */
    private static long bigStackAmount(Object bigStack) {
        if (bigStack == null) {
            return 0L;
        }
        if (bigStack instanceof ItemStack) {
            return ((ItemStack) bigStack).stackSize;
        }
        try {
            java.lang.reflect.Method m = bigStack.getClass()
                .getMethod("getStackSize");
            Object size = m.invoke(bigStack);
            if (size instanceof Number) {
                long val = ((Number) size).longValue();
                if (val > 0) {
                    return val;
                }
            }
        } catch (Throwable ignored) {}
        try {
            Field f = findField(bigStack.getClass(), "stackSize");
            if (f != null) {
                f.setAccessible(true);
                Object val = f.get(bigStack);
                if (val instanceof Number) {
                    long fieldVal = ((Number) val).longValue();
                    if (fieldVal > 0) {
                        return fieldVal;
                    }
                }
            }
        } catch (Throwable ignored) {}
        ItemStack stack = bigStackToItem(bigStack);
        return stack != null && stack.stackSize > 0 ? stack.stackSize : 0L;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static String readFactoryId(Object task) {
        try {
            java.lang.reflect.Method m = task.getClass()
                .getMethod("getFactoryID");
            Object rl = m.invoke(task);
            return rl != null ? rl.toString() : "";
        } catch (Throwable ignored) {
            return task.getClass()
                .getSimpleName();
        }
    }

    private static String readUnlocalizedName(Object task) {
        try {
            java.lang.reflect.Method m = task.getClass()
                .getMethod("getUnlocalisedName");
            Object name = m.invoke(task);
            return name != null ? name.toString() : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String registryNameForStack(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return "";
        }
        Object nameObj = net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
        return nameObj != null ? nameObj.toString() : "";
    }

    private static String localize(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        return StatCollector.translateToLocal(key);
    }

    public static FluidStack parseFluidStack(String fluidName, long amount) {
        if (fluidName == null || fluidName.isEmpty()) {
            return null;
        }
        net.minecraftforge.fluids.Fluid fluid = FluidRegistry.getFluid(fluidName);
        if (fluid == null) {
            return null;
        }
        int amt = (int) Math.min(Integer.MAX_VALUE, Math.max(1, amount));
        return new FluidStack(fluid, amt);
    }
}
