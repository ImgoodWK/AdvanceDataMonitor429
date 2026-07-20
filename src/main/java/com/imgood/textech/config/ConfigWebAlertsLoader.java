package com.imgood.textech.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.assistant.AssistantDataFiles;
import com.imgood.textech.webae.alerts.WebAlertsConfig;
import com.imgood.textech.webae.alerts.WebAlertsConfigValidator;

/**
 * Loads {@code TeXTech/WebAE/web-alerts.json} for WebAE automation alerts.
 */
public final class ConfigWebAlertsLoader {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();
    private static final String FILE_NAME = "web-alerts.json";
    private static WebAlertsConfig cached;
    private static long lastLoadMs;

    private ConfigWebAlertsLoader() {}

    public static synchronized WebAlertsConfig get() {
        long now = System.currentTimeMillis();
        if (cached != null && now - lastLoadMs < 30_000L) {
            return cached;
        }
        cached = loadFromDisk();
        lastLoadMs = now;
        return cached;
    }

    public static synchronized void reload() {
        cached = loadFromDisk();
        lastLoadMs = System.currentTimeMillis();
    }

    /**
     * Persist validated rules to disk and refresh the in-memory cache.
     *
     * @return {@code true} on success
     */
    public static synchronized boolean save(WebAlertsConfig cfg) {
        String err = WebAlertsConfigValidator.validate(cfg);
        if (err != null) {
            AdvanceDataMonitor.LOG.warn("[WebAE] web-alerts.json validation failed: {}", err);
            return false;
        }
        WebAlertsConfig normalized = WebAlertsConfigValidator.normalize(cfg);
        File file = AssistantDataFiles.dataFile(FILE_NAME);
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
                GSON.toJson(normalized, writer);
            }
            cached = normalized;
            lastLoadMs = System.currentTimeMillis();
            AdvanceDataMonitor.LOG.info(
                "[WebAE] Saved web-alerts.json ({} inventory rules, {} external notification targets)",
                normalized.inventoryThresholds.size(),
                normalized.notificationTargets.size());
            return true;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to save web-alerts.json", e);
            return false;
        }
    }

    private static WebAlertsConfig loadFromDisk() {
        File file = AssistantDataFiles.dataFile(FILE_NAME);
        if (!file.exists()) {
            WebAlertsConfig defaults = defaultConfig();
            writeDefaults(file, defaults);
            return defaults;
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8")) {
            WebAlertsConfig loaded = GSON.fromJson(reader, WebAlertsConfig.class);
            if (loaded == null) {
                return defaultConfig();
            }
            loaded = WebAlertsConfigValidator.normalize(loaded);
            String err = WebAlertsConfigValidator.validate(loaded);
            if (err != null) {
                AdvanceDataMonitor.LOG
                    .warn("[WebAE] Invalid external alert configuration; disabling external targets: {}", err);
                loaded.webhooks.clear();
                loaded.notificationTargets.clear();
                err = WebAlertsConfigValidator.validate(loaded);
                if (err != null) {
                    AdvanceDataMonitor.LOG.warn("[WebAE] Invalid web-alerts.json; using safe defaults: {}", err);
                    return defaultConfig();
                }
            }
            return loaded;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to load web-alerts.json; using defaults", e);
            return defaultConfig();
        }
    }

    private static WebAlertsConfig defaultConfig() {
        WebAlertsConfig cfg = new WebAlertsConfig();
        cfg.version = 2;
        cfg.enabled = true;
        cfg.pollIntervalSeconds = 10;
        cfg.cpuStuckMinutes = 5;
        cfg.gtErrorEnabled = true;
        cfg.orderCompleteEnabled = true;
        cfg.channelThresholdPercent = 90;
        cfg.channelThresholdAbsolute = 28;
        return cfg;
    }

    private static void writeDefaults(File file, WebAlertsConfig defaults) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
                GSON.toJson(defaults, writer);
            }
            AdvanceDataMonitor.LOG.info("[WebAE] Created default web-alerts.json at {}", file.getAbsolutePath());
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to write default web-alerts.json", e);
        }
    }
}
