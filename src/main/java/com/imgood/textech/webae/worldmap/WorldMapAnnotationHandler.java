package com.imgood.textech.webae.worldmap;

import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.imgood.textech.webae.access.WebAeNetworkAccess;
import com.imgood.textech.webae.auth.WebAuthSession;

import fi.iki.elonen.NanoHTTPD;

/** HTTP adapter for owner-scoped world-map annotation CRUD. */
public final class WorldMapAnnotationHandler {

    private static final String[] REQUIRED_FIELDS = { "networkId", "dimension", "x", "y", "z", "label", "color",
        "fromVersion", "toVersion" };
    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();
    private static final WorldMapAnnotationService PRODUCTION_SERVICE = new WorldMapAnnotationService();

    private WorldMapAnnotationHandler() {}

    /** Handles {@code GET /api/worldmap/annotations?network=<id>&version=<positive>}. */
    public static NanoHTTPD.Response handleList(Map<String, String> params, String effectiveOwner,
        WebAuthSession auth) {
        return handleList(params, effectiveOwner, auth, PRODUCTION_SERVICE);
    }

    /** Handles {@code POST /api/worldmap/annotations}. */
    public static NanoHTTPD.Response handleCreate(Map<String, String> params, String body, String effectiveOwner,
        WebAuthSession auth) {
        return handleCreate(params, body, effectiveOwner, auth, PRODUCTION_SERVICE);
    }

    /** Handles {@code PUT /api/worldmap/annotations/{id}}. */
    public static NanoHTTPD.Response handleUpdate(String id, Map<String, String> params, String body,
        String effectiveOwner, WebAuthSession auth) {
        return handleUpdate(id, params, body, effectiveOwner, auth, PRODUCTION_SERVICE);
    }

    /** Handles {@code DELETE /api/worldmap/annotations/{id}?network=<id>}. */
    public static NanoHTTPD.Response handleDelete(String id, Map<String, String> params, String effectiveOwner,
        WebAuthSession auth) {
        return handleDelete(id, params, effectiveOwner, auth, PRODUCTION_SERVICE);
    }

    static NanoHTTPD.Response handleList(Map<String, String> params, String effectiveOwner, WebAuthSession auth,
        WorldMapAnnotationService service) {
        Integer networkId = parseRequiredNetwork(params);
        if (networkId == null) {
            return invalid("invalid_network", "Missing or invalid 'network' parameter");
        }
        NanoHTTPD.Response denied = assertAccess(auth, effectiveOwner, networkId.intValue(), false);
        if (denied != null) {
            return denied;
        }
        Integer version = parseInteger(params == null ? null : params.get("version"));
        if (version == null || !WorldMapPacketAuthorization.isValidSnapshotVersion(version.intValue())) {
            return invalid("invalid_version", "Missing or invalid positive 'version' parameter");
        }

        WorldMapAnnotationResult<List<WorldMapAnnotationDto>> result = service
            .list(effectiveOwner, networkId.intValue(), version.intValue());
        if (!result.success) {
            return serviceError(result);
        }
        return json(NanoHTTPD.Response.Status.OK, new ListEnvelope(result.result));
    }

    static NanoHTTPD.Response handleCreate(Map<String, String> params, String body, String effectiveOwner,
        WebAuthSession auth, WorldMapAnnotationService service) {
        ParsedBody parsed = parseBody(body);
        if (!parsed.success) {
            return invalid(parsed.code, parsed.message);
        }
        NanoHTTPD.Response queryError = checkOptionalQueryNetwork(params, parsed.networkId);
        if (queryError != null) {
            return queryError;
        }
        NanoHTTPD.Response denied = assertAccess(auth, effectiveOwner, parsed.networkId, true);
        if (denied != null) {
            return denied;
        }

        WorldMapAnnotationResult<WorldMapAnnotationDto> result = service
            .create(effectiveOwner, parsed.networkId, auth.actorUuid, parsed.request);
        if (!result.success) {
            return serviceError(result);
        }
        return json(NanoHTTPD.Response.Status.CREATED, new AnnotationEnvelope(result.result));
    }

