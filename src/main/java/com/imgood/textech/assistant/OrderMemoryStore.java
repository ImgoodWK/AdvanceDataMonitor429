package com.imgood.textech.assistant;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.imgood.textech.AdvanceDataMonitor;

public class OrderMemoryStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();
    private static final OrderMemoryStore INSTANCE = new OrderMemoryStore();
    private final List<OrderMemoryEntry> entries = new ArrayList<OrderMemoryEntry>();
    private boolean loaded;

    public static OrderMemoryStore instance() {
        return INSTANCE;
    }

    public synchronized void remember(String rawText, CraftingCandidate candidate) {
        remember("", rawText, candidate);
    }

    public synchronized void remember(String owner, String rawText, CraftingCandidate candidate) {
        if (candidate == null) {
            return;
        }
        load();
        String effectiveOwner = owner == null ? "" : owner;
        String key = normalize(rawText);
        for (OrderMemoryEntry entry : entries) {
            normalizeLoaded(entry);
            if (entry.owner.equals(effectiveOwner) && entry.keywords.equals(key)
                && entry.registryName.equals(candidate.registryName)
                && entry.meta == candidate.meta) {
                entry.successCount++;
                entry.lastUsed = System.currentTimeMillis();
                save();
                return;
            }
        }
        OrderMemoryEntry entry = new OrderMemoryEntry();
        entry.owner = effectiveOwner;
        entry.rawText = rawText == null ? "" : rawText;
        entry.keywords = key;
        entry.displayName = candidate.displayName;
        entry.registryName = candidate.registryName;
        entry.meta = candidate.meta;
        entry.amount = candidate.amount;
        entry.successCount = 1;
        entry.lastUsed = System.currentTimeMillis();
        entries.add(entry);
        save();
    }

    public synchronized int score(String rawText, CraftingCandidate candidate) {
        return score("", rawText, candidate);
    }

    public synchronized int score(String owner, String rawText, CraftingCandidate candidate) {
        if (candidate == null) {
            return 0;
        }
        load();
        String effectiveOwner = owner == null ? "" : owner;
        String key = normalize(rawText);
        int best = 0;
        for (OrderMemoryEntry entry : entries) {
            normalizeLoaded(entry);
            if (!entry.owner.equals(effectiveOwner) || !entry.registryName.equals(candidate.registryName)
                || entry.meta != candidate.meta) {
                continue;
            }
            int score = entry.successCount * 10;
            if (!key.isEmpty() && (entry.keywords.contains(key) || key.contains(entry.keywords))) {
                score += 100;
            }
            best = Math.max(best, score);
        }
        return best;
    }

    public synchronized List<OrderMemoryEntry> recent() {
        load();
        List<OrderMemoryEntry> copy = new ArrayList<OrderMemoryEntry>(entries);
        Collections.sort(copy, new Comparator<OrderMemoryEntry>() {

            @Override
            public int compare(OrderMemoryEntry a, OrderMemoryEntry b) {
                return Long.compare(b.lastUsed, a.lastUsed);
            }
        });
        return copy;
    }

    private String normalize(String text) {
        return text == null ? ""
            : text.toLowerCase()
                .replaceAll("[\\s，。.?!？！]", "")
                .trim();
    }

    private void normalizeLoaded(OrderMemoryEntry entry) {
        if (entry.owner == null) {
            entry.owner = "";
        }
    }

    private void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        File file = AssistantDataFiles.dataFile("order-memory.json");
        if (!file.exists()) {
            return;
        }
        try (java.io.InputStreamReader reader = new java.io.InputStreamReader(new FileInputStream(file), "UTF-8")) {
            List<OrderMemoryEntry> loadedEntries = GSON
                .fromJson(reader, new TypeToken<List<OrderMemoryEntry>>() {}.getType());
            if (loadedEntries != null) {
                entries.clear();
                entries.addAll(loadedEntries);
                for (OrderMemoryEntry entry : entries) {
                    normalizeLoaded(entry);
                }
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("Failed to load order memory", e);
        }
    }

    private void save() {
        File file = AssistantDataFiles.dataFile("order-memory.json");
        try (java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
            GSON.toJson(entries, writer);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("Failed to save order memory", e);
        }
    }

    public static class OrderMemoryEntry {

        public String owner = "";
        public String rawText = "";
        public String keywords = "";
        public String displayName = "";
        public String registryName = "";
        public int meta;
        public long amount;
        public int successCount;
        public long lastUsed;
    }
}
