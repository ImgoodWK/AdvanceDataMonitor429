package com.imgood.textech.cardbattle.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Password encoding compatible with cardbattle-server Node:
 * {@code pbkdf2$sha256$iterations$saltB64$hashB64}
 */
public final class CardBattlePassword {

    private static final int ITERATIONS = 120_000;
    private static final int KEY_LEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CardBattlePassword() {}

    public static String hash(String password) throws Exception {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS, KEY_LEN_BYTES);
        return "pbkdf2$sha256$" + ITERATIONS + "$" + b64(salt) + "$" + b64(hash);
    }

    public static boolean verify(String password, String encoded) {
        try {
            String[] parts = encoded.split("\\$");
            if (parts.length != 5 || !"pbkdf2".equals(parts[0]) || !"sha256".equals(parts[1])) {
                return false;
            }
            int iterations = Integer.parseInt(parts[2]);
            if (iterations < 10_000) return false;
            byte[] salt = deb64(parts[3]);
            byte[] expected = deb64(parts[4]);
            byte[] actual = pbkdf2(password, salt, iterations, expected.length);
            return MessageDigest.isEqual(actual, expected);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void assertPolicy(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("密码至少 8 位");
        }
        if (password.length() > 128) {
            throw new IllegalArgumentException("密码过长");
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations, int keyLen)
        throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, keyLen * 8);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return skf.generateSecret(spec)
            .getEncoded();
    }

    private static String b64(byte[] raw) {
        return Base64.getEncoder()
            .encodeToString(raw);
    }

    private static byte[] deb64(String s) {
        return Base64.getDecoder()
            .decode(s);
    }

    public static String normalizeUsername(String username) {
        return username == null ? "" : username.trim()
            .toLowerCase(Locale.ROOT);
    }

    public static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
