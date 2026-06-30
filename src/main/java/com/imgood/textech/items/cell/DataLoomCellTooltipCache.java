package com.imgood.textech.items.cell;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.imgood.textech.Config;

/**
 * Caches computed tooltip fields on the cell stack NBT. Refreshed only when partition markers or
 * weave amplifier cards change in the Cell Workbench â€?not on every hover or weave tick.
 */
public final class DataLoomCellTooltipCache {

    public static final String NBT_KEY = "dataLoomTooltip";

    private static final String K_NORMAL_AMP = "normalAmp";
    private static final String K_SUPER_AMP = "superAmp";
    private static final String K_MARK_COUNT = "markCount";
    private static final String K_MULTIPLIER = "multiplier";
    private static final String K_RATE_PER_SEC = "ratePerSec";
    private static final String K_RATE_PER_INTERVAL = "ratePerInterval";
    private static final String K_PER_MARKER_INTERVAL = "perMarkerInterval";
    private static final String K_SYNC_INTERVAL = "syncInterval";
    private static final String K_FLUID_CHANNEL = "fluidChannel";

    private DataLoomCellTooltipCache() {}

    public static void refresh(ItemStack cellStack) {
        if (cellStack == null || cellStack.getItem() == null || !DataLoomCellUtil.isDataLoomCell(cellStack.getItem())) {
            return;
        }

        int markCount = resolveMarkCount(cellStack);
        double baseRate = resolveBaseRate(cellStack);
        boolean fluidChannel = cellStack.getItem() instanceof AbstractDataLoomFluidCell;
        int[] ampCounts = DataLoomCellUtil.countAmplifiersByType(cellStack);
        double multiplier = DataLoomCellUtil.getSpeedMultiplier(cellStack);
        int syncInterval = Config.dataLoomCellSyncIntervalSeconds;
        if (syncInterval < 1) {
            syncInterval = 1;
        }

        double ratePerSec = baseRate * multiplier;
        long ratePerInterval = (long) Math.floor(ratePerSec * syncInterval);
        long perMarkerInterval = markCount > 0L ? ratePerInterval / markCount : 0L;

        NBTTagCompound cache = new NBTTagCompound();
        cache.setInteger(K_NORMAL_AMP, ampCounts[0]);
        cache.setInteger(K_SUPER_AMP, ampCounts[1]);
        cache.setInteger(K_MARK_COUNT, markCount);
        cache.setDouble(K_MULTIPLIER, multiplier);
        cache.setDouble(K_RATE_PER_SEC, ratePerSec);
        cache.setLong(K_RATE_PER_INTERVAL, ratePerInterval);
        cache.setLong(K_PER_MARKER_INTERVAL, perMarkerInterval);
        cache.setInteger(K_SYNC_INTERVAL, syncInterval);
        cache.setBoolean(K_FLUID_CHANNEL, fluidChannel);

        DataLoomCellStorage.getOrCreateTag(cellStack)
            .setTag(NBT_KEY, cache);
    }

    public static void appendCachedLines(ItemStack cellStack, List tooltip) {
        if (cellStack == null || cellStack.getTagCompound() == null
            || !cellStack.getTagCompound()
                .hasKey(NBT_KEY, 10)) {
            tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("adm.tooltip.data_loom.no_cache"));
            return;
        }

        NBTTagCompound cache = cellStack.getTagCompound()
            .getCompoundTag(NBT_KEY);
        NumberFormat nf = NumberFormat.getInstance(Locale.ENGLISH);

