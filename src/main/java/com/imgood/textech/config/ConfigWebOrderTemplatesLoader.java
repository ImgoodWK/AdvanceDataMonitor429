package com.imgood.textech.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.assistant.AssistantDataFiles;
import com.imgood.textech.webae.order.WebOrderTemplate;
import com.imgood.textech.webae.order.WebOrderTemplatesStore;
import com.imgood.textech.webae.order.WebOrderTemplatesValidator;

/**
 * Loads and persists {@code config/textech/web-order-templates.json} (batch order presets per owner).
 */
public final class ConfigWebOrderTemplatesLoader {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .serializeNulls()
        .create();
    private static final String FILE_NAME = "web-order-templates.json";
    private static WebOrderTemplatesStore cached;
    private static long lastLoadMs;

    private ConfigWebOrderTemplatesLoader() {}

    public static synchronized List<WebOrderTemplate> getForOwner(String ownerUuid) {
        WebOrderTemplatesStore store = getStore();
        List<WebOrderTemplate> list = store.templatesForOwner(ownerUuid);
        return new ArrayList<WebOrderTemplate>(list);
    }

    public static synchronized boolean saveForOwner(String ownerUuid, List<WebOrderTemplate> templates) {
        if (ownerUuid == null || ownerUuid.isEmpty()) {
            return false;
        }
        String err = WebOrderTemplatesValidator.validateOwnerTemplates(templates);
        if (err != null) {
            AdvanceDataMonitor.LOG.warn("[WebAE] web-order-templates.json validation failed: {}", err);
            return false;
        }
        List<WebOrderTemplate> normalized = WebOrderTemplatesValidator.normalize(templates);
        WebOrderTemplatesStore store = getStore();
        if (store.owners == null) {
            store.owners = new java.util.HashMap<String, List<WebOrderTemplate>>();
        }
        store.owners.put(ownerUuid, normalized);
        return persist(store);
    }

    public static synchronized void reload() {
        cached = loadFromDisk();
        lastLoadMs = System.currentTimeMillis();
    }

    private static WebOrderTemplatesStore getStore() {
        long now = System.currentTimeMillis();
        if (cached != null && now - lastLoadMs < 30_000L) {
            return cached;
        }
        cached = loadFromDisk();
        lastLoadMs = now;
        return cached;
    }

    private static WebOrderTemplatesStore loadFromDisk() {
        File file = AssistantDataFiles.dataFile(FILE_NAME);
        if (!file.exists()) {
            WebOrderTemplatesStore defaults = defaultStore();
            writeDefaults(file, defaults);
            return defaults;
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8")) {
            WebOrderTemplatesStore loaded = GSON.fromJson(reader, WebOrderTemplatesStore.class);
            if (loaded == null) {
                return defaultStore();
            }
            if (loaded.owners == null) {
                loaded.owners = new java.util.HashMap<String, List<WebOrderTemplate>>();
            }
            return loaded;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to load web-order-templates.json; using defaults", e);
            return defaultStore();
        }
    }

    private static boolean persist(WebOrderTemplatesStore store) {
        File file = AssistantDataFiles.dataFile(FILE_NAME);
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
                GSON.toJson(store, writer);
            }
            cached = store;
            lastLoadMs = System.currentTimeMillis();
            AdvanceDataMonitor.LOG.info("[WebAE] Saved web-order-templates.json");
            return true;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to save web-order-templates.json", e);
            return false;
        }
    }

    private static WebOrderTemplatesStore defaultStore() {
        WebOrderTemplatesStore store = new WebOrderTemplatesStore();
        store.version = 1;
        store.owners = new java.util.HashMap<String, List<WebOrderTemplate>>();
        return store;
    }

    private static void writeDefaults(File file, WebOrderTemplatesStore defaults) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
                GSON.toJson(defaults, writer);
            }
            AdvanceDataMonitor.LOG
                .info("[WebAE] Created default web-order-templates.json at {}", file.getAbsolutePath());
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to write default web-order-templates.json", e);
        }
    }
}
