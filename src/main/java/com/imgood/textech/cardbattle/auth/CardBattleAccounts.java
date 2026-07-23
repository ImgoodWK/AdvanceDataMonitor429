package com.imgood.textech.cardbattle.auth;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.TeXTechDataDir;

/**
 * JSON account store under {@code TeXTech/CardBattle/accounts/}, schema-compatible with
 * cardbattle-server Node.
 */
public final class CardBattleAccounts {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern USERNAME_RE = Pattern.compile("^[a-zA-Z0-9_]{3,32}$");
    private static final long SESSION_TTL_MS = 30L * 24L * 60L * 60L * 1000L;
    private static final long BIND_TTL_MS = 10L * 60L * 1000L;
    private static final Object LOCK = new Object();

    public static final class User {
        public String id;
        public String username;
        public String displayName;
        public String passwordHash;
        public String role;
        public String mcUuid;
        public String mcName;
        public Long boundAt;
        public long createdAt;
        public long updatedAt;
        public boolean disabled;
    }

    public static final class Session {
        public String token;
        public String userId;
        public long createdAt;
        public long expiresAt;
        public long lastUsedAt;
    }

    public static final class BindCode {
        public String code;
        public String mcUuid;
        public String mcName;
        public long createdAt;
        public long expiresAt;
        public Long consumedAt;
    }

    public static final class AuthResult {
        public final User user;
        public final String token;

        public AuthResult(User user, String token) {
            this.user = user;
            this.token = token;
        }
    }

    private CardBattleAccounts() {}