    static NanoHTTPD.Response handleUpdate(String id, Map<String, String> params, String body, String effectiveOwner,
        WebAuthSession auth, WorldMapAnnotationService service) {
        ParsedBody parsed = parseBody(body);
        if (!parsed.success) {
            return invalid(parsed.code, parsed.message);
        }
        NanoHTTPD.Response queryError = checkOptionalQueryNetwork(params, parsed.networkId);
        if (queryError != null) {
            return queryError;
        }
        NanoHTTPD.Response denied = assertAccess(auth, effectiveOwner, parsed.networkId, true);
        if (denied != null) {
            return denied;
        }
        if (!WorldMapAnnotationStore.isCanonicalUuid(id)) {
            return invalid("invalid_id", "Annotation id must be a canonical UUID");
        }

        WorldMapAnnotationResult<WorldMapAnnotationDto> result = service
            .update(effectiveOwner, parsed.networkId, id, auth.actorUuid, parsed.request);
        if (!result.success) {
            return serviceError(result);
        }
        return json(NanoHTTPD.Response.Status.OK, new AnnotationEnvelope(result.result));
    }

    static NanoHTTPD.Response handleDelete(String id, Map<String, String> params, String effectiveOwner,
        WebAuthSession auth, WorldMapAnnotationService service) {
        Integer networkId = parseRequiredNetwork(params);
        if (networkId == null) {
            return invalid("invalid_network", "Missing or invalid 'network' parameter");
        }
        NanoHTTPD.Response denied = assertAccess(auth, effectiveOwner, networkId.intValue(), true);
        if (denied != null) {
            return denied;
        }
        if (!WorldMapAnnotationStore.isCanonicalUuid(id)) {
            return invalid("invalid_id", "Annotation id must be a canonical UUID");
        }

        WorldMapAnnotationResult<WorldMapAnnotationDto> result = service
            .delete(effectiveOwner, networkId.intValue(), id);
        if (!result.success) {
            return serviceError(result);
        }
        return json(NanoHTTPD.Response.Status.OK, new AnnotationEnvelope(result.result));
    }

    private static ParsedBody parseBody(String body) {
        if (body == null || body.trim()
            .isEmpty()) {
            return ParsedBody.failure("invalid_request", "Annotation JSON body is required");
        }
        final JsonElement root;
        try {
            root = new JsonParser().parse(body);
        } catch (JsonParseException e) {
            return ParsedBody.failure("invalid_request", "Annotation JSON body is invalid");
        } catch (RuntimeException e) {
            return ParsedBody.failure("invalid_request", "Annotation JSON body is invalid");
        }
        if (root == null || !root.isJsonObject()) {
            return ParsedBody.failure("invalid_request", "Annotation JSON root must be an object");
        }

        JsonObject object = root.getAsJsonObject();
        for (String field : REQUIRED_FIELDS) {
            if (!object.has(field) || object.get(field) == null
                || object.get(field)
                    .isJsonNull()) {
                return ParsedBody.failure("invalid_request", "Missing required annotation field: " + field);
            }
        }

        Integer networkId = integerField(object, "networkId");
        Integer dimension = integerField(object, "dimension");
        Integer x = integerField(object, "x");
        Integer y = integerField(object, "y");
        Integer z = integerField(object, "z");
        Integer fromVersion = integerField(object, "fromVersion");
        Integer toVersion = integerField(object, "toVersion");
        String label = stringField(object, "label");
        String color = stringField(object, "color");
        if (networkId == null || dimension == null
            || x == null
            || y == null
            || z == null
            || fromVersion == null
            || toVersion == null
            || label == null
            || color == null
            || !WorldMapPacketAuthorization.isValidNetworkId(networkId.intValue())) {
            return ParsedBody.failure("invalid_request", "Annotation fields have invalid JSON types or values");
        }

        String note = "";
        if (object.has("note") && object.get("note") != null
            && !object.get("note")
                .isJsonNull()) {
            note = stringField(object, "note");
            if (note == null) {
                return ParsedBody.failure("invalid_request", "Annotation note must be a string");
            }
        }

        final WorldMapAnnotationRequest request;
        try {
            request = GSON.fromJson(object, WorldMapAnnotationRequest.class);
        } catch (JsonParseException e) {
            return ParsedBody.failure("invalid_request", "Annotation JSON body is invalid");
        } catch (RuntimeException e) {
            return ParsedBody.failure("invalid_request", "Annotation JSON body is invalid");
        }
        if (request == null) {
            return ParsedBody.failure("invalid_request", "Annotation JSON body is invalid");
        }
        request.networkId = networkId;
        request.dimension = dimension.intValue();
        request.x = x.intValue();
        request.y = y.intValue();
        request.z = z.intValue();
        request.label = label;
        request.note = note;
        request.color = color;
        request.fromVersion = fromVersion.intValue();
        request.toVersion = toVersion.intValue();
        return ParsedBody.success(networkId.intValue(), request);
    }

