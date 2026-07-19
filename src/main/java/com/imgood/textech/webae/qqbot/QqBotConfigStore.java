package com.imgood.textech.webae.qqbot;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.TeXTechDataDir;

/**
 * Encrypted server-local QQ bot configuration. Public views expose only a
 * secret-presence flag and a short suffix hint.
 */
public final class QqBotConfigStore {

    private static final String SETTINGS_FILE = "qq-bot.json";
    private static final String MASTER_KEY_FILE = "qq-bot-master.key";
    private static final String CIPHER_PREFIX = "aes-gcm-v1:";
    private static final int MASTER_KEY_BYTES = 32;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final QqBotConfigStore INSTANCE = new QqBotConfigStore();

    private StoredConfig stored;

    private QqBotConfigStore() {}

    public static QqBotConfigStore instance() {
        return INSTANCE;
    }

    public synchronized QqBotConfig view() {
        StoredConfig value = load();
        QqBotConfig view = copy(value.settings);
        view.appSecret = "";
        view.appSecretConfigured = !safe(value.encryptedAppSecret).isEmpty();
        view.appSecretHint = view.appSecretConfigured ? safe(value.appSecretHint) : "";
        return view;
    }

    public synchronized RuntimeConfig runtime() {
        StoredConfig value = load();
        RuntimeConfig runtime = new RuntimeConfig();
        runtime.settings = copy(value.settings);
        runtime.settings.appSecret = "";
        runtime.settings.appSecretConfigured = !safe(value.encryptedAppSecret).isEmpty();
        runtime.settings.appSecretHint = runtime.settings.appSecretConfigured ? safe(value.appSecretHint) : "";
        runtime.appSecret = decrypt(value.encryptedAppSecret);
        runtime.validationError = QqBotConfigValidator.validate(runtime.settings, !runtime.appSecret.isEmpty());
        runtime.configured = runtime.validationError == null && !safe(runtime.settings.appId).isEmpty()
            && !runtime.appSecret.isEmpty();
        return runtime;
    }

    public synchronized QqBotConfig update(QqBotConfig incoming, String actorName) {
        StoredConfig current = load();
        QqBotConfig next = QqBotConfigValidator.normalize(copy(incoming));
        String submittedSecret = safe(next.appSecret);
        boolean replaceSecret = !submittedSecret.isEmpty() && !looksMasked(submittedSecret);
        boolean hasSecret = replaceSecret || !safe(current.encryptedAppSecret).isEmpty();
        String validation = QqBotConfigValidator.validate(next, hasSecret);
        if (validation != null) throw new IllegalArgumentException(validation);

        if (replaceSecret) {
            current.encryptedAppSecret = encrypt(submittedSecret);
            current.appSecretHint = mask(submittedSecret);
        }
        next.appSecret = "";
        next.appSecretConfigured = false;
        next.appSecretHint = "";
        current.settings = next;
        current.updatedAt = System.currentTimeMillis();
        current.updatedBy = truncate(safe(actorName), 80);
        save(current);
        stored = current;
        return view();
    }

    public synchronized QqBotConfig clearSecret(String actorName) {
        StoredConfig current = load();
        current.encryptedAppSecret = "";
        current.appSecretHint = "";
        if (current.settings != null) current.settings.enabled = false;
        current.updatedAt = System.currentTimeMillis();
        current.updatedBy = truncate(safe(actorName), 80);
        save(current);
        stored = current;
        return view();
    }

    public synchronized long updatedAt() {
        return load().updatedAt;
    }

    public synchronized String updatedBy() {
        return safe(load().updatedBy);
    }

    synchronized void resetForTest() {
        stored = null;
    }

