package com.imgood.textech.monitor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * Shared monitor/WebAE widget contract normalizer.
 *
 * <p>
 * Legacy monitor bindings still persist {@code dataType} and {@code name}. New code prefers
 * {@code kind}/{@code renderType} and {@code metricKey}/{@code title}, but keeps the legacy fields in sync so old
 * worlds and GUIs continue to load.
 * </p>
 */
public final class MonitorWidgetSpec {

    public static final String BUNDLE_FORMAT = "textech-monitor-widget-bundle";
    public static final int BUNDLE_VERSION = 1;

    public static final String KIND_STAT_CARD = "statCard";
    public static final String KIND_PROGRESS_BAR = "progressBar";
    public static final String KIND_GAUGE = "gauge";
    public static final String KIND_LINE_CHART = "lineChart";
    public static final String KIND_BAR_CHART = "barChart";
    public static final String KIND_PIE_CHART = "pieChart";
    public static final String KIND_DATA_TABLE = "dataTable";
    public static final String KIND_CRAFTING = "crafting";
    public static final String KIND_STORAGE = "storage";
    public static final String KIND_WEB_SURFACE = "web_surface";

    public static final String SOURCE_TILE_METRIC = "tile_metric";
    public static final String SOURCE_AE_METRIC = "ae_metric";
    public static final String SOURCE_WIRELESS_EU = "wireless_eu";
    public static final String SOURCE_WIRELESS_STEAM = "wireless_steam";
    public static final String SOURCE_STORAGE_SUMMARY = "storage_summary";
    public static final String SOURCE_GT_SUMMARY = "gt_summary";

    public static final String SERIES_TRANSFORM_DIFFERENCE = "difference";

    public static final String THRESHOLD_KEY = "threshold";

    public static final String PREVIEW_SCALAR = "scalar";
    public static final String PREVIEW_SERIES = "series";
    public static final String PREVIEW_CATEGORIES = "categories";
    public static final String PREVIEW_ROWS = "rows";

    private static final Set<String> SHARED_CORE_KINDS = new HashSet<String>(
        Arrays.asList(
            KIND_STAT_CARD,
            KIND_PROGRESS_BAR,
            KIND_GAUGE,
            KIND_LINE_CHART,
            KIND_BAR_CHART,
            KIND_PIE_CHART,
            KIND_DATA_TABLE));

    private MonitorWidgetSpec() {}

    public static void normalizeBinding(NBTTagCompound nbt, int defaultX, int defaultY, int defaultZ) {
        if (nbt == null) {
            return;
        }
        if (!nbt.hasKey("XYZ")) {
            nbt.setString("XYZ", defaultX + "," + defaultY + "," + defaultZ);
        }
        String legacyDataType = safeString(nbt, "dataType");
        String legacyRenderType = safeString(nbt, "renderType");
        String legacyKind = safeString(nbt, "kind");
        String kind = normalizeKind(
            !legacyKind.isEmpty() ? legacyKind : !legacyRenderType.isEmpty() ? legacyRenderType : legacyDataType);
        if (kind.isEmpty()) {
            kind = KIND_LINE_CHART;
        }
        nbt.setString("kind", kind);

        String renderType = normalizeRenderType(legacyRenderType, kind, legacyDataType);
        nbt.setString("renderType", renderType);

        boolean differenceAlias = isDifferenceAlias(legacyDataType) || isDifferenceAlias(legacyRenderType)
            || isDifferenceAlias(legacyKind);
        boolean chartAlias = isLegacyChartAlias(legacyDataType) || isLegacyChartAlias(legacyRenderType)
            || isLegacyChartAlias(legacyKind);
        if (!nbt.hasKey("dataType") || legacyDataType.isEmpty() || chartAlias) {
            nbt.setString("dataType", legacyDataTypeFromKind(kind));
        }
        if (differenceAlias) {
            nbt.setString("seriesTransform", SERIES_TRANSFORM_DIFFERENCE);
        }

        String metricKey = safeString(nbt, "metricKey");
        if (metricKey.isEmpty()) {
            metricKey = safeString(nbt, "name");
        }
        if (metricKey.isEmpty()) {
            metricKey = defaultMetricKeyForKind(kind);
        }
        nbt.setString("metricKey", metricKey);
        if (!nbt.hasKey("name") || safeString(nbt, "name").isEmpty()) {
            nbt.setString("name", metricKey);
        }

        String title = safeString(nbt, "title");
        if (title.isEmpty()) {
            title = safeString(nbt, "displayName");
        }
        if (title.isEmpty()) {
            title = metricKey;
        }
        nbt.setString("title", title);
        if (!nbt.hasKey("displayName") || safeString(nbt, "displayName").isEmpty()) {
            nbt.setString("displayName", title);
        }

        if (!nbt.hasKey("sourceKind") || safeString(nbt, "sourceKind").isEmpty()) {
            nbt.setString("sourceKind", inferSourceKind(kind, metricKey, legacyDataType));
        }

        if (!nbt.hasKey("targetValue")) {
            nbt.setDouble("targetValue", 0.0D);
        }
        if (!nbt.hasKey("style")) {
            nbt.setString("style", "");
        }
        if (!nbt.hasKey("sortMode")) {
            nbt.setString("sortMode", "default");
        }
        if (!nbt.hasKey("maxRows")) {
            nbt.setInteger("maxRows", 10);
        }
        if (!nbt.hasKey("seriesTransform")) {
            nbt.setString("seriesTransform", "");
        }
        if (!nbt.hasKey("colors")) {
            nbt.setTag("colors", new NBTTagCompound());
        }
        if (!nbt.hasKey("pins")) {
            nbt.setTag("pins", new NBTTagList());
        }
        if (nbt.hasKey("columns") && !nbt.hasKey("columns", 9)) {
            nbt.removeTag("columns");
        }
        if (!nbt.hasKey("revision")) {
            nbt.setInteger("revision", 0);
        }
        normalizeThreshold(nbt);
    }

