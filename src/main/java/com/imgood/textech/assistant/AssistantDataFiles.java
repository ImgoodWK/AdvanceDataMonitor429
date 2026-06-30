package com.imgood.textech.assistant;

import java.io.File;

public final class AssistantDataFiles {

    private AssistantDataFiles() {}

    public static File dataFile(String name) {
        File dir = new File("config/advancedatamonitor");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, name);
    }
}
