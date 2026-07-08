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
import com.imgood.textech.webae.context.WebAeOwnerContext;

public class WebAuthToken {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();

    private static File tokenFile() {
        return TeXTechDataDir.webAeFile("web-tokens.json");
    }

    private static List<WebAuthToken> tokenCache;

    private static volatile boolean dirty;

    private static volatile boolean savePending;

    public String token;

    public String type;

    public String ownerUuid;

    public String actorUuid;

    public String actorName;

    /** Legacy JSON field — migrated to {@link #ownerUuid}. */
    public String playerUuid;

    public long issuedAt;

    public long lastUsedAt;

    public WebAuthToken() {}

    private WebAuthToken(String ownerUuid, String actorUuid, String actorName, String type, String tokenValue,
        long issuedAt, long lastUsedAt) {
        this.ownerUuid = ownerUuid;
        this.actorUuid = actorUuid;
        this.actorName = actorName;
        this.type = type;
        this.playerUuid = ownerUuid;
        this.token = tokenValue;
        this.issuedAt = issuedAt;
        this.lastUsedAt = lastUsedAt;
    }

    public static WebAuthToken generateOwnerToken(String ownerUuid, String ownerName) {
        WebAuthToken authToken = new WebAuthToken(
            ownerUuid,
            ownerUuid,
            ownerName,
            WebAuthSession.TYPE_OWNER,
            UUID.randomUUID()
                .toString(),
            System.currentTimeMillis(),
            System.currentTimeMillis());
        WebAeOwnerContext.cacheOwnerName(ownerUuid, ownerName);

        List<WebAuthToken> tokens = getTokenList();
        synchronized (tokens) {
            Iterator<WebAuthToken> iter = tokens.iterator();
            while (iter.hasNext()) {
                WebAuthToken existing = iter.next();
                migrateEntry(existing);
                if (WebAuthSession.TYPE_OWNER.equals(existing.type) && ownerUuid.equals(existing.ownerUuid)) {
                    iter.remove();
                }
            }
            tokens.add(authToken);
        }
        scheduleSave();
        return authToken;
    }

    /** @deprecated use {@link #generateOwnerToken(String, String)} */
    public static WebAuthToken generateToken(String playerUuid) {
        String name = WebAeOwnerContext.resolveOwnerName(playerUuid);
        if (name.isEmpty()) {
            name = "Player";
        }
        return generateOwnerToken(playerUuid, name);
    }

    /**
     * Shareable guest invite link (not tied to a specific online player).
     */
    public static WebAuthToken generateShareGuestToken(String ownerUuid, String ownerName) {
        String inviteId = UUID.randomUUID()
            .toString();
        return generateGuestToken(ownerUuid, ownerName, "invite:" + inviteId, "Guest");
    }

    public static WebAuthToken generateGuestToken(String ownerUuid, String ownerName, String guestUuid,
        String guestName) {
        WebAuthToken authToken = new WebAuthToken(
            ownerUuid,
            guestUuid,
            guestName,
            WebAuthSession.TYPE_GUEST,
            UUID.randomUUID()
                .toString(),
            System.currentTimeMillis(),
            System.currentTimeMillis());
        WebAeOwnerContext.cacheOwnerName(ownerUuid, ownerName);

        List<WebAuthToken> tokens = getTokenList();
        synchronized (tokens) {
            Iterator<WebAuthToken> iter = tokens.iterator();
            while (iter.hasNext()) {
                WebAuthToken existing = iter.next();
                migrateEntry(existing);
                if (WebAuthSession.TYPE_GUEST.equals(existing.type) && ownerUuid.equals(existing.ownerUuid)
                    && guestUuid.equals(existing.actorUuid)) {
                    iter.remove();
                }
            }
            tokens.add(authToken);
        }
        scheduleSave();
        return authToken;
    }

