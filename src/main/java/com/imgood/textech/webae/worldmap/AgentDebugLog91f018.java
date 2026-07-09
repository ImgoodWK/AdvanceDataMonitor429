package com.imgood.textech.webae.worldmap;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Debug session 91f018 NDJSON logger (temporary instrumentation).
 */
public final class AgentDebugLog91f018 {

    private static final String SESSION = "91f018";
    private static final int MAX_LINES = 200;
    private static int lineCount;

    private AgentDebugLog91f018() {}

    public static void log(String hypothesisId, String location, String message, String dataJson) {
        if (lineCount >= MAX_LINES) {
            return;
        }
        lineCount++;
        long ts = System.currentTimeMillis();
        String line = "{\"sessionId\":\"" + SESSION + "\",\"hypothesisId\":\"" + esc(hypothesisId)
            + "\",\"location\":\"" + esc(location) + "\",\"message\":\"" + esc(message) + "\",\"data\":"
            + (dataJson != null ? dataJson : "{}") + ",\"timestamp\":" + ts + "}";
        FileWriter fw = null;
        try {
            fw = new FileWriter(resolveLogFile(), true);
            fw.write(line);
            fw.write('\n');
        } catch (IOException ignored) {
        } finally {
            if (fw != null) {
                try {
                    fw.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private static File resolveLogFile() {
        File cwd = new File(".").getAbsoluteFile();
        if ("run".equals(cwd.getName()) && cwd.getParentFile() != null) {
            return new File(cwd.getParentFile(), "debug-91f018.log");
        }
        return new File(cwd, "debug-91f018.log");
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
