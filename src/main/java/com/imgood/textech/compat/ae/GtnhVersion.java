package com.imgood.textech.compat.ae;

/**
 * Parsed GTNH pack or AE2 mod version for comparison.
 * Supports {@code 2.9.0-beta-1} style pack versions and {@code rv3-beta-977-GTNH} AE2 tags.
 */
public final class GtnhVersion implements Comparable<GtnhVersion> {

    public static final GtnhVersion THRESHOLD_290_BETA1 = parse("2.9.0-beta-1");

    /** AE2 rv3-beta number at or above this implies native fluid support (2.9.0 line uses 977). */
    public static final int AE2_NATIVE_MIN_BETA = 900;

    private final int major;
    private final int minor;
    private final int patch;
    /** {@code 0=none, 1=alpha, 2=beta, 3=rc} */
    private final int preReleaseRank;
    private final int preReleaseNumber;
    /** For AE2 {@code rv3-beta-NNN-GTNH} only; {@code -1} when not AE2 format. */
    private final int ae2BetaNumber;
    private final String raw;

    private GtnhVersion(String raw, int major, int minor, int patch, int preReleaseRank, int preReleaseNumber,
        int ae2BetaNumber) {
        this.raw = raw == null ? "" : raw;
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.preReleaseRank = preReleaseRank;
        this.preReleaseNumber = preReleaseNumber;
        this.ae2BetaNumber = ae2BetaNumber;
    }

    public static GtnhVersion parse(String raw) {
        if (raw == null || raw.trim()
            .isEmpty()) {
            return new GtnhVersion(raw, 0, 0, 0, 0, 0, -1);
        }
        String trimmed = raw.trim();
        int ae2Beta = parseAe2BetaNumber(trimmed);
        if (ae2Beta >= 0) {
            return new GtnhVersion(trimmed, 3, 0, 0, 2, ae2Beta, ae2Beta);
        }

        String[] mainAndPre = trimmed.split("-", 2);
        String main = mainAndPre[0];
        String pre = mainAndPre.length > 1 ? mainAndPre[1] : "";

        int major = 0;
        int minor = 0;
        int patch = 0;
        String[] parts = main.split("\\.");
        if (parts.length > 0) {
            major = parseInt(parts[0]);
        }
        if (parts.length > 1) {
            minor = parseInt(parts[1]);
        }
        if (parts.length > 2) {
            patch = parseInt(parts[2]);
        }

        int preRank = 0;
        int preNum = 0;
        if (!pre.isEmpty()) {
            String lower = pre.toLowerCase();
            if (lower.startsWith("alpha")) {
                preRank = 1;
                preNum = parseTrailingInt(lower.substring(5));
            } else if (lower.startsWith("beta")) {
                preRank = 2;
                preNum = parseTrailingInt(lower.substring(4));
            } else if (lower.startsWith("rc")) {
                preRank = 3;
                preNum = parseTrailingInt(lower.substring(2));
            }
        }

        return new GtnhVersion(trimmed, major, minor, patch, preRank, preNum, -1);
    }

    private static int parseAe2BetaNumber(String raw) {
        String lower = raw.toLowerCase();
        if (!lower.startsWith("rv3-beta-")) {
            return -1;
        }
        String rest = lower.substring("rv3-beta-".length());
        int dash = rest.indexOf('-');
        String numPart = dash >= 0 ? rest.substring(0, dash) : rest;
        try {
            return Integer.parseInt(numPart);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static int parseTrailingInt(String suffix) {
        if (suffix == null || suffix.isEmpty()) {
            return 0;
        }
        String digits = suffix.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 0;
        }
        return parseInt(digits);
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9-]", ""));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public boolean isAe2Format() {
        return ae2BetaNumber >= 0;
    }

    public int getAe2BetaNumber() {
        return ae2BetaNumber;
    }

    public String getRaw() {
        return raw;
    }

    /** {@code true} when this version is at or above GTNH 2.9.0-beta-1. */
    public boolean isAtLeast290Beta1() {
        return compareTo(THRESHOLD_290_BETA1) >= 0;
    }

    /** {@code true} when AE2 rv3-beta number meets the native-fluid threshold. */
    public boolean isAe2NativeFluidCapable() {
        return ae2BetaNumber >= AE2_NATIVE_MIN_BETA;
    }

    @Override
    public int compareTo(GtnhVersion other) {
        if (other == null) {
            return 1;
        }
        if (this.ae2BetaNumber >= 0 && other.ae2BetaNumber >= 0) {
            return Integer.compare(this.ae2BetaNumber, other.ae2BetaNumber);
        }
        if (this.ae2BetaNumber >= 0) {
            return this.isAe2NativeFluidCapable() ? 1 : -1;
        }
        if (other.ae2BetaNumber >= 0) {
            return other.isAe2NativeFluidCapable() ? -1 : 1;
        }

        int cmp = Integer.compare(this.major, other.major);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(this.minor, other.minor);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(this.patch, other.patch);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(this.preReleaseRank, other.preReleaseRank);
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(this.preReleaseNumber, other.preReleaseNumber);
    }

    @Override
    public String toString() {
        return raw;
    }
}