    private static Integer integerField(JsonObject object, String name) {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            return null;
        }
        return parseInteger(primitive.getAsString());
    }

    private static String stringField(JsonObject object, String name) {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        return primitive.isString() ? primitive.getAsString() : null;
    }

    private static NanoHTTPD.Response checkOptionalQueryNetwork(Map<String, String> params, int bodyNetworkId) {
        if (params == null || !params.containsKey("network")) {
            return null;
        }
        Integer queryNetwork = parseInteger(params.get("network"));
        if (queryNetwork == null || !WorldMapPacketAuthorization.isValidNetworkId(queryNetwork.intValue())) {
            return invalid("invalid_network", "Invalid query 'network' parameter");
        }
        if (queryNetwork.intValue() != bodyNetworkId) {
            return invalid("network_mismatch", "Query network does not match body networkId");
        }
        return null;
    }

    private static Integer parseRequiredNetwork(Map<String, String> params) {
        Integer networkId = parseInteger(params == null ? null : params.get("network"));
        return networkId != null && WorldMapPacketAuthorization.isValidNetworkId(networkId.intValue()) ? networkId
            : null;
    }

    private static Integer parseInteger(String raw) {
        if (raw == null || raw.trim()
            .isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static NanoHTTPD.Response assertAccess(WebAuthSession auth, String ownerUuid, int networkId,
        boolean write) {
        NanoHTTPD.Response denied = WebAeNetworkAccess.assertCanAccess(auth, ownerUuid, networkId);
        if (denied != null) {
            return denied;
        }
        return write ? WebAeNetworkAccess.assertCanWrite(auth) : null;
    }

    private static NanoHTTPD.Response serviceError(WorldMapAnnotationResult<?> result) {
        return error(statusForServiceCode(result.code), result.code, result.message);
    }

    private static NanoHTTPD.Response.Status statusForServiceCode(String code) {
        if ("not_found".equals(code)) {
            return NanoHTTPD.Response.Status.NOT_FOUND;
        }
        if ("record_limit".equals(code)) {
            return NanoHTTPD.Response.Status.CONFLICT;
        }
        if ("invalid_owner".equals(code) || "invalid_network".equals(code)
            || "invalid_version".equals(code)
            || "invalid_id".equals(code)
            || "invalid_request".equals(code)
            || "invalid_actor".equals(code)
            || "server_field".equals(code)
            || "cross_owner".equals(code)
            || "cross_network".equals(code)
            || "immutable_field".equals(code)
            || "invalid_scope".equals(code)) {
            return NanoHTTPD.Response.Status.BAD_REQUEST;
        }
        return NanoHTTPD.Response.Status.INTERNAL_ERROR;
    }

    private static NanoHTTPD.Response invalid(String code, String message) {
        return error(NanoHTTPD.Response.Status.BAD_REQUEST, code, message);
    }

    private static NanoHTTPD.Response error(NanoHTTPD.Response.Status status, String code, String message) {
        return json(status, new ErrorEnvelope(code, message));
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, Object body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", GSON.toJson(body));
    }

    private static final class ParsedBody {

        final boolean success;
        final String code;
        final String message;
        final int networkId;
        final WorldMapAnnotationRequest request;

        private ParsedBody(boolean success, String code, String message, int networkId,
            WorldMapAnnotationRequest request) {
            this.success = success;
            this.code = code;
            this.message = message;
            this.networkId = networkId;
            this.request = request;
        }

        static ParsedBody success(int networkId, WorldMapAnnotationRequest request) {
            return new ParsedBody(true, "ok", "ok", networkId, request);
        }

        static ParsedBody failure(String code, String message) {
            return new ParsedBody(false, code, message, -1, null);
        }
    }

    private static final class ListEnvelope {

        final boolean success = true;
        final List<WorldMapAnnotationDto> annotations;

        ListEnvelope(List<WorldMapAnnotationDto> annotations) {
            this.annotations = annotations;
        }
    }

    private static final class AnnotationEnvelope {

        final boolean success = true;
        final WorldMapAnnotationDto annotation;

        AnnotationEnvelope(WorldMapAnnotationDto annotation) {
            this.annotation = annotation;
        }
    }

    private static final class ErrorEnvelope {

        final boolean success = false;
        final String code;
        final String error;
        final String message;

        ErrorEnvelope(String code, String message) {
            this.code = code;
            this.error = code;
            this.message = message;
        }
    }
}
