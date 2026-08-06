package com.imgood.textech.webae.quest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.assistant.AssistantDataFiles;
import com.imgood.textech.webae.dto.QuestSubmitResultDto;

/**
 * Append-only audit log for Web quest submissions.
 */
public final class QuestSubmitLog {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();
    private static final String FILE_NAME = "quest-submit-log.json";
    private static final int MAX_ENTRIES = 500;

    private QuestSubmitLog() {}

    public static void append(String ownerUuid, QuestSubmitResultDto result) {
        if (result == null) {
            return;
        }
        try {
            File file = AssistantDataFiles.dataFile(FILE_NAME);
            List<Entry> entries = readEntries(file);
            Entry entry = new Entry();
            entry.atMs = System.currentTimeMillis();
            entry.ownerUuid = ownerUuid;
            entry.questId = result.questId;
            entry.success = result.success;
            entry.dryRun = result.dryRun;
            entry.message = result.message;
            entry.newState = result.newState;
            entries.add(entry);
            while (entries.size() > MAX_ENTRIES) {
                entries.remove(0);
            }
            LogFile doc = new LogFile();
            doc.entries = entries;
            if (file.getParentFile() != null) {
                file.getParentFile()
                    .mkdirs();
            }
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
                writer.write(GSON.toJson(doc));
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE Quest] submit log write failed: {}", e.toString());
        }
    }

    private static List<Entry> readEntries(File file) {
        if (file == null || !file.exists()) {
            return new ArrayList<Entry>();
        }
        try (java.io.InputStreamReader reader = new java.io.InputStreamReader(
            new java.io.FileInputStream(file),
            "UTF-8")) {
            LogFile doc = GSON.fromJson(reader, LogFile.class);
            if (doc != null && doc.entries != null) {
                return doc.entries;
            }
        } catch (Exception ignored) {}
        return new ArrayList<Entry>();
    }

    private static final class LogFile {

        List<Entry> entries;
    }

    private static final class Entry {

        long atMs;
        String ownerUuid;
        String questId;
        boolean success;
        boolean dryRun;
        String message;
        String newState;
    }
}
