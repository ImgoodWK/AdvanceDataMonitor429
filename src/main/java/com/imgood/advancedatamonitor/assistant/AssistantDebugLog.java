package com.imgood.advancedatamonitor.assistant;

import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;

public final class AssistantDebugLog {

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    private AssistantDebugLog() {}

    public static synchronized void append(String phase, String message) {
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(
                new java.io.OutputStreamWriter(
                    new FileOutputStream(AssistantDataFiles.dataFile("assistant-dialog-debug.log"), true),
                    "UTF-8"));
            writer.print(TIME_FORMAT.format(new Date()));
            writer.print(" [");
            writer.print(phase == null ? "" : phase);
            writer.print("] ");
            writer.println(
                message == null ? ""
                    : message.replace('\n', ' ')
                        .replace('\r', ' '));
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.debug("Failed to append assistant debug log", e);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }
}
