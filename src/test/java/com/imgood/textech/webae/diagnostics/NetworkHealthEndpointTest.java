package com.imgood.textech.webae.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.webae.api.handler.NetworkHealthHandler;
import com.imgood.textech.webae.api.handler.ServerDiagnosticsHandler;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.context.NetworkRegistry;
import com.imgood.textech.webae.context.WebAeOwnerContext.NetworkGroup;

import fi.iki.elonen.NanoHTTPD;

/** HTTP serialization and backward-compatible diagnostics response contracts. */
public class NetworkHealthEndpointTest {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String ACL_OWNER = "diagnostics-acl-owner";

    @After
    public void clearProvider() {
        NetworkHealthDiagnosticProvider.instance()
            .clear();
        NetworkRegistry.seedFromGroups(ACL_OWNER, Collections.<NetworkGroup>emptyList());
    }

    @Test
    public void endpointValidatesTheRequiredNetworkParameter() throws Exception {
        WebAuthSession owner = ownerSession("endpoint-owner");

        NanoHTTPD.Response missing = NetworkHealthHandler
            .handle(Collections.<String, String>emptyMap(), owner, owner.ownerUuid);
        assertEquals(NanoHTTPD.Response.Status.BAD_REQUEST, missing.getStatus());
        assertTrue(body(missing).contains("Missing 'network' parameter"));

        Map<String, String> invalidParams = new HashMap<String, String>();
        invalidParams.put("network", "not-an-id");
        NanoHTTPD.Response invalid = NetworkHealthHandler.handle(invalidParams, owner, owner.ownerUuid);
        assertEquals(NanoHTTPD.Response.Status.BAD_REQUEST, invalid.getStatus());
        assertTrue(body(invalid).contains("Invalid 'network' parameter"));
    }

    @Test
    public void endpointPreservesNullableEvidenceInsteadOfSerializingUnknownAsFalse() throws Exception {
        WebAuthSession owner = ownerSession("endpoint-owner-with-no-registration");
        Map<String, String> params = new HashMap<String, String>();
        params.put("network", "0");

        NanoHTTPD.Response response = NetworkHealthHandler.handle(params, owner, owner.ownerUuid);
        assertEquals(NanoHTTPD.Response.Status.OK, response.getStatus());

        JsonObject json = new JsonParser().parse(body(response))
            .getAsJsonObject();
        assertEquals(
            "failed",
            json.get("status")
                .getAsString());
        assertEquals(
            "endpoint-owner-with-no-registration",
            json.get("ownerUuid")
                .getAsString());
        assertEquals(
            0,
            json.get("networkId")
                .getAsInt());
        assertFalse(
            json.get("links")
                .getAsJsonObject()
                .get("registered")
                .getAsBoolean());
        assertTrue(
            json.get("links")
                .getAsJsonObject()
                .get("loaded")
                .isJsonNull());
        assertTrue(
            json.get("grid")
                .getAsJsonObject()
                .get("present")
                .isJsonNull());
        assertEquals(
            "no_registered_network",
            json.getAsJsonArray("issues")
                .get(0)
                .getAsJsonObject()
                .get("code")
                .getAsString());
    }

    @Test
    public void serverDiagnosticsKeepsLegacyFieldsAndAddsOwnerScopedNetworkHealth() throws Exception {
        NanoHTTPD.Response response = ServerDiagnosticsHandler.handle("diagnostics-owner");
        assertEquals(NanoHTTPD.Response.Status.OK, response.getStatus());

        JsonObject json = new JsonParser().parse(body(response))
            .getAsJsonObject();
        String[] legacyFields = { "success", "tps", "mspt", "onlinePlayers", "uptimeSeconds", "queueDepth",
            "tasksProcessedThisTick", "activeNetworks", "snapshotCacheSize", "phases", "collects", "topRoutes",
            "slowHttp", "history", "config" };
        for (String field : legacyFields) {
            assertTrue("Missing legacy diagnostics field: " + field, json.has(field));
        }
        assertTrue(json.has("networkHealth"));
        assertTrue(
            json.getAsJsonArray("networkHealth")
                .size() == 0);
    }

    @Test
    public void serverDiagnosticsFiltersNetworkHealthByGuestStableKeyAllowlist() throws Exception {
        NetworkGroup allowed = group(0, 10, 20, 30);
        NetworkGroup denied = group(1, 40, 50, 60);
        NetworkRegistry.seedFromGroups(ACL_OWNER, Arrays.asList(allowed, denied));

        NetworkHealthDiagnosticProvider provider = NetworkHealthDiagnosticProvider.instance();
        provider.putForTests(complete(ACL_OWNER, 0, "0:10:20:30"));
        provider.putForTests(complete(ACL_OWNER, 1, "1:40:50:60"));
        List<String> allowlist = Collections.singletonList("0:10:20:30");
        WebAuthSession guest = new WebAuthSession(
            "guest-token",
            WebAuthSession.TYPE_GUEST,
            ACL_OWNER,
            "guest-actor",
            "Guest",
            allowlist);

        NanoHTTPD.Response response = ServerDiagnosticsHandler.handle(guest, ACL_OWNER);
        assertEquals(NanoHTTPD.Response.Status.OK, response.getStatus());
        JsonObject json = new JsonParser().parse(body(response))
            .getAsJsonObject();
        assertEquals(
            1,
            json.getAsJsonArray("networkHealth")
                .size());
        assertEquals(
            "0:10:20:30",
            json.getAsJsonArray("networkHealth")
                .get(0)
                .getAsJsonObject()
                .get("networkKey")
                .getAsString());
    }

    private static NetworkGroup group(int dim, int x, int y, int z) {
        NetworkGroup group = new NetworkGroup();
        group.monitorDim = dim;
        group.monitorX = x;
        group.monitorY = y;
        group.monitorZ = z;
        return group;
    }

    private static NetworkHealthDiagnosticDto complete(String owner, int networkId, String key) {
        NetworkHealthDiagnosticDto dto = new NetworkHealthDiagnosticDto(owner, networkId, key);
        dto.checkedAt = System.currentTimeMillis();
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
        dto.channels.used = Integer.valueOf(1);
        dto.channels.max = Integer.valueOf(8);
        return dto;
    }

    private static WebAuthSession ownerSession(String ownerUuid) {
        return new WebAuthSession("token", WebAuthSession.TYPE_OWNER, ownerUuid, ownerUuid, "Owner");
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
