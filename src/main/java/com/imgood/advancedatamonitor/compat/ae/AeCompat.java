package com.imgood.advancedatamonitor.compat.ae;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.compat.ae.legacy.LegacyAeCellStatsAdapter;
import com.imgood.advancedatamonitor.compat.ae.legacy.LegacyAeFluidCellConfigFactory;
import com.imgood.advancedatamonitor.compat.ae.legacy.LegacyAeFluidMarkerAdapter;
import com.imgood.advancedatamonitor.compat.ae.legacy.LegacyAePatternFluidAdapter;
import com.imgood.advancedatamonitor.compat.ae.native_.NativeAeCellStatsAdapter;
import com.imgood.advancedatamonitor.compat.ae.native_.NativeAeFluidCellConfigFactory;
import com.imgood.advancedatamonitor.compat.ae.native_.NativeAeFluidMarkerAdapter;
import com.imgood.advancedatamonitor.compat.ae.native_.NativeAePatternFluidAdapter;

/** Runtime AE2 compatibility facade; initialized once during postInit. */
public final class AeCompat {

    private static AeCompatProfile profile = AeCompatProfile.LEGACY;
    private static AeCompatDetectionSource detectionSource = AeCompatDetectionSource.DEFAULT_LEGACY;
    private static String detectionDetail = "";
    private static AeCellStatsAdapter cellStats = LegacyAeCellStatsAdapter.INSTANCE;
    private static AeFluidMarkerAdapter fluidMarkers = LegacyAeFluidMarkerAdapter.INSTANCE;
    private static AePatternFluidAdapter patternFluids = LegacyAePatternFluidAdapter.INSTANCE;
    private static AeFluidCellConfigFactory fluidCellConfig = LegacyAeFluidCellConfigFactory.INSTANCE;
    private static boolean initialized = false;

    private AeCompat() {}

    public static void init() {
        if (initialized) {
            return;
        }
        GtnhEnvironmentProbe.ProbeResult result = GtnhEnvironmentProbe.probe();
        profile = result.profile;
        detectionSource = result.source;
        detectionDetail = result.detail;
        bindAdapters(profile);
        initialized = true;
        AdvanceDataMonitor.LOG
            .info("[ADM] AE compat profile={} (source={}, detail={})", profile, detectionSource, detectionDetail);
    }

    private static void bindAdapters(AeCompatProfile selected) {
        if (selected == AeCompatProfile.NATIVE_FLUID) {
            cellStats = NativeAeCellStatsAdapter.INSTANCE;
            fluidMarkers = NativeAeFluidMarkerAdapter.INSTANCE;
            patternFluids = NativeAePatternFluidAdapter.INSTANCE;
            fluidCellConfig = NativeAeFluidCellConfigFactory.INSTANCE;
            return;
        }
        cellStats = LegacyAeCellStatsAdapter.INSTANCE;
        fluidMarkers = LegacyAeFluidMarkerAdapter.INSTANCE;
        patternFluids = LegacyAePatternFluidAdapter.INSTANCE;
        fluidCellConfig = LegacyAeFluidCellConfigFactory.INSTANCE;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static AeCompatProfile profile() {
        return profile;
    }

    public static AeCompatDetectionSource detectionSource() {
        return detectionSource;
    }

    public static String detectionDetail() {
        return detectionDetail;
    }

    public static boolean isNativeFluid() {
        return profile == AeCompatProfile.NATIVE_FLUID;
    }

    public static AeCellStatsAdapter cells() {
        return cellStats;
    }

    public static AeFluidMarkerAdapter fluidMarkers() {
        return fluidMarkers;
    }

    public static AePatternFluidAdapter patternFluids() {
        return patternFluids;
    }

    public static AeFluidCellConfigFactory fluidCellConfig() {
        return fluidCellConfig;
    }
}
