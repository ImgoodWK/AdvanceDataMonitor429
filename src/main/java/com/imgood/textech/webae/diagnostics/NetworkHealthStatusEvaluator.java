package com.imgood.textech.webae.diagnostics;

import java.util.List;

/** Pure status rules for network-health DTOs. No Minecraft classes are used. */
public final class NetworkHealthStatusEvaluator {

    public static final String HEALTHY = "healthy";
    public static final String DEGRADED = "degraded";
    public static final String FAILED = "failed";
    public static final String UNKNOWN = "unknown";

    public static final String INFO = "info";
    public static final String WARNING = "warning";
    public static final String ERROR = "error";

    private NetworkHealthStatusEvaluator() {}

    /** Evaluate the contract status without mutating the DTO. */
    public static String evaluate(NetworkHealthDiagnosticDto dto) {
        if (dto == null) {
            return UNKNOWN;
        }

        boolean error = false;
        boolean warning = false;
        boolean unknownIssue = false;
        List<NetworkHealthDiagnosticDto.Issue> issues = dto.issues;
        if (issues != null) {
            for (NetworkHealthDiagnosticDto.Issue issue : issues) {
                if (issue == null || issue.severity == null) {
                    continue;
                }
                if (ERROR.equalsIgnoreCase(issue.severity)) {
                    error = true;
                } else if (WARNING.equalsIgnoreCase(issue.severity)) {
                    warning = true;
                } else if (UNKNOWN.equalsIgnoreCase(issue.severity)) {
                    unknownIssue = true;
                }
            }
        }

        // Freshness is authoritative: an expired former failure is no longer a current failure.
        if (dto.stale || hasStaleIssue(issues)) {
            return UNKNOWN;
        }

        if (error) {
            return FAILED;
        }
        if (unknownIssue || hasMissingEvidence(dto)) {
            return UNKNOWN;
        }

        // A known false required field is not healthy even if a caller forgot to
        // attach the corresponding issue. The server sampler normally emits the
        // more useful issue code as well.
        if (hasKnownFailure(dto)) {
            return warning ? DEGRADED : FAILED;
        }
        if (warning) {
            return DEGRADED;
        }
        return HEALTHY;
    }

    /** Recompute and assign {@link NetworkHealthDiagnosticDto#status}. */
    public static String evaluateInto(NetworkHealthDiagnosticDto dto) {
        String value = evaluate(dto);
        if (dto != null) {
            dto.status = value;
        }
        return value;
    }

    private static boolean hasStaleIssue(List<NetworkHealthDiagnosticDto.Issue> issues) {
        if (issues == null) {
            return false;
        }
        for (NetworkHealthDiagnosticDto.Issue issue : issues) {
            if (issue != null && "sample_stale".equals(issue.code)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMissingEvidence(NetworkHealthDiagnosticDto dto) {
        if (dto.links == null || dto.monitors == null || dto.grid == null || dto.channels == null) {
            return true;
        }
        if (dto.links.registered == null || dto.links.loaded == null || dto.links.reachable == null) {
            return true;
        }
        if (dto.monitors.registered == null || dto.monitors.bound == null || dto.monitors.valid == null) {
            return true;
        }
        if (dto.grid.present == null || dto.grid.storageAvailable == null
            || dto.grid.craftingAvailable == null
            || dto.grid.connectorAvailable == null) {
            return true;
        }
        if (dto.channels.available == null) {
            return true;
        }
        if (Boolean.TRUE.equals(dto.channels.available) && (dto.channels.used == null || dto.channels.max == null)) {
            return true;
        }
        // A false availability means the probe could not establish real used/max
        // values. It is deliberately unknown, never a simulated healthy value.
        return Boolean.FALSE.equals(dto.channels.available);
    }

    private static boolean hasKnownFailure(NetworkHealthDiagnosticDto dto) {
        return Boolean.FALSE.equals(dto.links.registered) || Boolean.FALSE.equals(dto.links.loaded)
            || Boolean.FALSE.equals(dto.links.reachable)
            || Boolean.FALSE.equals(dto.monitors.registered)
            || Boolean.FALSE.equals(dto.monitors.bound)
            || Boolean.FALSE.equals(dto.monitors.valid)
            || Boolean.FALSE.equals(dto.grid.present)
            || Boolean.FALSE.equals(dto.grid.storageAvailable)
            || Boolean.FALSE.equals(dto.grid.craftingAvailable)
            || Boolean.FALSE.equals(dto.grid.connectorAvailable)
            || (Boolean.TRUE.equals(dto.channels.available) && dto.channels.used != null
                && dto.channels.max != null
                && dto.channels.used.intValue() > dto.channels.max.intValue());
    }
}
