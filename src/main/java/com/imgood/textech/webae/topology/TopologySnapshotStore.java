package com.imgood.textech.webae.topology;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.assistant.AssistantDataFiles;

/**
 * Persists topology snapshots to {@code config/textech/web-topology/<ownerUuid>-<networkId>.json}.
 * Logical and spatial snapshots are stored in separate fields; cooldown timestamp is persisted too.
 */
public final class TopologySnapshotStore {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .setPrettyPrinting()
        .create();
    private static final String SUBDIR = "web-topology";

    private TopologySnapshotStore() {}

    public static final class PersistedNetwork {

        public String ownerUuid;
        public int networkId;
        public long lastCaptureAt;
        public TopologySnapshot logical;
        public TopologySnapshot spatial;
    }

    public static File fileFor(String ownerUuid, int networkId) {
        File dir = new File(
            AssistantDataFiles.dataFile(".")
                .getParentFile(),
            SUBDIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String safeOwner = ownerUuid == null ? "unknown" : ownerUuid.replaceAll("[^a-zA-Z0-9_-]", "_");
        return new File(dir, safeOwner + "-" + networkId + ".json");
    }

    public static PersistedNetwork load(String ownerUuid, int networkId) {
        File file = fileFor(ownerUuid, networkId);
        if (!file.exists()) {
            return null;
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8")) {
            PersistedNetwork data = GSON.fromJson(reader, PersistedNetwork.class);
            if (data == null) {
                return null;
            }
            data.ownerUuid = ownerUuid;
            data.networkId = networkId;
            return data;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to load topology snapshot from {}", file.getAbsolutePath(), e);
            return null;
        }
    }

    public static boolean save(String ownerUuid, int networkId, String mode, TopologySnapshot snapshot,
        long captureAt) {
        if (snapshot == null) {
            return false;
        }
        File file = fileFor(ownerUuid, networkId);
        try {
            PersistedNetwork data = load(ownerUuid, networkId);
            if (data == null) {
                data = new PersistedNetwork();
                data.ownerUuid = ownerUuid;
                data.networkId = networkId;
            }
            data.lastCaptureAt = captureAt;
            String normalizedMode = normalizeMode(mode);
            if ("spatial".equals(normalizedMode)) {
                data.spatial = snapshot;
            } else {
                data.logical = snapshot;
            }
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
                GSON.toJson(data, writer);
            }
            return true;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to save topology snapshot to {}", file.getAbsolutePath(), e);
            return false;
        }
    }

    public static TopologySnapshot loadSnapshot(String ownerUuid, int networkId, String mode) {
        PersistedNetwork data = load(ownerUuid, networkId);
        if (data == null) {
            return null;
        }
        return "spatial".equals(normalizeMode(mode)) ? data.spatial : data.logical;
    }

    public static long loadLastCaptureAt(String ownerUuid, int networkId) {
        PersistedNetwork data = load(ownerUuid, networkId);
        return data != null ? data.lastCaptureAt : 0L;
    }

    private static String normalizeMode(String mode) {
        if (mode != null && "spatial".equalsIgnoreCase(mode.trim())) {
            return "spatial";
        }
        return "logical";
    }
}
