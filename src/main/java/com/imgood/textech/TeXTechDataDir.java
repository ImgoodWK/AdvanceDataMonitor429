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
 * Legacy data under {@code config/textech/}, {@code config/advancedatamonitor/}, and
 * {@code TeXTechWebAE/} is moved at startup by {@link TeXTechDataMigration}.
 * </p>
 */
public final class TeXTechDataDir {

    public static final String ROOT_DIR_NAME = "TeXTech";
    public static final String WEBAE_DIR = "WebAE";
    public static final String ASSISTANT_DIR = "Assistant";
    public static final String GRAPPEL_DIR = "Grapple";
    public static final String CARD_BATTLE_DIR = "CardBattle";

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

    /** {@code <instance>/TeXTech/CardBattle/}. */
    public static File cardBattleRoot() {
        return subdir(CARD_BATTLE_DIR);
    }

    /** {@code TeXTech/WebAE/<fileName>}. */
    public static File webAeFile(String fileName) {
        File target = new File(webAeRoot(), fileName);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        return target;
    }

    /** {@code TeXTech/WebAE/<subdirName>/}. */
    public static File webAeDir(String subdirName) {
        File target = new File(webAeRoot(), subdirName);
        if (!target.exists() && !target.mkdirs()) {
            AdvanceDataMonitor.LOG.warn("[TeXTech] Failed to create WebAE dir {}", target.getAbsolutePath());
        }
        return target;
    }

    /** {@code TeXTech/Assistant/<fileName>}. */
    public static File assistantFile(String fileName) {
        File target = new File(assistantRoot(), fileName);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        return target;
    }

    /** {@code TeXTech/Grapple/<fileName>}. */
    public static File grappleFile(String fileName) {
        File target = new File(grappleRoot(), fileName);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
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

    /**
     * Move {@code legacy} to {@code target} when target is missing and legacy exists.
     * Prefers {@link File#renameTo(File)}; falls back to copy + delete source.
     */
    public static boolean moveIfNeeded(File legacy, File target) {
        if (target.exists() || legacy == null || !legacy.exists()) {
            return false;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try {
            if (legacy.isDirectory()) {
                return moveDirIfNeeded(legacy, target);
            }
            if (legacy.renameTo(target)) {
                logMoved(legacy, target);
                return true;
            }
            copyFile(legacy, target);
            if (!legacy.delete()) {
                AdvanceDataMonitor.LOG
                    .warn("[TeXTech] Copied data but failed to delete legacy file {}", legacy.getAbsolutePath());
            }
            logMoved(legacy, target);
            return true;
        } catch (IOException e) {
            AdvanceDataMonitor.LOG.warn("[TeXTech] Failed to migrate data from {}", legacy.getAbsolutePath(), e);
            return false;
        }
    }

    /** Recursively move {@code legacyDir} into {@code targetDir} when target is absent. */
    public static boolean moveDirIfNeeded(File legacyDir, File targetDir) {
        if (targetDir.exists() || legacyDir == null || !legacyDir.isDirectory()) {
            return false;
        }
        if (legacyDir.renameTo(targetDir)) {
            logMoved(legacyDir, targetDir);
            return true;
        }
        try {
            copyDirectory(legacyDir, targetDir);
            deleteDirectory(legacyDir);
            logMoved(legacyDir, targetDir);
            return true;
        } catch (IOException e) {
            AdvanceDataMonitor.LOG
                .warn("[TeXTech] Failed to migrate data directory from {}", legacyDir.getAbsolutePath(), e);
            return false;
        }
    }

    private static void logMoved(File legacy, File target) {
        AdvanceDataMonitor.LOG
            .info("[TeXTech] Migrated data: {} -> {}", legacy.getAbsolutePath(), target.getAbsolutePath());
    }

    private static void deleteDirectory(File dir) throws IOException {
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    deleteDirectory(child);
                } else if (!child.delete()) {
                    throw new IOException("Failed to delete " + child.getAbsolutePath());
                }
            }
        }
        if (!dir.delete()) {
            throw new IOException("Failed to delete " + dir.getAbsolutePath());
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
