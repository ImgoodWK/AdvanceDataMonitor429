package com.imgood.textech.monitor;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.Assert;
import org.junit.Test;

public class MonitorWidgetSpecTest {

    @Test
    public void migratesDifferenceAliasFromEveryLegacyEntryPoint() {
        String[] keys = { "dataType", "renderType", "kind" };
        for (String key : keys) {
            NBTTagCompound binding = new NBTTagCompound();
            binding.setString(key, "diffrence");
            MonitorWidgetSpec.normalizeBinding(binding, 1, 2, 3);
            Assert.assertEquals(MonitorWidgetSpec.KIND_LINE_CHART, binding.getString("kind"));
            Assert.assertEquals("line", binding.getString("renderType"));
            Assert.assertEquals("line", binding.getString("dataType"));
            Assert.assertEquals(MonitorWidgetSpec.SERIES_TRANSFORM_DIFFERENCE, binding.getString("seriesTransform"));
        }
    }

    @Test
    public void migratesLegacyBarsToSharedBarChart() {
        for (String alias : new String[] { "bar", "bar3d", "waterfall" }) {
            NBTTagCompound binding = new NBTTagCompound();
            binding.setString("dataType", alias);
            MonitorWidgetSpec.normalizeBinding(binding, 0, 0, 0);
            Assert.assertEquals(MonitorWidgetSpec.KIND_BAR_CHART, binding.getString("kind"));
            Assert.assertEquals(MonitorWidgetSpec.KIND_BAR_CHART, binding.getString("renderType"));
            Assert.assertEquals(MonitorWidgetSpec.KIND_BAR_CHART, binding.getString("dataType"));
        }
    }

    @Test
    public void infersAllSixSourceKindsFromLegacyBindings() {
        assertSource("customValue", "line", MonitorWidgetSpec.SOURCE_TILE_METRIC);
        assertSource("craftingStats", "crafting", MonitorWidgetSpec.SOURCE_AE_METRIC);
        assertSource("euStored", "line", MonitorWidgetSpec.SOURCE_WIRELESS_EU);
        assertSource("steamStored", "line", MonitorWidgetSpec.SOURCE_WIRELESS_STEAM);
        assertSource("itemTotal", "storage", MonitorWidgetSpec.SOURCE_STORAGE_SUMMARY);
        assertSource("gtMachineCount", "line", MonitorWidgetSpec.SOURCE_GT_SUMMARY);
    }

    @Test
    public void keepsSpecializedKindsOutOfSharedCore() {
        Assert.assertFalse(MonitorWidgetSpec.isSharedCoreKind(MonitorWidgetSpec.KIND_STORAGE));
        Assert.assertFalse(MonitorWidgetSpec.isSharedCoreKind(MonitorWidgetSpec.KIND_CRAFTING));
        Assert.assertFalse(MonitorWidgetSpec.isSharedCoreKind(MonitorWidgetSpec.KIND_WEB_SURFACE));
        Assert.assertTrue(MonitorWidgetSpec.isSharedCoreKind(MonitorWidgetSpec.KIND_DATA_TABLE));
    }

    private static void assertSource(String metricKey, String dataType, String expected) {
        NBTTagCompound binding = new NBTTagCompound();
        binding.setString("name", metricKey);
        binding.setString("dataType", dataType);
        MonitorWidgetSpec.normalizeBinding(binding, 0, 0, 0);
        Assert.assertEquals(expected, binding.getString("sourceKind"));
    }
}
