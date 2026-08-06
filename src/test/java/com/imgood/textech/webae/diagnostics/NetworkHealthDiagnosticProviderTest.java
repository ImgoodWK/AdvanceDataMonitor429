package com.imgood.textech.webae.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Cache, freshness, and owner-isolation contracts for the HTTP-safe provider. */
public class NetworkHealthDiagnosticProviderTest {

    private final NetworkHealthDiagnosticProvider provider = NetworkHealthDiagnosticProvider.instance();

    @Before
    public void clearBefore() {
        provider.clear();
    }

    @After
    public void clearAfter() {
        provider.clear();
    }

    @Test
    public void getReturnsAnOwnerScopedCopyAndRecomputesFreshStatus() {
        NetworkHealthDiagnosticDto cached = complete("owner-a", 2, "0:10:20:30");
        cached.status = NetworkHealthStatusEvaluator.UNKNOWN;
        provider.putForTests(cached);

        NetworkHealthDiagnosticDto result = provider.getCached("owner-a", 2, "0:10:20:30", System.currentTimeMillis());

        assertNotSame(cached, result);
        assertEquals("owner-a", result.ownerUuid);
        assertEquals(2, result.networkId);
        assertEquals("0:10:20:30", result.networkKey);
        assertEquals(NetworkHealthStatusEvaluator.HEALTHY, result.status);
        assertFalse(result.stale);
        assertTrue(result.sampleAgeMs != null && result.sampleAgeMs.longValue() >= 0L);
    }

    @Test
    public void stableKeySurvivesRuntimeNetworkIdReordering() {
        NetworkHealthDiagnosticDto cached = complete("owner-a", 0, "0:10:20:30");
        provider.putForTests(cached);

        NetworkHealthDiagnosticDto reordered = provider
            .getCached("owner-a", 7, "0:10:20:30", System.currentTimeMillis());

        assertEquals(7, reordered.networkId);
        assertEquals("0:10:20:30", reordered.networkKey);
        assertEquals(NetworkHealthStatusEvaluator.HEALTHY, reordered.status);
    }

    @Test
    public void staleCacheIsUnknownAndRetainsSampleStaleEvidence() {
        NetworkHealthDiagnosticDto cached = complete("owner-a", 2, "0:10:20:30");
        cached.checkedAt = System.currentTimeMillis() - NetworkHealthDiagnosticProvider.STALE_AFTER_MS - 1L;
        provider.putForTests(cached);

        NetworkHealthDiagnosticDto result = provider.getCached("owner-a", 2, "0:10:20:30", System.currentTimeMillis());

        assertEquals(NetworkHealthStatusEvaluator.UNKNOWN, result.status);
        assertTrue(result.stale);
        assertTrue(
            result.sampleAgeMs != null
                && result.sampleAgeMs.longValue() > NetworkHealthDiagnosticProvider.STALE_AFTER_MS);
        assertTrue(hasIssue(result, "sample_stale"));
    }

    @Test
    public void snapshotsAreIsolatedByOwner() {
        provider.putForTests(complete("owner-a", 0, "0:1:2:3"));
        provider.putForTests(complete("owner-b", 0, "0:1:2:3"));

        NetworkHealthDiagnosticDto ownerA = provider.getCached("owner-a", 0, "0:1:2:3", System.currentTimeMillis());
        NetworkHealthDiagnosticDto ownerB = provider.getCached("owner-b", 0, "0:1:2:3", System.currentTimeMillis());
        NetworkHealthDiagnosticDto crossOwner = provider.getCached("owner-c", 0, "0:1:2:3", System.currentTimeMillis());

        assertEquals("owner-a", ownerA.ownerUuid);
        assertEquals("owner-b", ownerB.ownerUuid);
        assertEquals(NetworkHealthStatusEvaluator.UNKNOWN, crossOwner.status);
        assertEquals(2, provider.cacheSizeForTests());
    }

    @Test
    public void missingCacheReturnsUnknownWithoutInventingEvidence() {
        NetworkHealthDiagnosticDto result = provider.getCached("owner-a", 99, "0:99:98:97", System.currentTimeMillis());

        assertEquals(NetworkHealthStatusEvaluator.UNKNOWN, result.status);
        assertTrue(result.stale);
        assertNull(result.sampleAgeMs);
        assertNull(result.links.registered);
        assertNull(result.grid.present);
        assertTrue(hasIssue(result, "sample_stale"));
    }

    @Test
    public void unregisteredRuntimeIdReturnsTheFixedRegistrationIssue() {
        NetworkHealthDiagnosticDto result = provider.get("health-test-owner-with-no-registrations", 99);

        assertEquals(NetworkHealthStatusEvaluator.FAILED, result.status);
        assertFalse(result.stale);
        assertEquals(Boolean.FALSE, result.links.registered);
        assertTrue(hasIssue(result, "no_registered_network"));
    }

    @Test
    public void channelOverLimitRequiresBothVerifiedRealValues() {
        NetworkHealthDiagnosticDto verified = complete("owner-a", 0, "0:1:2:3");
        NetworkHealthDiagnosticProvider.applyChannelProbe(verified, true, 9, 8, verified.networkKey);
        assertEquals(Boolean.TRUE, verified.channels.available);
        assertEquals(Integer.valueOf(9), verified.channels.used);
        assertEquals(Integer.valueOf(8), verified.channels.max);
        assertTrue(hasIssue(verified, "channel_over_limit"));

        NetworkHealthDiagnosticDto unknown = complete("owner-a", 0, "0:1:2:3");
        NetworkHealthDiagnosticProvider.applyChannelProbe(unknown, false, 9, -1, unknown.networkKey);
        assertEquals(Boolean.FALSE, unknown.channels.available);
        assertNull(unknown.channels.used);
        assertNull(unknown.channels.max);
        assertFalse(hasIssue(unknown, "channel_over_limit"));
    }

    private static boolean hasIssue(NetworkHealthDiagnosticDto dto, String code) {
        if (dto == null || dto.issues == null) {
            return false;
        }
        for (NetworkHealthDiagnosticDto.Issue issue : dto.issues) {
            if (issue != null && code.equals(issue.code)) {
                return true;
            }
        }
        return false;
    }

    private static NetworkHealthDiagnosticDto complete(String owner, int networkId, String key) {
        NetworkHealthDiagnosticDto dto = new NetworkHealthDiagnosticDto(owner, networkId, key);
        dto.checkedAt = System.currentTimeMillis();
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
