package com.imgood.textech.webae.diagnostics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only, owner-scoped health result for one WebAE network.
 *
 * <p>
 * The fields are deliberately simple public DTO fields. They are consumed
 * by Gson on the HTTP path and by the diagnostics/assistant integrations, while
 * the sampling code that fills them is restricted to the server thread.
 * </p>
 */
public final class NetworkHealthDiagnosticDto {

    public static final int SCHEMA_VERSION = 1;

    public int schemaVersion = SCHEMA_VERSION;
    public String ownerUuid;
    public int networkId;
    public String networkKey;
    public String status = NetworkHealthStatusEvaluator.UNKNOWN;
    public long checkedAt;
    public Long sampleAgeMs;
    public boolean stale;

    public Links links = new Links();
    public Monitors monitors = new Monitors();
    public Grid grid = new Grid();
    public Channels channels = new Channels();
    public List<Issue> issues = new ArrayList<Issue>();

    public NetworkHealthDiagnosticDto() {}

    public NetworkHealthDiagnosticDto(String ownerUuid, int networkId, String networkKey) {
        this.ownerUuid = ownerUuid;
        this.networkId = networkId;
        this.networkKey = networkKey;
    }

    /**
     * Build the explicit no-sample response used before the first server-side
     * sample (and after a cache has expired). It is intentionally not healthy.
     */
    public static NetworkHealthDiagnosticDto unknown(String ownerUuid, int networkId, String networkKey) {
        NetworkHealthDiagnosticDto dto = new NetworkHealthDiagnosticDto(ownerUuid, networkId, networkKey);
        dto.status = NetworkHealthStatusEvaluator.UNKNOWN;
        dto.checkedAt = 0L;
        dto.sampleAgeMs = null;
        dto.stale = true;
        dto.issues.add(
            Issue.unknown(
                "sample_stale",
                "webae.networkHealth.issue.sampleStale",
                "webae.networkHealth.suggestion.waitForSample",
                "no_sample"));
        return dto;
    }

    /** Deep copy suitable for adding request-time freshness without mutating the cache. */
    public NetworkHealthDiagnosticDto copy() {
        NetworkHealthDiagnosticDto out = new NetworkHealthDiagnosticDto(ownerUuid, networkId, networkKey);
        out.schemaVersion = schemaVersion;
        out.status = status;
        out.checkedAt = checkedAt;
        out.sampleAgeMs = sampleAgeMs;
        out.stale = stale;
        out.links = links == null ? new Links() : links.copy();
        out.monitors = monitors == null ? new Monitors() : monitors.copy();
        out.grid = grid == null ? new Grid() : grid.copy();
        out.channels = channels == null ? new Channels() : channels.copy();
        out.issues = new ArrayList<Issue>();
        if (issues != null) {
            for (Issue issue : issues) {
                if (issue != null) {
                    out.issues.add(issue.copy());
                }
            }
        }
        return out;
    }

    public static final class Links {

        public Boolean registered;
        public Boolean loaded;
        public Boolean reachable;

        Links copy() {
            Links out = new Links();
            out.registered = registered;
            out.loaded = loaded;
            out.reachable = reachable;
            return out;
        }
    }

    public static final class Monitors {

        public Boolean registered;
        public Boolean bound;
        public Boolean valid;

        Monitors copy() {
            Monitors out = new Monitors();
            out.registered = registered;
            out.bound = bound;
            out.valid = valid;
            return out;
        }
    }

    public static final class Grid {

        public Boolean present;
        public Boolean storageAvailable;
        public Boolean craftingAvailable;
        public Boolean connectorAvailable;

        Grid copy() {
            Grid out = new Grid();
            out.present = present;
            out.storageAvailable = storageAvailable;
            out.craftingAvailable = craftingAvailable;
            out.connectorAvailable = connectorAvailable;
            return out;
        }
    }

    public static final class Channels {

        public Boolean available;
        public Integer used;
        public Integer max;

        Channels copy() {
            Channels out = new Channels();
            out.available = available;
            out.used = used;
            out.max = max;
            return out;
        }
    }

    public static final class Issue {

        public String code;
        public String severity;
        public String messageKey;
        public String suggestionKey;
        /** Structured evidence is kept opaque so integrations can add details without a new DTO. */
        public Object evidence;

        public Issue() {}

        public Issue(String code, String severity, String messageKey, String suggestionKey, Object evidence) {
            this.code = code;
            this.severity = severity;
            this.messageKey = messageKey;
            this.suggestionKey = suggestionKey;
            this.evidence = evidence;
        }

        public static Issue info(String code, String messageKey, String suggestionKey, Object evidence) {
            return new Issue(code, NetworkHealthStatusEvaluator.INFO, messageKey, suggestionKey, evidence);
        }

        public static Issue warning(String code, String messageKey, String suggestionKey, Object evidence) {
            return new Issue(code, NetworkHealthStatusEvaluator.WARNING, messageKey, suggestionKey, evidence);
        }

        public static Issue error(String code, String messageKey, String suggestionKey, Object evidence) {
            return new Issue(code, NetworkHealthStatusEvaluator.ERROR, messageKey, suggestionKey, evidence);
        }

        public static Issue unknown(String code, String messageKey, String suggestionKey, Object evidence) {
            return new Issue(code, NetworkHealthStatusEvaluator.UNKNOWN, messageKey, suggestionKey, evidence);
        }

        Issue copy() {
            Object copiedEvidence = evidence;
            if (evidence instanceof Map<?, ?>) {
                Map<String, Object> map = new LinkedHashMap<String, Object>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) evidence).entrySet()) {
                    if (entry.getKey() != null) {
                        map.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                copiedEvidence = map;
            } else if (evidence instanceof List<?>) {
                copiedEvidence = new ArrayList<Object>((List<?>) evidence);
            }
            return new Issue(code, severity, messageKey, suggestionKey, copiedEvidence);
        }
    }
}
