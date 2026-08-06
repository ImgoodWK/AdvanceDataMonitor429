package com.imgood.textech.webae.monitor;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.Assert;
import org.junit.Test;

import com.imgood.textech.monitor.MonitorWidgetSpec;

public class MonitorPreviewCollectorTest {

    @Test
    public void collectsAllSevenSharedPreviewShapes() {
        assertPreview(MonitorWidgetSpec.KIND_STAT_CARD, MonitorWidgetSpec.PREVIEW_SCALAR);
        assertPreview(MonitorWidgetSpec.KIND_PROGRESS_BAR, MonitorWidgetSpec.PREVIEW_SCALAR);
        assertPreview(MonitorWidgetSpec.KIND_GAUGE, MonitorWidgetSpec.PREVIEW_SCALAR);
        assertPreview(MonitorWidgetSpec.KIND_LINE_CHART, MonitorWidgetSpec.PREVIEW_SERIES);
        assertPreview(MonitorWidgetSpec.KIND_BAR_CHART, MonitorWidgetSpec.PREVIEW_CATEGORIES);
        assertPreview(MonitorWidgetSpec.KIND_PIE_CHART, MonitorWidgetSpec.PREVIEW_CATEGORIES);
        assertPreview(MonitorWidgetSpec.KIND_DATA_TABLE, MonitorWidgetSpec.PREVIEW_ROWS);
    }

    @Test
    public void steamWithoutTargetHasNoFakeCapacity() {
        NBTTagCompound binding = binding(MonitorWidgetSpec.KIND_GAUGE);
        binding.setString("sourceKind", MonitorWidgetSpec.SOURCE_WIRELESS_STEAM);
        binding.setString("metricKey", "steamPercent");
        MonitorPreviewDto dto = MonitorPreviewCollector.collectBinding(binding, 240);
        Assert.assertNotNull(dto.scalar);
        Assert.assertFalse(dto.scalar.maxKnown);
        Assert.assertEquals(0.0D, dto.scalar.max, 0.0D);
    }

    private static void assertPreview(String kind, String previewType) {
        MonitorPreviewDto dto = MonitorPreviewCollector.collectBinding(binding(kind), 32);
        Assert.assertEquals(previewType, dto.previewType);
        Assert.assertTrue(dto.pointBudget <= 32);
    }

    private static NBTTagCompound binding(String kind) {
        NBTTagCompound binding = new NBTTagCompound();
        binding.setString("kind", kind);
        binding.setString("metricKey", "testRandomData");
        binding.setString("title", kind);
        binding.setInteger("maxRows", 10);
        NBTTagList values = new NBTTagList();
        for (int i = 0; i < 50; i++) {
            NBTTagCompound point = new NBTTagCompound();
            point.setDouble("data", i);
            values.appendTag(point);
        }
        binding.setTag("dataValues", values);
        return binding;
    }
}
