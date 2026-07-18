package com.imgood.textech.webae.spark;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.TeXTechDataDir;

/** Small bounded JSON store for Spark run metadata. */
public final class SparkProfileStore {

    private static final SparkProfileStore INSTANCE = new SparkProfileStore();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<SparkProfile> profiles = new ArrayList<SparkProfile>();
    private boolean loaded;

    private SparkProfileStore() {}

    public static SparkProfileStore instance() {
        return INSTANCE;
    }

    public synchronized void upsert(SparkProfile profile) {
        ensureLoaded();
        if (profile == null || profile.id == null || profile.id.isEmpty()) {
            return;
        }
        for (int i = 0; i < profiles.size(); i++) {
            if (profile.id.equals(profiles.get(i).id)) {
                profiles.set(i, profile);
                save();
                return;
            }
        }
        profiles.add(0, profile);
        trim();
        save();
    }

    public synchronized SparkProfile find(String id) {
        ensureLoaded();
        if (id == null) return null;
        for (SparkProfile profile : profiles) {
            if (id.equals(profile.id)) return profile;
        }
        return null;
    }

    public synchronized List<SparkProfile> all() {
        ensureLoaded();
        return new ArrayList<SparkProfile>(profiles);
    }

    public synchronized boolean remove(String id) {
        ensureLoaded();
        if (id == null) return false;
        for (int i = 0; i < profiles.size(); i++) {
            if (id.equals(profiles.get(i).id)) {
                profiles.remove(i);
                save();
                return true;
            }
        }
        return false;
    }

    public synchronized void saveNow() {
        ensureLoaded();
        saveToDisk();
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        File file = storeFile();
        if (!file.isFile()) return;
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            SparkProfile[] loadedProfiles = GSON.fromJson(reader, SparkProfile[].class);
            if (loadedProfiles != null) {
                for (SparkProfile profile : loadedProfiles) {
                    if (profile != null && profile.id != null && !profile.id.isEmpty()) {
                        if (profile.messages == null) profile.messages = new ArrayList<String>();
                        // A persisted active entry cannot still be sampling after
                        // a server restart; do not block the next run forever.
                        if (profile.isActive()) {
                            profile.status = "interrupted";
                            profile.completedAt = System.currentTimeMillis();
                        }
                        profiles.add(profile);
                    }
                }
            }
            trim();
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to load Spark history from {}", file.getAbsolutePath(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private void save() {
        // Spark completion messages are tiny and infrequent; writing the bounded
        // metadata synchronously keeps the history durable across a server crash.
        saveToDisk();
    }

    private void saveToDisk() {
        FileWriter writer = null;
        try {
            writer = new FileWriter(storeFile(), false);
            GSON.toJson(profiles, writer);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to save Spark history", e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private void trim() {
        int max = Math.max(1, Config.webSparkMaxHistory);
        while (profiles.size() > max) {
            profiles.remove(profiles.size() - 1);
        }
    }

    private static File storeFile() {
        return TeXTechDataDir.webAeFile("spark-history.json");
    }
}
