package com.imgood.advancedatamonitor.items.cell;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.Config;
import com.imgood.advancedatamonitor.utils.ModLogFiles;

/**
 * Server-side trace logging for data loom weaving.
 * Only active when {@link Config#dataLoomCellDebugLogging} is true.
 * Writes to {@code logs/advancedatamonitor/data-loom-debug.log} (not mixed into latest.log).
 */
public final class DataLoomDebugLog {

    public static final String LOG_FILE_NAME = "data-loom-debug.log";

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    private static boolean startupBannerLogged;

    private DataLoomDebugLog() {}

    public static boolean isEnabled() {
        return Config.dataLoomCellDebugLogging;
    }

    public static String logFilePath() {
        return ModLogFiles.modLogFile(LOG_FILE_NAME)
            .getPath();
    }

    /** One-time banner after config load when debug logging is active. */
    public static void logStartupBannerIfNeeded() {
        if (!isEnabled() || startupBannerLogged) {
            return;
        }
        startupBannerLogged = true;
        appendLine(
            "INFO",
            "Debug logging ON — file={} syncInterval={}s energyDrain={} AE/t dustRate={}/s formRate={}/s flowRate={}mB/s sourceRate={}mB/s",
            logFilePath(),
            Config.dataLoomCellSyncIntervalSeconds,
            Config.dataLoomCellEnergyDrainPerTick,
            Config.dataDustLoomCellItemRatePerSecond,
            Config.dataFormLoomCellItemRatePerSecond,
            Config.dataFlowCellFluidRatePerSecond,
            Config.dataSourceLoomCellEssentiaRatePerSecond);
        AdvanceDataMonitor.LOG.info("[DataLoomCell] Debug logging enabled — see {}", logFilePath());
    }

    public static void info(String message, Object... args) {
        if (!isEnabled()) {
            return;
        }
        appendLine("INFO", message, args);
    }

    public static void warn(String message, Object... args) {
        if (!isEnabled()) {
            return;
        }
        appendLine("WARN", message, args);
    }

    private static synchronized void appendLine(String level, String message, Object... args) {
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(ModLogFiles.modLogFile(LOG_FILE_NAME), true), "UTF-8"));
            writer.print(TIME_FORMAT.format(new Date()));
            writer.print(" [");
            writer.print(level);
            writer.print("] [DataLoomCell] ");
            writer.println(format(message, args));
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.debug("Failed to append data loom debug log", e);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private static String format(String message, Object... args) {
        if (message == null) {
            return "";
        }
        if (args == null || args.length == 0) {
            return message;
        }
        StringBuilder builder = new StringBuilder(message.length() + 32);
        int argIndex = 0;
        int cursor = 0;
        while (cursor < message.length()) {
            int placeholder = message.indexOf("{}", cursor);
            if (placeholder < 0 || argIndex >= args.length) {
                builder.append(message.substring(cursor));
                break;
            }
            builder.append(message, cursor, placeholder);
            builder.append(String.valueOf(args[argIndex++]));
            cursor = placeholder + 2;
        }
        return builder.toString();
    }

    public static String describeCell(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return "null";
        }
        Item item = stack.getItem();
        String name = item.getClass()
            .getSimpleName();
        String unloc = item.getUnlocalizedName();
        return name + "(" + unloc + ")x" + stack.stackSize;
    }

    public static String describeFluid(FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null) {
            return "null";
        }
        return fluid.getFluid()
            .getName() + "@"
            + fluid.amount
            + "mB";
    }

    public static int countConfigListSlots(ItemStack cellStack) {
        if (cellStack == null || !cellStack.hasTagCompound()) {
            return 0;
        }
        NBTTagCompound tag = cellStack.getTagCompound();
        if (!tag.hasKey(DataLoomCellUtil.NBT_CONFIG_LIST, 10)) {
            return 0;
        }
        NBTTagCompound listTag = tag.getCompoundTag(DataLoomCellUtil.NBT_CONFIG_LIST);
        int count = 0;
        for (int slot = 0; slot < 63; slot++) {
            if (!listTag.hasKey("#" + slot, 10)) {
                continue;
            }
            ItemStack marker = ItemStack.loadItemStackFromNBT(listTag.getCompoundTag("#" + slot));
            if (marker != null && marker.getItem() != null) {
                count++;
            }
        }
        return count;
    }

    public static int countConfigInventorySlots(net.minecraft.inventory.IInventory config) {
        if (config == null) {
            return 0;
        }
        int count = 0;
        for (int slot = 0; slot < config.getSizeInventory(); slot++) {
            if (config.getStackInSlot(slot) != null) {
                count++;
            }
        }
        return count;
    }
}