    private StoredConfig load() {
        if (stored != null) return stored;
        File file = settingsFile();
        if (!file.isFile()) {
            stored = defaults();
            save(stored);
            return stored;
        }
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StoredConfig loaded = GSON.fromJson(reader, StoredConfig.class);
            if (loaded == null) loaded = defaults();
            if (loaded.settings == null) loaded.settings = new QqBotConfig();
            loaded.settings = QqBotConfigValidator.normalize(loaded.settings);
            loaded.settings.appSecret = "";
            loaded.settings.appSecretConfigured = false;
            loaded.settings.appSecretHint = "";
            loaded.encryptedAppSecret = safe(loaded.encryptedAppSecret);
            loaded.appSecretHint = safe(loaded.appSecretHint);
            stored = loaded;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE/QQBot] Failed to read qq-bot.json; using safe disabled defaults", e);
            stored = defaults();
        }
        return stored;
    }

    private void save(StoredConfig value) {
        File file = settingsFile();
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        File temp = new File(parent, file.getName() + ".tmp");
        try (BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(temp), StandardCharsets.UTF_8))) {
            GSON.toJson(value, writer);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write QQ bot settings.", e);
        }
        restrictOwnerOnly(temp);
        try {
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            restrictOwnerOnly(file);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to replace QQ bot settings.", e);
        }
    }

    private String encrypt(String value) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(masterKey(), "AES"),
                new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return CIPHER_PREFIX + Base64.getEncoder().encodeToString(iv) + ":"
                + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt QQ ClientSecret.", e);
        }
    }

    private String decrypt(String encryptedValue) {
        String value = safe(encryptedValue);
        if (value.isEmpty()) return "";
        try {
            if (!value.startsWith(CIPHER_PREFIX)) throw new IllegalStateException("Unknown secret format.");
            String[] parts = value.substring(CIPHER_PREFIX.length()).split(":", 2);
            if (parts.length != 2) throw new IllegalStateException("Invalid secret format.");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(masterKey(), "AES"),
                new GCMParameterSpec(GCM_TAG_BITS, Base64.getDecoder().decode(parts[0])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error(
                "[WebAE/QQBot] Failed to decrypt ClientSecret; re-enter it in the admin console.");
            return "";
        }
    }

    private byte[] masterKey() throws Exception {
        File file = masterKeyFile();
        if (file.isFile()) {
            byte[] decoded = Base64.getDecoder().decode(
                new String(Files.readAllBytes(file.toPath()), StandardCharsets.US_ASCII).trim());
            if (decoded.length != MASTER_KEY_BYTES) throw new IllegalStateException("Invalid QQ bot master key.");
            return decoded;
        }
        byte[] key = new byte[MASTER_KEY_BYTES];
        RANDOM.nextBytes(key);
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        File temp = new File(parent, file.getName() + ".tmp");
        Files.write(temp.toPath(), Base64.getEncoder().encode(key));
        restrictOwnerOnly(temp);
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp.toPath(), file.toPath());
        }
        restrictOwnerOnly(file);
        return key;
    }

    private static StoredConfig defaults() {
        StoredConfig value = new StoredConfig();
        value.settings = new QqBotConfig();
        value.updatedAt = System.currentTimeMillis();
        value.updatedBy = "defaults";
        return value;
    }

    private static QqBotConfig copy(QqBotConfig value) {
        return GSON.fromJson(GSON.toJson(value == null ? new QqBotConfig() : value), QqBotConfig.class);
    }

    private static boolean looksMasked(String value) {
        return value.startsWith("***") || value.startsWith("••••");
    }

    private static String mask(String value) {
        int visible = Math.min(4, value.length());
        return "••••" + value.substring(value.length() - visible);
    }

    private static void restrictOwnerOnly(File file) {
        if (file == null || !file.exists()) return;
        try {
            file.setReadable(false, false);
            file.setWritable(false, false);
            file.setExecutable(false, false);
            file.setReadable(true, true);
            file.setWritable(true, true);
        } catch (SecurityException ignored) {}
    }

    private static File settingsFile() {
        return TeXTechDataDir.webAeFile(SETTINGS_FILE);
    }

    private static File masterKeyFile() {
        return TeXTechDataDir.webAeFile(MASTER_KEY_FILE);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static final class StoredConfig {

        int version = 1;
        QqBotConfig settings;
        String encryptedAppSecret = "";
        String appSecretHint = "";
        long updatedAt;
        String updatedBy = "";
    }

    public static final class RuntimeConfig {

        public QqBotConfig settings;
        public String appSecret = "";
        public boolean configured;
        public String validationError = "";
    }
}
