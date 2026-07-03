package com.imgood.textech.utils;

import java.io.File;

import com.imgood.textech.AdvanceDataMonitor;

/**
 * Paths for TeXTech-owned log files (under {@code logs/textech/}).
 */
public final class ModLogFiles {

    private ModLogFiles() {}

    public static File modLogFile(String fileName) {
        File dir = new File("logs", AdvanceDataMonitor.MODID);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, fileName);
    }
}
