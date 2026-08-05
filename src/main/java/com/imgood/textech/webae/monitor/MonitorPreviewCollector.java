package com.imgood.textech.webae.monitor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import com.imgood.textech.monitor.MonitorDownsampleUtil;
import com.imgood.textech.monitor.MonitorWidgetSpec;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.webae.context.WebAeOwnerContext;

/** Collects a bounded, type-aware preview from one monitor binding. */
public final class MonitorPreviewCollector {

    private MonitorPreviewCollector() {}

    public static MonitorPreviewDto collect(String ownerUuid, int dim, int x, int y, int z, int slotIndex) {
        return collect(ownerUuid, dim, x, y, z, slotIndex, MonitorDownsampleUtil.MAX_RENDER_POINTS);
    }

    public static MonitorPreviewDto collect(String ownerUuid, int dim, int x, int y, int z, int slotIndex,
        int visibleWidth) {
        String ownerName = WebAeOwnerContext.resolveOwnerName(ownerUuid);
        if (ownerName.isEmpty()) {
            return null;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return null;
        }
        WorldServer world = findWorld(server, dim);
        if (world == null || !world.blockExists(x, y, z)
            || !(world.getTileEntity(x, y, z) instanceof TileEntityAdvanceDataMonitor)) {
            return null;
        }
        TileEntityAdvanceDataMonitor monitor = (TileEntityAdvanceDataMonitor) world.getTileEntity(x, y, z);
        if (!ownerName.equals(monitor.getOwnerName())) {
            return null;
        }
        NBTTagCompound binding = monitor.getDataBoundList().get(Integer.valueOf(slotIndex));
        if (binding == null) {
            return null;
        }
        MonitorWidgetSpec.normalizeBinding(binding, x, y, z);

        MonitorPreviewDto dto = collectBinding(binding, visibleWidth);
        dto.monitorDim = dim;
        dto.monitorX = x;
        dto.monitorY = y;
        dto.monitorZ = z;
        dto.slotIndex = slotIndex;
        return dto;
    }

    static MonitorPreviewDto collectBinding(NBTTagCompound binding, int visibleWidth) {
        MonitorWidgetSpec.normalizeBinding(binding, 0, 0, 0);
        MonitorPreviewDto dto = new MonitorPreviewDto();
        dto.dataType = binding.getString("dataType");
        dto.kind = MonitorWidgetSpec.getKind(binding);
        dto.sourceKind = MonitorWidgetSpec.getSourceKind(binding);
        dto.metricKey = MonitorWidgetSpec.getMetricKey(binding);
        dto.title = MonitorWidgetSpec.getTitle(binding);
        dto.displayName = dto.title;
        dto.previewType = MonitorWidgetSpec.previewTypeForKind(dto.kind);
        dto.enabled = binding.getBoolean("enable");
        dto.yMin = binding.hasKey("yMin") ? binding.getDouble("yMin") : 0.0D;
        dto.yMax = binding.hasKey("yMax") ? binding.getDouble("yMax") : 0.0D;
        dto.dataLimit = binding.hasKey("dataLimit") ? binding.getInteger("dataLimit") : 10;
        dto.columns = readOptionalStringList(binding, "columns");

        List<Double> history = readHistory(binding);
        if (MonitorWidgetSpec.SERIES_TRANSFORM_DIFFERENCE.equals(MonitorWidgetSpec.getSeriesTransform(binding))) {
            history = MonitorDownsampleUtil.difference(history);
        }
        dto.pointBudget = MonitorDownsampleUtil.pointBudget(history.size(), visibleWidth);
        dto.values = MonitorDownsampleUtil.downsample(history, visibleWidth);

        if (MonitorWidgetSpec.PREVIEW_SCALAR.equals(dto.previewType)) {
            fillScalar(dto, binding, history);
        } else if (MonitorWidgetSpec.PREVIEW_CATEGORIES.equals(dto.previewType)) {
            fillCategories(dto, binding, history);
        } else if (MonitorWidgetSpec.PREVIEW_ROWS.equals(dto.previewType)) {
            fillRows(dto, binding, history);
        } else {
            MonitorPreviewDto.Series series = new MonitorPreviewDto.Series();
            series.id = dto.metricKey;
            series.label = dto.title;
            series.values = dto.values;
            dto.series.add(series);
        }
        dto.timestamp = System.currentTimeMillis();
        return dto;
    }

    private static WorldServer findWorld(MinecraftServer server, int dim) {
        if (server.worldServers == null) {
            return null;
        }
        for (WorldServer world : server.worldServers) {
            if (world != null && world.provider.dimensionId == dim) {
                return world;
            }
        }
        return null;
    }

