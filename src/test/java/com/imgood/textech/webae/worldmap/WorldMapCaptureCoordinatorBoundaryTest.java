package com.imgood.textech.webae.worldmap;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class WorldMapCaptureCoordinatorBoundaryTest {

    @Test
    public void sourceStatsRequireStrictClosedJsonObject() {
        Map<String, Integer> empty = WorldMapCaptureCoordinator.parseSourceStats("{}");
        Assert.assertNotNull(empty);
        Assert.assertTrue(empty.isEmpty());

        Map<String, Integer> valid = WorldMapCaptureCoordinator
            .parseSourceStats("{\"dynmap\":1,\"journeymap\":2,\"client_gl\":3}");
        Assert.assertNotNull(valid);
        Assert.assertEquals(Integer.valueOf(1), valid.get("dynmap"));
        Assert.assertEquals(Integer.valueOf(2), valid.get("journeymap"));
        Assert.assertEquals(Integer.valueOf(3), valid.get("client_gl"));

        assertInvalid(null);
        assertInvalid("");
        assertInvalid("[]");
        assertInvalid("{} {}");
        assertInvalid("{\"dynmap\":1,\"dynmap\":2}");
        assertInvalid("{\"mixed\":1}");
        assertInvalid("{\"unknown\":1}");
        assertInvalid("{\"dynmap\":\"1\"}");
        assertInvalid("{\"dynmap\":1.0}");
        assertInvalid("{\"dynmap\":1e2}");
        assertInvalid("{\"dynmap\":-1}");
        assertInvalid("{\"dynmap\":100001}");
        assertInvalid("{\"dynmap\":60000,\"journeymap\":40001}");
        assertInvalid(manyDuplicateKeys());
    }

    @Test
    public void activeJobExpiresAtIdleAndAbsoluteBounds() {
        long start = 1_000L;
        WorldMapCaptureCoordinator.ActiveJob job = new WorldMapCaptureCoordinator.ActiveJob();
        job.createdAtMs = start;
        job.lastTouchedMs = start;

        Assert.assertFalse(
            WorldMapCaptureCoordinator
                .isActiveJobExpired(job, start + WorldMapCaptureCoordinator.ACTIVE_JOB_IDLE_TTL_MS - 1L));
        Assert.assertTrue(
            WorldMapCaptureCoordinator
                .isActiveJobExpired(job, start + WorldMapCaptureCoordinator.ACTIVE_JOB_IDLE_TTL_MS));

        job.lastTouchedMs = start + WorldMapCaptureCoordinator.ACTIVE_JOB_ABSOLUTE_TTL_MS - 1L;
        Assert.assertTrue(
            WorldMapCaptureCoordinator
                .isActiveJobExpired(job, start + WorldMapCaptureCoordinator.ACTIVE_JOB_ABSOLUTE_TTL_MS));
        Assert.assertTrue(WorldMapCaptureCoordinator.isActiveJobExpired(new WorldMapCaptureCoordinator.ActiveJob(), 1L));
    }

    private static void assertInvalid(String json) {
        Assert.assertNull(WorldMapCaptureCoordinator.parseSourceStats(json));
    }

    private static String manyDuplicateKeys() {
        StringBuilder out = new StringBuilder("{");
        for (int i = 0; i < 33; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append("\"dynmap\":0");
        }
        return out.append('}')
            .toString();
    }
}
