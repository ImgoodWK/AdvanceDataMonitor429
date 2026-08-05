package com.imgood.textech.webae.worldmap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.context.NetworkRegistry;
import com.imgood.textech.webae.context.WebAeOwnerContext.NetworkGroup;

import fi.iki.elonen.NanoHTTPD;

public class WorldMapVersionHandlerTest {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String OWNER = "10000000-0000-0000-0000-000000000091";

    @After
    public void clearNetworkRegistry() {
        NetworkRegistry.seedFromGroups(OWNER, Collections.<NetworkGroup>emptyList());
    }

    @Test
    public void diffParsesVersionFiltersAndStrictBooleans() throws Exception {
        RecordingBackend backend = new RecordingBackend();
        backend.diffResult = diffResult(true, "ok", "ok");
        Map<String, String> params = params("network", "12");
        params.put("from", "2");
        params.put("to", "3");
        params.put("dimension", "-1");
        params.put("minX", "-32");
        params.put("maxX", "64");
        params.put("minZ", "-48");
        params.put("maxZ", "80");
        params.put("includeTiles", "false");
        params.put("includeMarkers", "TRUE");

        NanoHTTPD.Response response = WorldMapVersionHandler
            .handleDiff(params, OWNER, ownerSession(), backend);

        assertEquals(NanoHTTPD.Response.Status.OK, response.getStatus());
        assertTrue(json(response).get("success").getAsBoolean());
        assertEquals(12, backend.networkId);
        assertEquals(Integer.valueOf(2), backend.fromVersion);
        assertEquals(Integer.valueOf(3), backend.toVersion);
        assertEquals(Integer.valueOf(-1), backend.options.dimension);
        assertEquals(Integer.valueOf(-32), backend.options.minX);
        assertEquals(Integer.valueOf(64), backend.options.maxX);
        assertEquals(Integer.valueOf(-48), backend.options.minZ);
        assertEquals(Integer.valueOf(80), backend.options.maxZ);
        assertFalse(backend.options.includeTiles);
        assertTrue(backend.options.includeMarkers);

        RecordingBackend defaults = new RecordingBackend();
        defaults.diffResult = diffResult(true, "ok", "ok");
        NanoHTTPD.Response defaultResponse = WorldMapVersionHandler
            .handleDiff(params("network", "12"), OWNER, ownerSession(), defaults);
        assertEquals(NanoHTTPD.Response.Status.OK, defaultResponse.getStatus());
        close(defaultResponse);
        assertNull(defaults.fromVersion);
        assertNull(defaults.toVersion);
        assertTrue(defaults.options.includeTiles);
        assertTrue(defaults.options.includeMarkers);
    }

    @Test
    public void diffRejectsInvalidVersionsFiltersAndBooleansBeforeBackend() {
        assertInvalidDiff("from", "0");
        assertInvalidDiff("to", "not-a-version");
        assertInvalidDiff("dimension", String.valueOf(WorldMapPacketAuthorization.MAX_DIMENSION + 1));
        assertInvalidDiff("minX", "1.5");
        assertInvalidDiff("maxZ", "2147483648");
        assertInvalidDiff("includeTiles", "yes");
        assertInvalidDiff("includeMarkers", "1");
    }

    @Test
    public void versionsAndDiffExposeExplicitServiceStatusMappings() throws Exception {
        RecordingBackend backend = new RecordingBackend();
        Map<String, String> params = params("network", "0");

        backend.versionsResult = versionsResult(false, "unknown");
        assertStatus(NanoHTTPD.Response.Status.OK,
            WorldMapVersionHandler.handleVersions(params, OWNER, ownerSession(), backend));
        backend.versionsResult = versionsResult(false, "no_versions");
        assertStatus(NanoHTTPD.Response.Status.NOT_FOUND,
            WorldMapVersionHandler.handleVersions(params, OWNER, ownerSession(), backend));
        backend.versionsResult = versionsResult(false, "invalid");
        assertStatus(NanoHTTPD.Response.Status.BAD_REQUEST,
            WorldMapVersionHandler.handleVersions(params, OWNER, ownerSession(), backend));

        assertDiffStatus(backend, "unknown", "unknown_manifest", NanoHTTPD.Response.Status.OK);
        assertDiffStatus(backend, "error", "same", NanoHTTPD.Response.Status.CONFLICT);
        assertDiffStatus(backend, "error", "invalid", NanoHTTPD.Response.Status.BAD_REQUEST);
        assertDiffStatus(backend, "error", "no_previous", NanoHTTPD.Response.Status.CONFLICT);
        assertDiffStatus(backend, "error", "no_versions", NanoHTTPD.Response.Status.NOT_FOUND);
        assertDiffStatus(backend, "error", "not_retained", NanoHTTPD.Response.Status.NOT_FOUND);
    }

