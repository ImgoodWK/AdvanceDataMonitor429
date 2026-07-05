package com.imgood.textech.assistant;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import com.imgood.textech.AdvanceDataMonitor;

public final class AssistantDataFiles {

    private AssistantDataFiles() {}

    public static File dataFile(String name) {
        File dir = configDir();
        File file = new File(dir, name);
        if (!file.exists()) {
            File legacyFile = new File(legacyConfigDir(), name);
            migrateIfNeeded(legacyFile, file);
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return file;
    }

    private static File configDir() {
        return new File("config", AdvanceDataMonitor.MODID);
    }

    private static File legacyConfigDir() {
        return new File("config", AdvanceDataMonitor.LEGACY_MODID);
    }

    private static void migrateIfNeeded(File legacyFile, File targetFile) {
        if (targetFile.exists() || !legacyFile.exists()) {
            return;
        }
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileInputStream in = new FileInputStream(legacyFile);
            FileOutputStream out = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            AdvanceDataMonitor.LOG.info(
                "[TeXTech] Migrated config data: {} -> {}",
                legacyFile.getAbsolutePath(),
                targetFile.getAbsolutePath());
        } catch (Exception e) {
            AdvanceDataMonitor.LOG
                .warn("[TeXTech] Failed to migrate config data from {}", legacyFile.getAbsolutePath(), e);
        }
    }
}