    public static void normalizeThreshold(NBTTagCompound binding) {
        if (binding == null) {
            return;
        }
        NBTTagCompound threshold;
        if (binding.hasKey(THRESHOLD_KEY, 10)) {
            threshold = binding.getCompoundTag(THRESHOLD_KEY);
        } else {
            threshold = new NBTTagCompound();
        }
        if (!threshold.hasKey("enabled")) {
            threshold.setBoolean("enabled", false);
        }
        String operator = safeString(threshold, "operator");
        if (!MonitorThresholdEvaluator.OPERATOR_LTE.equals(operator)) {
            operator = MonitorThresholdEvaluator.OPERATOR_GTE;
        }
        threshold.setString("operator", operator);
        if (!threshold.hasKey("value") || !isFinite(threshold.getDouble("value"))) {
            threshold.setDouble("value", 0.0D);
        }
        double hysteresis = threshold.hasKey("hysteresis") ? threshold.getDouble("hysteresis") : 0.0D;
        threshold.setDouble("hysteresis", isFinite(hysteresis) ? Math.max(0.0D, hysteresis) : 0.0D);
        if (!threshold.hasKey("outputMin")) {
            threshold.setDouble("outputMin", 0.0D);
        }
        if (!threshold.hasKey("outputMax")) {
            threshold.setDouble("outputMax", 0.0D);
        }
        binding.setTag(THRESHOLD_KEY, threshold);
    }

    public static boolean isSharedCoreKind(String kind) {
        return SHARED_CORE_KINDS.contains(normalizeKind(kind));
    }

    public static String previewTypeForKind(String kind) {
        String normalized = normalizeKind(kind);
        if (KIND_STAT_CARD.equals(normalized) || KIND_PROGRESS_BAR.equals(normalized)
            || KIND_GAUGE.equals(normalized)) {
            return PREVIEW_SCALAR;
        }
        if (KIND_BAR_CHART.equals(normalized) || KIND_PIE_CHART.equals(normalized)) {
            return PREVIEW_CATEGORIES;
        }
        if (KIND_DATA_TABLE.equals(normalized) || KIND_STORAGE.equals(normalized) || KIND_CRAFTING.equals(normalized)) {
            return PREVIEW_ROWS;
        }
        return PREVIEW_SERIES;
    }

    public static String getKind(NBTTagCompound nbt) {
        if (nbt == null) {
            return KIND_LINE_CHART;
        }
        String kind = safeString(nbt, "kind");
        if (kind.isEmpty()) {
            kind = safeString(nbt, "renderType");
        }
        if (kind.isEmpty()) {
            kind = safeString(nbt, "dataType");
        }
        return normalizeKind(kind);
    }

    public static String getSourceKind(NBTTagCompound nbt) {
        String sourceKind = safeString(nbt, "sourceKind");
        if (!sourceKind.isEmpty()) {
            return sourceKind;
        }
        return inferSourceKind(getKind(nbt), getMetricKey(nbt), safeString(nbt, "dataType"));
    }

    public static String getMetricKey(NBTTagCompound nbt) {
        String metricKey = safeString(nbt, "metricKey");
        if (!metricKey.isEmpty()) {
            return metricKey;
        }
        metricKey = safeString(nbt, "name");
        return metricKey.isEmpty() ? defaultMetricKeyForKind(getKind(nbt)) : metricKey;
    }

    public static String getTitle(NBTTagCompound nbt) {
        String title = safeString(nbt, "title");
        if (!title.isEmpty()) {
            return title;
        }
        title = safeString(nbt, "displayName");
        return title.isEmpty() ? getMetricKey(nbt) : title;
    }

    public static String getSeriesTransform(NBTTagCompound nbt) {
        return safeString(nbt, "seriesTransform");
    }

    public static double getTargetValue(NBTTagCompound nbt) {
        return nbt != null && nbt.hasKey("targetValue") ? nbt.getDouble("targetValue") : 0.0D;
    }

    public static int getRevision(NBTTagCompound nbt) {
        return nbt != null && nbt.hasKey("revision") ? Math.max(0, nbt.getInteger("revision")) : 0;
    }