    private static File accountsDir() {
        File dir = new File(TeXTechDataDir.cardBattleRoot(), "accounts");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static File usersFile() {
        return new File(accountsDir(), "users.json");
    }

    private static File sessionsFile() {
        return new File(accountsDir(), "sessions.json");
    }

    private static File bindCodesFile() {
        return new File(accountsDir(), "bind-codes.json");
    }

    private static JsonObject readObject(File file) {
        if (!file.exists()) return new JsonObject();
        try {
            Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            try {
                JsonElement el = new JsonParser().parse(reader);
                return el != null && el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
            } finally {
                reader.close();
            }
        } catch (Throwable t) {
            return new JsonObject();
        }
    }

    private static void writeObject(File file, JsonObject obj) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        File tmp = new File(file.getAbsolutePath() + "." + UUID.randomUUID() + ".tmp");
        Writer writer = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8);
        try {
            GSON.toJson(obj, writer);
        } finally {
            writer.close();
        }
        if (file.exists() && !file.delete()) {
            // ignore
        }
        if (!tmp.renameTo(file)) {
            throw new IllegalStateException("failed to write " + file.getName());
        }
    }

    private static List<User> loadUsers() {
        JsonObject root = readObject(usersFile());
        JsonArray arr = root.has("users") && root.get("users")
            .isJsonArray() ? root.getAsJsonArray("users") : new JsonArray();
        List<User> out = new ArrayList<User>();
        for (JsonElement el : arr) {
            if (el != null && el.isJsonObject()) {
                out.add(GSON.fromJson(el, User.class));
            }
        }
        return out;
    }

    private static void saveUsers(List<User> users) throws Exception {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.add("users", GSON.toJsonTree(users));
        writeObject(usersFile(), root);
    }

    private static List<Session> loadSessions() {
        JsonObject root = readObject(sessionsFile());
        JsonArray arr = root.has("sessions") && root.get("sessions")
            .isJsonArray() ? root.getAsJsonArray("sessions") : new JsonArray();
        List<Session> out = new ArrayList<Session>();
        long now = System.currentTimeMillis();
        for (JsonElement el : arr) {
            if (el == null || !el.isJsonObject()) continue;
            Session s = GSON.fromJson(el, Session.class);
            if (s != null && s.expiresAt > now) out.add(s);
        }
        return out;
    }

    private static void saveSessions(List<Session> sessions) throws Exception {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.add("sessions", GSON.toJsonTree(sessions));
        writeObject(sessionsFile(), root);
    }

    private static List<BindCode> loadBindCodes() {
        JsonObject root = readObject(bindCodesFile());
        JsonArray arr = root.has("codes") && root.get("codes")
            .isJsonArray() ? root.getAsJsonArray("codes") : new JsonArray();
        List<BindCode> out = new ArrayList<BindCode>();
        long now = System.currentTimeMillis();
        for (JsonElement el : arr) {
            if (el == null || !el.isJsonObject()) continue;
            BindCode c = GSON.fromJson(el, BindCode.class);
            if (c != null && c.consumedAt == null && c.expiresAt > now) out.add(c);
        }
        return out;
    }

    private static void saveBindCodes(List<BindCode> codes) throws Exception {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.add("codes", GSON.toJsonTree(codes));
        writeObject(bindCodesFile(), root);
    }

    private static String randomToken() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        StringBuilder sb = new StringBuilder(raw.length * 2);
        for (byte b : raw) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    private static String randomBindCode() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private static Session createSession(String userId) {
        long now = System.currentTimeMillis();
        Session s = new Session();
        s.token = randomToken();
        s.userId = userId;
        s.createdAt = now;
        s.expiresAt = now + SESSION_TTL_MS;
        s.lastUsedAt = now;
        return s;
    }

    public static User findById(String id) {
        synchronized (LOCK) {
            for (User u : loadUsers()) {
                if (u != null && id != null && id.equals(u.id)) return u;
            }
            return null;
        }
    }

    public static User findByUsername(String username) {
        String key = CardBattlePassword.normalizeUsername(username);
        synchronized (LOCK) {
            for (User u : loadUsers()) {
                if (u != null && key.equals(u.username)) return u;
            }
            return null;
        }
    }

    public static User findByMcUuid(String mcUuid) {
        if (mcUuid == null) return null;
        String key = mcUuid.trim()
            .toLowerCase(Locale.ROOT);
        synchronized (LOCK) {
            for (User u : loadUsers()) {
                if (u != null && u.mcUuid != null && key.equals(u.mcUuid.toLowerCase(Locale.ROOT))) {
                    return u;
                }
            }
            return null;
        }
    }

    public static User resolveSession(String token) {
        if (token == null || token.isEmpty()) return null;
        synchronized (LOCK) {
            List<Session> sessions = loadSessions();
            Session hit = null;
            for (Session s : sessions) {
                if (token.equals(s.token)) {
                    hit = s;
                    break;
                }
            }
            if (hit == null) return null;
            hit.lastUsedAt = System.currentTimeMillis();
            try {
                saveSessions(sessions);
            } catch (Exception ignored) {}
            User user = findById(hit.userId);
            if (user == null || user.disabled) return null;
            return user;
        }
    }

    public static AuthResult register(String usernameRaw, String password, String displayName)
        throws Exception {
        String username = CardBattlePassword.normalizeUsername(usernameRaw);
        if (!USERNAME_RE.matcher(username)
            .matches()) {
            throw new IllegalArgumentException("用户名需为 3–32 位字母数字或下划线");
        }
        CardBattlePassword.assertPolicy(password);
        synchronized (LOCK) {
            List<User> users = loadUsers();
            for (User u : users) {
                if (username.equals(u.username)) {
                    throw new IllegalArgumentException("用户名已被占用");
                }
            }
            long now = System.currentTimeMillis();
            User user = new User();
            user.id = UUID.randomUUID()
                .toString();
            user.username = username;
            user.displayName = displayName != null && displayName.trim()
                .length() > 0 ? displayName.trim()
                    .substring(0, Math.min(48, displayName.trim()
                        .length()))
                    : username;
            user.passwordHash = CardBattlePassword.hash(password);
            user.role = "user";
            user.mcUuid = null;
            user.mcName = null;
            user.boundAt = null;
            user.createdAt = now;
            user.updatedAt = now;
            user.disabled = false;
            users.add(user);
            saveUsers(users);
            Session session = createSession(user.id);
            List<Session> sessions = loadSessions();
            sessions.add(session);
            saveSessions(sessions);
            return new AuthResult(user, session.token);
        }
    }

    public static AuthResult login(String usernameRaw, String password) throws Exception {
        synchronized (LOCK) {
            User user = findByUsername(usernameRaw);
            if (user == null || user.disabled || !CardBattlePassword.verify(password, user.passwordHash)) {
                throw new IllegalArgumentException("用户名或密码错误");
            }
            Session session = createSession(user.id);
            List<Session> sessions = loadSessions();
            sessions.add(session);
            saveSessions(sessions);
            return new AuthResult(user, session.token);
        }
    }

    public static void logout(String token) throws Exception {
        synchronized (LOCK) {
            List<Session> sessions = loadSessions();
            Iterator<Session> it = sessions.iterator();
            while (it.hasNext()) {
                Session s = it.next();
                if (token != null && token.equals(s.token)) it.remove();
            }
            saveSessions(sessions);
        }
    }

    public static void changePassword(String userId, String currentPassword, String newPassword)
        throws Exception {
        CardBattlePassword.assertPolicy(newPassword);
        synchronized (LOCK) {
            List<User> users = loadUsers();
            User user = null;
            for (User u : users) {
                if (userId.equals(u.id)) {
                    user = u;
                    break;
                }
            }
            if (user == null) throw new IllegalArgumentException("用户不存在");
            if (!CardBattlePassword.verify(currentPassword, user.passwordHash)) {
                throw new IllegalArgumentException("当前密码不正确");
            }
            user.passwordHash = CardBattlePassword.hash(newPassword);
            user.updatedAt = System.currentTimeMillis();
            saveUsers(users);
            List<Session> sessions = loadSessions();
            Iterator<Session> it = sessions.iterator();
            while (it.hasNext()) {
                if (userId.equals(it.next().userId)) it.remove();
            }
            saveSessions(sessions);
        }
    }

    public static BindCode issueBindCode(String mcUuid, String mcName) throws Exception {
        if (mcUuid == null || mcUuid.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("缺少 mcUuid");
        }
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            List<BindCode> codes = loadBindCodes();
            Iterator<BindCode> it = codes.iterator();
            while (it.hasNext()) {
                BindCode c = it.next();
                if (c.mcUuid != null && c.mcUuid.equalsIgnoreCase(mcUuid.trim())) it.remove();
            }
            BindCode entry = new BindCode();
            entry.code = randomBindCode();
            entry.mcUuid = mcUuid.trim();
            entry.mcName = mcName != null ? mcName : "Player";
            if (entry.mcName.length() > 32) entry.mcName = entry.mcName.substring(0, 32);
            entry.createdAt = now;
            entry.expiresAt = now + BIND_TTL_MS;
            entry.consumedAt = null;
            codes.add(entry);
            saveBindCodes(codes);
            return entry;
        }
    }

    public static User bindWithCode(String userId, String codeRaw) throws Exception {
        String code = codeRaw == null ? "" : codeRaw.trim()
            .toUpperCase(Locale.ROOT);
        if (code.isEmpty()) throw new IllegalArgumentException("请输入绑定码");
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            List<BindCode> codes = loadBindCodes();
            BindCode entry = null;
            int idx = -1;
            for (int i = 0; i < codes.size(); i++) {
                if (code.equals(codes.get(i).code)) {
                    entry = codes.get(i);
                    idx = i;
                    break;
                }
            }
            if (entry == null || entry.expiresAt <= now) {
                throw new IllegalArgumentException("绑定码无效或已过期");
            }
            List<User> users = loadUsers();
            User user = null;
            for (User u : users) {
                if (userId.equals(u.id)) user = u;
                if (u.mcUuid != null && u.mcUuid.equalsIgnoreCase(entry.mcUuid)) {
                    throw new IllegalArgumentException("该 MC 角色已绑定其他账号");
                }
            }
            if (user == null) throw new IllegalArgumentException("用户不存在");
            if (user.mcUuid != null) throw new IllegalArgumentException("已绑定角色，请先解绑");
            user.mcUuid = entry.mcUuid;
            user.mcName = entry.mcName;
            user.boundAt = now;
            user.updatedAt = now;
            codes.remove(idx);
            saveUsers(users);
            saveBindCodes(codes);
            return user;
        }
    }

    public static User unbind(String userId) throws Exception {
        synchronized (LOCK) {
            List<User> users = loadUsers();
            User user = null;
            for (User u : users) {
                if (userId.equals(u.id)) {
                    user = u;
                    break;
                }
            }
            if (user == null) throw new IllegalArgumentException("用户不存在");
            user.mcUuid = null;
            user.mcName = null;
            user.boundAt = null;
            user.updatedAt = System.currentTimeMillis();
            saveUsers(users);
            return user;
        }
    }

    public static JsonObject publicUser(User user) {
        JsonObject o = new JsonObject();
        o.addProperty("id", user.id);
        o.addProperty("username", user.username);
        o.addProperty("displayName", user.displayName);
        o.addProperty("role", user.role);
        if (user.mcUuid != null) o.addProperty("mcUuid", user.mcUuid);
        else o.add("mcUuid", null);
        if (user.mcName != null) o.addProperty("mcName", user.mcName);
        else o.add("mcName", null);
        if (user.boundAt != null) o.addProperty("boundAt", user.boundAt);
        else o.add("boundAt", null);
        o.addProperty("createdAt", user.createdAt);
        o.addProperty("disabled", user.disabled);
        return o;
    }

    public static boolean isAdmin(User user) {
        return user != null && ("admin".equals(user.role) || "superadmin".equals(user.role));
    }
}
