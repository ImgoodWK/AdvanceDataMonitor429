package com.imgood.textech.webae.auth;

import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived 6-digit login codes for owner self-service WebAE binding (Phase 5.1).
 * Codes expire after 5 minutes and are single-use.
 */
public final class WebLoginCodeStore {

    private static final long TTL_MS = 5L * 60_000L;
    private static final int MAX_ATTEMPTS = 32;
    private static final Random RANDOM = new Random();

    private static final ConcurrentHashMap<String, LoginCodeEntry> CODES = new ConcurrentHashMap<String, LoginCodeEntry>();

    private WebLoginCodeStore() {}

    public static String generateCode(String ownerUuid, String ownerName) {
        if (ownerUuid == null || ownerUuid.isEmpty()) {
            return null;
        }
        purgeExpired();
        invalidateCodesForOwner(ownerUuid);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = formatSixDigits(100_000 + RANDOM.nextInt(900_000));
            LoginCodeEntry entry = new LoginCodeEntry();
            entry.code = code;
            entry.ownerUuid = ownerUuid;
            entry.ownerName = ownerName != null ? ownerName : "";
            entry.createdAt = System.currentTimeMillis();
            if (CODES.putIfAbsent(code, entry) == null) {
                return code;
            }
        }
        return null;
    }

    public static ExchangeResult exchange(String code) {
        if (code == null) {
            return ExchangeResult.failure("missing_code");
        }
        String normalized = code.trim();
        if (normalized.length() != 6) {
            return ExchangeResult.failure("invalid_code");
        }
        purgeExpired();
        LoginCodeEntry entry = CODES.remove(normalized);
        if (entry == null) {
            return ExchangeResult.failure("invalid_or_used");
        }
        if (System.currentTimeMillis() - entry.createdAt > TTL_MS) {
            return ExchangeResult.failure("expired");
        }
        return ExchangeResult.success(entry.ownerUuid, entry.ownerName);
    }

    private static void invalidateCodesForOwner(String ownerUuid) {
        Iterator<Map.Entry<String, LoginCodeEntry>> iter = CODES.entrySet()
            .iterator();
        while (iter.hasNext()) {
            LoginCodeEntry entry = iter.next()
                .getValue();
            if (entry != null && ownerUuid.equals(entry.ownerUuid)) {
                iter.remove();
            }
        }
    }

    private static void purgeExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, LoginCodeEntry>> iter = CODES.entrySet()
            .iterator();
        while (iter.hasNext()) {
            LoginCodeEntry entry = iter.next()
                .getValue();
            if (entry == null || now - entry.createdAt > TTL_MS) {
                iter.remove();
            }
        }
    }

    private static String formatSixDigits(int value) {
        String s = Integer.toString(value);
        while (s.length() < 6) {
            s = "0" + s;
        }
        return s;
    }

    private static final class LoginCodeEntry {

        String code;
        String ownerUuid;
        String ownerName;
        long createdAt;
    }

    public static final class ExchangeResult {

        public final boolean success;
        public final String ownerUuid;
        public final String ownerName;
        public final String errorCode;

        private ExchangeResult(boolean success, String ownerUuid, String ownerName, String errorCode) {
            this.success = success;
            this.ownerUuid = ownerUuid;
            this.ownerName = ownerName;
            this.errorCode = errorCode;
        }

        public static ExchangeResult success(String ownerUuid, String ownerName) {
            return new ExchangeResult(true, ownerUuid, ownerName, null);
        }

        public static ExchangeResult failure(String errorCode) {
            return new ExchangeResult(false, null, null, errorCode);
        }
    }
}
