package com.imgood.textech.webae.auth;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.TeXTechDataDir;

/**
 * In-memory store for one-time-use admin bootstrap codes.
 * Each code is consumed exactly once via elevate; a {@code --reuse} option
 * on the issue command allows the same code to be used multiple times.
 *
 * <p>
 * Persisted to {@code TeXTech/WebAE/web-admin-bootstrap.json} so that
 * codes survive server restarts but are never exposed to the browser
 * beyond the single elevate exchange.
 * </p>
 */
public final class WebAdminBootstrapStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();
    private static final String PREFIX = "wae-adm-";
    private static final int CODE_LENGTH = 32;

    private static final List<BootstrapEntry> entries = new CopyOnWriteArrayList<BootstrapEntry>();

    static {
        loadFromDisk();
    }

    private WebAdminBootstrapStore() {}

    /**
     * Generate a high-entropy one-use bootstrap code.
     *
     * @param issuedBy   who issued this code (player name or "console")
     * @param label      optional device label
     * @param allowReuse if true the code can be consumed multiple times
     * @return the generated code string (with {@code wae-adm-} prefix)
     */
    public static String generate(String issuedBy, String label, boolean allowReuse) {
        String code = PREFIX + UUID.randomUUID()
            .toString()
            .replace("-", "");
        BootstrapEntry entry = new BootstrapEntry();
        entry.code = code;
        entry.issuedBy = issuedBy;
        entry.label = label != null ? label : "";
        entry.allowReuse = allowReuse;
        entry.issuedAt = System.currentTimeMillis();
        entry.used = false;
        entries.add(entry);
        saveToDisk();
        return code;
    }

    /**
     * Validate and consume a bootstrap code. Returns true if the code
     * is valid and not yet consumed (or allows reuse).
     *
     * @return true if the code was valid and consumed (or still valid for reuse)
     */
    public static boolean consume(String code) {
        if (code == null || code.isEmpty()) {
            return false;
        }
        for (BootstrapEntry entry : entries) {
            if (code.equals(entry.code)) {
                if (entry.allowReuse) {
                    entry.lastUsedAt = System.currentTimeMillis();
                    saveToDisk();
                    return true;
                }
                if (entry.used) {
                    return false;
                }
                entry.used = true;
                entry.lastUsedAt = System.currentTimeMillis();
                saveToDisk();
                return true;
            }
        }
        return false;
    }

    public static List<BootstrapEntry> listUnused() {
        java.util.List<BootstrapEntry> result = new java.util.ArrayList<BootstrapEntry>();
        for (BootstrapEntry entry : entries) {
            if (!entry.used || entry.allowReuse) {
                result.add(entry);
            }
        }
        return result;
    }

    /** Revoke all unconsumed bootstrap codes. */
    public static int revokeAllUnused() {
        int count = 0;
        Iterator<BootstrapEntry> iter = entries.iterator();
        while (iter.hasNext()) {
            BootstrapEntry entry = iter.next();
            if (!entry.used) {
                iter.remove();
                count++;
            }
        }
        if (count > 0) {
            saveToDisk();
        }
        return count;
    }

    public static String prefix() {
        return PREFIX;
    }

    private static File bootstrapFile() {
        return TeXTechDataDir.webAeFile("web-admin-bootstrap.json");
    }

    private static void saveToDisk() {
        File file = bootstrapFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        File tmp = new File(parent, file.getName() + ".tmp");
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(tmp, false));
            GSON.toJson(entries, writer);
            writer.flush();
            writer.close();
            writer = null;
            if (file.exists() && !file.delete()) {
                AdvanceDataMonitor.LOG.warn("[WebAE] Failed to remove old admin bootstrap file");
            }
            if (!tmp.renameTo(file)) {
                AdvanceDataMonitor.LOG.error("[WebAE] Failed to rename temp admin bootstrap file");
            }
        } catch (IOException e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to save admin bootstrap file: {}", e.getMessage());
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private static void loadFromDisk() {
        File file = bootstrapFile();
        if (!file.exists()) {
            return;
        }
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            List<BootstrapEntry> loaded = GSON.fromJson(reader, new TypeToken<List<BootstrapEntry>>() {}.getType());
            if (loaded != null) {
                entries.addAll(loaded);
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to load admin bootstrap file: {}", e.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {}
            }
        }
    }

    public static final class BootstrapEntry {

        public String code;
        public String issuedBy;
        public String label;
        public boolean allowReuse;
        public boolean used;
        public long issuedAt;
        public long lastUsedAt;
    }
}
