package com.imgood.textech.webae.api.handler;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.webae.access.WebAeNetworkAccess;
import com.imgood.textech.webae.access.WebAeNetworkKeys;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.diagnostics.NetworkHealthDiagnosticDto;
import com.imgood.textech.webae.diagnostics.NetworkHealthDiagnosticProvider;

import fi.iki.elonen.NanoHTTPD;

/**
 * Read-only network health endpoint.
 *
 * <p>
 * The handler deliberately performs only registry/key lookups and cache
 * reads. World, tile entity, and AE grid access belongs to the server-tick
 * sampler in {@link NetworkHealthDiagnosticProvider}.
 * </p>
 */
public final class NetworkHealthHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private NetworkHealthHandler() {}

    /**
     * Handle {@code GET /api/network/health?network=<id>}.
     *
     * <p>
     * Network ACL is checked twice on purpose: the first check preserves the
     * common runtime-id gate, while the second uses the stable coordinate key
     * used by ACL persistence. This keeps this endpoint safe if it is invoked
     * directly in a test or by a future router path.
     * </p>
     */
    public static NanoHTTPD.Response handle(Map<String, String> params, WebAuthSession session, String ownerUuid) {
        String raw = params == null ? null : params.get("network");
        if (raw == null || raw.trim()
            .isEmpty()) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing 'network' parameter\"}");
        }

        final int networkId;
        try {
            networkId = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Invalid 'network' parameter\"}");
        }
        if (networkId < 0) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Invalid 'network' parameter\"}");
        }

        NanoHTTPD.Response denied = WebAeNetworkAccess.assertCanAccess(session, ownerUuid, networkId);
        if (denied != null) {
            return denied;
        }

        // Resolve the stable key without touching a World and apply the ACL
        // again at the persistence identity boundary.
        String networkKey = WebAeNetworkKeys.fromNetworkId(ownerUuid, networkId);
        if (networkKey != null && !networkKey.isEmpty()) {
            denied = WebAeNetworkAccess.assertCanAccessKey(session, ownerUuid, networkKey);
            if (denied != null) {
                return denied;
            }
        }

        NetworkHealthDiagnosticDto dto = NetworkHealthDiagnosticProvider.instance()
            .get(ownerUuid, networkId);
        // A concurrently refreshed registry can briefly produce a DTO without
        // the key we resolved above. Preserve the request's owner/id boundary
        // while retaining the provider's explicit unknown status.
        if (dto == null || !equals(ownerUuid, dto.ownerUuid) || dto.networkId != networkId) {
            dto = NetworkHealthDiagnosticDto.unknown(ownerUuid, networkId, networkKey);
        } else if (dto.networkKey == null && networkKey != null) {
            dto.networkKey = networkKey;
        }
        return json(NanoHTTPD.Response.Status.OK, GSON.toJson(dto));
    }

    private static boolean equals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