        int markCount = cache.getInteger(K_MARK_COUNT);
        if (markCount <= 0) {
            tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("adm.tooltip.data_loom.no_markers"));
        } else {
            tooltip.add(
                EnumChatFormatting.GRAY
                    + String.format(StatCollector.translateToLocal("adm.tooltip.data_loom.markers"), markCount));
        }

        int normalAmp = cache.getInteger(K_NORMAL_AMP);
        int superAmp = cache.getInteger(K_SUPER_AMP);
        appendAmplifierLines(tooltip, normalAmp, superAmp, cache.getDouble(K_MULTIPLIER), nf);

        if (markCount > 0) {
            appendRateLines(tooltip, cache, nf);
        }
    }

    private static void appendAmplifierLines(List tooltip, int normalAmp, int superAmp, double multiplier,
        NumberFormat nf) {
        if (normalAmp <= 0 && superAmp <= 0) {
            tooltip
                .add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("adm.tooltip.data_loom.amplifiers_none"));
            return;
        }

        if (normalAmp > 0) {
            tooltip.add(
                EnumChatFormatting.WHITE + String.format(
                    StatCollector.translateToLocal("adm.tooltip.data_loom.amplifier_normal"),
                    normalAmp,
                    formatMultiplier(DataLoomAmplifierRates.NORMAL_MULTIPLIER, nf)));
        }
        if (superAmp > 0) {
            tooltip.add(
                EnumChatFormatting.LIGHT_PURPLE + String.format(
                    StatCollector.translateToLocal("adm.tooltip.data_loom.amplifier_super"),
                    superAmp,
                    formatMultiplier(DataLoomAmplifierRates.SUPER_MULTIPLIER, nf)));
        }
        tooltip.add(
            EnumChatFormatting.YELLOW + String.format(
                StatCollector.translateToLocal("adm.tooltip.data_loom.amplifier_total"),
                formatMultiplier(multiplier, nf)));
    }

    private static void appendRateLines(List tooltip, NBTTagCompound cache, NumberFormat nf) {
        boolean fluidChannel = cache.getBoolean(K_FLUID_CHANNEL);
        int syncInterval = cache.getInteger(K_SYNC_INTERVAL);
        double ratePerSec = cache.getDouble(K_RATE_PER_SEC);
        long ratePerInterval = cache.getLong(K_RATE_PER_INTERVAL);
        long perMarkerInterval = cache.getLong(K_PER_MARKER_INTERVAL);

        String rateKey = fluidChannel ? "adm.tooltip.data_loom.rate_fluid" : "adm.tooltip.data_loom.rate_item";
        tooltip.add(
            EnumChatFormatting.GRAY + String.format(
                StatCollector.translateToLocal(rateKey),
                formatAmount(ratePerSec, nf),
                syncInterval,
                formatAmount(ratePerInterval, nf)));

        if (cache.getInteger(K_MARK_COUNT) > 1 && perMarkerInterval > 0L) {
            tooltip.add(
                EnumChatFormatting.GRAY + String.format(
                    StatCollector.translateToLocal("adm.tooltip.data_loom.rate_per_marker"),
                    formatAmount(perMarkerInterval, nf),
                    syncInterval));
        }
    }

    private static int resolveMarkCount(ItemStack cellStack) {
        if (cellStack.getItem() instanceof AbstractDataLoomItemCell) {
            return ((AbstractDataLoomItemCell) cellStack.getItem()).getMarkedItems(cellStack)
                .size();
        }
        if (cellStack.getItem() instanceof AbstractDataLoomFluidCell) {
            return ((AbstractDataLoomFluidCell) cellStack.getItem()).getMarkedFluids(cellStack)
                .size();
        }
        return 0;
    }

    private static double resolveBaseRate(ItemStack cellStack) {
        if (cellStack.getItem() instanceof AbstractDataLoomItemCell) {
            return ((AbstractDataLoomItemCell) cellStack.getItem()).getItemRatePerSecond();
        }
        if (cellStack.getItem() instanceof AbstractDataLoomFluidCell) {
            return ((AbstractDataLoomFluidCell) cellStack.getItem()).getFluidRatePerSecond();
        }
        return 0.0D;
    }

    private static String formatMultiplier(double value, NumberFormat nf) {
        if (value >= 1.0E9D) {
            return nf.format(value);
        }
        if (value == Math.rint(value)) {
            return nf.format((long) value);
        }
        return nf.format(value);
    }

    private static String formatAmount(double value, NumberFormat nf) {
        if (value >= 1.0D && value == Math.rint(value)) {
            return nf.format((long) value);
        }
        return nf.format(value);
    }

    private static String formatAmount(long value, NumberFormat nf) {
        return nf.format(value);
    }

}
