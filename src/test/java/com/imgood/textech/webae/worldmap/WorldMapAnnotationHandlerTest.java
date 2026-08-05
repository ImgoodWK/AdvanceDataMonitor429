package com.imgood.textech.webae.worldmap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.context.NetworkRegistry;
import com.imgood.textech.webae.context.WebAeOwnerContext.NetworkGroup;

import fi.iki.elonen.NanoHTTPD;

public class WorldMapAnnotationHandlerTest {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String OWNER = "10000000-0000-0000-0000-000000000092";
    private static final String ACTOR = "20000000-0000-0000-0000-000000000092";
    private static final int NETWORK = 7;
    private static final String[] REQUIRED_FIELDS = { "networkId", "dimension", "x", "y", "z", "label", "color",
        "fromVersion", "toVersion" };

    private File tempDirectory;
    private WorldMapAnnotationService service;

    @Before
    public void setup() throws IOException {
        tempDirectory = Files.createTempDirectory("worldmap-annotation-handler-test").toFile();
        service = new WorldMapAnnotationService(new WorldMapAnnotationStore(tempDirectory));
    }

    @After
    public void cleanup() {
        NetworkRegistry.seedFromGroups(OWNER, Collections.<NetworkGroup>emptyList());
        deleteRecursively(tempDirectory);
    }

    @Test
    public void crudUsesExpectedStatusesEnvelopesAndAuthenticatedActor() throws Exception {
        JsonObject createBody = requestJson(NETWORK, "Initial");
        NanoHTTPD.Response created = WorldMapAnnotationHandler.handleCreate(
            Collections.<String, String>emptyMap(), createBody.toString(), OWNER, ownerSession(), service);
        assertEquals(NanoHTTPD.Response.Status.CREATED, created.getStatus());
        JsonObject createJson = json(created);
        assertTrue(createJson.get("success").getAsBoolean());
        JsonObject annotation = createJson.getAsJsonObject("annotation");
        String id = annotation.get("id").getAsString();
        assertTrue(WorldMapAnnotationStore.isCanonicalUuid(id));
        assertEquals(ACTOR, annotation.get("createdBy").getAsString());
        assertEquals("", annotation.get("note").getAsString());

        Map<String, String> listParams = params("network", String.valueOf(NETWORK));
        listParams.put("version", "1");
        NanoHTTPD.Response listed = WorldMapAnnotationHandler
            .handleList(listParams, OWNER, ownerSession(), service);
        assertEquals(NanoHTTPD.Response.Status.OK, listed.getStatus());
        JsonArray annotations = json(listed).getAsJsonArray("annotations");
        assertEquals(1, annotations.size());
        assertEquals(id, annotations.get(0).getAsJsonObject().get("id").getAsString());

        JsonObject updateBody = requestJson(NETWORK, "Updated");
        updateBody.addProperty("note", "Changed note");
        NanoHTTPD.Response updated = WorldMapAnnotationHandler.handleUpdate(
            id,
            params("network", String.valueOf(NETWORK)),
            updateBody.toString(),
            OWNER,
            ownerSession(),
            service);
        assertEquals(NanoHTTPD.Response.Status.OK, updated.getStatus());
        JsonObject updatedAnnotation = json(updated).getAsJsonObject("annotation");
        assertEquals("Updated", updatedAnnotation.get("label").getAsString());
        assertEquals("Changed note", updatedAnnotation.get("note").getAsString());
        assertEquals(ACTOR, updatedAnnotation.get("createdBy").getAsString());

        NanoHTTPD.Response deleted = WorldMapAnnotationHandler.handleDelete(
            id,
            params("network", String.valueOf(NETWORK)),
            OWNER,
            ownerSession(),
            service);
        assertEquals(NanoHTTPD.Response.Status.OK, deleted.getStatus());
        JsonObject deletedJson = json(deleted);
        assertTrue(deletedJson.get("success").getAsBoolean());
        assertEquals(id, deletedJson.getAsJsonObject("annotation").get("id").getAsString());

        NanoHTTPD.Response empty = WorldMapAnnotationHandler
            .handleList(listParams, OWNER, ownerSession(), service);
        assertEquals(0, json(empty).getAsJsonArray("annotations").size());
    }

