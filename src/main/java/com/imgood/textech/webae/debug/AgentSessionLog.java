package com.imgood.textech.webae.debug;

import java.io.FileWriter;

/**
 * Temporary agent debug session logger (NDJSON). Remove after debug session 03576a.
 */
public final class AgentSessionLog {

    private static final String LOG_PATH = "D:\\gtnhcode\\AdvanceDataMonitor429\\debug-03576a.log";
    private static final String SESSION_ID = "03576a";
    private static final String RUN_ID = "post-fix";

    private AgentSessionLog() {}

    public static void log(String hypothesisId, String location, String message, String dataJson) {
        // #region agent log
        try {
            long ts = System.currentTimeMillis();
            String data = dataJson != null && !dataJson.isEmpty() ? dataJson : "{}";
            String line = "{\"sessionId\":\"" + SESSION_ID + "\",\"hypothesisId\":\"" + escape(hypothesisId)
                + "\",\"location\":\"" + escape(location) + "\",\"message\":\"" + escape(message)
                + "\",\"data\":" + data + ",\"timestamp\":" + ts + ",\"runId\":\"" + RUN_ID + "\"}\n";
            FileWriter fw = new FileWriter(LOG_PATH, true);
            fw.write(line);
            fw.close();
        } catch (Exception ignored) {
            // debug-only
        }
        // #endregion
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n");
    }
}
