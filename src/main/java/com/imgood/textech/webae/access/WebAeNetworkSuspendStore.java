package com.imgood.textech.webae.access;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.TeXTechDataDir;
import com.imgood.textech.handler.HandlerTick;

/**
 * Persist per-network WebAE suspensions ({@code TeXTech/WebAE/web-network-suspends.json}).
 * Suspended networks are invisible / non-collectable for everyone including the owner.
 */
public final class WebAeNetworkSuspendStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();

    private static List<SuspendEntry> cache;
    private static volatile boolean dirty;
    private static volatile boolean savePending;

    private WebAeNetworkSuspendStore() {}

    private static File storeFile() {
        return TeXTechDataDir.webAeFile("web-network-suspends.json");
    }

    public static boolean isSuspended(String ownerUuid, String networkKey) {
        if (ownerUuid == null || networkKey == null || networkKey.isEmpty()) {
            return false;
        }
        List<SuspendEntry> list = getList();
        synchronized (list) {
            for (SuspendEntry e : list) {
                if (ownerUuid.equals(e.ownerUuid) && networkKey.equals(e.networkKey)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static SuspendEntry get(String ownerUuid, String networkKey) {
        if (ownerUuid == null || networkKey == null) {
            return null;
        }
        List<SuspendEntry> list = getList();
        synchronized (list) {
            for (SuspendEntry e : list) {
                if (ownerUuid.equals(e.ownerUuid) && networkKey.equals(e.networkKey)) {
                    return e;
                }
            }
        }
        return null;
    }

    public static List<SuspendEntry> listForOwner(String ownerUuid) {
        List<SuspendEntry> out = new ArrayList<SuspendEntry>();
        if (ownerUuid == null) {
            return out;
        }
        List<SuspendEntry> list = getList();
        synchronized (list) {
            for (SuspendEntry e : list) {
                if (ownerUuid.equals(e.ownerUuid)) {
                    out.add(e);
                }
            }
        }
        return out;
    }

    public static void suspend(String ownerUuid, String networkKey, String reason) {
        if (ownerUuid == null || networkKey == null || networkKey.isEmpty()) {
            return;
        }
        List<SuspendEntry> list = getList();
        synchronized (list) {
            for (SuspendEntry e : list) {
                if (ownerUuid.equals(e.ownerUuid) && networkKey.equals(e.networkKey)) {
                    e.reason = reason != null ? reason : "";
                    e.suspendedAt = System.currentTimeMillis();
                    scheduleSave();
                    return;
                }
            }
            SuspendEntry entry = new SuspendEntry();
            entry.ownerUuid = ownerUuid;
            entry.networkKey = networkKey;
            entry.reason = reason != null ? reason : "";
            entry.suspendedAt = System.currentTimeMillis();
            list.add(entry);
        }
        scheduleSave();
    }

    public static boolean resume(String ownerUuid, String networkKey) {
        if (ownerUuid == null || networkKey == null) {
            return false;
        }
        List<SuspendEntry> list = getList();
        boolean removed = false;
        synchronized (list) {
            Iterator<SuspendEntry> iter = list.iterator();
            while (iter.hasNext()) {
                SuspendEntry e = iter.next();
                if (ownerUuid.equals(e.ownerUuid) && networkKey.equals(e.networkKey)) {
                    iter.remove();
                    removed = true;
                }
            }
        }
        if (removed) {
            scheduleSave();
        }
        return removed;
    }

    private static List<SuspendEntry> getList() {
        if (cache == null) {
            synchronized (WebAeNetworkSuspendStore.class) {
                if (cache == null) {
                    cache = loadAll();
                }
            }
        }
        return cache;
    }

    private static void scheduleSave() {
        dirty = true;
        if (savePending) {
            return;
        }
        savePending = true;
        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                try {
                    if (dirty) {
                        List<SuspendEntry> snapshot;
                        synchronized (getList()) {
                            snapshot = new ArrayList<SuspendEntry>(cache);
                            dirty = false;
                        }
                        saveAll(snapshot);
                    }
                } finally {
                    savePending = false;
                }
            }
        });
    }

    private static List<SuspendEntry> loadAll() {
        File file = storeFile();
        if (!file.exists()) {
            return new ArrayList<SuspendEntry>();
        }
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            List<SuspendEntry> loaded = GSON.fromJson(reader, new TypeToken<List<SuspendEntry>>() {}.getType());
            return loaded != null ? new ArrayList<SuspendEntry>(loaded) : new ArrayList<SuspendEntry>();
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to load network suspends: {}", e.getMessage());
            return new ArrayList<SuspendEntry>();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private static void saveAll(List<SuspendEntry> entries) {
        File file = storeFile();
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
                AdvanceDataMonitor.LOG.warn("[WebAE] Failed to remove old network-suspends file");
            }
            if (!tmp.renameTo(file)) {
                AdvanceDataMonitor.LOG.error("[WebAE] Failed to rename temp network-suspends file");
            }
        } catch (IOException e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to save network suspends: {}", e.getMessage());
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {}
            }
        }
    }

    public static final class SuspendEntry {

        public String ownerUuid;
        public String networkKey;
        public String reason;
        public long suspendedAt;
    }
}
