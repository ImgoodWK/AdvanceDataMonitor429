package com.imgood.textech.webae.favorites;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.assistant.AssistantDataFiles;

/**
 * Persists per-owner recipe/pattern/item favorites at {@code TeXTech/WebAE/web-favorites.json}.
 */
public final class WebFavoritesStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .serializeNulls()
        .create();
    private static final String FILE_NAME = "web-favorites.json";
    private static final WebFavoritesStore INSTANCE = new WebFavoritesStore();
    private final Map<String, OwnerFavorites> owners = new HashMap<String, OwnerFavorites>();
    private boolean loaded;

    public static WebFavoritesStore instance() {
        return INSTANCE;
    }

    public synchronized OwnerFavorites getForOwner(String ownerUuid) {
        load();
        String key = normalizeOwner(ownerUuid);
        OwnerFavorites fav = owners.get(key);
        if (fav == null) {
            fav = new OwnerFavorites();
            owners.put(key, fav);
        }
        return fav.copy();
    }

    public synchronized boolean saveForOwner(String ownerUuid, OwnerFavorites incoming) {
        load();
        if (incoming == null) {
            return false;
        }
        String key = normalizeOwner(ownerUuid);
        OwnerFavorites normalized = incoming.copy();
        if (normalized.recipes == null) {
            normalized.recipes = new ArrayList<String>();
        }
        if (normalized.patterns == null) {
            normalized.patterns = new ArrayList<String>();
        }
        if (normalized.items == null) {
            normalized.items = new ArrayList<String>();
        }
        owners.put(key, normalized);
        return persist();
    }

    private synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        File file = AssistantDataFiles.dataFile(FILE_NAME);
        if (!file.exists()) {
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8")) {
            FavoritesFile data = GSON.fromJson(reader, FavoritesFile.class);
            if (data != null && data.owners != null) {
                owners.putAll(data.owners);
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to load web-favorites.json", e);
        }
    }

    private boolean persist() {
        File file = AssistantDataFiles.dataFile(FILE_NAME);
        FavoritesFile data = new FavoritesFile();
        data.owners = owners;
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
            GSON.toJson(data, writer);
            return true;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to save web-favorites.json", e);
            return false;
        }
    }

    private static String normalizeOwner(String ownerUuid) {
        if (ownerUuid == null || ownerUuid.trim()
            .isEmpty()) {
            return "00000000-0000-0000-0000-000000000000";
        }
        return ownerUuid.trim();
    }

    public static final class OwnerFavorites {

        public List<String> recipes = new ArrayList<String>();
        public List<String> patterns = new ArrayList<String>();
        public List<String> items = new ArrayList<String>();

        public OwnerFavorites copy() {
            OwnerFavorites copy = new OwnerFavorites();
            copy.recipes = new ArrayList<String>(this.recipes);
            copy.patterns = new ArrayList<String>(this.patterns);
            copy.items = new ArrayList<String>(this.items);
            return copy;
        }
    }

    private static final class FavoritesFile {

        public Map<String, OwnerFavorites> owners = new HashMap<String, OwnerFavorites>();
    }
}
