package com.imgood.textech.webae.display;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.TeXTechDataDir;

/**
 * File-backed store for published dashboard displays under {@code TeXTech/WebAE/displays/}.
 */
public final class DisplayStore {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .setPrettyPrinting()
        .create();
    private static final String SUBDIR = "displays";
    private static final int MAX_TITLE = 96;
    private static final int MAX_LAYOUT_CHARS = 512 * 1024;
    private static final int MAX_PER_OWNER = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final ConcurrentHashMap<String, DisplayRecord> BY_ID = new ConcurrentHashMap<String, DisplayRecord>();
    private static final ConcurrentHashMap<String, String> TOKEN_TO_ID = new ConcurrentHashMap<String, String>();
    private static volatile boolean loaded;

    private DisplayStore() {}

    public static synchronized void ensureLoaded() {
        if (loaded) return;
        File dir = TeXTechDataDir.webAeDir(SUBDIR);
        if (!dir.isDirectory()) {
            dir.mkdirs();
            loaded = true;
            return;
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file == null || !file.isFile() || !file.getName().endsWith(".json")) continue;
                try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8")) {
                    DisplayRecord record = GSON.fromJson(reader, DisplayRecord.class);
                    if (record == null || record.id == null || record.viewToken == null) continue;
                    BY_ID.put(record.id, record);
                    TOKEN_TO_ID.put(record.viewToken, record.id);
                } catch (Exception e) {
                    AdvanceDataMonitor.LOG.warn("[WebAE] Failed to load display {}", file.getName(), e);
                }
            }
        }
        loaded = true;
    }

    public static DisplayRecord getById(String id) {
        ensureLoaded();
        if (id == null || id.isEmpty()) return null;
        return BY_ID.get(id);
    }

    public static DisplayRecord getByViewToken(String viewToken) {
        ensureLoaded();
        if (viewToken == null || viewToken.isEmpty()) return null;
        String id = TOKEN_TO_ID.get(viewToken);
        return id == null ? null : BY_ID.get(id);
    }

    public static DisplayRecord publish(String ownerUuid, String title, JsonObject layout, int viewportWidth,
        int viewportHeight, String reuseId) {
        ensureLoaded();
        if (ownerUuid == null || ownerUuid.isEmpty()) return null;
        JsonObject sanitized = sanitizeLayout(layout);
        if (sanitized == null) return null;
        String layoutJson = GSON.toJson(sanitized);
        if (layoutJson.length() > MAX_LAYOUT_CHARS) return null;

        DisplayRecord existing = reuseId != null && !reuseId.isEmpty() ? BY_ID.get(reuseId) : null;
        if (existing != null && !ownerUuid.equals(existing.ownerUuid)) return null;

        int owned = 0;
        for (DisplayRecord r : BY_ID.values()) {
            if (ownerUuid.equals(r.ownerUuid)) owned++;
        }
        if (existing == null && owned >= MAX_PER_OWNER) return null;

        long now = System.currentTimeMillis();
        DisplayRecord record = existing != null ? existing : new DisplayRecord();
        if (existing == null) {
            record.id = newId();
            record.viewToken = newToken();
            record.ownerUuid = ownerUuid;
            record.createdAt = now;
        }
        record.title = boundTitle(title);
        record.updatedAt = now;
        record.viewportWidth = clamp(viewportWidth, 64, 1600, 960);
        record.viewportHeight = clamp(viewportHeight, 64, 1200, 720);
        record.layout = sanitized;

        if (!save(record)) return null;
        BY_ID.put(record.id, record);
        TOKEN_TO_ID.put(record.viewToken, record.id);
        return record;
    }

    public static boolean delete(String ownerUuid, String id) {
        ensureLoaded();
        DisplayRecord record = BY_ID.get(id);
        if (record == null || !ownerUuid.equals(record.ownerUuid)) return false;
        BY_ID.remove(id);
        TOKEN_TO_ID.remove(record.viewToken);
        File file = fileFor(id);
        if (file.isFile() && !file.delete()) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to delete display file {}", file.getAbsolutePath());
        }
        return true;
    }

    private static boolean save(DisplayRecord record) {
        File file = fileFor(record.id);
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
                GSON.toJson(record, writer);
            }
            return true;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to save display {}", record.id, e);
            return false;
        }
    }

    private static File fileFor(String id) {
        String safe = id == null ? "unknown" : id.replaceAll("[^a-zA-Z0-9_-]", "_");
        return new File(TeXTechDataDir.webAeDir(SUBDIR), safe + ".json");
    }

    private static String newId() {
        return Long.toHexString(System.currentTimeMillis()) + UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 8);
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", Integer.valueOf(b & 0xff)));
        }
        return sb.toString();
    }

    private static String boundTitle(String title) {
        if (title == null || title.trim()
            .isEmpty()) {
            return "WebAE Dashboard";
        }
        String t = title.replaceAll("\\s+", " ")
            .trim();
        return t.length() > MAX_TITLE ? t.substring(0, MAX_TITLE) : t;
    }

    private static int clamp(int value, int min, int max, int fallback) {
        if (value < min || value > max) return fallback;
        return value;
    }

    /**
     * Drop known secret-bearing keys recursively; keep dashboard layout/settings only.
     */
    public static JsonObject sanitizeLayout(JsonObject layout) {
        if (layout == null || !layout.isJsonObject()) return null;
        JsonObject copy = deepCopyObject(layout);
        stripSecrets(copy);
        if (!copy.has("widgets") || !copy.get("widgets")
            .isJsonArray()) {
            copy.add("widgets", new JsonArray());
        }
        return copy;
    }

    private static void stripSecrets(JsonElement element) {
        if (element == null) return;
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            Iterator<Map.Entry<String, JsonElement>> it = obj.entrySet()
                .iterator();
            while (it.hasNext()) {
                Map.Entry<String, JsonElement> entry = it.next();
                String key = entry.getKey() == null ? "" : entry.getKey()
                    .toLowerCase();
                if (isSecretKey(key)) {
                    it.remove();
                    continue;
                }
                stripSecrets(entry.getValue());
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                stripSecrets(child);
            }
        }
    }

    private static boolean isSecretKey(String key) {
        return key.contains("token") || key.contains("apikey") || key.contains("api_key") || key.contains("secret")
            || key.contains("password") || key.contains("webhook") || key.contains("authorization")
            || key.equals("bearer") || key.contains("qq") && key.contains("key") || key.contains("mail")
                && (key.contains("pass") || key.contains("auth"));
    }

    private static JsonObject deepCopyObject(JsonObject src) {
        return GSON.fromJson(GSON.toJson(src), JsonObject.class);
    }
}