    private static List<Double> readHistory(NBTTagCompound binding) {
        List<Double> values = new ArrayList<Double>();
        NBTTagList dataValues = binding.getTagList("dataValues", 10);
        for (int i = 0; i < dataValues.tagCount(); i++) {
            values.add(Double.valueOf(dataValues.getCompoundTagAt(i).getDouble("data")));
        }
        return values;
    }

    private static void fillScalar(MonitorPreviewDto dto, NBTTagCompound binding, List<Double> history) {
        MonitorPreviewDto.Scalar scalar = new MonitorPreviewDto.Scalar();
        scalar.value = history.isEmpty() ? 0.0D : history.get(history.size() - 1).doubleValue();
        double targetValue = MonitorWidgetSpec.getTargetValue(binding);
        boolean percentMetric = dto.metricKey.toLowerCase(java.util.Locale.ROOT).contains("percent")
            && (!MonitorWidgetSpec.SOURCE_WIRELESS_STEAM.equals(dto.sourceKind) || targetValue > 0.0D);
        if (percentMetric) {
            scalar.max = 100.0D;
            scalar.maxKnown = true;
            scalar.percentage = clampPercent(scalar.value);
        } else if (targetValue > 0.0D) {
            scalar.max = targetValue;
            scalar.maxKnown = true;
            scalar.percentage = clampPercent((scalar.value / targetValue) * 100.0D);
        } else {
            scalar.max = 0.0D;
            scalar.maxKnown = false;
            scalar.percentage = 0.0D;
        }
        dto.scalar = scalar;
    }

    private static void fillCategories(MonitorPreviewDto dto, NBTTagCompound binding, List<Double> history) {
        int maxRows = Math.max(0, Math.min(64, binding.hasKey("maxRows") ? binding.getInteger("maxRows") : 10));
        NBTTagList storageItems = binding.getTagList("storageItems", 10);
        if (storageItems.tagCount() > 0) {
            for (int i = 0; i < storageItems.tagCount() && dto.categories.size() < maxRows; i++) {
                NBTTagCompound item = storageItems.getCompoundTagAt(i);
                MonitorPreviewDto.Category category = new MonitorPreviewDto.Category();
                category.label = nonEmpty(item.getString("displayName"), "#" + (i + 1));
                category.value = item.getLong("count");
                dto.categories.add(category);
            }
            return;
        }
        int start = Math.max(0, history.size() - maxRows);
        for (int i = start; i < history.size(); i++) {
            MonitorPreviewDto.Category category = new MonitorPreviewDto.Category();
            category.label = "#" + (i + 1);
            category.value = history.get(i).doubleValue();
            dto.categories.add(category);
        }
    }

    private static void fillRows(MonitorPreviewDto dto, NBTTagCompound binding, List<Double> history) {
        int maxRows = Math.max(0, Math.min(64, binding.hasKey("maxRows") ? binding.getInteger("maxRows") : 10));
        NBTTagList storageItems = binding.getTagList("storageItems", 10);
        for (int i = 0; i < storageItems.tagCount() && dto.rows.size() < maxRows; i++) {
            NBTTagCompound item = storageItems.getCompoundTagAt(i);
            MonitorPreviewDto.Row row = new MonitorPreviewDto.Row();
            row.cells.put("name", nonEmpty(item.getString("displayName"), "#" + (i + 1)));
            row.cells.put("amount", String.valueOf(item.getLong("count")));
            row.cells.put("delta", String.valueOf(item.getLong("countDelta")));
            row.cells.put("kind", nonEmpty(item.getString("type"), "item"));
            dto.rows.add(row);
        }
        if (!dto.rows.isEmpty()) {
            return;
        }
        NBTTagList lines = binding.hasKey("networkLines") ? binding.getTagList("networkLines", 8)
            : binding.getTagList("lines", 8);
        for (int i = 0; i < lines.tagCount() && dto.rows.size() < maxRows; i++) {
            MonitorPreviewDto.Row row = new MonitorPreviewDto.Row();
            row.cells.put("text", lines.getStringTagAt(i));
            dto.rows.add(row);
        }
        if (!dto.rows.isEmpty()) {
            return;
        }
        int start = Math.max(0, history.size() - maxRows);
        for (int i = start; i < history.size(); i++) {
            MonitorPreviewDto.Row row = new MonitorPreviewDto.Row();
            row.cells.put("index", String.valueOf(i));
            row.cells.put("value", String.valueOf(history.get(i)));
            dto.rows.add(row);
        }
    }

    private static List<String> readOptionalStringList(NBTTagCompound binding, String key) {
        if (!binding.hasKey(key)) {
            return null;
        }
        List<String> result = new ArrayList<String>();
        NBTTagList list = binding.getTagList(key, 8);
        for (int i = 0; i < list.tagCount(); i++) {
            result.add(list.getStringTagAt(i));
        }
        return result;
    }

    private static double clampPercent(double value) {
        return Math.max(0.0D, Math.min(100.0D, value));
    }

    private static String nonEmpty(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }
}
