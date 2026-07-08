package com.imgood.textech;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import cpw.mods.fml.common.Loader;

/**
 * Unified runtime data paths under {@code <instance>/TeXTech/<feature>/}.
 * Forge {@code .cfg} files remain in {@code config/textech/}.
 *
 * <p>
 * On first access, copies legacy data from {@code config/textech/},
 * {@code config/advancedatamonitor/}, and {@code TeXTechWebAE/} without deleting sources.
 * </p>
 */
public final class TeXTechDataDir {

    public static final String ROOT_DIR_NAME = "TeXTech";
    public static final String WEBAE_DIR = "WebAE";
    public static final String ASSISTANT_DIR = "Assistant";
    public static final String GRAPPEL_DIR = "Grapple";

    /** Pre-merge client/server local folder for NEI export and NESQL import. */
    public static final String LEGACY_WEBAE_DIR = "TeXTechWebAE";

    private TeXTechDataDir() {}

    /** Minecraft instance root ({@code .minecraft} on client, server root on dedicated). */
    public static File instanceRoot() {
        try {
            File configDir = Loader.instance()
                .getConfigDir();
            if (configDir != null && configDir.getParentFile() != null) {
                return configDir.getParentFile();
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return new File(".").getAbsoluteFile();
    }

    /** {@code <instance>/TeXTech/}. */
    public static File root() {
        return new File(instanceRoot(), ROOT_DIR_NAME);
    }

    /** {@code <instance>/TeXTech/<name>/}; creates the directory if missing. */
    public static File subdir(String name) {
        File dir = new File(root(), name);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static File webAeRoot() {
        return subdir(WEBAE_DIR);
    }

    public static File assistantRoot() {
        return subdir(ASSISTANT_DIR);
    }

    public static File grappleRoot() {
        return subdir(GRAPPEL_DIR);
    }

    /**
     * {@code TeXTech/WebAE/<fileName>}; migrates from {@code config/textech/},
     * {@code config/advancedatamonitor/}, and {@code TeXTechWebAE/} when the target is absent.
     */
    public static File webAeFile(String fileName) {
        File target = new File(webAeRoot(), fileName);
        if (!target.exists()) {
            migrateFileFromLegacySources(fileName, target, true);
        }
        return target;
    }

    /**
     * {@code TeXTech/WebAE/<subdirName>/}; migrates from the matching {@code config/textech/web-*} tree.
     */
    public static File webAeDir(String subdirName) {
        File target = new File(webAeRoot(), subdirName);
        if (!target.exists()) {
            String legacyConfigName = legacyWebAeDirName(subdirName);
            if (legacyConfigName != null) {
                migrateDirFromLegacySources(legacyConfigName, target);
            } else if (!target.mkdirs() && !target.exists()) {
                AdvanceDataMonitor.LOG.warn("[TeXTech] Failed to create WebAE dir {}", target.getAbsolutePath());
            }
        }
        return target;
    }

    /** Legacy {@code config/textech/web-map-tiles/} for read-only tile fallback. */
    public static File legacyWebMapTilesDir() {
        return new File(legacyConfigDir(), "web-map-tiles");
    }

    /** {@code TeXTech/Assistant/<fileName>}. */
    public static File assistantFile(String fileName) {
        File target = new File(assistantRoot(), fileName);
        if (!target.exists()) {
            migrateFileFromLegacySources(fileName, target, false);
        }
        return target;
    }

    /** {@code TeXTech/Grapple/<fileName>}. */
    public static File grappleFile(String fileName) {
        File target = new File(grappleRoot(), fileName);
        if (!target.exists()) {
            migrateFileFromLegacySources(fileName, target, false);
        }
        return target;
    }

    /** {@code <instance>/TeXTechWebAE/} (pre-merge local data). */
    public static File legacyWebAeRoot() {
        return new File(instanceRoot(), LEGACY_WEBAE_DIR);
    }

    public static File legacyConfigDir() {
        return new File("config", AdvanceDataMonitor.MODID);
    }

    public static File legacyModConfigDir() {
        return new File("config", AdvanceDataMonitor.LEGACY_MODID);
    }

    private static String legacyWebAeDirName(String subdirName) {
        if ("icons".equals(subdirName)) {
            return "web-icons";
        }
        if ("map-tiles".equals(subdirName)) {
            return "web-map-tiles";
        }
        if ("topology".equals(subdirName)) {
            return "web-topology";
        }
        return null;
    }

    private static void migrateFileFromLegacySources(String fileName, File target, boolean includeLegacyWebAe) {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        File fromConfig = new File(legacyConfigDir(), fileName);
        File fromLegacyMod = new File(legacyModConfigDir(), fileName);
        if (migrateIfNeeded(fromConfig, target)) {
            return;
        }
        if (migrateIfNeeded(fromLegacyMod, target)) {
            return;
        }
        if (includeLegacyWebAe) {
            File fromWebAe = new File(legacyWebAeRoot(), fileName);
            migrateIfNeeded(fromWebAe, target);
        }
    }

    private static void migrateDirFromLegacySources(String legacyDirName, File target) {
        File fromConfig = new File(legacyConfigDir(), legacyDirName);
        File fromLegacyMod = new File(legacyModConfigDir(), legacyDirName);
        if (migrateDirIfNeeded(fromConfig, target)) {
            return;
        }
        migrateDirIfNeeded(fromLegacyMod, target);
    }

    /** Copy {@code legacy} to {@code target} when target is missing and legacy exists. */
    public static boolean migrateIfNeeded(File legacy, File target) {
        if (target.exists() || legacy == null || !legacy.exists()) {
            return false;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try {
            if (legacy.isDirectory()) {
                return migrateDirIfNeeded(legacy, target);
            }
            copyFile(legacy, target);
            AdvanceDataMonitor.LOG.info(
                "[TeXTech] Migrated data: {} -> {}",
                legacy.getAbsolutePath(),
                target.getAbsolutePath());
            return true;
        } catch (IOException e) {
            AdvanceDataMonitor.LOG.warn(
                "[TeXTech] Failed to migrate data from {}",
                legacy.getAbsolutePath(),
                e);
            return false;
        }
    }

    /** Recursively copy {@code legacyDir} into {@code targetDir} when target is absent. */
    public static boolean migrateDirIfNeeded(File legacyDir, File targetDir) {
        if (targetDir.exists() || legacyDir == null || !legacyDir.isDirectory()) {
            return false;
        }
        try {
            copyDirectory(legacyDir, targetDir);
            AdvanceDataMonitor.LOG.info(
                "[TeXTech] Migrated data directory: {} -> {}",
                legacyDir.getAbsolutePath(),
                targetDir.getAbsolutePath());
            return true;
        } catch (IOException e) {
            AdvanceDataMonitor.LOG.warn(
                "[TeXTech] Failed to migrate data directory from {}",
                legacyDir.getAbsolutePath(),
                e);
            return false;
        }
    }

    private static void copyFile(File source, File dest) throws IOException {
        FileInputStream in = new FileInputStream(source);
        try {
            FileOutputStream out = new FileOutputStream(dest);
            try {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, read);
                }
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    private static void copyDirectory(File sourceDir, File targetDir) throws IOException {
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Failed to create " + targetDir.getAbsolutePath());
        }
        File[] children = sourceDir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            File destChild = new File(targetDir, child.getName());
            if (child.isDirectory()) {
                copyDirectory(child, destChild);
            } else {
                copyFile(child, destChild);
            }
        }
    }
}