    public static String normalizeKind(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return "";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if ("line".equals(lower) || KIND_LINE_CHART.toLowerCase(Locale.ROOT)
            .equals(lower) || "diffrence".equals(lower) || "difference".equals(lower)) {
            return KIND_LINE_CHART;
        }
        if ("bar".equals(lower) || "bar3d".equals(lower)
            || "waterfall".equals(lower)
            || KIND_BAR_CHART.toLowerCase(Locale.ROOT)
                .equals(lower)) {
            return KIND_BAR_CHART;
        }
        if ("pie".equals(lower) || KIND_PIE_CHART.toLowerCase(Locale.ROOT)
            .equals(lower)) {
            return KIND_PIE_CHART;
        }
        if ("table".equals(lower) || KIND_DATA_TABLE.toLowerCase(Locale.ROOT)
            .equals(lower)) {
            return KIND_DATA_TABLE;
        }
        if ("stat".equals(lower) || "scalar".equals(lower)
            || KIND_STAT_CARD.toLowerCase(Locale.ROOT)
                .equals(lower)) {
            return KIND_STAT_CARD;
        }
        if ("progress".equals(lower) || KIND_PROGRESS_BAR.toLowerCase(Locale.ROOT)
            .equals(lower)) {
            return KIND_PROGRESS_BAR;
        }
        if (KIND_GAUGE.toLowerCase(Locale.ROOT)
            .equals(lower)) {
            return KIND_GAUGE;
        }
        if (KIND_CRAFTING.equals(lower)) {
            return KIND_CRAFTING;
        }
        if (KIND_STORAGE.equals(lower)) {
            return KIND_STORAGE;
        }
        if (KIND_WEB_SURFACE.equals(lower) || "webae_dashboard".equals(lower)) {
            return KIND_WEB_SURFACE;
        }
        return value;
    }

    public static String legacyDataTypeFromKind(String kind) {
        String normalized = normalizeKind(kind);
        if (KIND_LINE_CHART.equals(normalized)) {
            return "line";
        }
        return normalized;
    }

    private static String normalizeRenderType(String renderType, String kind, String dataType) {
        String explicit = normalizeKind(renderType);
        if (!explicit.isEmpty()) {
            if (KIND_LINE_CHART.equals(explicit)) {
                return "line";
            }
            return explicit;
        }
        if (KIND_LINE_CHART.equals(kind)) {
            return "line";
        }
        if (safeString(dataType).equals("webae_dashboard")) {
            return KIND_WEB_SURFACE;
        }
        if (!normalizeKind(dataType).isEmpty()) {
            String normalized = normalizeKind(dataType);
            return KIND_LINE_CHART.equals(normalized) ? "line" : normalized;
        }
        return kind;
    }

    private static String inferSourceKind(String kind, String metricKey, String dataType) {
        String normalizedKind = normalizeKind(kind);
        String lowerMetric = metricKey == null ? "" : metricKey.toLowerCase(Locale.ROOT);
        if (lowerMetric.startsWith("steam")) {
            return SOURCE_WIRELESS_STEAM;
        }
        if (lowerMetric.startsWith("eu")) {
            return SOURCE_WIRELESS_EU;
        }
        if (KIND_STORAGE.equals(normalizedKind) || SOURCE_STORAGE_SUMMARY.equals(dataType)
            || lowerMetric.startsWith("top")
            || lowerMetric.contains("storage")
            || lowerMetric.contains("item")
            || lowerMetric.contains("fluid")) {
            return SOURCE_STORAGE_SUMMARY;
        }
        if (KIND_CRAFTING.equals(normalizedKind)) {
            return SOURCE_AE_METRIC;
        }
        if (lowerMetric.startsWith("gt") || lowerMetric.contains("machine")) {
            return SOURCE_GT_SUMMARY;
        }
        return SOURCE_TILE_METRIC;
    }

    private static String defaultMetricKeyForKind(String kind) {
        if (KIND_STORAGE.equals(kind)) {
            return "storageItems";
        }
        if (KIND_CRAFTING.equals(kind)) {
            return "craftingStats";
        }
        if (KIND_WEB_SURFACE.equals(kind)) {
            return "webDashboard";
        }
        return "testRandomData";
    }

    private static boolean isDifferenceAlias(String value) {
        return "diffrence".equalsIgnoreCase(safeString(value)) || "difference".equalsIgnoreCase(safeString(value));
    }

    private static boolean isLegacyChartAlias(String value) {
        String lower = safeString(value).toLowerCase(Locale.ROOT);
        return "line".equals(lower) || "bar".equals(lower)
            || "bar3d".equals(lower)
            || "waterfall".equals(lower)
            || "pie".equals(lower)
            || "table".equals(lower)
            || "stat".equals(lower)
            || "scalar".equals(lower)
            || "progress".equals(lower)
            || isDifferenceAlias(lower);
    }

    private static String safeString(NBTTagCompound nbt, String key) {
        return nbt != null && nbt.hasKey(key) ? safeString(nbt.getString(key)) : "";
    }

    private static String safeString(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
