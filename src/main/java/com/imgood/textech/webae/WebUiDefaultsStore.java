package com.imgood.textech.webae;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.commons.io.IOUtils;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.TeXTechDataDir;

/**
 * Loads WebAE UI default settings JSON for first-time browser visitors.
 *
 * <p>
 * Priority: {@code TeXTech/WebAE/ui-defaults.json} on the instance, then jar
 * {@code /assets/textech/webae/ui-defaults.json}.
 * </p>
 */
public final class WebUiDefaultsStore {

    public static final String FILENAME = "ui-defaults.json";

    private static final String JAR_RESOURCE = "/assets/textech/webae/" + FILENAME;

    private WebUiDefaultsStore() {}

    public enum Source {
        INSTANCE,
        JAR,
        NONE
    }

    public static final class LoadedDefaults {

        public final String json;
        public final Source source;
        public final File instanceFile;

        LoadedDefaults(String json, Source source, File instanceFile) {
            this.json = json;
            this.source = source;
            this.instanceFile = instanceFile;
        }
    }

    public static File instanceFile() {
        return TeXTechDataDir.webAeFile(FILENAME);
    }

    /** Read defaults JSON; returns null when no file or empty object. */
    public static LoadedDefaults load() {
        File instance = instanceFile();
        if (instance.isFile() && instance.length() > 0) {
            try {
                String json = new String(Files.readAllBytes(instance.toPath()), StandardCharsets.UTF_8);
                if (isUsableJson(json)) {
                    return new LoadedDefaults(json.trim(), Source.INSTANCE, instance);
                }
            } catch (Exception e) {
                AdvanceDataMonitor.LOG.warn("[WebAE] Failed to read instance ui-defaults.json", e);
            }
        }

        try (InputStream stream = WebUiDefaultsStore.class.getResourceAsStream(JAR_RESOURCE)) {
            if (stream != null) {
                String json = IOUtils.toString(stream, StandardCharsets.UTF_8);
                if (isUsableJson(json)) {
                    return new LoadedDefaults(json.trim(), Source.JAR, null);
                }
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to read bundled ui-defaults.json", e);
        }

        return new LoadedDefaults(null, Source.NONE, instance);
    }

    private static boolean isUsableJson(String json) {
        if (json == null) {
            return false;
        }
        String trimmed = json.trim();
        if (trimmed.isEmpty() || "{}".equals(trimmed) || "null".equals(trimmed)) {
            return false;
        }
        return trimmed.startsWith("{");
    }
}