    @Test
    public void handlersValidateNetworkAndApplyGuestStableKeyAcl() throws Exception {
        RecordingBackend backend = new RecordingBackend();
        backend.versionsResult = versionsResult(true, "ok");
        backend.diffResult = diffResult(true, "ok", "ok");

        assertStatus(NanoHTTPD.Response.Status.BAD_REQUEST, WorldMapVersionHandler.handleVersions(
            Collections.<String, String>emptyMap(), OWNER, ownerSession(), backend));
        assertStatus(NanoHTTPD.Response.Status.BAD_REQUEST,
            WorldMapVersionHandler.handleDiff(params("network", "-1"), OWNER, ownerSession(), backend));
        assertFalse(backend.versionCalled);
        assertFalse(backend.diffCalled);

        NetworkRegistry.seedFromGroups(OWNER, Collections.singletonList(group()));
        WebAuthSession guest = new WebAuthSession(
            "guest-token",
            WebAuthSession.TYPE_GUEST,
            OWNER,
            "guest-actor",
            "Guest",
            Collections.<String>emptyList());
        assertStatus(NanoHTTPD.Response.Status.FORBIDDEN,
            WorldMapVersionHandler.handleVersions(params("network", "0"), OWNER, guest, backend));
        assertStatus(NanoHTTPD.Response.Status.FORBIDDEN,
            WorldMapVersionHandler.handleDiff(params("network", "0"), OWNER, guest, backend));
        assertFalse(backend.versionCalled);
        assertFalse(backend.diffCalled);
    }

    private static void assertInvalidDiff(String name, String value) {
        RecordingBackend backend = new RecordingBackend();
        backend.diffResult = diffResult(true, "ok", "ok");
        Map<String, String> params = params("network", "0");
        params.put(name, value);
        NanoHTTPD.Response response = WorldMapVersionHandler
            .handleDiff(params, OWNER, ownerSession(), backend);
        assertEquals(name, NanoHTTPD.Response.Status.BAD_REQUEST, response.getStatus());
        close(response);
        assertFalse(name, backend.diffCalled);
    }

    private static void assertDiffStatus(RecordingBackend backend, String status, String code,
        NanoHTTPD.Response.Status expected) throws Exception {
        backend.diffResult = diffResult(false, status, code);
        NanoHTTPD.Response response = WorldMapVersionHandler
            .handleDiff(params("network", "0"), OWNER, ownerSession(), backend);
        assertEquals(code, expected, response.getStatus());
        JsonObject json = json(response);
        assertEquals(code, json.get("code").getAsString());
        assertEquals(status, json.get("status").getAsString());
    }

    private static void assertStatus(NanoHTTPD.Response.Status expected, NanoHTTPD.Response response) {
        try {
            assertEquals(expected, response.getStatus());
        } finally {
            close(response);
        }
    }

    private static WorldMapSnapshotVersionsDto versionsResult(boolean success, String status) {
        WorldMapSnapshotVersionsDto result = new WorldMapSnapshotVersionsDto();
        result.success = success;
        result.status = status;
        return result;
    }

    private static WorldMapSnapshotDiffDto diffResult(boolean success, String status, String code) {
        WorldMapSnapshotDiffDto result = new WorldMapSnapshotDiffDto();
        result.success = success;
        result.status = status;
        result.code = code;
        return result;
    }

    private static Map<String, String> params(String name, String value) {
        Map<String, String> params = new HashMap<String, String>();
        params.put(name, value);
        return params;
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

    private static JsonObject json(NanoHTTPD.Response response) throws IOException {
        return new JsonParser().parse(body(response)).getAsJsonObject();
    }

    private static void close(NanoHTTPD.Response response) {
        try {
            response.close();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
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

    private static final class RecordingBackend implements WorldMapVersionHandler.Backend {

        boolean versionCalled;
        boolean diffCalled;
        int networkId;
        Integer fromVersion;
        Integer toVersion;
        WorldMapSnapshotDiffOptions options;
        WorldMapSnapshotVersionsDto versionsResult;
        WorldMapSnapshotDiffDto diffResult;

        @Override
        public WorldMapSnapshotVersionsDto listVersions(String ownerUuid, int networkId) {
            versionCalled = true;
            this.networkId = networkId;
            return versionsResult;
        }

        @Override
        public WorldMapSnapshotDiffDto diff(String ownerUuid, int networkId, Integer fromVersion,
            Integer toVersion, WorldMapSnapshotDiffOptions options) {
            diffCalled = true;
            this.networkId = networkId;
            this.fromVersion = fromVersion;
            this.toVersion = toVersion;
            this.options = options;
            return diffResult;
        }
    }
}