    @Test
    public void createAndUpdateRequireObjectRootAndEveryExplicitPrimitiveField() {
        assertStatus(NanoHTTPD.Response.Status.BAD_REQUEST, WorldMapAnnotationHandler.handleCreate(
            Collections.<String, String>emptyMap(), "[]", OWNER, ownerSession(), service));
        assertStatus(NanoHTTPD.Response.Status.BAD_REQUEST, WorldMapAnnotationHandler.handleCreate(
            Collections.<String, String>emptyMap(), "{", OWNER, ownerSession(), service));

        for (String field : REQUIRED_FIELDS) {
            JsonObject body = requestJson(NETWORK, "Missing " + field);
            body.remove(field);
            NanoHTTPD.Response response = WorldMapAnnotationHandler.handleCreate(
                Collections.<String, String>emptyMap(), body.toString(), OWNER, ownerSession(), service);
            assertEquals(field, NanoHTTPD.Response.Status.BAD_REQUEST, response.getStatus());
            close(response);
        }

        JsonObject wrongType = requestJson(NETWORK, "Wrong type");
        wrongType.addProperty("networkId", String.valueOf(NETWORK));
        assertStatus(NanoHTTPD.Response.Status.BAD_REQUEST, WorldMapAnnotationHandler.handleCreate(
            Collections.<String, String>emptyMap(), wrongType.toString(), OWNER, ownerSession(), service));

        JsonObject nullRequired = requestJson(NETWORK, "Null field");
        nullRequired.add("color", null);
        assertStatus(NanoHTTPD.Response.Status.BAD_REQUEST, WorldMapAnnotationHandler.handleUpdate(
            UUID.randomUUID().toString(),
            Collections.<String, String>emptyMap(),
            nullRequired.toString(),
            OWNER,
            ownerSession(),
            service));
    }

    @Test
    public void mutationsRejectQueryBodyMismatchInvalidUuidAndGuestWrites() {
        JsonObject body = requestJson(NETWORK, "Mutation validation");
        assertStatus(NanoHTTPD.Response.Status.BAD_REQUEST, WorldMapAnnotationHandler.handleCreate(
            params("network", String.valueOf(NETWORK + 1)), body.toString(), OWNER, ownerSession(), service));
        assertStatus(NanoHTTPD.Response.Status.BAD_REQUEST, WorldMapAnnotationHandler.handleUpdate(
            "not-a-uuid",
            Collections.<String, String>emptyMap(),
            body.toString(),
            OWNER,
            ownerSession(),
            service));
        assertStatus(NanoHTTPD.Response.Status.BAD_REQUEST, WorldMapAnnotationHandler.handleDelete(
            "not-a-uuid", params("network", String.valueOf(NETWORK)), OWNER, ownerSession(), service));

        WebAuthSession guest = new WebAuthSession(
            "guest-token", WebAuthSession.TYPE_GUEST, OWNER, "guest-actor", "Guest");
        NanoHTTPD.Response guestResponse = WorldMapAnnotationHandler.handleCreate(
            Collections.<String, String>emptyMap(), body.toString(), OWNER, guest, service);
        assertEquals(NanoHTTPD.Response.Status.FORBIDDEN, guestResponse.getStatus());
        try {
            assertEquals("guest_readonly", json(guestResponse).get("code").getAsString());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void listRequiresPositiveVersionAndAppliesNetworkAcl() {
        Map<String, String> params = params("network", String.valueOf(NETWORK));
        params.put("version", "0");
        assertStatus(NanoHTTPD.Response.Status.BAD_REQUEST,
            WorldMapAnnotationHandler.handleList(params, OWNER, ownerSession(), service));
        params.remove("version");
        assertStatus(NanoHTTPD.Response.Status.BAD_REQUEST,
            WorldMapAnnotationHandler.handleList(params, OWNER, ownerSession(), service));

        NetworkRegistry.seedFromGroups(OWNER, Collections.singletonList(group()));
        WebAuthSession guest = new WebAuthSession(
            "guest-token",
            WebAuthSession.TYPE_GUEST,
            OWNER,
            "guest-actor",
            "Guest",
            Collections.<String>emptyList());
        Map<String, String> aclParams = params("network", "0");
        aclParams.put("version", "1");
        assertStatus(NanoHTTPD.Response.Status.FORBIDDEN,
            WorldMapAnnotationHandler.handleList(aclParams, OWNER, guest, service));
    }

    @Test
    public void missingAnnotationMapsToNotFound() throws Exception {
        String id = UUID.randomUUID().toString();
        JsonObject body = requestJson(NETWORK, "Missing");
        NanoHTTPD.Response update = WorldMapAnnotationHandler.handleUpdate(
            id,
            Collections.<String, String>emptyMap(),
            body.toString(),
            OWNER,
            ownerSession(),
            service);
        assertEquals(NanoHTTPD.Response.Status.NOT_FOUND, update.getStatus());
        JsonObject error = json(update);
        assertFalse(error.get("success").getAsBoolean());
        assertEquals("not_found", error.get("code").getAsString());
    }

    private static JsonObject requestJson(int networkId, String label) {
        JsonObject body = new JsonObject();
        body.addProperty("networkId", networkId);
        body.addProperty("dimension", 0);
        body.addProperty("x", 10);
        body.addProperty("y", 64);
        body.addProperty("z", -20);
        body.addProperty("label", label);
        body.addProperty("color", "#12ABEF");
        body.addProperty("fromVersion", 0);
        body.addProperty("toVersion", 0);
        return body;
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
        return new WebAuthSession("owner-token", WebAuthSession.TYPE_OWNER, OWNER, ACTOR, "Owner");
    }

    private static void assertStatus(NanoHTTPD.Response.Status expected, NanoHTTPD.Response response) {
        try {
            assertEquals(expected, response.getStatus());
        } finally {
            close(response);
        }
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

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