    public static WebAuthSession validateToken(String tokenValue) {
        if (tokenValue == null || tokenValue.isEmpty()) {
            return null;
        }
        List<WebAuthToken> tokens = getTokenList();
        synchronized (tokens) {
            for (WebAuthToken t : tokens) {
                migrateEntry(t);
                if (t.token.equals(tokenValue)) {
                    int ttlHours = Config.webTokenLifetimeHours;
                    if (ttlHours > 0) {
                        long ttlMs = (long) ttlHours * 3600_000L;
                        if (System.currentTimeMillis() - t.issuedAt > ttlMs) {
                            return null;
                        }
                    }
                    t.lastUsedAt = System.currentTimeMillis();
                    scheduleSave();
                    return new WebAuthSession(t.token, t.type, t.ownerUuid, t.actorUuid, t.actorName);
                }
            }
        }
        return null;
    }

    public static boolean revokeTokenByToken(String tokenValue) {
        List<WebAuthToken> tokens = getTokenList();
        boolean removed;
        synchronized (tokens) {
            Iterator<WebAuthToken> iter = tokens.iterator();
            removed = false;
            while (iter.hasNext()) {
                if (iter.next().token.equals(tokenValue)) {
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

    public static String revokeOwnerToken(String ownerUuid) {
        List<WebAuthToken> tokens = getTokenList();
        WebAuthToken found = null;
        synchronized (tokens) {
            Iterator<WebAuthToken> iter = tokens.iterator();
            while (iter.hasNext()) {
                WebAuthToken t = iter.next();
                migrateEntry(t);
                if (WebAuthSession.TYPE_OWNER.equals(t.type) && ownerUuid.equals(t.ownerUuid)) {
                    found = t;
                    iter.remove();
                    break;
                }
            }
        }
        if (found != null) {
            scheduleSave();
            return found.token;
        }
        return null;
    }

    /** @deprecated use {@link #revokeOwnerToken(String)} */
    public static String revokeTokenByPlayerUuid(String playerUuid) {
        return revokeOwnerToken(playerUuid);
    }

    public static String revokeGuestToken(String ownerUuid, String guestUuid) {
        List<WebAuthToken> tokens = getTokenList();
        WebAuthToken found = null;
        synchronized (tokens) {
            Iterator<WebAuthToken> iter = tokens.iterator();
            while (iter.hasNext()) {
                WebAuthToken t = iter.next();
                migrateEntry(t);
                if (WebAuthSession.TYPE_GUEST.equals(t.type) && ownerUuid.equals(t.ownerUuid)
                    && guestUuid.equals(t.actorUuid)) {
                    found = t;
                    iter.remove();
                    break;
                }
            }
        }
        if (found != null) {
            scheduleSave();
            return found.token;
        }
        return null;
    }

    public static String revokeGuestTokenByActorName(String ownerUuid, String guestName) {
        if (guestName == null || guestName.isEmpty()) {
            return null;
        }
        List<WebAuthToken> tokens = getTokenList();
        WebAuthToken found = null;
        synchronized (tokens) {
            Iterator<WebAuthToken> iter = tokens.iterator();
            while (iter.hasNext()) {
                WebAuthToken t = iter.next();
                migrateEntry(t);
                if (WebAuthSession.TYPE_GUEST.equals(t.type) && ownerUuid.equals(t.ownerUuid)
                    && guestName.equalsIgnoreCase(t.actorName)) {
                    found = t;
                    iter.remove();
                    break;
                }
            }
        }
        if (found != null) {
            scheduleSave();
            return found.token;
        }
        return null;
    }

    public static List<WebAuthToken> listAll() {
        List<WebAuthToken> tokens = getTokenList();
        synchronized (tokens) {
            List<WebAuthToken> copy = new ArrayList<WebAuthToken>();
            for (WebAuthToken t : tokens) {
                migrateEntry(t);
                copy.add(t);
            }
            return copy;
        }
    }

    /** Unique owner UUIDs with non-expired WebAE tokens (for alert polling). */
    public static List<String> listActiveOwnerUuids() {
        List<String> owners = new ArrayList<String>();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        long now = System.currentTimeMillis();
        List<WebAuthToken> tokens = getTokenList();
        synchronized (tokens) {
            for (WebAuthToken t : tokens) {
                migrateEntry(t);
                if (t.ownerUuid == null || t.ownerUuid.isEmpty()) {
                    continue;
                }
                if (isExpired(t, now)) {
                    continue;
                }
                if (seen.add(t.ownerUuid)) {
                    owners.add(t.ownerUuid);
                }
            }
        }
        return owners;
    }

    private static boolean isExpired(WebAuthToken t, long now) {
        int hours = Config.webTokenLifetimeHours;
        if (hours <= 0 || t.issuedAt <= 0) {
            return false;
        }
        return now - t.issuedAt > (long) hours * 3600_000L;
    }

    public static WebAuthToken findByActorUuid(String actorUuid) {
        if (actorUuid == null || actorUuid.isEmpty()) {
            return null;
        }
        List<WebAuthToken> tokens = getTokenList();
        synchronized (tokens) {
            for (WebAuthToken t : tokens) {
                migrateEntry(t);
                if (actorUuid.equals(t.actorUuid)) {
                    return t;
                }
            }
        }
        return null;
    }

    private static void migrateEntry(WebAuthToken t) {
        if (t == null) {
            return;
        }
        if (t.ownerUuid == null || t.ownerUuid.isEmpty()) {
            t.ownerUuid = t.playerUuid;
        }
        if (t.actorUuid == null || t.actorUuid.isEmpty()) {
            t.actorUuid = t.ownerUuid;
        }
        if (t.type == null || t.type.isEmpty()) {
            t.type = WebAuthSession.TYPE_OWNER;
        }
        if (t.actorName == null) {
            t.actorName = "";
        }
        if (t.playerUuid == null || t.playerUuid.isEmpty()) {
            t.playerUuid = t.ownerUuid;
        }
    }

    private static List<WebAuthToken> getTokenList() {
        if (tokenCache == null) {
            synchronized (WebAuthToken.class) {
                if (tokenCache == null) {
                    tokenCache = loadAllFromDisk();
                }
            }
        }
        return tokenCache;
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
                        List<WebAuthToken> snapshot;
                        synchronized (getTokenList()) {
                            snapshot = new ArrayList<WebAuthToken>(tokenCache);
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

    private static List<WebAuthToken> loadAllFromDisk() {
        File tokenFile = tokenFile();
        if (!tokenFile.exists()) {
            return new ArrayList<WebAuthToken>();
        }
        FileReader reader = null;
        try {
            reader = new FileReader(tokenFile);
            List<WebAuthToken> tokens = GSON.fromJson(reader, new TypeToken<List<WebAuthToken>>() {}.getType());
            List<WebAuthToken> result = tokens != null ? new ArrayList<WebAuthToken>(tokens)
                : new ArrayList<WebAuthToken>();
            for (WebAuthToken t : result) {
                migrateEntry(t);
            }
            return result;
        } catch (IOException e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to load tokens file: {}", e.getMessage());
            return new ArrayList<WebAuthToken>();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private static void saveAllAtomic(List<WebAuthToken> tokens) {
        File tokenFile = tokenFile();
        File parent = tokenFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        File tmp = new File(tokenFile.getParentFile(), tokenFile.getName() + ".tmp");
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(tmp, false));
            GSON.toJson(tokens, writer);
            writer.flush();
            writer.close();
            writer = null;
            if (tokenFile.exists() && !tokenFile.delete()) {
                AdvanceDataMonitor.LOG.warn("[WebAE] Failed to remove old tokens file before rename");
            }
            if (!tmp.renameTo(tokenFile)) {
                AdvanceDataMonitor.LOG.error("[WebAE] Failed to rename temp tokens file");
            }
        } catch (IOException e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to save tokens file: {}", e.getMessage());
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {}
            }
        }
    }
}
