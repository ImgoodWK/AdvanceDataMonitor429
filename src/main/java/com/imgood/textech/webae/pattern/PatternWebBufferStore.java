package com.imgood.textech.webae.pattern;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.TeXTechDataDir;
import com.imgood.textech.webae.dto.PatternDto.PatternItemEntry;

/**
 * Persistent, WebAE-only holding area for physical encoded patterns.
 *
 * <p>
 * Moving an interface pattern into this store removes it from the interface only after the buffer file is safely
 * written. Placing it reverses that operation without consuming another blank pattern. Entries are isolated by AE
 * owner and network and bounded to avoid an unbounded runtime data file.
 * </p>
 */
public final class PatternWebBufferStore {

    private static final PatternWebBufferStore INSTANCE = new PatternWebBufferStore();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();
    private static final int MAX_ENTRIES_PER_NETWORK = 54;
    private static final String FILE_NAME = "web-pattern-buffer.json";

    private final List<Entry> entries = new ArrayList<Entry>();
    private boolean loaded;

    private PatternWebBufferStore() {}

    public static PatternWebBufferStore instance() {
        return INSTANCE;
    }

    public synchronized List<Entry> list(String ownerUuid, int networkId) {
        ensureLoaded();
        List<Entry> result = new ArrayList<Entry>();
        for (Entry entry : entries) {
            if (entry != null && ownerUuid.equals(entry.ownerUuid) && entry.networkId == networkId) {
                result.add(entry.copy());
            }
        }
        Collections.sort(result, new Comparator<Entry>() {

            @Override
            public int compare(Entry a, Entry b) {
                return a.createdAt < b.createdAt ? -1 : a.createdAt == b.createdAt ? 0 : 1;
            }
        });
        return result;
    }

    public synchronized Entry get(String ownerUuid, int networkId, String id) {
        ensureLoaded();
        Entry entry = find(ownerUuid, networkId, id);
        return entry != null ? entry.copy() : null;
    }

    /** Add and persist before the caller clears the source interface slot. */
    public synchronized Entry add(String ownerUuid, int networkId, String encodedNbt, String sourceInterfaceName,
        int sourceSlot, boolean crafting, List<PatternItemEntry> outputs) {
        ensureLoaded();
        int count = 0;
        for (Entry entry : entries) {
            if (entry != null && ownerUuid.equals(entry.ownerUuid) && entry.networkId == networkId) count++;
        }
        if (count >= MAX_ENTRIES_PER_NETWORK) return null;

        Entry entry = new Entry();
        entry.id = UUID.randomUUID()
            .toString();
        entry.ownerUuid = ownerUuid;
        entry.networkId = networkId;
        entry.encodedNbt = encodedNbt;
        entry.sourceInterfaceName = sourceInterfaceName != null ? sourceInterfaceName : "";
        entry.sourceSlot = sourceSlot;
        entry.crafting = crafting;
        entry.createdAt = System.currentTimeMillis();
        if (outputs != null) entry.outputs.addAll(outputs);
        entries.add(entry);
        if (!save()) {
            entries.remove(entry);
            return null;
        }
        return entry.copy();
    }

    /** Remove and persist after the caller has placed the pattern in a target slot. */
    public synchronized Entry remove(String ownerUuid, int networkId, String id) {
        ensureLoaded();
        Entry entry = find(ownerUuid, networkId, id);
        if (entry == null) return null;
        int index = entries.indexOf(entry);
        entries.remove(index);
        if (!save()) {
            entries.add(index, entry);
            return null;
        }
        return entry.copy();
    }

    private Entry find(String ownerUuid, int networkId, String id) {
        if (id == null || id.isEmpty()) return null;
        for (Entry entry : entries) {
            if (entry != null && id.equals(entry.id)
                && ownerUuid.equals(entry.ownerUuid)
                && entry.networkId == networkId) {
                return entry;
            }
        }
        return null;
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        File file = file();
        if (!file.isFile()) return;
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            State state = GSON.fromJson(reader, State.class);
            if (state != null && state.entries != null) entries.addAll(state.entries);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to load {}", FILE_NAME, e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private boolean save() {
        File target = file();
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(temp), "UTF-8");
            State state = new State();
            state.entries.addAll(entries);
            GSON.toJson(state, writer);
            writer.close();
            writer = null;
            try {
                java.nio.file.Files.move(
                    temp.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                java.nio.file.Files
                    .move(temp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to save {}", FILE_NAME, e);
            return false;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private static File file() {
        return TeXTechDataDir.webAeFile(FILE_NAME);
    }

    private static final class State {

        int version = 1;
        List<Entry> entries = new ArrayList<Entry>();
    }

    public static class Entry {

        public String id;
        public String ownerUuid;
        public int networkId;
        public String encodedNbt;
        public String sourceInterfaceName;
        public int sourceSlot;
        public boolean crafting;
        public long createdAt;
        public List<PatternItemEntry> outputs = new ArrayList<PatternItemEntry>();

        Entry copy() {
            Entry copy = new Entry();
            copy.id = id;
            copy.ownerUuid = ownerUuid;
            copy.networkId = networkId;
            copy.encodedNbt = encodedNbt;
            copy.sourceInterfaceName = sourceInterfaceName;
            copy.sourceSlot = sourceSlot;
            copy.crafting = crafting;
            copy.createdAt = createdAt;
            copy.outputs.addAll(outputs);
            return copy;
        }
    }
}
