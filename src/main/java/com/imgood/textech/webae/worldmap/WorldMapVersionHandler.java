package com.imgood.textech.webae.worldmap;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.webae.access.WebAeNetworkAccess;
import com.imgood.textech.webae.auth.WebAuthSession;

import fi.iki.elonen.NanoHTTPD;

/** HTTP adapter for retained world-map snapshot versions and pure snapshot diffs. */
public final class WorldMapVersionHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();
    private static final Backend PRODUCTION_BACKEND = new Backend() {

        @Override
        public WorldMapSnapshotVersionsDto listVersions(String ownerUuid, int networkId) {
            return WorldMapSnapshotDiffService.listVersions(ownerUuid, networkId);
        }

        @Override
        public WorldMapSnapshotDiffDto diff(String ownerUuid, int networkId, Integer fromVersion, Integer toVersion,
            WorldMapSnapshotDiffOptions options) {
            return WorldMapSnapshotDiffService.diff(ownerUuid, networkId, fromVersion, toVersion, options);
        }
    };

    private WorldMapVersionHandler() {}

    /** Handles {@code GET /api/worldmap/versions?network=<id>}. */
    public static NanoHTTPD.Response handleVersions(Map<String, String> params, String effectiveOwner,
        WebAuthSession auth) {
        return handleVersions(params, effectiveOwner, auth, PRODUCTION_BACKEND);
    }

    /** Handles {@code GET /api/worldmap/diff?network=<id>}. */
    public static NanoHTTPD.Response handleDiff(Map<String, String> params, String effectiveOwner,
        WebAuthSession auth) {
        return handleDiff(params, effectiveOwner, auth, PRODUCTION_BACKEND);
    }

    static NanoHTTPD.Response handleVersions(Map<String, String> params, String effectiveOwner, WebAuthSession auth,
        Backend backend) {
        Integer networkId = parseNetwork(params);
        if (networkId == null) {
            return error(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "invalid_network",
                "Missing or invalid 'network' parameter");
        }
        NanoHTTPD.Response denied = WebAeNetworkAccess.assertCanAccess(auth, effectiveOwner, networkId.intValue());
        if (denied != null) {
            return denied;
        }

        WorldMapSnapshotVersionsDto result = backend.listVersions(effectiveOwner, networkId.intValue());
        if (result == null) {
            return error(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "internal_error",
                "World map version service returned no result");
        }
        return json(statusForVersions(result), result);
    }

    static NanoHTTPD.Response handleDiff(Map<String, String> params, String effectiveOwner, WebAuthSession auth,
        Backend backend) {
        Integer networkId = parseNetwork(params);
        if (networkId == null) {
            return error(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "invalid_network",
                "Missing or invalid 'network' parameter");
        }
        NanoHTTPD.Response denied = WebAeNetworkAccess.assertCanAccess(auth, effectiveOwner, networkId.intValue());
        if (denied != null) {
            return denied;
        }

        ParsedDiffRequest parsed = parseDiffRequest(params);
        if (parsed == null) {
            return error(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "invalid_parameter",
                "Invalid world map diff parameter");
        }
        WorldMapSnapshotDiffDto result = backend
            .diff(effectiveOwner, networkId.intValue(), parsed.fromVersion, parsed.toVersion, parsed.options);
        if (result == null) {
            return error(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "internal_error",
                "World map diff service returned no result");
        }
        return json(statusForDiff(result), result);
    }

    private static NanoHTTPD.Response.Status statusForVersions(WorldMapSnapshotVersionsDto result) {
        if (result.success || "ok".equals(result.status) || "unknown".equals(result.status)) {
            return NanoHTTPD.Response.Status.OK;
        }
        if ("invalid".equals(result.status)) {
            return NanoHTTPD.Response.Status.BAD_REQUEST;
        }
        if ("no_versions".equals(result.status)) {
            return NanoHTTPD.Response.Status.NOT_FOUND;
        }
        return NanoHTTPD.Response.Status.INTERNAL_ERROR;
    }

    private static NanoHTTPD.Response.Status statusForDiff(WorldMapSnapshotDiffDto result) {
        if (result.success || "ok".equals(result.code)
            || "unknown".equals(result.status)
            || "unknown_manifest".equals(result.code)) {
            return NanoHTTPD.Response.Status.OK;
        }
        if ("invalid".equals(result.code)) {
            return NanoHTTPD.Response.Status.BAD_REQUEST;
        }
        if ("same".equals(result.code) || "no_previous".equals(result.code)) {
            return NanoHTTPD.Response.Status.CONFLICT;
        }
        if ("no_versions".equals(result.code) || "not_retained".equals(result.code)) {
            return NanoHTTPD.Response.Status.NOT_FOUND;
        }
        return NanoHTTPD.Response.Status.INTERNAL_ERROR;
    }

    private static Integer parseNetwork(Map<String, String> params) {
        String raw = params == null ? null : params.get("network");
        Integer parsed = parseInteger(raw);
        return parsed != null && WorldMapPacketAuthorization.isValidNetworkId(parsed.intValue()) ? parsed : null;
    }

    private static ParsedDiffRequest parseDiffRequest(Map<String, String> params) {
        Integer from = parseOptionalInteger(params, "from");
        Integer to = parseOptionalInteger(params, "to");
        if ((has(params, "from") && !isValidVersion(from)) || (has(params, "to") && !isValidVersion(to))) {
            return null;
        }

        WorldMapSnapshotDiffOptions options = new WorldMapSnapshotDiffOptions();
        options.dimension = parseOptionalInteger(params, "dimension");
        options.minX = parseOptionalInteger(params, "minX");
        options.maxX = parseOptionalInteger(params, "maxX");
        options.minZ = parseOptionalInteger(params, "minZ");
        options.maxZ = parseOptionalInteger(params, "maxZ");
        if (!validOptionalInteger(params, "dimension", options.dimension)
            || !validOptionalInteger(params, "minX", options.minX)
            || !validOptionalInteger(params, "maxX", options.maxX)
            || !validOptionalInteger(params, "minZ", options.minZ)
            || !validOptionalInteger(params, "maxZ", options.maxZ)) {
            return null;
        }
        if (options.dimension != null
            && Math.abs((long) options.dimension.intValue()) > WorldMapPacketAuthorization.MAX_DIMENSION) {
            return null;
        }

        Boolean includeTiles = parseOptionalBoolean(params, "includeTiles");
        Boolean includeMarkers = parseOptionalBoolean(params, "includeMarkers");
        if ((has(params, "includeTiles") && includeTiles == null)
            || (has(params, "includeMarkers") && includeMarkers == null)) {
            return null;
        }
        options.includeTiles = includeTiles == null || includeTiles.booleanValue();
        options.includeMarkers = includeMarkers == null || includeMarkers.booleanValue();
        return new ParsedDiffRequest(from, to, options);
    }

    private static boolean isValidVersion(Integer value) {
        return value != null && WorldMapPacketAuthorization.isValidSnapshotVersion(value.intValue());
    }

    private static boolean validOptionalInteger(Map<String, String> params, String name, Integer value) {
        return !has(params, name) || value != null;
    }

    private static Integer parseOptionalInteger(Map<String, String> params, String name) {
        return has(params, name) ? parseInteger(params.get(name)) : null;
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

    private static Boolean parseOptionalBoolean(Map<String, String> params, String name) {
        if (!has(params, name)) {
            return null;
        }
        String raw = params.get(name);
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        if ("true".equalsIgnoreCase(normalized)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(normalized)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static boolean has(Map<String, String> params, String name) {
        return params != null && params.containsKey(name);
    }

    private static NanoHTTPD.Response error(NanoHTTPD.Response.Status status, String code, String message) {
        return json(status, new ErrorEnvelope(code, message));
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, Object body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", GSON.toJson(body));
    }

    interface Backend {

        WorldMapSnapshotVersionsDto listVersions(String ownerUuid, int networkId);

        WorldMapSnapshotDiffDto diff(String ownerUuid, int networkId, Integer fromVersion, Integer toVersion,
            WorldMapSnapshotDiffOptions options);
    }

    private static final class ParsedDiffRequest {

        final Integer fromVersion;
        final Integer toVersion;
        final WorldMapSnapshotDiffOptions options;

        ParsedDiffRequest(Integer fromVersion, Integer toVersion, WorldMapSnapshotDiffOptions options) {
            this.fromVersion = fromVersion;
            this.toVersion = toVersion;
            this.options = options;
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
