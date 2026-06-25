package com.imgood.advancedatamonitor.compat.ae;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.Config;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.relauncher.FMLInjectionData;

/** Detects GTNH pack / AE2 environment and selects {@link AeCompatProfile}. */
public final class GtnhEnvironmentProbe {

    private static final String GTNH_VERSION_DOTFILE = ".gtnh-version";
    private static final String GTNH_VERSION_CONFIG = "config/gtnh/version.txt";
    private static final String NATIVE_FLUID_CELL_CLASS = "appeng.items.storage.ItemBasicFluidStorageCell";

    private GtnhEnvironmentProbe() {}

    public static final class ProbeResult {

        public final AeCompatProfile profile;
        public final AeCompatDetectionSource source;
        public final String detail;

        public ProbeResult(AeCompatProfile profile, AeCompatDetectionSource source, String detail) {
            this.profile = profile;
            this.source = source;
            this.detail = detail == null ? "" : detail;
        }
    }

    public static ProbeResult probe() {
        AeCompatProfile forced = parseConfigOverride(Config.compatAeProfileOverride);
        if (forced != null) {
            return new ProbeResult(forced, AeCompatDetectionSource.CONFIG_OVERRIDE, Config.compatAeProfileOverride);
        }

        String packVersion = readGtnhVersionFile();
        if (packVersion != null && !packVersion.isEmpty()) {
            GtnhVersion parsed = GtnhVersion.parse(packVersion);
            if (parsed.isAtLeast290Beta1()) {
                return new ProbeResult(
                    AeCompatProfile.NATIVE_FLUID,
                    AeCompatDetectionSource.GTNH_VERSION_FILE,
                    packVersion);
            }
            return new ProbeResult(AeCompatProfile.LEGACY, AeCompatDetectionSource.GTNH_VERSION_FILE, packVersion);
        }

        String ae2Version = readModVersion("appliedenergistics2");
        if (ae2Version != null && !ae2Version.isEmpty()) {
            GtnhVersion parsed = GtnhVersion.parse(ae2Version);
            if (parsed.isAe2NativeFluidCapable()) {
                return new ProbeResult(
                    AeCompatProfile.NATIVE_FLUID,
                    AeCompatDetectionSource.AE2_MOD_VERSION,
                    ae2Version);
            }
            return new ProbeResult(AeCompatProfile.LEGACY, AeCompatDetectionSource.AE2_MOD_VERSION, ae2Version);
        }

        if (hasNativeFluidCapability()) {
            return new ProbeResult(
                AeCompatProfile.NATIVE_FLUID,
                AeCompatDetectionSource.CAPABILITY,
                NATIVE_FLUID_CELL_CLASS);
        }

        return new ProbeResult(AeCompatProfile.LEGACY, AeCompatDetectionSource.DEFAULT_LEGACY, "pre-2.9.0-default");
    }

    private static AeCompatProfile parseConfigOverride(String override) {
        if (override == null) {
            return null;
        }
        String normalized = override.trim()
            .toLowerCase();
        if ("legacy".equals(normalized)) {
            return AeCompatProfile.LEGACY;
        }
        if ("native".equals(normalized)) {
            return AeCompatProfile.NATIVE_FLUID;
        }
        return null;
    }

    private static String readGtnhVersionFile() {
        File gameDir = resolveGameDirectory();
        if (gameDir == null) {
            return null;
        }
        String fromDot = readFirstLine(new File(gameDir, GTNH_VERSION_DOTFILE));
        if (fromDot != null && !fromDot.isEmpty()) {
            return fromDot;
        }
        return readFirstLine(new File(gameDir, GTNH_VERSION_CONFIG));
    }

    private static File resolveGameDirectory() {
        try {
            File configDir = Loader.instance()
                .getConfigDir();
            if (configDir != null && configDir.getParentFile() != null) {
                return configDir.getParentFile();
            }
        } catch (Throwable ignored) {
            // fall through
        }
        try {
            return (File) FMLInjectionData.data()[6];
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String readFirstLine(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String line = reader.readLine();
            if (line == null) {
                return null;
            }
            return line.trim();
        } catch (IOException e) {
            AdvanceDataMonitor.LOG
                .debug("[ADM] Could not read GTNH version file {}: {}", file.getAbsolutePath(), e.getMessage());
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private static String readModVersion(String modId) {
        try {
            ModContainer container = Loader.instance()
                .getIndexedModList()
                .get(modId);
            if (container == null) {
                return null;
            }
            return container.getVersion();
        } catch (Throwable ignored) {
            return null;
        }
    }

    static boolean hasNativeFluidCapability() {
        try {
            Class.forName(NATIVE_FLUID_CELL_CLASS, false, GtnhEnvironmentProbe.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
