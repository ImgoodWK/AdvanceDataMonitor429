package com.imgood.textech.webae.cpu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.webae.api.handler.CpuCapacityHandler;
import com.imgood.textech.webae.api.handler.CpuHistoryHandler;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.context.NetworkRegistry;
import com.imgood.textech.webae.context.WebAeOwnerContext.NetworkGroup;

import fi.iki.elonen.NanoHTTPD;

public class CpuHistoryEndpointTest {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String OWNER = "cpu-endpoint-owner";
    private static final String KEY = "0:10:20:30";

    @Before
    public void setup() {
        CpuHistoryService.instance()
            .clearForTests();
        NetworkRegistry.seedFromGroups(OWNER, Collections.singletonList(group()));
    }

    @After
    public void cleanup() {
        CpuHistoryService.instance()
            .clearForTests();
        NetworkRegistry.seedFromGroups(OWNER, Collections.<NetworkGroup>emptyList());
    }

    @Test
    public void historyRequiresGetParametersAndReportsTruncation() throws Exception {
        long now = System.currentTimeMillis();
        CpuHistoryState state = new CpuHistoryState(OWNER, 0, KEY);
        for (int i = 0; i < 3; i++) {
            CpuJobHistoryDto job = new CpuJobHistoryDto();
            job.jobId = "job-" + i;
            job.ownerUuid = OWNER;
            job.networkId = 0;
            job.networkKey = KEY;
            job.status = CpuJobHistoryDto.STATUS_RUNNING;
            job.queuedAt = now + i;
            state.jobs.add(job);
        }
        CpuHistoryService.instance()
            .putForTests(state);

        Map<String, String> missing = new HashMap<String, String>();
        NanoHTTPD.Response missingResponse = CpuHistoryHandler.handle(missing, ownerSession(), OWNER);
        assertEquals(NanoHTTPD.Response.Status.BAD_REQUEST, missingResponse.getStatus());

        Map<String, String> params = new HashMap<String, String>();
        params.put("network", "0");
        params.put("from", String.valueOf(now - 1_000L));
        params.put("to", String.valueOf(now + 2_000L));
        params.put("limit", "1");
        NanoHTTPD.Response response = CpuHistoryHandler.handle(params, ownerSession(), OWNER);
        assertEquals(NanoHTTPD.Response.Status.OK, response.getStatus());
        JsonObject json = new JsonParser().parse(body(response))
            .getAsJsonObject();
        assertTrue(
            json.get("success")
                .getAsBoolean());
        assertEquals(
            1,
            json.getAsJsonArray("jobs")
                .size());
        assertTrue(
            json.get("truncated")
                .getAsBoolean());
        assertEquals(
            KEY,
            json.get("networkKey")
                .getAsString());
    }

    @Test
    public void capacityDoesNotInventOneCpuWhenHistoryIsEmpty() throws Exception {
        Map<String, String> params = new HashMap<String, String>();
        params.put("network", "0");
        NanoHTTPD.Response response = CpuCapacityHandler.handle(params, ownerSession(), OWNER);
        assertEquals(NanoHTTPD.Response.Status.OK, response.getStatus());
        JsonObject json = new JsonParser().parse(body(response))
            .getAsJsonObject();
        assertTrue(
            json.get("success")
                .getAsBoolean());
        assertTrue(
            json.get("requiredCpuCountEstimate")
                .isJsonNull());
        assertTrue(
            json.get("busyRatio")
                .isJsonNull());
        assertFalse(
            json.getAsJsonArray("bottlenecks")
                .size() == 0);
    }

    @Test
    public void guestNetworkAllowlistIsAppliedAtStableKeyBoundary() {
        WebAuthSession guest = new WebAuthSession(
            "guest-token",
            WebAuthSession.TYPE_GUEST,
            OWNER,
            "guest-actor",
            "Guest",
            Collections.<String>emptyList());
        Map<String, String> params = new HashMap<String, String>();
        params.put("network", "0");
        NanoHTTPD.Response denied = CpuHistoryHandler.handle(params, guest, OWNER);
        assertEquals(NanoHTTPD.Response.Status.FORBIDDEN, denied.getStatus());
    }

    @Test
    public void rejectsWindowsLongerThanRetention() {
        Map<String, String> params = new HashMap<String, String>();
        params.put("network", "0");
        params.put("from", "0");
        params.put("to", String.valueOf(CpuHistoryService.RETENTION_MS + 1L));
        NanoHTTPD.Response response = CpuHistoryHandler.handle(params, ownerSession(), OWNER);
        assertEquals(NanoHTTPD.Response.Status.BAD_REQUEST, response.getStatus());
    }

    private static NetworkGroup group() {
        NetworkGroup group = new NetworkGroup();
        group.monitorDim = 0;
        group.monitorX = 10;
        group.monitorY = 20;
        group.monitorZ = 30;
        return group;
    }

    private static WebAuthSession ownerSession() {
        return new WebAuthSession("owner-token", WebAuthSession.TYPE_OWNER, OWNER, OWNER, "Owner");
    }

    private static String body(NanoHTTPD.Response response) throws IOException {
        try {
            InputStream in = response.getData();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), UTF8);
        } finally {
            response.close();
        }
    }
}
