package com.imgood.textech.utils;

import java.io.File;

/**
 * Paths for AdvanceDataMonitor-owned log files (under {@code logs/advancedatamonitor/}).
 */
public final class ModLogFiles {

    private ModLogFiles() {}

    public static File modLogFile(String fileName) {
        File dir = new File("logs/advancedatamonitor");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, fileName);
    }
}
