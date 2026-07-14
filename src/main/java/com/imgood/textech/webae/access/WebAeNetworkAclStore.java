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
 * Actor-level network ACL overrides ({@code TeXTech/WebAE/web-network-acl.json}).
 * Phase 1 only supports {@code effect=deny} over guest token allowlists.
 */
public final class WebAeNetworkAclStore {

    public static final String EFFECT_DENY = "deny";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();

    private static List<AclEntry> cache;
    private static volatile boolean dirty;
    private static volatile boolean savePending;

    private WebAeNetworkAclStore() {}

    private static File storeFile() {
        return TeXTechDataDir.webAeFile("web-network-acl.json");
    }

    public static boolean isDenied(String ownerUuid, String actorUuid, String networkKey) {
        if (ownerUuid == null || actorUuid == null || networkKey == null) {
            return false;
        }
        List<AclEntry> list = getList();
        synchronized (list) {
            for (AclEntry e : list) {
                if (ownerUuid.equals(e.ownerUuid) && actorUuid.equals(e.actorUuid)
                    && networkKey.equals(e.networkKey) && EFFECT_DENY.equals(e.effect)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static List<AclEntry> listForActor(String actorUuid) {
        List<AclEntry> out = new ArrayList<AclEntry>();
        if (actorUuid == null) {
            return out;
        }
        List<AclEntry> list = getList();
        synchronized (list) {
            for (AclEntry e : list) {
                if (actorUuid.equals(e.actorUuid)) {
                    out.add(e);
                }
            }
        }
        return out;
    }

    public static List<AclEntry> listForOwner(String ownerUuid) {
        List<AclEntry> out = new ArrayList<AclEntry>();
        if (ownerUuid == null) {
            return out;
        }
        List<AclEntry> list = getList();
        synchronized (list) {
            for (AclEntry e : list) {
                if (ownerUuid.equals(e.ownerUuid)) {
                    out.add(e);
                }
            }
        }
        return out;
    }

    public static void deny(String ownerUuid, String actorUuid, String networkKey) {
        if (ownerUuid == null || actorUuid == null || networkKey == null || networkKey.isEmpty()) {
            return;
        }
        List<AclEntry> list = getList();
        synchronized (list) {
            for (AclEntry e : list) {
                if (ownerUuid.equals(e.ownerUuid) && actorUuid.equals(e.actorUuid)
                    && networkKey.equals(e.networkKey)) {
                    e.effect = EFFECT_DENY;
                    e.updatedAt = System.currentTimeMillis();
                    scheduleSave();
                    return;
                }
            }
            AclEntry entry = new AclEntry();
            entry.ownerUuid = ownerUuid;
            entry.actorUuid = actorUuid;
            entry.networkKey = networkKey;
            entry.effect = EFFECT_DENY;
            entry.updatedAt = System.currentTimeMillis();
            list.add(entry);
        }
        scheduleSave();
    }

    /** Remove deny (allow again subject to token allowlist). */
    public static boolean clearDeny(String ownerUuid, String actorUuid, String networkKey) {
        if (ownerUuid == null || actorUuid == null || networkKey == null) {
            return false;
        }
        List<AclEntry> list = getList();
        boolean removed = false;
        synchronized (list) {
            Iterator<AclEntry> iter = list.iterator();
            while (iter.hasNext()) {
                AclEntry e = iter.next();
                if (ownerUuid.equals(e.ownerUuid) && actorUuid.equals(e.actorUuid)
                    && networkKey.equals(e.networkKey)) {
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

    private static List<AclEntry> getList() {
        if (cache == null) {
            synchronized (WebAeNetworkAclStore.class) {
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
                        List<AclEntry> snapshot;
                        synchronized (getList()) {
                            snapshot = new ArrayList<AclEntry>(cache);
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

    private static List<AclEntry> loadAll() {
        File file = storeFile();
        if (!file.exists()) {
            return new ArrayList<AclEntry>();
        }
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            List<AclEntry> loaded = GSON.fromJson(reader, new TypeToken<List<AclEntry>>() {}.getType());
            return loaded != null ? new ArrayList<AclEntry>(loaded) : new ArrayList<AclEntry>();
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to load network ACL: {}", e.getMessage());
            return new ArrayList<AclEntry>();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private static void saveAll(List<AclEntry> entries) {
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
                AdvanceDataMonitor.LOG.warn("[WebAE] Failed to remove old network-acl file");
            }
            if (!tmp.renameTo(file)) {
                AdvanceDataMonitor.LOG.error("[WebAE] Failed to rename temp network-acl file");
            }
        } catch (IOException e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to save network ACL: {}", e.getMessage());
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {}
            }
        }
    }

    public static final class AclEntry {

        public String ownerUuid;
        public String actorUuid;
        public String networkKey;
        public String effect;
        public long updatedAt;
    }
}
