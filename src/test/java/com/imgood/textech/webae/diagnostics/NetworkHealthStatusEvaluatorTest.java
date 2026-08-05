package com.imgood.textech.webae.diagnostics;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Pure contract tests for the network-health status precedence rules. */
public class NetworkHealthStatusEvaluatorTest {

    @Test
    public void completeEvidenceWithoutIssuesIsHealthy() {
        NetworkHealthDiagnosticDto dto = complete();
        assertEquals(NetworkHealthStatusEvaluator.HEALTHY,
            NetworkHealthStatusEvaluator.evaluate(dto));
    }

    @Test
    public void warningIsDegraded() {
        NetworkHealthDiagnosticDto dto = complete();
        dto.issues.add(NetworkHealthDiagnosticDto.Issue.warning(
            "monitor_unbound", "message", "suggestion", "evidence"));
        assertEquals(NetworkHealthStatusEvaluator.DEGRADED,
            NetworkHealthStatusEvaluator.evaluate(dto));
    }

    @Test
    public void observedOptionalGridServiceFailuresAreDegraded() {
        NetworkHealthDiagnosticDto dto = complete();
        dto.grid.storageAvailable = Boolean.FALSE;
        dto.grid.craftingAvailable = Boolean.FALSE;
        dto.grid.connectorAvailable = Boolean.FALSE;
        dto.issues.add(NetworkHealthDiagnosticDto.Issue.warning(
            "storage_unavailable", "message", "suggestion", null));
        dto.issues.add(NetworkHealthDiagnosticDto.Issue.warning(
            "crafting_unavailable", "message", "suggestion", null));
        dto.issues.add(NetworkHealthDiagnosticDto.Issue.warning(
            "network_connector_unavailable", "message", "suggestion", null));

        assertEquals(NetworkHealthStatusEvaluator.DEGRADED,
            NetworkHealthStatusEvaluator.evaluate(dto));
    }

    @Test
    public void confirmedMissingLinkOrGridIsFailed() {
        NetworkHealthDiagnosticDto noLink = complete();
        noLink.links.loaded = Boolean.FALSE;
        noLink.links.reachable = Boolean.FALSE;
        noLink.issues.add(NetworkHealthDiagnosticDto.Issue.error(
            "no_link", "message", "suggestion", null));
        assertEquals(NetworkHealthStatusEvaluator.FAILED,
            NetworkHealthStatusEvaluator.evaluate(noLink));

        NetworkHealthDiagnosticDto noGrid = complete();
        noGrid.grid.present = Boolean.FALSE;
        noGrid.issues.add(NetworkHealthDiagnosticDto.Issue.error(
            "grid_missing", "message", "suggestion", null));
        assertEquals(NetworkHealthStatusEvaluator.FAILED,
            NetworkHealthStatusEvaluator.evaluate(noGrid));
    }

    @Test
    public void errorTakesPrecedenceOverWarning() {
        NetworkHealthDiagnosticDto dto = complete();
        dto.issues.add(NetworkHealthDiagnosticDto.Issue.warning(
            "monitor_stale", "message", "suggestion", null));
        dto.issues.add(NetworkHealthDiagnosticDto.Issue.error(
            "grid_missing", "message", "suggestion", null));
        assertEquals(NetworkHealthStatusEvaluator.FAILED,
            NetworkHealthStatusEvaluator.evaluate(dto));
    }

    @Test
    public void staleOrUnknownEvidenceNeverLooksHealthy() {
        NetworkHealthDiagnosticDto dto = complete();
        dto.stale = true;
        assertEquals(NetworkHealthStatusEvaluator.UNKNOWN,
            NetworkHealthStatusEvaluator.evaluate(dto));

        dto.stale = false;
        dto.issues.add(NetworkHealthDiagnosticDto.Issue.unknown(
            "sample_stale", "message", "suggestion", null));
        assertEquals(NetworkHealthStatusEvaluator.UNKNOWN,
            NetworkHealthStatusEvaluator.evaluate(dto));
    }

    @Test
    public void staleSnapshotMasksAnOldErrorAsUnknown() {
        NetworkHealthDiagnosticDto dto = complete();
        dto.stale = true;
        dto.issues.add(NetworkHealthDiagnosticDto.Issue.error(
            "grid_missing", "message", "suggestion", null));

        assertEquals(NetworkHealthStatusEvaluator.UNKNOWN,
            NetworkHealthStatusEvaluator.evaluate(dto));
    }

    @Test
    public void knownFalseEvidenceFailsWithoutAttachedIssue() {
        NetworkHealthDiagnosticDto dto = complete();
        dto.grid.storageAvailable = Boolean.FALSE;
        assertEquals(NetworkHealthStatusEvaluator.FAILED,
            NetworkHealthStatusEvaluator.evaluate(dto));
    }

    @Test
    public void unavailableChannelProbeIsUnknown() {
        NetworkHealthDiagnosticDto dto = complete();
        dto.channels.available = Boolean.FALSE;
        dto.channels.used = null;
        dto.channels.max = null;
        assertEquals(NetworkHealthStatusEvaluator.UNKNOWN,
            NetworkHealthStatusEvaluator.evaluate(dto));
    }

    private static NetworkHealthDiagnosticDto complete() {
        NetworkHealthDiagnosticDto dto = new NetworkHealthDiagnosticDto("owner", 0, "0:1:2:3");
        dto.checkedAt = 1L;
        dto.sampleAgeMs = Long.valueOf(0L);
        dto.stale = false;
        dto.links.registered = Boolean.TRUE;
        dto.links.loaded = Boolean.TRUE;
        dto.links.reachable = Boolean.TRUE;
        dto.monitors.registered = Boolean.TRUE;
        dto.monitors.bound = Boolean.TRUE;
        dto.monitors.valid = Boolean.TRUE;
        dto.grid.present = Boolean.TRUE;
        dto.grid.storageAvailable = Boolean.TRUE;
        dto.grid.craftingAvailable = Boolean.TRUE;
        dto.grid.connectorAvailable = Boolean.TRUE;
        dto.channels.available = Boolean.TRUE;
        dto.channels.used = Integer.valueOf(3);
        dto.channels.max = Integer.valueOf(8);
        return dto;
    }
}
