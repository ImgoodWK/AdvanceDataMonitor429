package com.imgood.textech.webae.debug;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.utils.ModLogFiles;

/**
 * Per-feature debug logging for the WebAE console. Each feature writes to its
 * own {@code logs/textech/webae-<feature>.log} file, gated by the corresponding
 * {@code [debug] webaeXxx} switch in {@code textech.cfg} (all default false).
 *
 * <p>
 * Usage:
 * 
 * <pre>
 * {@code
 *   if (WebAeDebugLog.isEnabled(WebAeFeature.ICONS)) {
 *       WebAeDebugLog.info(WebAeFeature.ICONS, "served icon {} in {}ms", itemId, elapsed);
 *   }
 * }
 * </pre>
 *
 * <p>
 * Critical errors should still go through {@code AdvanceDataMonitor.LOG}
 * unconditionally; this class is for verbose diagnostic trace only.
 * </p>
 */
public final class WebAeDebugLog {

    /** WebAE debug feature categories, each maps to its own log file + config gate. */
    public enum Feature {

        ICONS("webae-icons", "icons"),
        CHAT("webae-chat", "chat"),
        DASHBOARD("webae-dashboard", "dashboard"),
        SYNTHESIS("webae-synthesis", "synthesis"),
        PATTERNS("webae-patterns", "patterns"),
        PERF("webae-perf", "perf");

        private final String logFileName;
        private final String tag;

        Feature(String logFileName, String tag) {
            this.logFileName = logFileName;
            this.tag = tag;
        }

        public String logFileName() {
            return logFileName;
        }

        public String tag() {
            return tag;
        }
    }

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    private WebAeDebugLog() {}

    public static boolean isEnabled(Feature feature) {
        switch (feature) {
            case ICONS:
                return Config.webDebugIcons;
            case CHAT:
                return Config.webDebugChat;
            case DASHBOARD:
                return Config.webDebugDashboard;
            case SYNTHESIS:
                return Config.webDebugSynthesis;
            case PATTERNS:
                return Config.webDebugPatterns;
            case PERF:
                return Config.webDebugPerf;
            default:
                return false;
        }
    }

    /**
     * Always append a line for hard slow-path events (tick/HTTP thresholds),
     * even when the feature debug switch is off.
     */
    public static void infoAlways(Feature feature, String message, Object... args) {
        appendLine(feature, "INFO", message, args);
    }

    public static String logFilePath(Feature feature) {
        return ModLogFiles.modLogFile(feature.logFileName)
            .getPath();
    }

    public static void info(Feature feature, String message, Object... args) {
        if (!isEnabled(feature)) {
            return;
        }
        appendLine(feature, "INFO", message, args);
    }

    public static void warn(Feature feature, String message, Object... args) {
        if (!isEnabled(feature)) {
            return;
        }
        appendLine(feature, "WARN", message, args);
    }

    private static synchronized void appendLine(Feature feature, String level, String message, Object... args) {
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(
                new OutputStreamWriter(
                    new FileOutputStream(ModLogFiles.modLogFile(feature.logFileName), true),
                    "UTF-8"));
            writer.print(TIME_FORMAT.format(new Date()));
            writer.print(" [");
            writer.print(level);
            writer.print("] [WebAE/");
            writer.print(feature.tag());
            writer.print("] ");
            writer.println(format(message, args));
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.debug("Failed to append webae debug log", e);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private static String format(String message, Object... args) {
        if (message == null) {
            return "";
        }
        if (args == null || args.length == 0) {
            return message;
        }
        StringBuilder builder = new StringBuilder(message.length() + 32);
        int argIndex = 0;
        int cursor = 0;
        while (cursor < message.length()) {
            int placeholder = message.indexOf("{}", cursor);
            if (placeholder < 0 || argIndex >= args.length) {
                builder.append(message.substring(cursor));
                break;
            }
            builder.append(message, cursor, placeholder);
            builder.append(String.valueOf(args[argIndex++]));
            cursor = placeholder + 2;
        }
        return builder.toString();
    }
}
