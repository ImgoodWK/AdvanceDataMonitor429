package com.imgood.textech.webae.console;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.TeXTechDataDir;
import com.imgood.textech.webae.util.AsyncJsonFileSaver;

/**
 * Bounded, server-side persistence for WebAE command presets and audit history.
 * File serialization and disk I/O are both kept off the Minecraft tick thread.
 */
public final class AdminConsoleStore {

    public static final int MAX_PRESETS = 64;
    public static final int MAX_HISTORY = 40;
    public static final int MAX_OUTPUT_LINES = 24;
    public static final int MAX_OUTPUT_LINE_LENGTH = 256;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AdminConsoleStore INSTANCE = new AdminConsoleStore();
    private static final AsyncJsonFileSaver FILE_SAVER = new AsyncJsonFileSaver("AdminConsole");
    private static final ScheduledExecutorService SAVE_PREPARER = Executors.newSingleThreadScheduledExecutor(
        new java.util.concurrent.ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "WebAE-AdminConsole-SavePrepare");
                thread.setDaemon(true);
                return thread;
            }
        });
    private static final AtomicBoolean SAVE_QUEUED = new AtomicBoolean(false);
    private static final AtomicBoolean SAVE_DIRTY = new AtomicBoolean(false);

    private final List<CommandPreset> presets = new ArrayList<CommandPreset>();
    private final List<CommandAuditEntry> history = new ArrayList<CommandAuditEntry>();
    private boolean loaded;

    private AdminConsoleStore() {}

    public static AdminConsoleStore instance() {
        return INSTANCE;
    }

    public synchronized List<CommandPreset> presets() {
        ensureLoaded();
        List<CommandPreset> copy = new ArrayList<CommandPreset>(presets.size());
        for (CommandPreset preset : presets) copy.add(copyPreset(preset));
        Collections.sort(copy, new Comparator<CommandPreset>() {
            @Override
            public int compare(CommandPreset left, CommandPreset right) {
                return left.label.compareToIgnoreCase(right.label);
            }
        });
        return copy;
    }

    public synchronized CommandPreset savePreset(
        String id,
        String label,
        String command,
        String description,
        String actorName) {
        ensureLoaded();
        String cleanLabel = requireText(label, "Preset name", 64);
        String cleanCommand = AdminCommandService.normalizeCommand(command);
        String cleanDescription = safeText(description, 200);
        long now = System.currentTimeMillis();
        CommandPreset target = null;
        if (id != null && !id.trim().isEmpty()) {
            for (CommandPreset preset : presets) {
                if (id.trim().equals(preset.id)) {
                    target = preset;
                    break;
                }
            }
            if (target == null) throw new IllegalArgumentException("Preset not found.");
        } else {
            if (presets.size() >= MAX_PRESETS) {
                throw new IllegalArgumentException("At most " + MAX_PRESETS + " presets may be saved.");
            }
            target = new CommandPreset();
            target.id = UUID.randomUUID().toString();
            target.createdAt = now;
            presets.add(target);
        }
        target.label = cleanLabel;
        target.command = cleanCommand;
        target.description = cleanDescription;
        target.updatedAt = now;
        target.updatedBy = safeText(actorName, 64);
        CommandPreset result = copyPreset(target);
        requestSave();
        return result;
    }

    public synchronized boolean deletePreset(String id) {
        ensureLoaded();
        if (id == null || id.isEmpty()) return false;
        for (int i = 0; i < presets.size(); i++) {
            if (id.equals(presets.get(i).id)) {
                presets.remove(i);
                requestSave();
                return true;
            }
        }
        return false;
    }

    public synchronized CommandAuditEntry createQueued(String command, String actorUuid, String actorName) {
        ensureLoaded();
        CommandAuditEntry entry = new CommandAuditEntry();
        entry.id = UUID.randomUUID().toString();
        entry.command = command;
        entry.actorUuid = safeText(actorUuid, 64);
        entry.actorName = safeText(actorName, 64);
        entry.status = "queued";
        entry.createdAt = System.currentTimeMillis();
        entry.output = new ArrayList<String>();
        history.add(0, entry);
        trimHistory();
        requestSave();
        return copyEntry(entry, true);
    }

    public synchronized CommandAuditEntry complete(
        String id,
        String status,
        int affected,
        long startedAt,
        long completedAt,
        List<String> output,
        String error) {
        ensureLoaded();
        CommandAuditEntry entry = findEntry(id);
        if (entry == null) return null;
        entry.status = status == null ? "failed" : status;
        entry.affected = affected;
        entry.startedAt = startedAt;
        entry.completedAt = completedAt;
        entry.durationMs = completedAt > startedAt ? completedAt - startedAt : 0L;
        entry.output = boundedOutput(output);
        entry.outputTruncated = output != null && output.size() > entry.output.size();
        entry.error = safeText(error, 500);
        requestSave();
        return copyEntry(entry, true);
    }

    public synchronized List<CommandAuditEntry> historySummaries() {
        ensureLoaded();
        List<CommandAuditEntry> copy = new ArrayList<CommandAuditEntry>(history.size());
        for (CommandAuditEntry entry : history) copy.add(copyEntry(entry, false));
        return copy;
    }

    public synchronized CommandAuditEntry historyEntry(String id) {
        ensureLoaded();
        CommandAuditEntry entry = findEntry(id);
        return entry == null ? null : copyEntry(entry, true);
    }

    public synchronized void clearHistory() {
        ensureLoaded();
        for (int i = history.size() - 1; i >= 0; i--) {
            CommandAuditEntry entry = history.get(i);
            if (!"queued".equals(entry.status)) history.remove(i);
        }
        requestSave();
    }

    private synchronized PersistedState snapshot() {
        ensureLoaded();
        PersistedState state = new PersistedState();
        state.version = 1;
        state.presets = new ArrayList<CommandPreset>(presets.size());
        for (CommandPreset preset : presets) state.presets.add(copyPreset(preset));
        state.history = new ArrayList<CommandAuditEntry>(history.size());
        for (CommandAuditEntry entry : history) state.history.add(copyEntry(entry, true));
        return state;
    }

    private void requestSave() {
        SAVE_DIRTY.set(true);
        if (!SAVE_QUEUED.compareAndSet(false, true)) return;
        SAVE_PREPARER.schedule(new Runnable() {
            @Override
            public void run() {
                try {
                    SAVE_DIRTY.set(false);
                    FILE_SAVER.schedule(snapshot(), storeFile());
                } finally {
                    SAVE_QUEUED.set(false);
                    if (SAVE_DIRTY.get()) requestSave();
                }
            }
        }, 250L, TimeUnit.MILLISECONDS);
    }

    private synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        File file = storeFile();
        if (!file.isFile()) return;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            PersistedState state = GSON.fromJson(reader, PersistedState.class);
            if (state != null && state.presets != null) {
                for (CommandPreset preset : state.presets) {
                    if (isUsablePreset(preset) && presets.size() < MAX_PRESETS) presets.add(preset);
                }
            }
            if (state != null && state.history != null) {
                for (CommandAuditEntry entry : state.history) {
                    if (entry != null && entry.id != null && !entry.id.isEmpty()) {
                        entry.output = boundedOutput(entry.output);
                        history.add(entry);
                        if (history.size() >= MAX_HISTORY) break;
                    }
                }
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to load admin console store: {}", e.getMessage());
        } finally {
            if (reader != null) try {
                reader.close();
            } catch (Exception ignored) {}
        }
    }

    private CommandAuditEntry findEntry(String id) {
        if (id == null || id.isEmpty()) return null;
        for (CommandAuditEntry entry : history) if (id.equals(entry.id)) return entry;
        return null;
    }

    private void trimHistory() {
        while (history.size() > MAX_HISTORY) history.remove(history.size() - 1);
    }

    private static List<String> boundedOutput(List<String> raw) {
        List<String> result = new ArrayList<String>();
        if (raw == null || raw.isEmpty()) return result;
        int start = Math.max(0, raw.size() - MAX_OUTPUT_LINES);
        for (int i = start; i < raw.size(); i++) {
            String line = safeText(raw.get(i), MAX_OUTPUT_LINE_LENGTH);
            if (!line.isEmpty()) result.add(line);
        }
        return result;
    }

    private static boolean isUsablePreset(CommandPreset preset) {
        return preset != null && preset.id != null && !preset.id.isEmpty()
            && preset.label != null && !preset.label.isEmpty()
            && preset.command != null && !preset.command.isEmpty();
    }

    private static String requireText(String value, String label, int maxLength) {
        String clean = safeText(value, maxLength);
        if (clean.isEmpty()) throw new IllegalArgumentException(label + " is required.");
        return clean;
    }

    static String safeText(String value, int maxLength) {
        if (value == null) return "";
        String clean = value.replace('\r', ' ').replace('\n', ' ').trim();
        return clean.length() <= maxLength ? clean : clean.substring(0, maxLength);
    }

    private static CommandPreset copyPreset(CommandPreset source) {
        CommandPreset copy = new CommandPreset();
        copy.id = source.id;
        copy.label = source.label;
        copy.command = source.command;
        copy.description = source.description;
        copy.createdAt = source.createdAt;
        copy.updatedAt = source.updatedAt;
        copy.updatedBy = source.updatedBy;
        return copy;
    }

    private static CommandAuditEntry copyEntry(CommandAuditEntry source, boolean includeOutput) {
        CommandAuditEntry copy = new CommandAuditEntry();
        copy.id = source.id;
        copy.command = source.command;
        copy.actorUuid = source.actorUuid;
        copy.actorName = source.actorName;
        copy.status = source.status;
        copy.createdAt = source.createdAt;
        copy.startedAt = source.startedAt;
        copy.completedAt = source.completedAt;
        copy.durationMs = source.durationMs;
        copy.affected = source.affected;
        copy.outputTruncated = source.outputTruncated;
        copy.error = source.error;
        if (includeOutput) {
            copy.output = source.output == null ? new ArrayList<String>() : new ArrayList<String>(source.output);
        } else {
            copy.output = null;
            copy.outputPreview = source.error != null && !source.error.isEmpty()
                ? source.error : firstOutputLine(source.output);
        }
        return copy;
    }

    private static String firstOutputLine(List<String> lines) {
        return lines == null || lines.isEmpty() ? "" : safeText(lines.get(0), 160);
    }

    private static File storeFile() {
        return TeXTechDataDir.webAeFile("admin-console.json");
    }

    public static final class CommandPreset {
        public String id;
        public String label;
        public String command;
        public String description;
        public long createdAt;
        public long updatedAt;
        public String updatedBy;
    }

    public static final class CommandAuditEntry {
        public String id;
        public String command;
        public String actorUuid;
        public String actorName;
        public String status;
        public long createdAt;
        public long startedAt;
        public long completedAt;
        public long durationMs;
        public int affected;
        public List<String> output;
        public String outputPreview;
        public boolean outputTruncated;
        public String error;
    }

    private static final class PersistedState {
        int version = 1;
        List<CommandPreset> presets;
        List<CommandAuditEntry> history;
    }
}
