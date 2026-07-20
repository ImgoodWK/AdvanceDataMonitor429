package com.imgood.textech.webae.auth;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.TeXTechDataDir;
import com.imgood.textech.handler.HandlerTick;

/**
 * Persistent store for admin device grants.
 *
 * <p>
 * After a browser exchanges a one-time bootstrap code, a long-lived
 * {@code adminToken} is issued and persisted here. The browser stores
 * the token in {@code localStorage: webae_admin_token} and sends it
 * via {@code X-WebAE-Admin} header on each admin request.
 * </p>
 *
 * <p>
 * File: {@code TeXTech/WebAE/web-admin-grants.json}
 * </p>
 */
public final class WebAdminGrantStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();
    private static final int DEFAULT_GRANT_DAYS = 90;

    private static List<GrantEntry> grantCache;
    private static volatile boolean dirty;
    private static volatile boolean savePending;

    private WebAdminGrantStore() {}

    /**
     * Create a long-lived admin grant token after a successful bootstrap
     * code exchange.
     *
     * @param ownerUuid AE network owner UUID (from session)
     * @param actorUuid actor UUID (from session)
     * @param actorName actor name (from session)
     * @param label     optional device label
     * @return the generated grant entry (contains the raw token)
     */
    public static GrantEntry createGrant(String ownerUuid, String actorUuid, String actorName, String label) {
        if (ownerUuid == null || actorUuid == null) {
            return null;
        }
        GrantEntry entry = new GrantEntry();
        entry.adminToken = UUID.randomUUID()
            .toString()
            .replace("-", "");
        entry.boundOwnerUuid = ownerUuid;
        entry.boundActorUuid = actorUuid;
        entry.boundActorName = actorName != null ? actorName : "";
        entry.label = label != null ? label : "";
        entry.issuedAt = System.currentTimeMillis();
        int days = Config.webAdminGrantDays > 0 ? Config.webAdminGrantDays : DEFAULT_GRANT_DAYS;
        entry.expiresAt = days > 0 ? entry.issuedAt + (long) days * 86400_000L : 0;

        List<GrantEntry> grants = getGrantList();
        synchronized (grants) {
            grants.add(entry);
        }
        scheduleSave();
        return entry;
    }

    /**
     * Validate an admin token. Returns the matching grant entry if valid
     * and not expired, or null.
     */
    public static GrantEntry validate(String adminToken) {
        if (adminToken == null || adminToken.isEmpty()) {
            return null;
        }
        List<GrantEntry> grants = getGrantList();
        synchronized (grants) {
            long now = System.currentTimeMillis();
            for (GrantEntry entry : grants) {
                if (adminToken.equals(entry.adminToken)) {
                    if (entry.expiresAt > 0 && now > entry.expiresAt) {
                        return null;
                    }
                    entry.lastUsedAt = now;
                    scheduleSave();
                    return entry;
                }
            }
        }
        return null;
    }

    /**
     * List all grants for a given owner UUID (for admin self-management).
     * Token values are masked (first 4 + last 4 chars only).
     */
    public static List<GrantEntry> listByOwner(String ownerUuid) {
        List<GrantEntry> result = new ArrayList<GrantEntry>();
        if (ownerUuid == null || ownerUuid.isEmpty()) {
            return result;
        }
        List<GrantEntry> grants = getGrantList();
        synchronized (grants) {
            for (GrantEntry entry : grants) {
                if (ownerUuid.equals(entry.boundOwnerUuid)) {
                    result.add(maskedCopy(entry));
                }
            }
        }
        return result;
    }

    /** List all grants (OP only, masked tokens). */
    public static List<GrantEntry> listAll() {
        List<GrantEntry> grants = getGrantList();
        List<GrantEntry> result = new ArrayList<GrantEntry>();
        synchronized (grants) {
            for (GrantEntry entry : grants) {
                result.add(maskedCopy(entry));
            }
        }
        return result;
    }

    /** Revoke a grant by its admin token. */
    public static boolean revokeByToken(String adminToken) {
        if (adminToken == null || adminToken.isEmpty()) {
            return false;
        }
        List<GrantEntry> grants = getGrantList();
        boolean removed;
        synchronized (grants) {
            Iterator<GrantEntry> iter = grants.iterator();
            removed = false;
            while (iter.hasNext()) {
                if (adminToken.equals(iter.next().adminToken)) {
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

    /** Revoke all grants for a given owner UUID. */
    public static int revokeByOwner(String ownerUuid) {
        if (ownerUuid == null || ownerUuid.isEmpty()) {
            return 0;
        }
        List<GrantEntry> grants = getGrantList();
        int count = 0;
        synchronized (grants) {
            Iterator<GrantEntry> iter = grants.iterator();
            while (iter.hasNext()) {
                if (ownerUuid.equals(iter.next().boundOwnerUuid)) {
                    iter.remove();
                    count++;
                }
            }
        }
        if (count > 0) {
            scheduleSave();
        }
        return count;
    }

    /** Revoke all grants. Used by rotate command. */
    public static int revokeAll() {
        List<GrantEntry> grants = getGrantList();
        int count;
        synchronized (grants) {
            count = grants.size();
            grants.clear();
        }
        if (count > 0) {
            scheduleSave();
        }
        return count;
    }

    static String maskToken(String token) {
        if (token == null || token.length() <= 8) {
            return "****";
        }
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }

    private static GrantEntry maskedCopy(GrantEntry entry) {
        GrantEntry copy = new GrantEntry();
        copy.adminToken = maskToken(entry.adminToken);
        copy.boundOwnerUuid = entry.boundOwnerUuid;
        copy.boundActorUuid = entry.boundActorUuid;
        copy.boundActorName = entry.boundActorName;
        copy.label = entry.label;
        copy.issuedAt = entry.issuedAt;
        copy.expiresAt = entry.expiresAt;
        copy.lastUsedAt = entry.lastUsedAt;
        return copy;
    }

    private static File grantFile() {
        return TeXTechDataDir.webAeFile("web-admin-grants.json");
    }

    private static List<GrantEntry> getGrantList() {
        if (grantCache == null) {
            synchronized (WebAdminGrantStore.class) {
                if (grantCache == null) {
                    grantCache = loadAllFromDisk();
                }
            }
        }
        return grantCache;
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
                        List<GrantEntry> snapshot;
                        synchronized (getGrantList()) {
                            snapshot = new ArrayList<GrantEntry>(grantCache);
                            dirty = false;
                        }
                        saveAllAtomic(snapshot);
                    }
                } finally {
                    savePending = false;
                }
            }
        });
    }

    private static List<GrantEntry> loadAllFromDisk() {
        File file = grantFile();
        if (!file.exists()) {
            return new ArrayList<GrantEntry>();
        }
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            List<GrantEntry> loaded = GSON.fromJson(reader, new TypeToken<List<GrantEntry>>() {}.getType());
            return loaded != null ? new ArrayList<GrantEntry>(loaded) : new ArrayList<GrantEntry>();
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to load admin grants file: {}", e.getMessage());
            return new ArrayList<GrantEntry>();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private static void saveAllAtomic(List<GrantEntry> grants) {
        File file = grantFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        File tmp = new File(parent, file.getName() + ".tmp");
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(tmp, false));
            GSON.toJson(grants, writer);
            writer.flush();
            writer.close();
            writer = null;
            if (file.exists() && !file.delete()) {
                AdvanceDataMonitor.LOG.warn("[WebAE] Failed to remove old admin grants file");
            }
            if (!tmp.renameTo(file)) {
                AdvanceDataMonitor.LOG.error("[WebAE] Failed to rename temp admin grants file");
            }
        } catch (IOException e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to save admin grants file: {}", e.getMessage());
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {}
            }
        }
    }

    public static final class GrantEntry {

        public String adminToken;
        public String boundOwnerUuid;
        public String boundActorUuid;
        public String boundActorName;
        public String label;
        public long issuedAt;
        public long expiresAt;
        public long lastUsedAt;
    }
}
